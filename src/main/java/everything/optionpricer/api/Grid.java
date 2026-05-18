package everything.optionpricer.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.AsianOption;
import everything.optionpricer.model.BarrierOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.LookbackOption;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.pricing.BlackScholesEngine;
import everything.optionpricer.pricing.LongstaffSchwartzEngine;
import everything.optionpricer.pricing.MonteCarloEngine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


/**
 * Grid pricing — one set of market parameters + an array of
 * {@code (strike, type)} contracts. For path-dependent options the
 * Monte Carlo paths are simulated once and shared across every
 * contract. The per-path "path statistic" (running average, min, max,
 * survival weight, …) is K-independent for fixed-other-parameters
 * grids, so the payoff per contract is an O(1) evaluation on the
 * shared stats — total work scales as O(paths) rather than
 * O(paths × strikes).
 *
 * European and American grids loop the per-contract pricer (each call
 * is already independent and cheap).
 *
 * @author lorenzobarbagelata
 */
final class Grid {

    private static final Gson GSON = new Gson();
    private static final int DEFAULT_SIMULATIONS = 100_000;

    private Grid() {}


    // ----- Request / response DTOs ----- //

    public record Contract(String type, double strike) {}

    public record EuropeanGridRequest(
            double spot, double rate, double volatility, double timeToExpiry,
            ApiServer.DividendsDto dividends,
            Contract[] contracts) {}

    public record AmericanGridRequest(
            double spot, double rate, double volatility, double timeToExpiry,
            int exerciseDates, Integer simulations,
            ApiServer.DividendsDto dividends,
            Contract[] contracts) {}

    public record PathGridRequest(
            double spot, double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring,
            // Asian-only
            Boolean arithmeticAverage,
            // Barrier-only
            Double barrier, Boolean upBarrier, Boolean inBarrier,
            // Lookback-only
            Boolean fixedStrike,
            Integer simulations, Long seed, Boolean useAntithetic,
            ApiServer.DividendsDto dividends,
            Contract[] contracts) {}

    public record GridResult(String type, double strike, double price) {}

    public record GridResponse(GridResult[] results) {}


    // ============================================================
    //  European — loop BS per contract
    // ============================================================

    static final ApiServer.SafeHandler european = ex -> {
        EuropeanGridRequest req = Binary.wantsBinary(ex)
                ? Binary.readEuropeanGrid(ex)
                : readJson(ex, EuropeanGridRequest.class);
        validateGrid(req.contracts);
        DividendSchedule divs = ApiServer.toSchedule(req.dividends);
        GridResult[] results = new GridResult[req.contracts.length];
        for(int i = 0; i < req.contracts.length; i++) {
            Contract c = req.contracts[i];
            EuropeanOption opt = EuropeanOption.of(parseType(c.type), c.strike, req.timeToExpiry);
            double p = BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            results[i] = new GridResult(c.type, c.strike, p);
        }
        sendGridResponse(ex, results);
    };


    // ============================================================
    //  American — loop LSM per contract (each call internally
    //  parallel; running them sequentially keeps the FJ pool from
    //  oversubscribing)
    // ============================================================

    static final ApiServer.SafeHandler american = ex -> {
        AmericanGridRequest req = Binary.wantsBinary(ex)
                ? Binary.readAmericanGrid(ex)
                : readJson(ex, AmericanGridRequest.class);
        validateGrid(req.contracts);
        DividendSchedule divs = ApiServer.toSchedule(req.dividends);
        int paths = req.simulations != null ? req.simulations : 50_000;
        GridResult[] results = new GridResult[req.contracts.length];
        for(int i = 0; i < req.contracts.length; i++) {
            Contract c = req.contracts[i];
            AmericanOption opt = new AmericanOption(c.strike, req.timeToExpiry,
                    parseType(c.type), req.exerciseDates);
            double p = LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, paths, divs).getPrice();
            results[i] = new GridResult(c.type, c.strike, p);
        }
        sendGridResponse(ex, results);
    };


    // ============================================================
    //  Asian — share paths, vary K
    //
    //  Asian arithmetic payoff = max(±(avg − K), 0). The average is
    //  K-independent, so we compute it once per simulated path and
    //  evaluate the payoff for every contract.
    //
    //  Asian geometric is the same with exp(meanOfLogs) as the
    //  "average".
    // ============================================================

    static final ApiServer.SafeHandler asian = ex -> {
        PathGridRequest req = Binary.wantsBinary(ex)
                ? Binary.readAsianGrid(ex)
                : readJson(ex, PathGridRequest.class);
        validateGrid(req.contracts);
        boolean arith = Boolean.TRUE.equals(req.arithmeticAverage);
        runPathGrid(ex, req, new PathStatGenerator() {
            @Override public PathStat[] simulate(int paths, double[] z1) {
                PathStat[] out = new PathStat[paths];
                for(int i = 0; i < paths; i++) out[i] = new PathStat();
                return out;
            }
            @Override public void update(PathStat s, double price, double logPrice) {
                if(arith) { s.sum += price; s.count++; }
                else      { s.lnSum += logPrice; s.count++; }
            }
            @Override public double payoff(PathStat s, Contract c) {
                double avg = arith ? (s.sum / s.count) : Math.exp(s.lnSum / s.count);
                int sign = "CALL".equalsIgnoreCase(c.type) ? 1 : -1;
                return Math.max(sign * (avg - c.strike), 0.0);
            }
        });
    };


    // ============================================================
    //  Barrier — fixed B / direction / in-out, vary K
    //  Path-survival weight is K-independent; vanilla payoff at
    //  expiry uses K.
    // ============================================================

    static final ApiServer.SafeHandler barrier = ex -> {
        PathGridRequest req = Binary.wantsBinary(ex)
                ? Binary.readBarrierGrid(ex)
                : readJson(ex, PathGridRequest.class);
        validateGrid(req.contracts);
        if(req.barrier == null || req.upBarrier == null || req.inBarrier == null) {
            throw ApiException.badRequest("barrier endpoint needs `barrier`, `upBarrier`, `inBarrier`");
        }
        double barrier = req.barrier;
        double logB = Math.log(barrier);
        boolean upB = req.upBarrier;
        boolean inB = req.inBarrier;
        boolean discrete = req.discreteMonitoring;

        runPathGrid(ex, req, new PathStatGenerator() {
            @Override public PathStat[] simulate(int paths, double[] z1) {
                PathStat[] out = new PathStat[paths];
                for(int i = 0; i < paths; i++) { out[i] = new PathStat(); out[i].logSurvival = 0; }
                return out;
            }
            @Override public void update(PathStat s, double price, double logPrice) {
                // Discrete monitoring: hit check on the observed point.
                if(discrete) {
                    if(!s.knockedOut) {
                        if(upB ? logPrice >= logB : logPrice <= logB) s.knockedOut = true;
                    }
                } else {
                    // Continuous (Brownian bridge) — accumulate survival.
                    if(s.count == 0) {
                        if(upB ? logPrice >= logB : logPrice <= logB) s.knockedOut = true;
                    } else if(!s.knockedOut) {
                        if(upB ? logPrice >= logB : logPrice <= logB) {
                            s.knockedOut = true;
                        } else {
                            double a = upB ? (logB - s.prevLogPrice) : (s.prevLogPrice - logB);
                            double b = upB ? (logB - logPrice)       : (logPrice       - logB);
                            double surv = 1.0 - Math.exp(-2.0 * a * b / s.sigmaSqDt);
                            if(surv <= 0.0) s.knockedOut = true;
                            else            s.logSurvival += Math.log(surv);
                        }
                    }
                }
                s.prevLogPrice = logPrice;
                s.lastLogPrice = logPrice;
                s.count++;
            }
            @Override public double payoff(PathStat s, Contract c) {
                double weight = discrete
                        ? (inB ? (s.knockedOut ? 1.0 : 0.0) : (s.knockedOut ? 0.0 : 1.0))
                        : (inB ? (s.knockedOut ? 1.0 : (1.0 - Math.exp(s.logSurvival)))
                               : (s.knockedOut ? 0.0 :  Math.exp(s.logSurvival)));
                if(weight <= 0.0) return 0.0;
                double last = Math.exp(s.lastLogPrice);
                int sign = "CALL".equalsIgnoreCase(c.type) ? 1 : -1;
                return weight * Math.max(sign * (last - c.strike), 0.0);
            }
        });
    };


    // ============================================================
    //  Lookback — fixed strike type, vary K
    // ============================================================

    static final ApiServer.SafeHandler lookback = ex -> {
        PathGridRequest req = Binary.wantsBinary(ex)
                ? Binary.readLookbackGrid(ex)
                : readJson(ex, PathGridRequest.class);
        validateGrid(req.contracts);
        if(req.fixedStrike == null) {
            throw ApiException.badRequest("lookback endpoint needs `fixedStrike`");
        }
        boolean fixed = req.fixedStrike;
        runPathGrid(ex, req, new PathStatGenerator() {
            @Override public PathStat[] simulate(int paths, double[] z1) {
                PathStat[] out = new PathStat[paths];
                for(int i = 0; i < paths; i++) {
                    out[i] = new PathStat();
                    out[i].logMin =  Double.POSITIVE_INFINITY;
                    out[i].logMax =  Double.NEGATIVE_INFINITY;
                }
                return out;
            }
            @Override public void update(PathStat s, double price, double logPrice) {
                if(logPrice < s.logMin) s.logMin = logPrice;
                if(logPrice > s.logMax) s.logMax = logPrice;
                s.lastLogPrice = logPrice;
            }
            @Override public double payoff(PathStat s, Contract c) {
                boolean call = "CALL".equalsIgnoreCase(c.type);
                if(fixed) {
                    return call ? Math.max(Math.exp(s.logMax) - c.strike, 0.0)
                                : Math.max(c.strike - Math.exp(s.logMin), 0.0);
                }
                double last = Math.exp(s.lastLogPrice);
                return call ? Math.max(last - Math.exp(s.logMin), 0.0)
                            : Math.max(Math.exp(s.logMax) - last, 0.0);
            }
        });
    };


    // ============================================================
    //  Shared path-grid driver
    // ============================================================

    private static void runPathGrid(HttpExchange ex, PathGridRequest req,
                                    PathStatGenerator gen) throws IOException {
        GridResult[] out = computePathGrid(req, gen);
        sendGridResponse(ex, out);
    }


    /** Body-only variant — used by both the JSON and binary entry points. */
    private static GridResult[] computePathGrid(PathGridRequest req, PathStatGenerator gen) {
        DividendSchedule divs = ApiServer.toSchedule(req.dividends);
        int sims = req.simulations != null ? req.simulations : DEFAULT_SIMULATIONS;
        boolean antithetic = req.useAntithetic == null || req.useAntithetic;
        boolean seeded = req.seed != null;

        int tS = req.timeSteps;
        double T = req.timeToExpiry;
        double q = divs.continuousYield();
        double dt = T / (tS - 1);
        double sqrtDt = Math.sqrt(dt);
        double drift = (req.rate - q - 0.5 * req.volatility * req.volatility) * dt;
        double diff = req.volatility * sqrtDt;
        double logSpot = Math.log(req.spot);
        double sigmaSqDt = req.volatility * req.volatility * dt;

        double[] stepDivs = divs.stepwiseAmounts(dt, tS);
        boolean hasDivs = divs.hasDiscrete();

        int totalPaths;
        if(antithetic) {
            int pairs = (sims + 1) / 2;
            totalPaths = pairs * 2;
        } else {
            totalPaths = sims;
        }

        PathStat[] stats = gen.simulate(totalPaths, null);
        // Seed sigmaSqDt for barrier continuous monitoring.
        for(int i = 0; i < totalPaths; i++) stats[i].sigmaSqDt = sigmaSqDt;

        long seedVal = seeded ? req.seed : 0L;
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        int unit = antithetic ? 2 : 1;
        int totalUnits = totalPaths / unit;
        int batchSize = (totalUnits + parallelism - 1) / parallelism;

        IntStream.range(0, parallelism).parallel().forEach(t -> {
            int start = t * batchSize;
            int end   = Math.min(totalUnits, start + batchSize);
            if(start >= end) return;
            simulateBatch(stats, antithetic, seeded, seedVal,
                          start, end, unit,
                          req.spot, logSpot, drift, diff, tS,
                          stepDivs, hasDivs, gen);
        });

        // Discount the average payoff for each contract.
        double discount = Math.exp(-req.rate * T);
        GridResult[] out = new GridResult[req.contracts.length];
        for(int ci = 0; ci < req.contracts.length; ci++) {
            Contract c = req.contracts[ci];
            double sum = 0.0;
            for(PathStat s : stats) sum += gen.payoff(s, c);
            double price = discount * (sum / totalPaths);
            out[ci] = new GridResult(c.type, c.strike, price);
        }
        return out;
    }


    /**
     * Emit results as either JSON or binary, depending on the request's
     * Content-Type. Used by every {@code /v1/grid/*} handler.
     */
    static void sendGridResponse(HttpExchange ex, GridResult[] results) throws IOException {
        if(Binary.wantsBinary(ex)) {
            Binary.writeGridResponse(ex, results);
        } else {
            sendJson(ex, 200, new GridResponse(results));
        }
    }


    private static void simulateBatch(PathStat[] stats, boolean antithetic,
                                      boolean seeded, long seed,
                                      int unitStart, int unitEnd, int unit,
                                      double spot, double logSpot,
                                      double drift, double diff, int tS,
                                      double[] stepDivs, boolean hasDivs,
                                      PathStatGenerator gen) {
        ThreadLocalRandom tlr = seeded ? null : ThreadLocalRandom.current();
        for(int u = unitStart; u < unitEnd; u++) {
            SplittableRandom srng = seeded
                    ? new SplittableRandom(MonteCarloEngine.mixSeed(seed, u))
                    : null;
            int posIdx = u * unit;
            int negIdx = antithetic ? posIdx + 1 : -1;
            PathStat sPos = stats[posIdx];
            PathStat sNeg = antithetic ? stats[negIdx] : null;
            // Seed the initial observation.
            gen.update(sPos, spot, logSpot);
            if(sNeg != null) gen.update(sNeg, spot, logSpot);
            double logPos = logSpot, logNeg = logSpot;

            for(int i = 1; i < tS; i++) {
                double z = seeded ? srng.nextGaussian() : tlr.nextGaussian();
                double dz = diff * z;
                logPos += drift + dz;
                if(antithetic) logNeg += drift - dz;

                double pricePos = Math.exp(logPos);
                double priceNeg = antithetic ? Math.exp(logNeg) : 0;
                if(hasDivs && stepDivs[i] != 0.0) {
                    pricePos -= stepDivs[i]; if(pricePos < 1e-9) pricePos = 1e-9;
                    logPos = Math.log(pricePos);
                    if(antithetic) {
                        priceNeg -= stepDivs[i]; if(priceNeg < 1e-9) priceNeg = 1e-9;
                        logNeg = Math.log(priceNeg);
                    }
                }
                gen.update(sPos, pricePos, logPos);
                if(sNeg != null) gen.update(sNeg, priceNeg, logNeg);
            }
        }
    }


    /** Mutable per-path stats. Different options use different subsets. */
    private static final class PathStat {
        // Asian
        double sum = 0;
        double lnSum = 0;
        int    count = 0;
        // Barrier
        boolean knockedOut = false;
        double  logSurvival = 0;
        double  prevLogPrice = 0;
        double  sigmaSqDt = 0;
        // Lookback
        double logMin = 0;
        double logMax = 0;
        // Common
        double lastLogPrice = 0;
    }


    /** Plug-in per option type. */
    private interface PathStatGenerator {
        PathStat[] simulate(int paths, double[] z1);  // pre-allocate
        void update(PathStat s, double price, double logPrice);
        double payoff(PathStat s, Contract c);
    }


    // ============================================================
    //  Helpers
    // ============================================================

    private static void validateGrid(Contract[] contracts) {
        if(contracts == null || contracts.length == 0)
            throw ApiException.badField("contracts", "must be a non-empty array");
        for(int i = 0; i < contracts.length; i++) {
            Contract c = contracts[i];
            if(c.strike <= 0) throw ApiException.badField("contracts[" + i + "].strike", "must be positive");
            if(c.type == null) throw ApiException.badField("contracts[" + i + "].type", "missing");
        }
    }


    private static OptionType parseType(String t) {
        try { return OptionType.valueOf(t.trim().toUpperCase()); }
        catch(Exception e) { throw ApiException.badField("contracts[].type", "must be CALL or PUT"); }
    }


    private static <T> T readJson(HttpExchange ex, Class<T> type) throws IOException {
        try (Reader r = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(r, type);
            if(parsed == null) throw ApiException.badRequest("empty request body");
            return parsed;
        } catch(JsonSyntaxException e) {
            throw ApiException.malformedJson(e.getMessage());
        }
    }


    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}

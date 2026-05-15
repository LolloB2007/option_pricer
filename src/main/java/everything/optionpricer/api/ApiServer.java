package everything.optionpricer.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.AsianOption;
import everything.optionpricer.model.BarrierOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.HestonParams;
import everything.optionpricer.model.LookbackOption;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.BinomialEngine;
import everything.optionpricer.pricing.BlackScholesEngine;
import everything.optionpricer.pricing.FiniteDifferenceEngine;
import everything.optionpricer.pricing.Greeks;
import everything.optionpricer.pricing.GreeksCalculator;
import everything.optionpricer.pricing.HestonEngine;
import everything.optionpricer.pricing.ImpliedVolatility;
import everything.optionpricer.pricing.LongstaffSchwartzEngine;
import everything.optionpricer.pricing.MonteCarloEngine;
import everything.optionpricer.pricing.MultiModelPrice;
import everything.optionpricer.pricing.PricingModel;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;


/**
 * HTTP/JSON pricing API.
 *
 * Endpoints
 *   GET  /health
 *   POST /price/european
 *   POST /price/asian
 *   POST /price/barrier
 *   POST /price/lookback
 *   POST /price/american
 *
 * Every endpoint emits permissive CORS headers and handles {@code OPTIONS}
 * preflight, so the API is callable from a browser context.
 *
 * Backed by {@code com.sun.net.httpserver.HttpServer} (no servlet container
 * needed) and Gson for JSON. The handlers map directly onto the existing
 * pricing engines — they are stateless and thread-safe, so requests are
 * served on a fixed thread pool sized to the available cores.
 *
 * @author lorenzobarbagelata
 */
public final class ApiServer {

    private static final Gson GSON = new Gson();

    private ApiServer() {}


    // ===================================================================
    //  Request / response DTOs (records — Gson 2.10+ deserialises directly)
    // ===================================================================

    public record EuropeanRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            DividendsDto dividends, String model, HestonParamsDto heston) {}

    public record AsianRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring, boolean arithmeticAverage,
            Integer simulations, DividendsDto dividends) {}

    public record BarrierRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring,
            double barrier, boolean upBarrier, boolean inBarrier,
            Integer simulations, DividendsDto dividends) {}

    public record LookbackRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring, boolean fixedStrike,
            Integer simulations, DividendsDto dividends) {}

    public record AmericanRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int exerciseDates, Integer simulations, DividendsDto dividends,
            String model) {}

    public record HestonParamsDto(double v0, double kappa, double theta, double xi, double rho) {}

    public record ImpliedVolRequest(
            String type, double spot, double strike,
            double rate, double timeToExpiry, double marketPrice,
            DividendsDto dividends) {}

    public record ImpliedVolAsianRequest(
            String type, double spot, double strike,
            double rate, double timeToExpiry, double marketPrice,
            int timeSteps, boolean discreteMonitoring, boolean arithmeticAverage,
            Integer simulations, DividendsDto dividends) {}

    public record ImpliedVolBarrierRequest(
            String type, double spot, double strike,
            double rate, double timeToExpiry, double marketPrice,
            int timeSteps, boolean discreteMonitoring,
            double barrier, boolean upBarrier, boolean inBarrier,
            Integer simulations, DividendsDto dividends) {}

    public record ImpliedVolLookbackRequest(
            String type, double spot, double strike,
            double rate, double timeToExpiry, double marketPrice,
            int timeSteps, boolean discreteMonitoring, boolean fixedStrike,
            Integer simulations, DividendsDto dividends) {}

    public record ImpliedVolAmericanRequest(
            String type, double spot, double strike,
            double rate, double timeToExpiry, double marketPrice,
            int exerciseDates, Integer simulations, DividendsDto dividends) {}

    /**
     * Dividend specification — both fields optional. Continuous yield in
     * decimal (3% = 0.03). Discrete dividends as a list of {time, amount}.
     */
    public record DividendsDto(Double continuousYield, DiscreteDividend[] discrete) {}

    public record DiscreteDividend(double time, double amount) {}

    public record PriceResponse(double price)      {}
    public record ModelPriceResponse(double price, String model, java.util.Map<String, Double> contributions) {}
    public record ErrorResponse(String error)      {}
    public record HealthResponse(String status)    {}
    public record PriceAndGreeksResponse(double price, Greeks greeks) {}
    public record ImpliedVolResponse(double impliedVolatility, double price) {}


    // ===================================================================
    //  Bootstrap
    // ===================================================================

    /**
     * Start the API server on {@code port} and return the running instance.
     * The server runs in background threads; the caller stays alive by
     * keeping the JVM alive (e.g. through {@link Main}).
     */
    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health",          wrap(ApiServer::health,           false));
        server.createContext("/price/european",  wrap(ApiServer::priceEuropean,    true));
        server.createContext("/price/asian",     wrap(ApiServer::priceAsian,       true));
        server.createContext("/price/barrier",   wrap(ApiServer::priceBarrier,     true));
        server.createContext("/price/lookback",  wrap(ApiServer::priceLookback,    true));
        server.createContext("/price/american",  wrap(ApiServer::priceAmerican,    true));
        server.createContext("/greeks/european", wrap(ApiServer::greeksEuropean,   true));
        server.createContext("/greeks/asian",    wrap(ApiServer::greeksAsian,      true));
        server.createContext("/greeks/barrier",  wrap(ApiServer::greeksBarrier,    true));
        server.createContext("/greeks/lookback", wrap(ApiServer::greeksLookback,   true));
        server.createContext("/greeks/american", wrap(ApiServer::greeksAmerican,   true));
        server.createContext("/implied-vol/european", wrap(ApiServer::impliedVolEuropean, true));
        server.createContext("/implied-vol/asian",    wrap(ApiServer::impliedVolAsian,    true));
        server.createContext("/implied-vol/barrier",  wrap(ApiServer::impliedVolBarrier,  true));
        server.createContext("/implied-vol/lookback", wrap(ApiServer::impliedVolLookback, true));
        server.createContext("/implied-vol/american", wrap(ApiServer::impliedVolAmerican, true));

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        server.setExecutor(Executors.newFixedThreadPool(threads));
        server.start();
        return server;
    }


    // ===================================================================
    //  Handler plumbing — CORS, method-check, error mapping
    // ===================================================================

    @FunctionalInterface
    private interface SafeHandler {
        void handle(HttpExchange ex) throws IOException;
    }


    /**
     * Wrap a {@link SafeHandler} with CORS, method validation, and error
     * mapping. If {@code requirePost} is true the handler only accepts POST
     * (plus OPTIONS preflight).
     */
    private static HttpHandler wrap(SafeHandler inner, boolean requirePost) {
        return ex -> {
            addCorsHeaders(ex);
            String method = ex.getRequestMethod();

            if("OPTIONS".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            if(requirePost && !"POST".equalsIgnoreCase(method)) {
                sendJson(ex, 405, new ErrorResponse("method not allowed; use POST"));
                return;
            }

            try {
                inner.handle(ex);
            } catch(IllegalArgumentException iae) {
                sendJson(ex, 400, new ErrorResponse(iae.getMessage() == null ? "bad request" : iae.getMessage()));
            } catch(Exception e) {
                sendJson(ex, 500, new ErrorResponse(e.getClass().getSimpleName() + ": " + e.getMessage()));
            } finally {
                ex.close();
            }
        };
    }


    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }


    private static <T> T readJson(HttpExchange ex, Class<T> type) throws IOException {
        try (Reader r = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(r, type);
            if(parsed == null) throw new IllegalArgumentException("empty request body");
            return parsed;
        } catch(JsonSyntaxException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage());
        }
    }


    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }


    private static OptionType parseType(String t) {
        if(t == null) throw new IllegalArgumentException("'type' required (CALL or PUT)");
        try {
            return OptionType.valueOf(t.trim().toUpperCase());
        } catch(IllegalArgumentException e) {
            throw new IllegalArgumentException("'type' must be CALL or PUT");
        }
    }


    private static PricingModel parseModel(String model, PricingModel defaultModel) {
        if(model == null || model.isBlank()) return defaultModel;
        try {
            return PricingModel.valueOf(model.trim().toUpperCase());
        } catch(IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown model '" + model + "' — try one of "
                    + java.util.Arrays.toString(PricingModel.values()));
        }
    }


    private static HestonParams toHestonParams(HestonParamsDto h) {
        if(h == null) throw new IllegalArgumentException("Heston params required when model=HESTON");
        return new HestonParams(h.v0(), h.kappa(), h.theta(), h.xi(), h.rho());
    }


    private static java.util.Map<String, Double> stringKeyed(java.util.Map<PricingModel, Double> in) {
        java.util.Map<String, Double> out = new java.util.LinkedHashMap<>();
        for(var e : in.entrySet()) out.put(e.getKey().name(), e.getValue());
        return out;
    }


    /** Convert the JSON dividends DTO to the engine's {@link DividendSchedule}. */
    private static DividendSchedule toSchedule(DividendsDto d) {
        if(d == null) return DividendSchedule.NONE;
        double q = d.continuousYield() == null ? 0.0 : d.continuousYield();
        DiscreteDividend[] disc = d.discrete();
        if(disc == null || disc.length == 0) return DividendSchedule.continuous(q);
        double[] times = new double[disc.length];
        double[] amts  = new double[disc.length];
        for(int i = 0; i < disc.length; i++) {
            times[i] = disc[i].time();
            amts[i]  = disc[i].amount();
        }
        return new DividendSchedule(q, times, amts);
    }


    // ===================================================================
    //  Endpoint handlers
    // ===================================================================

    private static void health(HttpExchange ex) throws IOException {
        sendJson(ex, 200, new HealthResponse("ok"));
    }


    private static void priceEuropean(HttpExchange ex) throws IOException {
        EuropeanRequest req = readJson(ex, EuropeanRequest.class);
        EuropeanOption opt = EuropeanOption.of(parseType(req.type), req.strike, req.timeToExpiry);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.BS);

        switch(model) {
            case BS -> {
                double p = BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                sendJson(ex, 200, new ModelPriceResponse(p, "BS", null));
            }
            case BINOMIAL -> {
                double p = BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                sendJson(ex, 200, new ModelPriceResponse(p, "BINOMIAL", null));
            }
            case PDE -> {
                double p = FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                sendJson(ex, 200, new ModelPriceResponse(p, "PDE", null));
            }
            case HESTON -> {
                HestonParams h = toHestonParams(req.heston);
                double p = HestonEngine.price(opt, req.spot, req.rate, h, divs).getPrice();
                sendJson(ex, 200, new ModelPriceResponse(p, "HESTON", null));
            }
            case AUTO -> {
                MultiModelPrice.Builder b = MultiModelPrice.builder()
                        .add(PricingModel.BS,       BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                        .add(PricingModel.BINOMIAL, BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                        .add(PricingModel.PDE,      FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice());
                if(req.heston != null) {
                    b.add(PricingModel.HESTON, HestonEngine.price(opt, req.spot, req.rate, toHestonParams(req.heston), divs).getPrice());
                }
                MultiModelPrice.Aggregated agg = b.build();
                sendJson(ex, 200, new ModelPriceResponse(agg.price(), "AUTO", stringKeyed(agg.contributions())));
            }
            default -> throw new IllegalArgumentException("model " + model + " is not applicable to European pricing");
        }
    }


    private static void priceAsian(HttpExchange ex) throws IOException {
        AsianRequest req = readJson(ex, AsianRequest.class);
        AsianOption opt = new AsianOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.arithmeticAverage);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingResult res = (req.simulations != null)
                ? MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs)
                : MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, divs);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }


    private static void priceBarrier(HttpExchange ex) throws IOException {
        BarrierRequest req = readJson(ex, BarrierRequest.class);
        BarrierOption opt = new BarrierOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring,
                req.barrier, req.upBarrier, req.inBarrier);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingResult res = (req.simulations != null)
                ? MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs)
                : MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, divs);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }


    private static void priceLookback(HttpExchange ex) throws IOException {
        LookbackRequest req = readJson(ex, LookbackRequest.class);
        LookbackOption opt = new LookbackOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.fixedStrike);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingResult res = (req.simulations != null)
                ? MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs)
                : MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, divs);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }


    private static void priceAmerican(HttpExchange ex) throws IOException {
        AmericanRequest req = readJson(ex, AmericanRequest.class);
        AmericanOption opt = new AmericanOption(
                req.strike, req.timeToExpiry, parseType(req.type), req.exerciseDates);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.LSM);

        switch(model) {
            case LSM -> {
                double p = (req.simulations != null)
                        ? LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs).getPrice()
                        : LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                sendJson(ex, 200, new ModelPriceResponse(p, "LSM", null));
            }
            case BINOMIAL -> {
                double p = BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                sendJson(ex, 200, new ModelPriceResponse(p, "BINOMIAL", null));
            }
            case PDE -> {
                double p = FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                sendJson(ex, 200, new ModelPriceResponse(p, "PDE", null));
            }
            case AUTO -> {
                double lsm = (req.simulations != null)
                        ? LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs).getPrice()
                        : LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                MultiModelPrice.Aggregated agg = MultiModelPrice.builder()
                        .add(PricingModel.LSM,      lsm)
                        .add(PricingModel.BINOMIAL, BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                        .add(PricingModel.PDE,      FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                        .build();
                sendJson(ex, 200, new ModelPriceResponse(agg.price(), "AUTO", stringKeyed(agg.contributions())));
            }
            default -> throw new IllegalArgumentException("model " + model + " is not applicable to American pricing");
        }
    }


    // ===================================================================
    //  /greeks/* — returns both the price and the Greeks
    // ===================================================================

    private static void greeksEuropean(HttpExchange ex) throws IOException {
        EuropeanRequest req = readJson(ex, EuropeanRequest.class);
        EuropeanOption opt = EuropeanOption.of(parseType(req.type), req.strike, req.timeToExpiry);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingResult res = BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs);
        Greeks g = GreeksCalculator.compute(opt, req.spot, req.rate, req.volatility, divs);
        sendJson(ex, 200, new PriceAndGreeksResponse(res.getPrice(), g));
    }

    private static void greeksAsian(HttpExchange ex) throws IOException {
        AsianRequest req = readJson(ex, AsianRequest.class);
        AsianOption opt = new AsianOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.arithmeticAverage);
        priceAndGreeksMc(ex, opt, req.spot, req.rate, req.volatility, req.simulations, toSchedule(req.dividends));
    }

    private static void greeksBarrier(HttpExchange ex) throws IOException {
        BarrierRequest req = readJson(ex, BarrierRequest.class);
        BarrierOption opt = new BarrierOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring,
                req.barrier, req.upBarrier, req.inBarrier);
        priceAndGreeksMc(ex, opt, req.spot, req.rate, req.volatility, req.simulations, toSchedule(req.dividends));
    }

    private static void greeksLookback(HttpExchange ex) throws IOException {
        LookbackRequest req = readJson(ex, LookbackRequest.class);
        LookbackOption opt = new LookbackOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.fixedStrike);
        priceAndGreeksMc(ex, opt, req.spot, req.rate, req.volatility, req.simulations, toSchedule(req.dividends));
    }

    private static void greeksAmerican(HttpExchange ex) throws IOException {
        AmericanRequest req = readJson(ex, AmericanRequest.class);
        AmericanOption opt = new AmericanOption(
                req.strike, req.timeToExpiry, parseType(req.type), req.exerciseDates);
        DividendSchedule divs = toSchedule(req.dividends);
        double price = (req.simulations != null)
                ? LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs).getPrice()
                : LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
        Greeks g = (req.simulations != null)
                ? GreeksCalculator.compute(opt, req.spot, req.rate, req.volatility, req.simulations, divs)
                : GreeksCalculator.compute(opt, req.spot, req.rate, req.volatility, divs);
        sendJson(ex, 200, new PriceAndGreeksResponse(price, g));
    }


    // ===================================================================
    //  /implied-vol/european
    // ===================================================================

    private static void impliedVolEuropean(HttpExchange ex) throws IOException {
        ImpliedVolRequest req = readJson(ex, ImpliedVolRequest.class);
        EuropeanOption opt = EuropeanOption.of(parseType(req.type), req.strike, req.timeToExpiry);
        DividendSchedule divs = toSchedule(req.dividends);
        double iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs);
        double pAt = BlackScholesEngine.price(opt, req.spot, req.rate, iv, divs).getPrice();
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt));
    }


    private static void impliedVolAsian(HttpExchange ex) throws IOException {
        ImpliedVolAsianRequest req = readJson(ex, ImpliedVolAsianRequest.class);
        AsianOption opt = new AsianOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.arithmeticAverage);
        DividendSchedule divs = toSchedule(req.dividends);
        int sims = req.simulations != null ? req.simulations : ImpliedVolatility.DEFAULT_MC_SIMS;
        double iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, sims);
        double pAt = MonteCarloEngine.price(opt, req.spot, req.rate, iv, sims, divs).getPrice();
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt));
    }


    private static void impliedVolBarrier(HttpExchange ex) throws IOException {
        ImpliedVolBarrierRequest req = readJson(ex, ImpliedVolBarrierRequest.class);
        BarrierOption opt = new BarrierOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring,
                req.barrier, req.upBarrier, req.inBarrier);
        DividendSchedule divs = toSchedule(req.dividends);
        int sims = req.simulations != null ? req.simulations : ImpliedVolatility.DEFAULT_MC_SIMS;
        double iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, sims);
        double pAt = MonteCarloEngine.price(opt, req.spot, req.rate, iv, sims, divs).getPrice();
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt));
    }


    private static void impliedVolLookback(HttpExchange ex) throws IOException {
        ImpliedVolLookbackRequest req = readJson(ex, ImpliedVolLookbackRequest.class);
        LookbackOption opt = new LookbackOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.fixedStrike);
        DividendSchedule divs = toSchedule(req.dividends);
        int sims = req.simulations != null ? req.simulations : ImpliedVolatility.DEFAULT_MC_SIMS;
        double iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, sims);
        double pAt = MonteCarloEngine.price(opt, req.spot, req.rate, iv, sims, divs).getPrice();
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt));
    }


    private static void impliedVolAmerican(HttpExchange ex) throws IOException {
        ImpliedVolAmericanRequest req = readJson(ex, ImpliedVolAmericanRequest.class);
        AmericanOption opt = new AmericanOption(
                req.strike, req.timeToExpiry, parseType(req.type), req.exerciseDates);
        DividendSchedule divs = toSchedule(req.dividends);
        int paths = req.simulations != null ? req.simulations : ImpliedVolatility.DEFAULT_LSM_PATHS;
        double iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, paths);
        double pAt = LongstaffSchwartzEngine.price(opt, req.spot, req.rate, iv, paths, divs).getPrice();
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt));
    }


    private static void priceAndGreeksMc(HttpExchange ex,
                                         everything.optionpricer.model.PathDependentOption opt,
                                         double spot, double r, double sigma, Integer simulations,
                                         DividendSchedule dividends) throws IOException {
        double price = (simulations != null)
                ? MonteCarloEngine.price(opt, spot, r, sigma, simulations, dividends).getPrice()
                : MonteCarloEngine.price(opt, spot, r, sigma, dividends).getPrice();
        Greeks g = (simulations != null)
                ? GreeksCalculator.compute(opt, spot, r, sigma, simulations, dividends)
                : GreeksCalculator.compute(opt, spot, r, sigma, dividends);
        sendJson(ex, 200, new PriceAndGreeksResponse(price, g));
    }
}

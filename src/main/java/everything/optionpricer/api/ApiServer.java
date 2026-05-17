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
import everything.optionpricer.pricing.HestonMonteCarloEngine;
import everything.optionpricer.pricing.ImpliedVolatility;
import everything.optionpricer.pricing.LongstaffSchwartzEngine;
import everything.optionpricer.pricing.MonteCarloEngine;
import everything.optionpricer.pricing.MultiModelPrice;
import everything.optionpricer.pricing.PricingModel;
import everything.optionpricer.pricing.VolatilitySurface;
import java.util.List;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


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

    /**
     * Configured bearer token. {@code null} → no auth (every endpoint open).
     * When non-null, every endpoint except {@code /health} and {@code OPTIONS}
     * preflight requires {@code Authorization: Bearer <token>}.
     */
    private static volatile String apiToken;

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
            Integer simulations, DividendsDto dividends,
            String model, HestonParamsDto heston, Long seed) {}

    public record BarrierRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring,
            double barrier, boolean upBarrier, boolean inBarrier,
            Integer simulations, DividendsDto dividends,
            String model, HestonParamsDto heston, Long seed) {}

    public record LookbackRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring, boolean fixedStrike,
            Integer simulations, DividendsDto dividends,
            String model, HestonParamsDto heston, Long seed) {}

    public record AmericanRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int exerciseDates, Integer simulations, DividendsDto dividends,
            String model) {}

    public record HestonParamsDto(double v0, double kappa, double theta, double xi, double rho) {}

    public record ImpliedVolRequest(
            String type, double spot, double strike,
            double rate, double timeToExpiry, double marketPrice,
            DividendsDto dividends, String model, HestonParamsDto heston) {}

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
            int exerciseDates, Integer simulations, DividendsDto dividends,
            String model) {}

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
    public record ImpliedVolResponse(double impliedVolatility, double price, int iterations) {}

    public record VolSurfaceFitRequest(
            double spot, double rate, DividendsDto dividends,
            QuoteDto[] quotes) {}

    public record QuoteDto(double strike, double timeToExpiry, String type, double marketPrice) {}

    public record VolSurfacePointDto(double strike, double timeToExpiry, double impliedVolatility) {}

    public record VolSurfaceFailureDto(QuoteDto quote, String reason) {}

    public record VolSurfaceFitResponse(VolSurfacePointDto[] points, VolSurfaceFailureDto[] failures) {}

    // ----- Spread (multi-leg European) ----- //
    public record SpreadLeg(String type, double strike, String side, double qty) {}

    public record SpreadRequest(
            double spot, double rate, double volatility, double timeToExpiry,
            DividendsDto dividends,
            SpreadLeg[] legs) {}

    public record SpreadLegResult(String type, double strike, String side, double qty,
                                  double price, Greeks greeks) {}

    public record SpreadResponse(double netPrice, Greeks netGreeks, SpreadLegResult[] legs) {}


    // ===================================================================
    //  Bootstrap
    // ===================================================================

    /**
     * Start the API server on {@code port} with no authentication. Every
     * endpoint is open. Intended for localhost / development.
     */
    public static HttpServer start(int port) throws IOException {
        return start(port, null);
    }


    /**
     * Start the API server on {@code port}. If {@code authToken} is
     * non-null and non-empty, every endpoint except {@code /health} and
     * {@code OPTIONS} preflight requires the header
     * {@code Authorization: Bearer <authToken>}.
     */
    public static HttpServer start(int port, String authToken) throws IOException {
        apiToken = (authToken == null || authToken.isBlank()) ? null : authToken;
        return startInternal(port);
    }


    /** Set once at server start; flipped to true when {@code shutdown()} runs. */
    private static volatile HttpServer        runningServer;
    private static volatile ExecutorService   serverExecutor;
    private static final AtomicBoolean        shuttingDown = new AtomicBoolean(false);
    /** Server-side timestamp at which the warmup pricer call has completed. */
    private static volatile boolean           ready = false;


    private static HttpServer startInternal(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // ----- Legacy (un-versioned) routes — keep current behaviour ----- //
        register(server, "/health",          ApiServer::health,           false, /*v1*/ false);
        register(server, "/price/european",  ApiServer::priceEuropean,    true,  false);
        register(server, "/price/asian",     ApiServer::priceAsian,       true,  false);
        register(server, "/price/barrier",   ApiServer::priceBarrier,     true,  false);
        register(server, "/price/lookback",  ApiServer::priceLookback,    true,  false);
        register(server, "/price/american",  ApiServer::priceAmerican,    true,  false);
        register(server, "/greeks/european", ApiServer::greeksEuropean,   true,  false);
        register(server, "/greeks/asian",    ApiServer::greeksAsian,      true,  false);
        register(server, "/greeks/barrier",  ApiServer::greeksBarrier,    true,  false);
        register(server, "/greeks/lookback", ApiServer::greeksLookback,   true,  false);
        register(server, "/greeks/american", ApiServer::greeksAmerican,   true,  false);
        register(server, "/vol-surface/fit", ApiServer::volSurfaceFit,    true,  false);
        register(server, "/implied-vol/european", ApiServer::impliedVolEuropean, true, false);
        register(server, "/implied-vol/asian",    ApiServer::impliedVolAsian,    true, false);
        register(server, "/implied-vol/barrier",  ApiServer::impliedVolBarrier,  true, false);
        register(server, "/implied-vol/lookback", ApiServer::impliedVolLookback, true, false);
        register(server, "/implied-vol/american", ApiServer::impliedVolAmerican, true, false);

        // ----- /v1/ routes — same handlers, structured error shape ----- //
        register(server, "/v1/health",            ApiServer::health,             false, true);
        register(server, "/v1/ready",             ApiServer::ready,              false, true);
        register(server, "/v1/price/european",    ApiServer::priceEuropean,      true,  true);
        register(server, "/v1/price/asian",       ApiServer::priceAsian,         true,  true);
        register(server, "/v1/price/barrier",     ApiServer::priceBarrier,       true,  true);
        register(server, "/v1/price/lookback",    ApiServer::priceLookback,      true,  true);
        register(server, "/v1/price/american",    ApiServer::priceAmerican,      true,  true);
        register(server, "/v1/greeks/european",   ApiServer::greeksEuropean,     true,  true);
        register(server, "/v1/greeks/asian",      ApiServer::greeksAsian,        true,  true);
        register(server, "/v1/greeks/barrier",    ApiServer::greeksBarrier,      true,  true);
        register(server, "/v1/greeks/lookback",   ApiServer::greeksLookback,     true,  true);
        register(server, "/v1/greeks/american",   ApiServer::greeksAmerican,     true,  true);
        register(server, "/v1/vol-surface/fit",   ApiServer::volSurfaceFit,      true,  true);
        register(server, "/v1/implied-vol/european", ApiServer::impliedVolEuropean, true, true);
        register(server, "/v1/implied-vol/asian",    ApiServer::impliedVolAsian,    true, true);
        register(server, "/v1/implied-vol/barrier",  ApiServer::impliedVolBarrier,  true, true);
        register(server, "/v1/implied-vol/lookback", ApiServer::impliedVolLookback, true, true);
        register(server, "/v1/implied-vol/american", ApiServer::impliedVolAmerican, true, true);

        // ----- Bot-oriented v1-only endpoints ----- //
        register(server, "/v1/batch/price/european",  Batch.priceEuropeanBatch,  true, true);
        register(server, "/v1/batch/price/asian",     Batch.priceAsianBatch,     true, true);
        register(server, "/v1/batch/price/barrier",   Batch.priceBarrierBatch,   true, true);
        register(server, "/v1/batch/price/lookback",  Batch.priceLookbackBatch,  true, true);
        register(server, "/v1/batch/price/american",  Batch.priceAmericanBatch,  true, true);
        register(server, "/v1/batch/greeks/european", Batch.greeksEuropeanBatch, true, true);
        register(server, "/v1/batch/greeks/american", Batch.greeksAmericanBatch, true, true);

        register(server, "/v1/grid/european", Grid.european, true, true);
        register(server, "/v1/grid/american", Grid.american, true, true);
        register(server, "/v1/grid/asian",    Grid.asian,    true, true);
        register(server, "/v1/grid/barrier",  Grid.barrier,  true, true);
        register(server, "/v1/grid/lookback", Grid.lookback, true, true);

        register(server, "/v1/price/spread",  ApiServer::priceSpread, true, true);

        // ----- Observability (no auth, no /v1 — operational endpoints) ----- //
        server.createContext("/metrics", ex -> {
            addCorsHeaders(ex);
            if(!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            byte[] body = Metrics.exposition().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        serverExecutor = Executors.newFixedThreadPool(threads);
        server.setExecutor(serverExecutor);
        server.start();
        runningServer = server;

        // Warm up the engines so /v1/ready can flip true before traffic.
        // Tiny BS call exercises the JIT path. MC engines warm lazily on
        // first real request — acceptable.
        try {
            BlackScholesEngine.price(EuropeanOption.call(100, 1.0), 100, 0.05, 0.20);
            ready = true;
        } catch(Exception ignored) {
            ready = true;
        }

        // Graceful shutdown on SIGTERM / Ctrl-C.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(5), "OptionPricer-shutdown"));
        return server;
    }


    /**
     * Stop accepting new requests; wait up to {@code drainSeconds} for
     * in-flight ones to finish; then shut down the executor.
     */
    public static void shutdown(int drainSeconds) {
        if(!shuttingDown.compareAndSet(false, true)) return;
        try {
            if(runningServer != null) {
                System.out.println("OptionPricer API draining for up to " + drainSeconds + "s …");
                runningServer.stop(drainSeconds);
            }
            if(serverExecutor != null) {
                serverExecutor.shutdown();
                if(!serverExecutor.awaitTermination(drainSeconds, TimeUnit.SECONDS)) {
                    serverExecutor.shutdownNow();
                }
            }
        } catch(InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }


    /** Register both `/v1/`-aware structured-error and legacy un-versioned handlers. */
    private static void register(HttpServer server, String path, SafeHandler handler,
                                 boolean requirePost, boolean structuredErrors) {
        server.createContext(path, wrap(handler, requirePost, structuredErrors));
    }


    // ===================================================================
    //  Handler plumbing — CORS, method-check, error mapping
    // ===================================================================

    @FunctionalInterface
    interface SafeHandler {
        void handle(HttpExchange ex) throws IOException;
    }


    /**
     * Wrap a {@link SafeHandler} with CORS, method validation, error
     * mapping, metrics + structured request logging. If
     * {@code requirePost} is true the handler only accepts POST (plus
     * OPTIONS preflight). If {@code structuredErrors} is true (the
     * {@code /v1/*} routes), error responses use the
     * {@link ErrorEnvelopeV1} shape instead of the flat legacy shape.
     */
    private static HttpHandler wrap(SafeHandler inner, boolean requirePost, boolean structuredErrors) {
        return ex -> {
            String path = ex.getRequestURI().getPath();
            String httpMethod = ex.getRequestMethod();
            String reqId = RequestLogger.newId();
            ex.getResponseHeaders().add("X-Request-Id", reqId);

            long startNs = System.nanoTime();
            Metrics.onRequestStarted();
            int statusForMetrics = 500;

            addCorsHeaders(ex);
            try {
                // Browsers send OPTIONS preflight without the Authorization header.
                // CORS would break if we 401'd preflight.
                if("OPTIONS".equalsIgnoreCase(httpMethod)) {
                    ex.sendResponseHeaders(204, -1);
                    statusForMetrics = 204;
                    return;
                }

                // Auth gate. /health, /v1/health, /v1/ready, /metrics are
                // intentionally exempt so liveness/readiness probes and
                // metrics scrapers don't need credentials.
                if(apiToken != null && !isAuthExempt(path)) {
                    if(!hasValidBearerToken(ex)) {
                        ex.getResponseHeaders().add("WWW-Authenticate", "Bearer");
                        statusForMetrics = 401;
                        emitError(ex, structuredErrors, 401, "UNAUTHORIZED", null,
                                "missing or invalid Authorization header — expected: Bearer <token>");
                        return;
                    }
                }

                if(requirePost && !"POST".equalsIgnoreCase(httpMethod)) {
                    statusForMetrics = 405;
                    emitError(ex, structuredErrors, 405, "METHOD_NOT_ALLOWED", null,
                            "method not allowed; use POST");
                    return;
                }

                inner.handle(ex);
                statusForMetrics = 200;
            } catch(ApiException ae) {
                statusForMetrics = ae.status;
                emitError(ex, structuredErrors, ae.status, ae.code, ae.field, ae.getMessage());
            } catch(IllegalArgumentException iae) {
                statusForMetrics = 400;
                emitError(ex, structuredErrors, 400, "INVALID_PARAMETER", null,
                        iae.getMessage() == null ? "bad request" : iae.getMessage());
            } catch(Exception e) {
                statusForMetrics = 500;
                emitError(ex, structuredErrors, 500, "INTERNAL_ERROR", null,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                long latencyNs = System.nanoTime() - startNs;
                Metrics.onRequestFinished(Metrics.labelFor(path), statusForMetrics, latencyNs);
                RequestLogger.log(reqId, httpMethod, path, statusForMetrics, latencyNs);
                ex.close();
            }
        };
    }


    private static boolean isAuthExempt(String path) {
        return "/health".equals(path) || "/v1/health".equals(path) || "/v1/ready".equals(path);
    }


    /**
     * Serialise an error response in the right shape — structured
     * envelope on {@code /v1/*} routes, flat legacy shape elsewhere.
     */
    private static void emitError(HttpExchange ex, boolean structured, int status,
                                  String code, String field, String message) throws IOException {
        if(structured) {
            sendJson(ex, status, new ErrorEnvelopeV1(new ApiError(code, field, message)));
        } else {
            sendJson(ex, status, new ErrorResponse(message == null ? "bad request" : message));
        }
    }


    /** Envelope shape for structured errors on /v1/* routes. */
    public record ErrorEnvelopeV1(ApiError error) {}


    private static void ready(HttpExchange ex) throws IOException {
        if(!ready) {
            throw new ApiException(503, "NOT_READY", "engines warming up");
        }
        sendJson(ex, 200, new HealthResponse("ready"));
    }


    /**
     * Validate {@code Authorization: Bearer <token>} in constant time
     * against the configured token. Returns {@code false} if the header
     * is missing, malformed, or the token doesn't match.
     */
    private static boolean hasValidBearerToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if(header == null) return false;

        String prefix = "Bearer ";
        if(header.length() <= prefix.length()
                || !header.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return false;
        }
        String presented = header.substring(prefix.length()).trim();

        byte[] a = presented.getBytes(StandardCharsets.UTF_8);
        byte[] b = apiToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
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
    static DividendSchedule toSchedule(DividendsDto d) {
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
        sendJson(ex, 200, dispatchPriceEuropean(req));
    }


    /** Body-producing variant of {@link #priceEuropean}, callable from Batch / Grid. */
    static ModelPriceResponse dispatchPriceEuropean(EuropeanRequest req) {
        EuropeanOption opt = EuropeanOption.of(parseType(req.type), req.strike, req.timeToExpiry);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.BS);

        switch(model) {
            case BS -> {
                double p = BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                return new ModelPriceResponse(p, "BS", null);
            }
            case BINOMIAL -> {
                double p = BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                return new ModelPriceResponse(p, "BINOMIAL", null);
            }
            case PDE -> {
                double p = FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                return new ModelPriceResponse(p, "PDE", null);
            }
            case HESTON -> {
                HestonParams h = toHestonParams(req.heston);
                double p = HestonEngine.price(opt, req.spot, req.rate, h, divs).getPrice();
                return new ModelPriceResponse(p, "HESTON", null);
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
                return new ModelPriceResponse(agg.price(), "AUTO", stringKeyed(agg.contributions()));
            }
            default -> throw ApiException.unsupportedModel("model " + model + " is not applicable to European pricing");
        }
    }


    private static void priceAsian(HttpExchange ex) throws IOException {
        sendJson(ex, 200, dispatchPriceAsian(readJson(ex, AsianRequest.class)));
    }

    static ModelPriceResponse dispatchPriceAsian(AsianRequest req) {
        AsianOption opt = new AsianOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.arithmeticAverage);
        DividendSchedule divs = toSchedule(req.dividends);
        return pricePathDependent(opt, req.spot, req.rate, req.volatility,
                                  req.simulations, divs, req.model, req.heston, req.seed);
    }


    private static void priceBarrier(HttpExchange ex) throws IOException {
        sendJson(ex, 200, dispatchPriceBarrier(readJson(ex, BarrierRequest.class)));
    }

    static ModelPriceResponse dispatchPriceBarrier(BarrierRequest req) {
        BarrierOption opt = new BarrierOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring,
                req.barrier, req.upBarrier, req.inBarrier);
        DividendSchedule divs = toSchedule(req.dividends);
        return pricePathDependent(opt, req.spot, req.rate, req.volatility,
                                  req.simulations, divs, req.model, req.heston, req.seed);
    }


    private static void priceLookback(HttpExchange ex) throws IOException {
        sendJson(ex, 200, dispatchPriceLookback(readJson(ex, LookbackRequest.class)));
    }

    static ModelPriceResponse dispatchPriceLookback(LookbackRequest req) {
        LookbackOption opt = new LookbackOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.fixedStrike);
        DividendSchedule divs = toSchedule(req.dividends);
        return pricePathDependent(opt, req.spot, req.rate, req.volatility,
                                  req.simulations, divs, req.model, req.heston, req.seed);
    }


    /**
     * Shared dispatch for the three path-dependent price endpoints. Model
     * defaults to MC (GBM); selecting HESTON requires Heston params and
     * runs {@link HestonMonteCarloEngine}.
     */
    private static ModelPriceResponse pricePathDependent(
            everything.optionpricer.model.PathDependentOption opt,
            double spot, double rate, double sigma,
            Integer simulations, DividendSchedule divs,
            String modelStr, HestonParamsDto hestonDto, Long seed) {

        PricingModel model = parseModel(modelStr, PricingModel.MC);
        int sims = simulations != null ? simulations : 100_000;
        switch(model) {
            case MC -> {
                double p;
                if(seed != null) {
                    p = MonteCarloEngine.priceSeeded(opt, spot, rate, sigma, sims, seed, divs).getPrice();
                } else {
                    p = (simulations != null)
                            ? MonteCarloEngine.price(opt, spot, rate, sigma, simulations, divs).getPrice()
                            : MonteCarloEngine.price(opt, spot, rate, sigma, divs).getPrice();
                }
                return new ModelPriceResponse(p, "MC", null);
            }
            case HESTON -> {
                HestonParams h = toHestonParams(hestonDto);
                double p;
                if(seed != null) {
                    p = HestonMonteCarloEngine.priceSeeded(opt, spot, rate, h, sims, seed, divs).getPrice();
                } else {
                    p = (simulations != null)
                            ? HestonMonteCarloEngine.price(opt, spot, rate, h, simulations, divs).getPrice()
                            : HestonMonteCarloEngine.price(opt, spot, rate, h, divs).getPrice();
                }
                return new ModelPriceResponse(p, "HESTON", null);
            }
            default -> throw ApiException.unsupportedModel("model " + model + " is not applicable to path-dependent options");
        }
    }


    private static void priceAmerican(HttpExchange ex) throws IOException {
        sendJson(ex, 200, dispatchPriceAmerican(readJson(ex, AmericanRequest.class)));
    }

    static ModelPriceResponse dispatchPriceAmerican(AmericanRequest req) {
        AmericanOption opt = new AmericanOption(
                req.strike, req.timeToExpiry, parseType(req.type), req.exerciseDates);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.LSM);

        switch(model) {
            case LSM -> {
                double p = (req.simulations != null)
                        ? LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs).getPrice()
                        : LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                return new ModelPriceResponse(p, "LSM", null);
            }
            case BINOMIAL -> {
                double p = BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                return new ModelPriceResponse(p, "BINOMIAL", null);
            }
            case PDE -> {
                double p = FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                return new ModelPriceResponse(p, "PDE", null);
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
                return new ModelPriceResponse(agg.price(), "AUTO", stringKeyed(agg.contributions()));
            }
            default -> throw ApiException.unsupportedModel("model " + model + " is not applicable to American pricing");
        }
    }


    // ===================================================================
    //  /greeks/* — returns both the price and the Greeks
    // ===================================================================

    private static void greeksEuropean(HttpExchange ex) throws IOException {
        sendJson(ex, 200, dispatchGreeksEuropean(readJson(ex, EuropeanRequest.class)));
    }

    static PriceAndGreeksResponse dispatchGreeksEuropean(EuropeanRequest req) {
        EuropeanOption opt = EuropeanOption.of(parseType(req.type), req.strike, req.timeToExpiry);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.BS);
        HestonParams heston = (model == PricingModel.HESTON || req.heston != null) ? toHestonParams(req.heston) : null;

        double price;
        switch(model) {
            case BS       -> price = BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            case BINOMIAL -> price = BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            case PDE      -> price = FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            case HESTON   -> price = HestonEngine.price(opt, req.spot, req.rate, heston, divs).getPrice();
            case AUTO     -> price = MultiModelPrice.builder()
                    .add(PricingModel.BS,       BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                    .add(PricingModel.BINOMIAL, BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                    .add(PricingModel.PDE,      FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                    .build().price();
            default       -> throw ApiException.unsupportedModel("model " + model + " not applicable to European");
        }

        Greeks g = GreeksCalculator.compute(opt, req.spot, req.rate, req.volatility, divs, model, heston);
        return new PriceAndGreeksResponse(price, g);
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
        sendJson(ex, 200, dispatchGreeksAmerican(readJson(ex, AmericanRequest.class)));
    }

    static PriceAndGreeksResponse dispatchGreeksAmerican(AmericanRequest req) {
        AmericanOption opt = new AmericanOption(
                req.strike, req.timeToExpiry, parseType(req.type), req.exerciseDates);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.LSM);

        double price;
        switch(model) {
            case LSM -> price = (req.simulations != null)
                    ? LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs).getPrice()
                    : LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            case BINOMIAL -> price = BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            case PDE      -> price = FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            case AUTO -> {
                double lsm = (req.simulations != null)
                        ? LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations, divs).getPrice()
                        : LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
                price = MultiModelPrice.builder()
                        .add(PricingModel.LSM, lsm)
                        .add(PricingModel.BINOMIAL, BinomialEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                        .add(PricingModel.PDE, FiniteDifferenceEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice())
                        .build().price();
            }
            default -> throw ApiException.unsupportedModel("model " + model + " not applicable to American");
        }

        Greeks g = (req.simulations != null)
                ? GreeksCalculator.compute(opt, req.spot, req.rate, req.volatility, req.simulations, divs, model)
                : GreeksCalculator.compute(opt, req.spot, req.rate, req.volatility, divs, model);
        return new PriceAndGreeksResponse(price, g);
    }


    // ===================================================================
    //  /implied-vol/european
    // ===================================================================

    private static void impliedVolEuropean(HttpExchange ex) throws IOException {
        ImpliedVolRequest req = readJson(ex, ImpliedVolRequest.class);
        EuropeanOption opt = EuropeanOption.of(parseType(req.type), req.strike, req.timeToExpiry);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.BS);
        HestonParams heston = (model == PricingModel.HESTON || req.heston != null) ? toHestonParams(req.heston) : null;

        double iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, model, heston);

        // Re-price at the solved σ using the same model so the caller sees how close we landed.
        double pAt;
        switch(model) {
            case BS, AUTO -> pAt = BlackScholesEngine.price(opt, req.spot, req.rate, iv, divs).getPrice();
            case BINOMIAL -> pAt = BinomialEngine.price(opt, req.spot, req.rate, iv, divs).getPrice();
            case PDE      -> pAt = FiniteDifferenceEngine.price(opt, req.spot, req.rate, iv, divs).getPrice();
            case HESTON -> {
                HestonParams hSolved = new HestonParams(iv * iv, heston.kappa(), heston.theta(), heston.xi(), heston.rho());
                pAt = HestonEngine.price(opt, req.spot, req.rate, hSolved, divs).getPrice();
            }
            default -> throw new IllegalArgumentException("model " + model + " not applicable to European IV");
        }
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt, ImpliedVolatility.lastIterations()));
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
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt, ImpliedVolatility.lastIterations()));
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
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt, ImpliedVolatility.lastIterations()));
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
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt, ImpliedVolatility.lastIterations()));
    }


    private static void impliedVolAmerican(HttpExchange ex) throws IOException {
        ImpliedVolAmericanRequest req = readJson(ex, ImpliedVolAmericanRequest.class);
        AmericanOption opt = new AmericanOption(
                req.strike, req.timeToExpiry, parseType(req.type), req.exerciseDates);
        DividendSchedule divs = toSchedule(req.dividends);
        PricingModel model = parseModel(req.model, PricingModel.LSM);
        int paths = req.simulations != null ? req.simulations : ImpliedVolatility.DEFAULT_LSM_PATHS;

        double iv;
        double pAt;
        switch(model) {
            case LSM, AUTO -> {
                iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, paths);
                pAt = LongstaffSchwartzEngine.price(opt, req.spot, req.rate, iv, paths, divs).getPrice();
            }
            case BINOMIAL -> {
                iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, model);
                pAt = BinomialEngine.price(opt, req.spot, req.rate, iv, divs).getPrice();
            }
            case PDE -> {
                iv = ImpliedVolatility.impliedVolatility(opt, req.spot, req.rate, req.marketPrice, divs, model);
                pAt = FiniteDifferenceEngine.price(opt, req.spot, req.rate, iv, divs).getPrice();
            }
            default -> throw new IllegalArgumentException("model " + model + " not applicable to American IV");
        }
        sendJson(ex, 200, new ImpliedVolResponse(iv, pAt, ImpliedVolatility.lastIterations()));
    }


    // ===================================================================
    //  /vol-surface/fit
    // ===================================================================

    private static void volSurfaceFit(HttpExchange ex) throws IOException {
        VolSurfaceFitRequest req = readJson(ex, VolSurfaceFitRequest.class);
        if(req.quotes == null || req.quotes.length == 0)
            throw new IllegalArgumentException("`quotes` array must not be empty");

        List<VolatilitySurface.Quote> quotes = new java.util.ArrayList<>(req.quotes.length);
        for(QuoteDto q : req.quotes) {
            quotes.add(new VolatilitySurface.Quote(
                    q.strike, q.timeToExpiry, parseType(q.type), q.marketPrice));
        }
        DividendSchedule divs = toSchedule(req.dividends);
        VolatilitySurface surf = VolatilitySurface.fit(quotes, req.spot, req.rate, divs);

        VolSurfacePointDto[]   points    = surf.points().stream()
                .map(p -> new VolSurfacePointDto(p.strike(), p.timeToExpiry(), p.impliedVolatility()))
                .toArray(VolSurfacePointDto[]::new);
        VolSurfaceFailureDto[] failures  = surf.failures().stream()
                .map(f -> new VolSurfaceFailureDto(
                        new QuoteDto(f.quote().strike(), f.quote().timeToExpiry(),
                                     f.quote().type().name(), f.quote().marketPrice()),
                        f.reason()))
                .toArray(VolSurfaceFailureDto[]::new);

        sendJson(ex, 200, new VolSurfaceFitResponse(points, failures));
    }


    // ===================================================================
    //  /v1/price/spread — multi-leg European at one expiry.
    //  Returns net premium (BUY = +leg, SELL = -leg) and aggregated Greeks.
    // ===================================================================

    private static void priceSpread(HttpExchange ex) throws IOException {
        SpreadRequest req = readJson(ex, SpreadRequest.class);
        if(req.legs == null || req.legs.length == 0)
            throw ApiException.badField("legs", "must be a non-empty array");

        DividendSchedule divs = toSchedule(req.dividends);
        SpreadLegResult[] legResults = new SpreadLegResult[req.legs.length];
        double netPrice = 0.0;
        double netDelta = 0.0, netGamma = 0.0, netVega = 0.0, netTheta = 0.0, netRho = 0.0;

        for(int i = 0; i < req.legs.length; i++) {
            SpreadLeg leg = req.legs[i];
            if(leg.strike <= 0)        throw ApiException.badField("legs[" + i + "].strike", "must be positive");
            if(leg.qty <= 0)           throw ApiException.badField("legs[" + i + "].qty", "must be positive");
            if(leg.side == null)       throw ApiException.badField("legs[" + i + "].side", "missing");
            int sideSign = "SELL".equalsIgnoreCase(leg.side) ? -1
                         : "BUY".equalsIgnoreCase(leg.side) ?  +1
                         : 0;
            if(sideSign == 0)
                throw ApiException.badField("legs[" + i + "].side", "must be BUY or SELL");

            EuropeanOption opt = EuropeanOption.of(parseType(leg.type), leg.strike, req.timeToExpiry);
            double legPrice = BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility, divs).getPrice();
            Greeks legGreeks = BlackScholesEngine.greeks(opt, req.spot, req.rate, req.volatility, divs);

            double weight = sideSign * leg.qty;
            netPrice += weight * legPrice;
            netDelta += weight * legGreeks.delta();
            netGamma += weight * legGreeks.gamma();
            netVega  += weight * legGreeks.vega();
            netTheta += weight * legGreeks.theta();
            netRho   += weight * legGreeks.rho();

            legResults[i] = new SpreadLegResult(leg.type, leg.strike, leg.side, leg.qty,
                                                legPrice, legGreeks);
        }
        Greeks netGreeks = new Greeks(netDelta, netGamma, netVega, netTheta, netRho);
        sendJson(ex, 200, new SpreadResponse(netPrice, netGreeks, legResults));
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

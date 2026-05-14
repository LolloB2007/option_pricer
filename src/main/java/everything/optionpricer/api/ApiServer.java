package everything.optionpricer.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.AsianOption;
import everything.optionpricer.model.BarrierOption;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.LookbackOption;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.BlackScholesEngine;
import everything.optionpricer.pricing.LongstaffSchwartzEngine;
import everything.optionpricer.pricing.MonteCarloEngine;

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
            double rate, double volatility, double timeToExpiry) {}

    public record AsianRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring, boolean arithmeticAverage,
            Integer simulations) {}

    public record BarrierRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring,
            double barrier, boolean upBarrier, boolean inBarrier,
            Integer simulations) {}

    public record LookbackRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int timeSteps, boolean discreteMonitoring, boolean fixedStrike,
            Integer simulations) {}

    public record AmericanRequest(
            String type, double spot, double strike,
            double rate, double volatility, double timeToExpiry,
            int exerciseDates, Integer simulations) {}

    public record PriceResponse(double price)      {}
    public record ErrorResponse(String error)      {}
    public record HealthResponse(String status)    {}


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

        server.createContext("/health",         wrap(ApiServer::health,          false));
        server.createContext("/price/european", wrap(ApiServer::priceEuropean,   true));
        server.createContext("/price/asian",    wrap(ApiServer::priceAsian,      true));
        server.createContext("/price/barrier",  wrap(ApiServer::priceBarrier,    true));
        server.createContext("/price/lookback", wrap(ApiServer::priceLookback,   true));
        server.createContext("/price/american", wrap(ApiServer::priceAmerican,   true));

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


    // ===================================================================
    //  Endpoint handlers
    // ===================================================================

    private static void health(HttpExchange ex) throws IOException {
        sendJson(ex, 200, new HealthResponse("ok"));
    }


    private static void priceEuropean(HttpExchange ex) throws IOException {
        EuropeanRequest req = readJson(ex, EuropeanRequest.class);
        EuropeanOption opt = EuropeanOption.of(parseType(req.type), req.strike, req.timeToExpiry);
        PricingResult res = BlackScholesEngine.price(opt, req.spot, req.rate, req.volatility);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }


    private static void priceAsian(HttpExchange ex) throws IOException {
        AsianRequest req = readJson(ex, AsianRequest.class);
        AsianOption opt = new AsianOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.arithmeticAverage);
        PricingResult res = (req.simulations != null)
                ? MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations)
                : MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }


    private static void priceBarrier(HttpExchange ex) throws IOException {
        BarrierRequest req = readJson(ex, BarrierRequest.class);
        BarrierOption opt = new BarrierOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring,
                req.barrier, req.upBarrier, req.inBarrier);
        PricingResult res = (req.simulations != null)
                ? MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations)
                : MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }


    private static void priceLookback(HttpExchange ex) throws IOException {
        LookbackRequest req = readJson(ex, LookbackRequest.class);
        LookbackOption opt = new LookbackOption(
                req.strike, req.timeToExpiry, parseType(req.type),
                req.timeSteps, req.discreteMonitoring, req.fixedStrike);
        PricingResult res = (req.simulations != null)
                ? MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations)
                : MonteCarloEngine.price(opt, req.spot, req.rate, req.volatility);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }


    private static void priceAmerican(HttpExchange ex) throws IOException {
        AmericanRequest req = readJson(ex, AmericanRequest.class);
        AmericanOption opt = new AmericanOption(
                req.strike, req.timeToExpiry, parseType(req.type), req.exerciseDates);
        PricingResult res = (req.simulations != null)
                ? LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility, req.simulations)
                : LongstaffSchwartzEngine.price(opt, req.spot, req.rate, req.volatility);
        sendJson(ex, 200, new PriceResponse(res.getPrice()));
    }
}

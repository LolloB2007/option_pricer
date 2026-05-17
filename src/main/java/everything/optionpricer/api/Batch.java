package everything.optionpricer.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * Batch endpoints — accept an array of per-element requests sharing the
 * same shape as the single-element endpoint and return an array of
 * per-element results, each marked {@code ok: true/false}.
 *
 * One bad element does NOT fail the whole batch; the bot pricing a 200-
 * strike chain can still consume the good prices and skip the failures.
 *
 * For pure path-dependent options the {@link Grid} endpoints are far
 * cheaper because they share the Monte Carlo paths across all contracts.
 * Batch is for the heterogeneous case (different timeToExpiry per
 * element, mixed types, etc.).
 *
 * @author lorenzobarbagelata
 */
final class Batch {

    private static final Gson GSON = new Gson();

    private Batch() {}


    // ----- Per-element result shape ----- //

    public record BatchElement(boolean ok, Object result, ApiError error) {
        public static BatchElement ok(Object body)    { return new BatchElement(true, body, null); }
        public static BatchElement err(ApiError e)    { return new BatchElement(false, null, e); }
    }

    public record BatchResponse(BatchElement[] results) {}


    /**
     * Reads a top-level {@code { "requests": [...] }} body and returns the
     * raw JSON elements. Throws ApiException on parse failure.
     */
    private static JsonElement[] readRequests(HttpExchange ex) throws IOException {
        try (Reader r = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonElement root = com.google.gson.JsonParser.parseReader(r);
            if(root == null || !root.isJsonObject())
                throw ApiException.badRequest("expected an object with a `requests` array");
            JsonObject obj = root.getAsJsonObject();
            JsonElement reqs = obj.get("requests");
            if(reqs == null || !reqs.isJsonArray())
                throw ApiException.badField("requests", "missing or not an array");
            return reqs.getAsJsonArray().asList().toArray(new JsonElement[0]);
        } catch(JsonSyntaxException e) {
            throw ApiException.malformedJson(e.getMessage());
        }
    }


    /** Generic batch driver — applies {@code priceOne} to each element. */
    private static void runBatch(HttpExchange ex, JsonElement[] rawElements,
                                 ElementPricer priceOne) throws IOException {
        BatchElement[] results = new BatchElement[rawElements.length];
        for(int i = 0; i < rawElements.length; i++) {
            try {
                Object body = priceOne.price(rawElements[i]);
                results[i] = BatchElement.ok(body);
            } catch(ApiException ae) {
                results[i] = BatchElement.err(new ApiError(ae.code, ae.field, ae.getMessage()));
            } catch(IllegalArgumentException iae) {
                results[i] = BatchElement.err(ApiError.of("INVALID_PARAMETER",
                        iae.getMessage() == null ? "bad request" : iae.getMessage()));
            } catch(Exception e) {
                results[i] = BatchElement.err(ApiError.of("INTERNAL_ERROR",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        sendJson(ex, 200, new BatchResponse(results));
    }


    @FunctionalInterface
    private interface ElementPricer {
        Object price(JsonElement raw) throws IOException;
    }


    // ============================================================
    //  Public handler refs registered in ApiServer
    // ============================================================

    static final ApiServer.SafeHandler priceEuropeanBatch = ex -> {
        JsonElement[] raw = readRequests(ex);
        runBatch(ex, raw, e -> ApiServer.dispatchPriceEuropean(parse(e, ApiServer.EuropeanRequest.class)));
    };

    static final ApiServer.SafeHandler priceAsianBatch = ex -> {
        JsonElement[] raw = readRequests(ex);
        runBatch(ex, raw, e -> ApiServer.dispatchPriceAsian(parse(e, ApiServer.AsianRequest.class)));
    };

    static final ApiServer.SafeHandler priceBarrierBatch = ex -> {
        JsonElement[] raw = readRequests(ex);
        runBatch(ex, raw, e -> ApiServer.dispatchPriceBarrier(parse(e, ApiServer.BarrierRequest.class)));
    };

    static final ApiServer.SafeHandler priceLookbackBatch = ex -> {
        JsonElement[] raw = readRequests(ex);
        runBatch(ex, raw, e -> ApiServer.dispatchPriceLookback(parse(e, ApiServer.LookbackRequest.class)));
    };

    static final ApiServer.SafeHandler priceAmericanBatch = ex -> {
        JsonElement[] raw = readRequests(ex);
        runBatch(ex, raw, e -> ApiServer.dispatchPriceAmerican(parse(e, ApiServer.AmericanRequest.class)));
    };

    static final ApiServer.SafeHandler greeksEuropeanBatch = ex -> {
        JsonElement[] raw = readRequests(ex);
        runBatch(ex, raw, e -> ApiServer.dispatchGreeksEuropean(parse(e, ApiServer.EuropeanRequest.class)));
    };

    static final ApiServer.SafeHandler greeksAmericanBatch = ex -> {
        JsonElement[] raw = readRequests(ex);
        runBatch(ex, raw, e -> ApiServer.dispatchGreeksAmerican(parse(e, ApiServer.AmericanRequest.class)));
    };


    private static <T> T parse(JsonElement raw, Class<T> type) {
        if(raw == null) throw ApiException.badRequest("missing request element");
        try {
            T parsed = GSON.fromJson(raw, type);
            if(parsed == null) throw ApiException.badRequest("empty request element");
            return parsed;
        } catch(JsonSyntaxException e) {
            throw ApiException.malformedJson(e.getMessage());
        }
    }


    /** Write a JSON object response. Mirrors ApiServer.sendJson. */
    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}

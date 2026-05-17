package everything.optionpricer.api;


/**
 * Carries a structured error code and HTTP status alongside the message.
 * Thrown from handlers; the {@code wrap()} middleware maps it to either
 * structured-error JSON (on {@code /v1/*} routes) or the flat legacy
 * shape (on un-versioned routes).
 *
 * Error codes the API emits today:
 *   INVALID_PARAMETER      — request body validation failure
 *   NO_ARBITRAGE_BAND      — IV target price outside the no-arb bounds
 *   UNSUPPORTED_MODEL      — `model` field isn't applicable to the endpoint
 *   UNAUTHORIZED           — missing / wrong bearer token
 *   METHOD_NOT_ALLOWED     — non-POST on a POST endpoint
 *   MALFORMED_JSON         — JSON body failed to parse
 *   INTERNAL_ERROR         — anything else
 *
 * @author lorenzobarbagelata
 */
public final class ApiException extends RuntimeException {

    public final int    status;
    public final String code;
    public final String field;

    public ApiException(int status, String code, String message) {
        this(status, code, null, message);
    }

    public ApiException(int status, String code, String field, String message) {
        super(message);
        this.status = status;
        this.code   = code;
        this.field  = field;
    }


    /** 400 INVALID_PARAMETER. */
    public static ApiException badRequest(String message) {
        return new ApiException(400, "INVALID_PARAMETER", message);
    }

    public static ApiException badField(String field, String message) {
        return new ApiException(400, "INVALID_PARAMETER", field, message);
    }

    public static ApiException unsupportedModel(String message) {
        return new ApiException(400, "UNSUPPORTED_MODEL", message);
    }

    public static ApiException noArbBand(String message) {
        return new ApiException(400, "NO_ARBITRAGE_BAND", message);
    }

    public static ApiException malformedJson(String detail) {
        return new ApiException(400, "MALFORMED_JSON", detail);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }

    public static ApiException methodNotAllowed(String message) {
        return new ApiException(405, "METHOD_NOT_ALLOWED", message);
    }

    public static ApiException internal(String message) {
        return new ApiException(500, "INTERNAL_ERROR", message);
    }
}

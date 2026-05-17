package everything.optionpricer.api;


/**
 * Structured error code returned on {@code /v1/*} routes.
 *
 * Bot/client code matches on {@link #code}; humans read {@link #message}.
 * {@link #field} is non-null only when the error pins to a specific
 * request field (e.g. {@code "strike"} for a no-arb-band rejection).
 *
 * @author lorenzobarbagelata
 */
public record ApiError(String code, String field, String message) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, null, message);
    }

    public static ApiError of(String code, String field, String message) {
        return new ApiError(code, field, message);
    }
}

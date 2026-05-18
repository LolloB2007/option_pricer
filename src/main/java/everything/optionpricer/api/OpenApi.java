package everything.optionpricer.api;


/**
 * Hand-rolled OpenAPI 3.0 spec for the v1 surface. Served from
 * {@code /openapi.json}. Lets downstream clients (the bot, Swagger UI,
 * codegen tools) introspect endpoints, request/response shapes, and
 * auth without us hand-maintaining a separate document.
 *
 * It's a static document — kept here as a multi-line string so the
 * spec lives next to the code. When endpoints change, this file
 * changes alongside.
 *
 * @author lorenzobarbagelata
 */
final class OpenApi {

    private OpenApi() {}


    static String spec() {
        return """
{
  "openapi": "3.0.3",
  "info": {
    "title": "OptionPricer API",
    "version": "3.2",
    "description": "HTTP/JSON pricing API. European, Asian, Barrier, Lookback, American options under BS / Binomial / PDE / Heston / Monte Carlo / Longstaff-Schwartz."
  },
  "servers": [{ "url": "http://localhost:8080", "description": "Local dev" }],
  "components": {
    "securitySchemes": {
      "BearerAuth": { "type": "http", "scheme": "bearer" }
    },
    "schemas": {
      "Dividends": {
        "type": "object",
        "properties": {
          "continuousYield": { "type": "number", "description": "Annual continuous yield in decimal (3% = 0.03)" },
          "discrete": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "time":   { "type": "number" },
                "amount": { "type": "number" }
              }
            }
          }
        }
      },
      "HestonParams": {
        "type": "object",
        "required": ["v0", "kappa", "theta", "xi", "rho"],
        "properties": {
          "v0":    { "type": "number" },
          "kappa": { "type": "number" },
          "theta": { "type": "number" },
          "xi":    { "type": "number" },
          "rho":   { "type": "number" }
        }
      },
      "Greeks": {
        "type": "object",
        "properties": {
          "delta": { "type": "number" },
          "gamma": { "type": "number" },
          "vega":  { "type": "number", "description": "per 1% σ" },
          "theta": { "type": "number", "description": "per calendar day" },
          "rho":   { "type": "number", "description": "per 1% rate" }
        }
      },
      "ModelPriceResponse": {
        "type": "object",
        "properties": {
          "price":         { "type": "number" },
          "model":         { "type": "string" },
          "contributions": { "type": "object", "additionalProperties": { "type": "number" }, "nullable": true },
          "stdError":      { "type": "number", "nullable": true, "description": "Estimator standard error (MC/LSM only)" },
          "paths":         { "type": "integer", "nullable": true }
        }
      },
      "ErrorEnvelope": {
        "type": "object",
        "properties": {
          "error": {
            "type": "object",
            "properties": {
              "code":    { "type": "string", "enum": [
                "INVALID_PARAMETER", "NO_ARBITRAGE_BAND", "UNSUPPORTED_MODEL",
                "UNAUTHORIZED", "METHOD_NOT_ALLOWED", "MALFORMED_JSON",
                "INTERNAL_ERROR", "NOT_READY"] },
              "field":   { "type": "string", "nullable": true },
              "message": { "type": "string" }
            }
          }
        }
      }
    }
  },
  "security": [{ "BearerAuth": [] }],
  "paths": {
    "/v1/health": {
      "get": {
        "summary": "Liveness check",
        "security": [],
        "responses": { "200": { "description": "ok" } }
      }
    },
    "/v1/ready": {
      "get": {
        "summary": "Readiness — engines have JIT-warmed",
        "security": [],
        "responses": { "200": { "description": "ready" }, "503": { "description": "NOT_READY" } }
      }
    },
    "/metrics": {
      "get": {
        "summary": "Prometheus text-format scrape endpoint",
        "security": [],
        "responses": { "200": { "description": "metrics" } }
      }
    },
    "/v1/price/european":   { "post": { "summary": "European option price" } },
    "/v1/price/asian":      { "post": { "summary": "Asian option price (MC / Heston-MC)" } },
    "/v1/price/barrier":    { "post": { "summary": "Barrier option price (MC / Heston-MC)" } },
    "/v1/price/lookback":   { "post": { "summary": "Lookback option price (MC / Heston-MC)" } },
    "/v1/price/american":   { "post": { "summary": "American option price (LSM / Binomial / PDE)" } },
    "/v1/greeks/european":  { "post": { "summary": "European price + Greeks" } },
    "/v1/greeks/american":  { "post": { "summary": "American price + Greeks" } },
    "/v1/implied-vol/european": { "post": { "summary": "European IV (Newton + bisection); response includes iterations" } },
    "/v1/implied-vol/asian":    { "post": { "summary": "Asian IV (CRN MC inversion)" } },
    "/v1/implied-vol/barrier":  { "post": { "summary": "Barrier IV (CRN MC inversion)" } },
    "/v1/implied-vol/lookback": { "post": { "summary": "Lookback IV (CRN MC inversion)" } },
    "/v1/implied-vol/american": { "post": { "summary": "American IV (CRN LSM inversion)" } },
    "/v1/vol-surface/fit":      { "post": { "summary": "Fit a vol surface from market quotes" } },
    "/v1/vol-surface/eval":     { "post": { "summary": "Fit and interpolate at a single (K, T) in one call" } },
    "/v1/heston/calibrate":     { "post": { "summary": "Nelder-Mead calibration of (v0, kappa, theta, xi, rho) against market quotes" } },
    "/v1/price/spread":         { "post": { "summary": "Multi-leg European spread — net premium + aggregated Greeks" } },
    "/v1/price/european-forward":   { "post": { "summary": "Black-76 — price European option on a forward / futures price" } },
    "/v1/greeks/european-forward":  { "post": { "summary": "Black-76 Greeks" } },
    "/v1/prob/itm":             { "post": { "summary": "Risk-neutral probability the option finishes in the money" } },
    "/v1/prob/touch":           { "post": { "summary": "Probability the underlying touches a barrier before expiry" } },
    "/v1/batch/price/european": { "post": { "summary": "Batch European pricing — one HTTP for N requests" } },
    "/v1/batch/price/asian":    { "post": { "summary": "Batch Asian pricing" } },
    "/v1/batch/price/barrier":  { "post": { "summary": "Batch Barrier pricing" } },
    "/v1/batch/price/lookback": { "post": { "summary": "Batch Lookback pricing" } },
    "/v1/batch/price/american": { "post": { "summary": "Batch American pricing" } },
    "/v1/batch/greeks/european":{ "post": { "summary": "Batch European Greeks" } },
    "/v1/batch/greeks/american":{ "post": { "summary": "Batch American Greeks" } },
    "/v1/grid/european":        { "post": { "summary": "Grid European — array of (strike, type) at same (S, σ, T)" } },
    "/v1/grid/american":        { "post": { "summary": "Grid American" } },
    "/v1/grid/asian":           { "post": { "summary": "Grid Asian — MC paths reused across strikes" } },
    "/v1/grid/barrier":         { "post": { "summary": "Grid Barrier — MC paths reused across strikes (B fixed)" } },
    "/v1/grid/lookback":        { "post": { "summary": "Grid Lookback — MC paths reused across strikes" } }
  }
}
""";
    }
}

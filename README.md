# OptionPricer

A Java application for pricing options — **European, Asian, Barrier, Lookback, and American** — through a modern Swing GUI **or** as an HTTP/JSON service.

## Release

**Current release: v3.2**

Highlights since v1.0:

- Path-dependent options (Asian, Barrier, Lookback) priced by a parallel antithetic Monte Carlo engine.
- American options priced by **Longstaff–Schwartz** regression Monte Carlo.
- Both **discrete** and **continuous** monitoring for the path-dependent types, including Brownian-bridge corrections.
- **Multiple pricing engines** — Black-Scholes closed form, **Cox-Ross-Rubinstein binomial**, **Crank-Nicolson PDE**, **Heston (Fourier inversion)** alongside Monte Carlo and LSM.
- **Heston Monte Carlo** for path-dependent options (Asian, Barrier, Lookback) — two-factor full-truncation Euler discretisation; antithetic + parallel.
- **Volatility surface fitter** — inverts a list of market quotes into a 2D implied-vol surface with strike skew and term structure; linear-in-strike, linear-in-time interpolation.
- **API bearer-token authentication** — enable with `--token <value>` or the `OPTIONPRICER_API_TOKEN` env var. Constant-time token comparison; 401 with `WWW-Authenticate: Bearer` on failure. `/health` and `OPTIONS` preflight stay open.

### v3.2 — bot-consumer feedback

- **MC standard error** — every Monte Carlo / LSM response now carries `stdError` (in price units) and `paths`. Closed-form pricers return `null` for both. The bot can size positions against the actual estimator uncertainty instead of treating a single MC price as if it were exact.
- **Heston calibration** — `POST /v1/heston/calibrate`. Nelder-Mead simplex on `(v0, κ, θ, ξ, ρ)` against a list of European market quotes. Returns the fitted params + RMSE + iteration count. Makes `model=HESTON` actually usable in production.
- **Vol-surface as pricer input** — `/v1/price/european` and `/v1/greeks/european` accept an optional `surface: { quotes: [...] }` field. When supplied, the engine fits the surface from the quotes and looks up σ at the option's `(K, T)` instead of using the flat `volatility` field. Collapses the bot's two-round-trip "IV first, then price" pattern into one call.
- **Vol-surface eval endpoint** — `POST /v1/vol-surface/eval { spot, rate, quotes, strike, timeToExpiry }` returns the interpolated implied vol at one `(K, T)`. Saves the client from re-implementing the linear-in-K / linear-in-T interpolation.
- **Forward / Black-76 pricing** — `POST /v1/price/european-forward` and `/v1/greeks/european-forward` accept a `forward` field directly. For futures options / CFDs / instruments without a clean `(spot, r, q)` decomposition.
- **Probability endpoints** — `POST /v1/prob/itm` (risk-neutral probability of finishing in the money) and `POST /v1/prob/touch` (probability the underlying touches a barrier before expiry). Closed-form under GBM. Useful for strategy filters ("only sell condors where short-strike touch-prob < 30%").
- **`maxLatencyMs` cooperative deadline** — any request can include `maxLatencyMs: <number>` in its body. MC / LSM engines check the deadline at simulation-batch boundaries on every parallel worker; if exceeded, the response is `HTTP 504` with code `DEADLINE_EXCEEDED`. Lets the bot cap tail latency on heavy calibration / batch-grid calls.
- **Multiple bearer tokens** — `--token a,b,c` (or comma-separated `OPTIONPRICER_API_TOKEN`) accepts any of the listed values. Supports rotation: issue a new token, deploy, then revoke the old one once clients have migrated. Constant-time compare against every token to avoid timing-based discovery of token count or position.
- **OpenAPI spec** — `GET /openapi.json` returns an OpenAPI 3.0 spec describing every v1 endpoint. Lets downstream clients generate typed bindings instead of hand-writing DTOs.

### v3.1 — bot-oriented API surface

- **Versioned API** — every endpoint also lives at `/v1/...`. Legacy un-versioned routes keep working unchanged.
- **Structured errors** on `/v1/*` — `{ "error": { "code", "field?", "message" } }` with codes a client can match on (`INVALID_PARAMETER`, `NO_ARBITRAGE_BAND`, `UNSUPPORTED_MODEL`, `UNAUTHORIZED`, `METHOD_NOT_ALLOWED`, `MALFORMED_JSON`, `INTERNAL_ERROR`). Legacy routes keep the flat `{ "error": "..." }` shape.
- **Batch endpoints** — `POST /v1/batch/{price,greeks}/<method>`. One HTTP round-trip for a whole option chain; per-element `{ ok, result | error }` envelope so one bad strike doesn't fail the batch.
- **Grid pricing with path reuse** — `POST /v1/grid/<method>`. For path-dependent options (Asian / Barrier / Lookback), Monte Carlo paths are simulated once and the payoff is evaluated against the shared per-path statistics for every `(strike, type)` contract. ~100× speedup vs running N independent MC pricings.
- **Multi-leg spread pricing** — `POST /v1/price/spread`. European legs at one expiry; returns net premium and aggregated Greeks, plus per-leg breakdown. Covers verticals / condors / butterflies / straddles / strangles.
- **IV iteration count + reproducible MC** — every `/v1/implied-vol/*` response carries `iterations` (Newton + bisection steps). MC path-dependent endpoints accept optional `seed: <long>` for CRN reproducibility in tests / debugging.
- **Observability**
  - `GET /metrics` — Prometheus text format. `pricer_requests_total{endpoint,status}` counter, `pricer_request_seconds{endpoint}` histogram, `pricer_in_flight_requests` gauge.
  - `GET /v1/ready` — flipped true once the JIT-warmup pricing call completes.
  - **Structured request logging** — one JSON line per request: `{ ts, reqId, method, path, status, latencyMs }`. Every response also carries `X-Request-Id` for end-to-end correlation.
  - **Graceful shutdown** — `SIGTERM` / Ctrl-C drains in-flight requests (up to 5s) before exit.
- **Auto mode**: trimmed mean of model outputs ("mean of the two middle outputs") — robust against a single misbehaving engine.
- **Greeks** (Δ, Γ, ν, Θ, ρ) **dispatched by the selected pricing model** — closed-form under BS, deterministic-pricer finite differences under Binomial / PDE / Heston, CRN-seeded MC / LSM finite differences for path-dependent + American; **Auto** trims across the σ-based models component-by-component.
- **Implied volatility** solver **dispatched by the selected pricing model** — closed-form Newton + bisection for BS, Newton on the deterministic pricer for Binomial / PDE, `v₀` inversion (reported as `√v₀`) for Heston, CRN-seeded MC / LSM for path-dependent + American.
- **Continuous dividend yield + discrete cash dividends** across every pricer.
- Tabbed UI with a live 5/50/95% percentile chart + a payoff diagram on each tab. Launches **maximised by default**.
- **HTTP/JSON API** so other applications can request prices, Greeks and implied vol over the network.

## Overview

OptionPricer is a Java-based finance application built as a passion project to combine Java desktop development, GUI design, numerical finance, and mathematical modelling.

The application can be used in two ways:

1. As a **desktop GUI** — enter inputs, get a price, and see the option's value distribution over time.
2. As an **HTTP/JSON pricing service** — start the same JAR in API mode and request prices from any other application.

## Features

### Pricing
- **European Call & Put** — pick from closed-form **Black-Scholes**, **Cox-Ross-Rubinstein binomial tree**, **Crank-Nicolson PDE**, or **Heston** (stochastic volatility) via Fourier inversion
- **Asian** — arithmetic or geometric, discrete or continuous monitoring (Monte Carlo)
- **Barrier** — up/down × in/out, discrete or continuous monitoring (Brownian-bridge survival probability)
- **Lookback** — fixed or floating strike, discrete or continuous monitoring (Brownian-bridge max/min sampling)
- **American** — pick from **Longstaff-Schwartz** Monte Carlo, **CRR binomial tree**, or **Crank-Nicolson PDE** (the binomial / PDE engines also handle early-exercise via the standard `max(V, intrinsic)` projection)

### Pricing model selector

Every applicable engine can be selected explicitly. There is also an **Auto** mode that runs all engines applicable to the chosen option type, drops the smallest and largest outputs, and averages the rest — the "mean of the two middle outputs" once a fourth engine is in the mix (degenerates to the median for three). Robust against a single misbehaving engine.

| Option type | Engines that apply | Auto trims across |
|-------------|--------------------|-------------------|
| European    | BS, Binomial, PDE, Heston | BS, Binomial, PDE |
| American    | LSM, Binomial, PDE | LSM, Binomial, PDE |
| Asian / Barrier / Lookback | MC (GBM), Heston-MC | — |

### Volatility surface

`VolatilitySurface.fit(quotes, spot, rate, dividends)` inverts a list of European market quotes `(strike, T, type, marketPrice)` under Black-Scholes, organises the resulting implied vols into per-expiry slices, and exposes `volAt(strike, T)`. Strike interpolation is linear inside each bracketing slice; time interpolation is linear between slices; flat extrapolation outside the convex hull. Captures both **term structure** (different σ per expiry slice) and **skew** (σ varying with K inside a slice).

### Greeks & implied vol
- **Greeks** (delta, gamma, vega, theta, rho) for every option type, dispatched by the selected pricing model
  - European + BS: closed-form Black–Scholes (instant)
  - European + Binomial / PDE: central finite differences against the deterministic pricer (~8 repricings, ~30 ms each)
  - European + Heston: finite differences on the Heston pricer; vega bumps σ₀ ≡ √v₀ and converts via chain rule so the value is reported in the practitioner per-1%-σ convention
  - European + Auto: per-Greek trimmed mean across {BS, Binomial, PDE} — drops min and max, returns the median (or the average of the two middle values for ≥ 4 contributors)
  - American + LSM: CRN-seeded LSM finite differences
  - American + Binomial / PDE: deterministic-pricer finite differences
  - American + Auto: per-Greek trimmed mean across {LSM, Binomial, PDE}
  - Path-dependent (Asian / Barrier / Lookback): CRN-seeded MC finite differences
  - Output in practitioner conventions: vega per 1% σ, theta per day, rho per 1% rate
- **Implied volatility** solver for every option type, also dispatched by model
  - European + BS / Auto: Newton-Raphson with analytic BS vega + bisection fallback (closed-form, sub-millisecond)
  - European + Binomial / PDE: Newton on the deterministic pricer with finite-difference vega + bisection on the same bracket
  - European + Heston: solves for `v₀` and reports `σ = √v₀` so the value reads as a regular vol — natural Heston IV
  - American + LSM / Auto: CRN-seeded LSM inversion
  - American + Binomial / PDE: Newton on the deterministic pricer
  - Path-dependent: CRN-seeded MC inversion
  - Pre-flight no-arbitrage / reachability check on the target price
  - Caveat: for options whose price is non-monotonic in σ (e.g. up-and-out calls), the solver returns one valid root — there may be a second

### Dividends
- **Continuous dividend yield** `q` honoured by every pricer — drift becomes `r − q` in BS, MC and LSM
- **Discrete cash dividends** as `(time, amount)` pairs
  - BS uses the escrowed model (subtracts PV of dividends from spot)
  - MC / LSM drop the simulated path by the dividend amount at the corresponding step
- Greeks and implied vol pass the same schedule through every bumped re-price

### Monte Carlo engine
- Parallel simulation across all available CPU cores (explicit chunked workload on the common ForkJoinPool)
- Antithetic variates (halves RNG calls and roughly halves variance)
- Streaming `PathAccumulator` API — no full path arrays allocated per simulation
- Log-domain stepping; per-step `Math.exp` skipped for options that only need log-prices (Barrier, Lookback, geometric Asian)
- Early termination for knocked-out OUT barriers
- `ThreadLocalRandom` everywhere — no synchronised RNG state

### UI
- Tabbed Swing GUI styled with **FlatLaf** (dark) + custom theme tweaks
- Launches **maximised** by default
- **European** tab: pricing-model dropdown (Auto / BS / Binomial / PDE / Heston); a Heston-params sub-card slides in when Heston is selected; inline implied-vol solver below
- **Monte Carlo** tab: segmented method picker (Asian / Barrier / Lookback / American); for the American method, a model dropdown (Auto / LSM / Binomial / PDE); async Greeks and implied-vol via SwingWorker
- **Payoff diagram** on every tab — intrinsic-at-maturity hockey stick + smooth current-value curve, with S₀ guide line and K marker
- Analytic **5 / 50 / 95% percentile chart** of the option's value over time, with a filled uncertainty band
- **Hover readout** on the percentile chart — vertical crosshair, dots at the three lines, tooltip with exact values at the cursor's time
- **Dividend yield** input on both tabs

### API
- Same JAR; switch modes with a CLI flag
- Endpoints for every pricing method (see [API](#api))
- Permissive CORS + `OPTIONS` preflight — callable from a browser

## Supported Inputs

Common inputs for every option type:

- **Option Type** (`Call` or `Put`)
- **Spot Price** (`S`)
- **Strike Price** (`K`)
- **Risk-Free Rate** (`r`)
- **Volatility** (`σ`)
- **Time to Expiry** (`T`)
- **Dividends** (optional) — continuous yield `q` and/or a list of discrete cash dividends

Extra inputs by method:

| Method | Extra inputs |
|--------|--------------|
| Asian   | time steps, monitoring (discrete/continuous), averaging (arithmetic/geometric) |
| Barrier | time steps, monitoring, barrier level, direction (up/down), in/out |
| Lookback | time steps, monitoring, strike type (fixed/floating) |
| American | exercise dates |

## Input Conventions

- **Risk-Free Rate** in **decimal form** — `6% = 0.06`
- **Volatility** in **decimal form** — `15% = 0.15`
- **Time to Expiry** in **years** — `6 months = 0.5`
- **Continuous dividend yield** in **decimal form** — `3% = 0.03`
- **Discrete dividend** times in **years from today**; amounts in the underlying's currency

### Greek conventions

| Greek | Meaning |
|-------|---------|
| **Δ** delta | change in price per **1.00** change in spot |
| **Γ** gamma | change in delta per **1.00** change in spot |
| **ν** vega  | change in price per **1% absolute** vol bump |
| **Θ** theta | change in price per **calendar day** |
| **ρ** rho   | change in price per **1% absolute** rate bump |

## Run

### Requirements

- **Java 17**
- **Maven** (only required to build from source)

### Build from source

```bash
mvn clean package
```

This produces `target/OptionPricer-1.0-SNAPSHOT.jar`.

### GUI mode (default)

```bash
java -jar target/OptionPricer-1.0-SNAPSHOT.jar
```

### API mode

```bash
java -jar target/OptionPricer-1.0-SNAPSHOT.jar --api 8080
```

The server listens on the given port (defaults to 8080 if omitted). With no token configured, **every endpoint is open** — use this for localhost dev only.

For any non-trivial deployment enable bearer-token auth:

```bash
java -jar target/OptionPricer-1.0-SNAPSHOT.jar --api 8080 --token sk_secret_xyz
# or via env var
OPTIONPRICER_API_TOKEN=sk_secret_xyz java -jar target/OptionPricer-1.0-SNAPSHOT.jar --api 8080
```

When a token is set, every endpoint except `/health` and `OPTIONS` preflight requires the header `Authorization: Bearer sk_secret_xyz`. Mismatches return HTTP 401 with `WWW-Authenticate: Bearer`. Comparisons are constant-time. The startup log makes it clear which mode you're in.

Transport security (TLS / mTLS) is out of scope — terminate at a reverse proxy (nginx, Caddy, Cloudflare, etc.).

### Prebuilt JAR

[**Download release**]([https://drive.google.com/drive/folders/1CB3HEip-fFKy7sxvxaY_SiWdSmCw3Uis?usp=sharing]) — note that you need to select the latest version.

## API

All endpoints accept and return `application/json`. Successful responses have the shape:

```json
{ "price": 10.450575619322287 }
```

Errors return an HTTP 4xx/5xx status. On the legacy un-versioned routes the body is:

```json
{ "error": "human-readable message" }
```

On the `/v1/*` routes the body is **structured** so a client can match on a code rather than parse a string:

```json
{ "error": { "code": "INVALID_PARAMETER", "field": "strike", "message": "..." } }
```

Codes the API emits today: `INVALID_PARAMETER`, `NO_ARBITRAGE_BAND`, `UNSUPPORTED_MODEL`, `UNAUTHORIZED`, `METHOD_NOT_ALLOWED`, `MALFORMED_JSON`, `INTERNAL_ERROR`. `NOT_READY` from `/v1/ready` while the engines are still warming up. `field` is non-null only when the error pins to a specific request field.

Every response also carries `X-Request-Id` — pair it with the server's structured log line for end-to-end correlation.

### Versioning

Every endpoint is registered at **both** its legacy path (`/price/european`) and its versioned path (`/v1/price/european`). Old clients keep working. New clients should use `/v1/...` to get structured errors and access the bot-oriented endpoints (`/v1/batch/*`, `/v1/grid/*`, `/v1/price/spread`, `/v1/ready`).

### Endpoints

**Price only** — returns `{ "price": <number> }`:

| Verb | Path | Description |
|------|------|-------------|
| `GET`  | `/health` | Liveness check; returns `{"status":"ok"}` |
| `POST` | `/price/european` | Black–Scholes closed form |
| `POST` | `/price/asian`    | Asian option (Monte Carlo) |
| `POST` | `/price/barrier`  | Barrier option (Monte Carlo) |
| `POST` | `/price/lookback` | Lookback option (Monte Carlo) |
| `POST` | `/price/american` | American option (Longstaff–Schwartz) |

**Price + Greeks** — same request body, returns `{ "price": <number>, "greeks": { delta, gamma, vega, theta, rho } }`:

| Verb | Path |
|------|------|
| `POST` | `/greeks/european` |
| `POST` | `/greeks/asian`    |
| `POST` | `/greeks/barrier`  |
| `POST` | `/greeks/lookback` |
| `POST` | `/greeks/american` |

**Implied volatility** — returns `{ "impliedVolatility": <number>, "price": <number> }`:

| Verb | Path | Notes |
|------|------|-------|
| `POST` | `/implied-vol/european` | Closed-form (Newton + bisection) |
| `POST` | `/implied-vol/asian`    | CRN-seeded MC inversion |
| `POST` | `/implied-vol/barrier`  | CRN-seeded MC inversion |
| `POST` | `/implied-vol/lookback` | CRN-seeded MC inversion |
| `POST` | `/implied-vol/american` | CRN-seeded LSM inversion |

**Volatility surface** — returns `{ "points": [{ strike, timeToExpiry, impliedVolatility }, ...], "failures": [...] }`:

| Verb | Path | Description |
|------|------|-------------|
| `POST` | `/vol-surface/fit` | Inverts a list of European quotes into a vol-surface point cloud |

**Batch** — one HTTP round-trip for N requests of the same method. Returns `{ "results": [{ "ok": true, "result": {...} } | { "ok": false, "error": {...} }, ...] }`:

| Verb | Path |
|------|------|
| `POST` | `/v1/batch/price/european` |
| `POST` | `/v1/batch/price/asian` |
| `POST` | `/v1/batch/price/barrier` |
| `POST` | `/v1/batch/price/lookback` |
| `POST` | `/v1/batch/price/american` |
| `POST` | `/v1/batch/greeks/european` |
| `POST` | `/v1/batch/greeks/american` |

**Grid** — one set of market parameters + an array of `(strike, type)` contracts. Returns `{ "results": [{ "type", "strike", "price" }, ...] }`. For path-dependent options the Monte Carlo paths are simulated **once** and reused across every contract — pricing 100 strikes at the same `(spot, σ, T, divs, B?)` costs roughly the same as pricing 1.

| Verb | Path | Notes |
|------|------|-------|
| `POST` | `/v1/grid/european` | Loops BS per contract (BS is already fast). |
| `POST` | `/v1/grid/american` | Loops LSM per contract (each call internally parallel). |
| `POST` | `/v1/grid/asian`    | Shares paths; computes per-path average once, evaluates payoff per contract. |
| `POST` | `/v1/grid/barrier`  | Shares paths; barrier level / direction / in-out fixed across the grid (vary K). |
| `POST` | `/v1/grid/lookback` | Shares paths; strike-type fixed across the grid (vary K). |

**Multi-leg spread** — `POST /v1/price/spread`. European legs at one expiry. Body:
```json
{
  "spot": 100, "rate": 0.05, "volatility": 0.20, "timeToExpiry": 1.0,
  "legs": [
    { "type": "CALL", "strike": 100, "side": "BUY",  "qty": 1 },
    { "type": "CALL", "strike": 110, "side": "SELL", "qty": 1 }
  ]
}
```
Response carries the net premium (BUY = +, SELL = −, multiplied by qty), aggregated Greeks, and the per-leg breakdown.

**Observability**

| Verb | Path | Description |
|------|------|-------------|
| `GET`  | `/v1/health`     | Liveness — server is alive. Always open (no auth). |
| `GET`  | `/v1/ready`      | Readiness — engines have JIT-warmed. 503 with `NOT_READY` until they have. |
| `GET`  | `/metrics`       | Prometheus text-format scrape endpoint. Always open (no auth). |
| `GET`  | `/openapi.json`  | OpenAPI 3.0 spec for the v1 surface. Always open (no auth). |

**v3.2 endpoints**

| Verb | Path | Description |
|------|------|-------------|
| `POST` | `/v1/heston/calibrate`       | Nelder-Mead calibration of `(v0, κ, θ, ξ, ρ)` against market quotes. Returns fitted params + RMSE + iterations. |
| `POST` | `/v1/vol-surface/eval`       | Fit a surface from quotes and return the interpolated σ at one `(K, T)`. |
| `POST` | `/v1/price/european-forward` | Black-76 — price European on a forward / futures. |
| `POST` | `/v1/greeks/european-forward`| Black-76 Greeks. |
| `POST` | `/v1/prob/itm`               | Risk-neutral P(S_T finishes ITM). |
| `POST` | `/v1/prob/touch`             | P(underlying touches barrier B before expiry). |

**MC standard error on Monte Carlo responses**

Any MC / LSM / Heston-MC response now includes `stdError` (in price units) and `paths`:

```json
{ "price": 5.7516, "model": "MC", "stdError": 0.0247, "paths": 50000 }
```

Closed-form pricers (BS, Binomial, PDE, Heston-Fourier) return `null` for both.

**Vol-surface as pricer input**

`/v1/price/european` and `/v1/greeks/european` accept an optional `surface` field. When present the engine fits a surface from the supplied quotes and uses σ from `volAt(K, T)` instead of the request's flat `volatility`:

```json
{
  "type": "CALL", "spot": 100, "strike": 100,
  "rate": 0.05, "volatility": 0.0, "timeToExpiry": 1.0,
  "surface": {
    "quotes": [
      { "strike":  90, "timeToExpiry": 1.0, "type": "CALL", "marketPrice": 15.7 },
      { "strike": 100, "timeToExpiry": 1.0, "type": "CALL", "marketPrice": 10.45 },
      { "strike": 110, "timeToExpiry": 1.0, "type": "CALL", "marketPrice": 6.5 }
    ]
  }
}
```

The `volatility` field is required by the schema but ignored when `surface` is present.

**maxLatencyMs**

Any v1 endpoint accepts an optional top-level `maxLatencyMs: <number>` field. MC / LSM engines check the deadline at simulation-batch boundaries on every parallel worker; if exceeded the response is HTTP 504 with code `DEADLINE_EXCEEDED`. Closed-form pricers finish well under any sensible deadline and aren't affected.

**Multiple bearer tokens**

`--token a,b,c` (or `OPTIONPRICER_API_TOKEN=a,b,c`) accepts any of the listed values. Constant-time compare across the whole set so timing doesn't leak how many tokens exist. Rotate by deploying the new value as an additional accepted token, migrating clients, then redeploying with the old value removed.

### Request bodies

All bodies accept an optional **`dividends`** field of the shape:

```json
"dividends": {
  "continuousYield": 0.03,
  "discrete": [
    { "time": 0.25, "amount": 1.5 },
    { "time": 0.50, "amount": 1.5 }
  ]
}
```

Both `continuousYield` and `discrete` are independently optional; omit either or both. Times are in years from today.

**Pricing-model selector** — `/price/*`, `/greeks/*` and `/implied-vol/*` for European and American accept an optional `model` field. Allowed values:

| Endpoint family | `model` values | Default |
|-----------------|----------------|---------|
| `/price/european`, `/greeks/european`, `/implied-vol/european` | `BS`, `BINOMIAL`, `PDE`, `HESTON`, `AUTO` | `BS` |
| `/price/american`, `/greeks/american`, `/implied-vol/american` | `LSM`, `BINOMIAL`, `PDE`, `AUTO` | `LSM` |
| `/price/asian`, `/price/barrier`, `/price/lookback` | `MC`, `HESTON` | `MC` |

When `model=HESTON`, the body must also include a `heston` object:
```json
"heston": { "v0": 0.04, "kappa": 1.5, "theta": 0.04, "xi": 0.30, "rho": -0.7 }
```

When `model=AUTO`, the response includes the per-model contributions:
```json
{
  "price": 10.4486,
  "model": "AUTO",
  "contributions": { "BS": 10.4506, "BINOMIAL": 10.4486, "PDE": 10.4481 }
}
```

**European**
```json
{
  "type": "CALL",
  "spot": 100,
  "strike": 100,
  "rate": 0.05,
  "volatility": 0.20,
  "timeToExpiry": 1.0
}
```

**Asian**
```json
{
  "type": "CALL",
  "spot": 100, "strike": 100, "rate": 0.05, "volatility": 0.20, "timeToExpiry": 1.0,
  "timeSteps": 252,
  "discreteMonitoring": true,
  "arithmeticAverage": true,
  "simulations": 100000
}
```

**Barrier**
```json
{
  "type": "CALL",
  "spot": 100, "strike": 100, "rate": 0.05, "volatility": 0.20, "timeToExpiry": 1.0,
  "timeSteps": 252,
  "discreteMonitoring": false,
  "barrier": 120,
  "upBarrier": true,
  "inBarrier": false,
  "simulations": 100000
}
```

**Lookback**
```json
{
  "type": "CALL",
  "spot": 100, "strike": 100, "rate": 0.05, "volatility": 0.20, "timeToExpiry": 1.0,
  "timeSteps": 252,
  "discreteMonitoring": false,
  "fixedStrike": true,
  "simulations": 100000
}
```

**American**
```json
{
  "type": "PUT",
  "spot": 100, "strike": 100, "rate": 0.05, "volatility": 0.20, "timeToExpiry": 1.0,
  "exerciseDates": 50,
  "simulations": 50000
}
```

**Implied volatility (European)**
```json
{
  "type": "CALL",
  "spot": 100, "strike": 100, "rate": 0.05, "timeToExpiry": 1.0,
  "marketPrice": 10.45
}
```

**Implied volatility (Asian / Barrier / Lookback / American)** — same shape as the corresponding `/price/*` request, but with `marketPrice` instead of `volatility`. Example for Asian:
```json
{
  "type": "CALL",
  "spot": 100, "strike": 100, "rate": 0.05, "timeToExpiry": 1.0,
  "marketPrice": 5.78,
  "timeSteps": 252, "discreteMonitoring": true, "arithmeticAverage": true,
  "simulations": 25000
}
```

`simulations` is optional on every Monte Carlo / LSM endpoint; omit it to use the engine default (100 000 for path-dependent pricing, 50 000 for American pricing, 50 000 / 25 000 for path-dependent / American Greeks, 25 000 / 15 000 for path-dependent / American IV). `type` accepts `"CALL"` or `"PUT"` case-insensitively. `dividends` is optional on every endpoint.

### CORS

Every endpoint sends `Access-Control-Allow-Origin: *` and handles `OPTIONS` preflight, so the API is callable from a browser context (e.g. `fetch`).

### Authentication

If the server is started with `--token <value>` (or `OPTIONPRICER_API_TOKEN` is set in the environment), every endpoint **except `/health` and `OPTIONS` preflight** requires:

```
Authorization: Bearer <value>
```

- Wrong / missing / malformed header → **HTTP 401** with `WWW-Authenticate: Bearer` and a JSON body `{ "error": "missing or invalid Authorization header — expected: Bearer <token>" }`.
- Token comparison is constant-time (`MessageDigest.isEqual`) to avoid timing leaks.
- CORS headers are still emitted on 401 so browser-side `fetch` can read the error.
- Without a token configured the server runs in **open mode** — intended for localhost dev only.

Transport security is not implemented in-process — terminate TLS / mTLS at a reverse proxy.

### Examples

```bash
# Health check
curl -s http://localhost:8080/health
# → {"status":"ok"}

# European call (textbook BS)
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0}' \
     http://localhost:8080/price/european
# → {"price":10.450575619322287}

# Same call, server started with --token sk_secret_xyz
curl -s -X POST -H "Content-Type: application/json" \
     -H "Authorization: Bearer sk_secret_xyz" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0}' \
     http://localhost:8080/price/european
# → {"price":10.450575619322287,"model":"BS"}

# Same call with a 3% continuous dividend yield
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "dividends":{"continuousYield":0.03}}' \
     http://localhost:8080/price/european
# → {"price":8.652526401581632,"model":"BS"}

# Same call priced with CRR binomial
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "model":"BINOMIAL"}' \
     http://localhost:8080/price/european
# → {"price":10.4486,"model":"BINOMIAL"}

# Heston stochastic vol
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "model":"HESTON",
          "heston":{"v0":0.04,"kappa":1.5,"theta":0.04,"xi":0.30,"rho":-0.7}}' \
     http://localhost:8080/price/european
# → {"price":10.3619,"model":"HESTON"}

# Auto-mode trimmed mean — also returns per-model contributions
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"PUT","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "exerciseDates":50,"model":"AUTO"}' \
     http://localhost:8080/price/american
# → {"price":6.0861,"model":"AUTO","contributions":{"LSM":6.053,"BINOMIAL":6.090,"PDE":6.086}}

# European Greeks
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0}' \
     http://localhost:8080/greeks/european
# → {"price":10.45,"greeks":{"delta":0.6368,"gamma":0.0188,"vega":0.3752,"theta":-0.0176,"rho":0.5323}}

# Implied volatility (European)
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"timeToExpiry":1.0,"marketPrice":10.45}' \
     http://localhost:8080/implied-vol/european
# → {"impliedVolatility":0.1999847,"price":10.45000}

# Implied volatility (Asian arithmetic call) via CRN-seeded MC inversion
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"timeToExpiry":1.0,
          "marketPrice":5.78,"timeSteps":252,
          "discreteMonitoring":true,"arithmeticAverage":true,"simulations":25000}' \
     http://localhost:8080/implied-vol/asian
# → {"impliedVolatility":~0.20,"price":~5.78}

# American put (LSM)
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"PUT","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,"exerciseDates":50}' \
     http://localhost:8080/price/american
# → {"price":~6.03}

# Asian arithmetic call under Heston (two-factor MC)
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "timeSteps":252,"discreteMonitoring":true,"arithmeticAverage":true,
          "model":"HESTON",
          "heston":{"v0":0.04,"kappa":1.5,"theta":0.04,"xi":0.30,"rho":-0.7}}' \
     http://localhost:8080/price/asian
# → {"price":~5.77,"model":"HESTON"}

# Volatility surface fit
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"spot":100,"rate":0.05,"quotes":[
          {"strike":90,"timeToExpiry":0.25,"type":"CALL","marketPrice":11.5},
          {"strike":100,"timeToExpiry":0.25,"type":"CALL","marketPrice":4.6},
          {"strike":110,"timeToExpiry":0.25,"type":"CALL","marketPrice":1.0},
          {"strike":90,"timeToExpiry":1.0,"type":"CALL","marketPrice":15.7},
          {"strike":100,"timeToExpiry":1.0,"type":"CALL","marketPrice":10.45},
          {"strike":110,"timeToExpiry":1.0,"type":"CALL","marketPrice":6.5}]}' \
     http://localhost:8080/vol-surface/fit
# → {"points":[{strike,timeToExpiry,impliedVolatility}, ...],"failures":[]}

# Asian arithmetic call with two discrete dividends
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "timeSteps":252,"discreteMonitoring":true,"arithmeticAverage":true,
          "dividends":{"discrete":[{"time":0.25,"amount":1},{"time":0.75,"amount":1}]}}' \
     http://localhost:8080/price/asian

# Grid pricing — one MC pass, five strikes
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"spot":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "timeSteps":252,"discreteMonitoring":true,"arithmeticAverage":true,
          "simulations":100000,"seed":42,
          "contracts":[
            {"type":"CALL","strike":90},{"type":"CALL","strike":95},
            {"type":"CALL","strike":100},{"type":"CALL","strike":105},{"type":"CALL","strike":110}]}' \
     http://localhost:8080/v1/grid/asian
# → {"results":[{"type":"CALL","strike":90,"price":...}, ...]}

# Batch pricing — 200 strikes in one call
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"requests":[
          {"type":"CALL","spot":100,"strike":95, "rate":0.05,"volatility":0.20,"timeToExpiry":1.0},
          {"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0},
          {"type":"CALL","spot":100,"strike":105,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0}]}' \
     http://localhost:8080/v1/batch/price/european
# → {"results":[{"ok":true,"result":{"price":...,"model":"BS"}}, ...]}

# Bull call spread — net premium + aggregated Greeks
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"spot":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "legs":[
            {"type":"CALL","strike":100,"side":"BUY","qty":1},
            {"type":"CALL","strike":110,"side":"SELL","qty":1}]}' \
     http://localhost:8080/v1/price/spread
# → {"netPrice":4.41,"netGreeks":{...},"legs":[...]}

# Metrics scrape
curl -s http://localhost:8080/metrics | head

# Heston calibration against market quotes
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"spot":100,"rate":0.05,"quotes":[
          {"strike":90, "timeToExpiry":0.5,"type":"CALL","marketPrice":13.0},
          {"strike":100,"timeToExpiry":0.5,"type":"CALL","marketPrice":6.5},
          {"strike":110,"timeToExpiry":0.5,"type":"CALL","marketPrice":2.5},
          {"strike":90, "timeToExpiry":1.0,"type":"CALL","marketPrice":15.7},
          {"strike":100,"timeToExpiry":1.0,"type":"CALL","marketPrice":10.45},
          {"strike":110,"timeToExpiry":1.0,"type":"CALL","marketPrice":6.5}]}' \
     http://localhost:8080/v1/heston/calibrate
# → {"params":{"v0":0.04,"kappa":1.5,"theta":0.04,"xi":0.30,"rho":-0.7},"rmse":0.02,"iterations":120,"converged":true}

# Black-76 — option on a future
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","forward":105,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0}' \
     http://localhost:8080/v1/price/european-forward
# → {"price":10.37,"model":"BLACK76"}

# Probability of touch — short-strike risk filter
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"spot":100,"barrier":120,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0}' \
     http://localhost:8080/v1/prob/touch
# → {"probability":0.413}

# Surface as pricer input — no separate IV round-trip
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0,"timeToExpiry":1.0,
          "surface":{"quotes":[
            {"strike":90, "timeToExpiry":1.0,"type":"CALL","marketPrice":15.7},
            {"strike":100,"timeToExpiry":1.0,"type":"CALL","marketPrice":10.45},
            {"strike":110,"timeToExpiry":1.0,"type":"CALL","marketPrice":6.5}]}}' \
     http://localhost:8080/v1/price/european
# → σ from surface ≈ 0.20, price = 10.45

# maxLatencyMs cap on a heavy MC request
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "timeSteps":252,"discreteMonitoring":true,"arithmeticAverage":true,
          "simulations":10000000,"maxLatencyMs":50}' \
     http://localhost:8080/v1/price/asian
# → 504 {"error":{"code":"DEADLINE_EXCEEDED",...}}

# OpenAPI spec
curl -s http://localhost:8080/openapi.json | jq '.paths | keys[:5]'
```

## Tech Stack

- Java 17
- Maven
- Swing + **FlatLaf** + **MigLayout**
- **Gson** for JSON
- `com.sun.net.httpserver.HttpServer` (JDK built-in) — no servlet container required

## Validation Rules

- Spot price must be positive
- Strike price must be positive
- Risk-free rate in `(−0.2, 1.0)` (negative rates allowed)
- Volatility in `(0, 5)`
- Time to expiry must be positive (in years)
- Time steps must be `≥ 2`
- Barrier level must be positive
- American exercise dates must be `≥ 2`
- Continuous dividend yield in `[0, 1)`
- Discrete dividend times must be positive; amounts must be non-negative
- `spot − PV(discrete dividends)` must remain positive
- For implied-vol: market price must lie within the no-arbitrage band

## Current Limitations

- Discrete-dividend handling in the closed-form / tree / PDE engines uses the escrowed model — exact for the standard convention, but not the only valid approach
- For options non-monotone in σ (most notably up-and-out and down-and-out calls), the IV solver returns one valid root — there can be a second on the same target price
- Binomial / PDE engines apply only to European and American payoffs; path-dependent options use MC or Heston-MC
- The path-dependent Brownian-bridge corrections (continuous-monitoring Barrier / Lookback) assume constant σ — under Heston they become an approximation (`σ ≈ √θ`). Prefer discrete monitoring with a fine grid for Heston path-dependent
- For options whose price is non-monotonic in σ (e.g. up-and-out calls) the IV solver returns one valid root — there may be a second
- IV solver floors σ at 1% (covers every real-world option; avoids the CRR binomial parametrisation degeneracy when `|r-q|·√Δt > σ·√Δt`)
- TLS / mTLS is out of scope in-process — when exposing beyond localhost, terminate at a reverse proxy. Bearer-token authentication is built in (see [Authentication](#authentication)).

## Roadmap

v3.2 closes the bot-consumer roadmap. One enhancement remains for a future cut if a real production use case demands it:

- **WebSocket streaming Greeks** — a `/stream/greeks` channel that pushes Δ / Γ / ν updates when spot crosses a configurable bucket. Useful for sub-50 ms delta-hedging loops; the JDK `HttpServer` doesn't speak WS so the implementation would need either a WS library or a hand-rolled RFC 6455 framer.

Quant-model extensions that are deliberately out of scope (each is real work and only worth doing for a specific concrete use case): full discount-curve input replacing the flat-rate `rate` field; FX / borrow carry conventions separate from dividend yield; vanna / volga / charm / color second-order Greeks; SABR calibration (Heston is already in).

## Motivation

This project was built as a passion project to combine:

- Java desktop development
- GUI design
- numerical finance
- mathematical modelling

## Author

**Lorenzo Barbagelata**

## License

This project is distributed under a **Custom Proprietary License**.

You may:

- use the software for personal, educational, or internal evaluation purposes

You may not:

- modify, adapt, alter, translate, or create derivative works of the software
- redistribute, rehost, mirror, sublicense, sell, or independently distribute the source code or any compiled binary
- publish modified or unmodified copies of the software
- remove copyright or authorship notices

All rights not expressly granted are reserved by the author.

If you would like permission to modify, redistribute, or use this software beyond the scope above, please contact the author directly.

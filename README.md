# OptionPricer

A Java application for pricing options — **European, Asian, Barrier, Lookback, and American** — through a modern Swing GUI **or** as an HTTP/JSON service.

## Release

**Current release: v2.3**

Highlights since v1.0:

- Path-dependent options (Asian, Barrier, Lookback) priced by a parallel antithetic Monte Carlo engine.
- American options priced by **Longstaff–Schwartz** regression Monte Carlo.
- Both **discrete** and **continuous** monitoring for the path-dependent types, including Brownian-bridge corrections.
- **Multiple pricing engines** — Black-Scholes closed form, **Cox-Ross-Rubinstein binomial**, **Crank-Nicolson PDE**, **Heston (Fourier inversion)** alongside Monte Carlo and LSM.
- **Auto mode**: trimmed mean of model outputs ("mean of the two middle outputs") — robust against a single misbehaving engine.
- **Greeks** (Δ, Γ, ν, Θ, ρ) for every option type — closed-form for European, common-random-numbers finite differences for the others.
- **Implied volatility** solver for **every** option type — closed-form for European, CRN-based MC / LSM solver for the rest.
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
| Asian / Barrier / Lookback | MC (path-dependent — no Auto) | — |

### Greeks & implied vol
- **Greeks** (delta, gamma, vega, theta, rho) for every option type
  - European: closed-form Black–Scholes
  - Path-dependent / American: common-random-numbers (CRN) finite differences against seeded MC / LSM — variance stays in the bump, not the simulation
  - Output in practitioner conventions: vega per 1% σ, theta per day, rho per 1% rate
- **Implied volatility** solver for every option type
  - European: Newton-Raphson with analytic vega + bisection fallback
  - Path-dependent / American: CRN-seeded pricer so each σ candidate is a deterministic, smooth function of σ (no MC noise across iterations); Newton-Raphson with finite-difference vega; bisection fallback on a pre-checked `[σ_lo, σ_hi]` bracket
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

The server listens on the given port (defaults to 8080 if omitted).

### Prebuilt v1.0 JAR

[**Download the v1.0 release**](https://drive.google.com/file/d/1REndptovCK-ZLfrBIOfMd7VRly_QwX_W/view?usp=sharing) — note this is the original European-only release; for the full feature set, build from source.

## API

All endpoints accept and return `application/json`. Successful responses have the shape:

```json
{ "price": 10.450575619322287 }
```

Errors return an HTTP 4xx/5xx status with:

```json
{ "error": "human-readable message" }
```

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

**Pricing-model selector** — `/price/european` and `/price/american` accept an optional `model` field. Allowed values:

| Endpoint | `model` values | Default |
|----------|----------------|---------|
| `/price/european` | `BS`, `BINOMIAL`, `PDE`, `HESTON`, `AUTO` | `BS` |
| `/price/american` | `LSM`, `BINOMIAL`, `PDE`, `AUTO` | `LSM` |

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

# Asian arithmetic call with two discrete dividends
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"type":"CALL","spot":100,"strike":100,"rate":0.05,"volatility":0.20,"timeToExpiry":1.0,
          "timeSteps":252,"discreteMonitoring":true,"arithmeticAverage":true,
          "dividends":{"discrete":[{"time":0.25,"amount":1},{"time":0.75,"amount":1}]}}' \
     http://localhost:8080/price/asian
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
- Binomial / PDE / Heston engines apply only to European and (for binomial/PDE) American payoffs; the path-dependent option types remain MC-only
- Greeks / implied vol still use the engines from v2.2 (closed-form BS for European, CRN MC/LSM for the rest) — they do not yet dispatch by `model`
- No API authentication — bind to localhost or front with a reverse proxy in any non-trivial deployment

## Roadmap

Possible future improvements:

- model-aware Greeks and implied volatility (currently they always use the v2.2 engines regardless of the price selector)
- Heston for path-dependent payoffs via two-factor MC
- volatility surface fitter (term structure + skew)
- batch-pricing endpoint on the API
- API authentication for non-local deployments

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

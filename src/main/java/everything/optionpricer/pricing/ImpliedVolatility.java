package everything.optionpricer.pricing;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.Option;
import everything.optionpricer.model.PathDependentOption;

import java.util.concurrent.ThreadLocalRandom;


/**
 * Implied volatility solver.
 *
 * European (closed-form):
 *   Newton-Raphson using analytic Black-Scholes vega, with a bisection
 *   fallback for deep ITM/OTM (where vega is too flat).
 *
 * Path-dependent / American (Monte Carlo / LSM):
 *   We price with {@code priceSeeded(...)} so that for a fixed seed the
 *   price is a deterministic, smooth function of σ — Monte Carlo noise
 *   does NOT enter as iteration jitter. Newton-Raphson uses a finite-
 *   difference vega from two CRN-seeded re-prices; bisection fallback
 *   on a pre-checked bracket {@code [σ_lo, σ_hi]}.
 *
 * Caveat for non-monotonic price/σ relationships (e.g. up-and-out calls
 * become less valuable as σ grows past the knockout threshold): the
 * price-vs-σ curve can have two intersections with the target price.
 * The solver finds one of them; which one depends on the starting guess.
 *
 * @author lorenzobarbagelata
 */
public final class ImpliedVolatility {

    private static final double TOL = 1.0e-8;
    private static final int    MAX_NEWTON = 80;
    private static final int    MAX_BISECT = 200;

    // Engines reject σ <= 0 or σ >= 5; stay strictly inside that band.
    private static final double SIGMA_LOWER = 1.0e-3;
    private static final double SIGMA_UPPER = 4.99;

    /** Defaults for MC / LSM IV — kept modest because we revaluate many times. */
    public static final int DEFAULT_MC_SIMS   = 25_000;
    public static final int DEFAULT_LSM_PATHS = 15_000;

    private ImpliedVolatility() {}


    // ============================================================
    //  European — closed-form path (analytic vega + bisection)
    // ============================================================

    public static double impliedVolatility(EuropeanOption option, double spot,
                                           double riskFreeRate, double marketPrice) {
        return impliedVolatility(option, spot, riskFreeRate, marketPrice, DividendSchedule.NONE);
    }


    public static double impliedVolatility(EuropeanOption option, double spot,
                                           double riskFreeRate, double marketPrice,
                                           DividendSchedule dividends) {

        if(option == null)        throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0)             throw new IllegalArgumentException("Spot must be positive");
        if(marketPrice <= 0)      throw new IllegalArgumentException("Market price must be positive");
        if(dividends == null)     dividends = DividendSchedule.NONE;

        double K = option.getStrikePrice();
        double T = option.getTimeToExpiry();
        double q = dividends.continuousYield();
        double Seff = spot - dividends.presentValueBefore(T, riskFreeRate);
        if(Seff <= 0) throw new IllegalArgumentException("Spot minus PV of discrete dividends is non-positive");

        double discS = Seff * Math.exp(-q          * T);
        double discK = K    * Math.exp(-riskFreeRate * T);

        double lower, upper;
        if(option.isCall()) { lower = Math.max(discS - discK, 0.0); upper = discS; }
        else                { lower = Math.max(discK - discS, 0.0); upper = discK; }
        if(marketPrice < lower - 1e-9 || marketPrice > upper + 1e-9) {
            throw new IllegalArgumentException(String.format(
                    "Market price %.4f is outside the no-arbitrage band [%.4f, %.4f]",
                    marketPrice, lower, upper));
        }

        double sigma = Math.sqrt(2.0 * Math.PI / T) * marketPrice / discS;
        if(!Double.isFinite(sigma) || sigma <= SIGMA_LOWER) sigma = 0.2;
        if(sigma > SIGMA_UPPER) sigma = SIGMA_UPPER * 0.5;

        for(int i = 0; i < MAX_NEWTON; i++) {
            double price = BlackScholesEngine.price(option, spot, riskFreeRate, sigma, dividends).getPrice();
            double err   = price - marketPrice;
            if(Math.abs(err) < TOL) return sigma;

            Greeks g = BlackScholesEngine.greeks(option, spot, riskFreeRate, sigma, dividends);
            double vegaPerUnit = g.vega() / 0.01;
            if(vegaPerUnit < 1e-10) break;

            double step = err / vegaPerUnit;
            double next = sigma - step;
            if(!Double.isFinite(next) || next <= SIGMA_LOWER || next >= SIGMA_UPPER) break;
            if(Math.abs(next - sigma) > 1.0) break;
            sigma = next;
        }

        // Bisection on BS — no need to bracket-check, the closed form is well-behaved.
        DividendSchedule divs = dividends;
        return bisect(
                s -> BlackScholesEngine.price(option, spot, riskFreeRate, s, divs).getPrice(),
                marketPrice, SIGMA_LOWER, SIGMA_UPPER, sigma);
    }


    // ============================================================
    //  Path-dependent (Asian/Barrier/Lookback) — MC with CRN
    // ============================================================

    public static double impliedVolatility(PathDependentOption option, double spot,
                                           double riskFreeRate, double marketPrice) {
        return impliedVolatility(option, spot, riskFreeRate, marketPrice,
                                 DividendSchedule.NONE, DEFAULT_MC_SIMS);
    }

    public static double impliedVolatility(PathDependentOption option, double spot,
                                           double riskFreeRate, double marketPrice,
                                           DividendSchedule dividends) {
        return impliedVolatility(option, spot, riskFreeRate, marketPrice, dividends, DEFAULT_MC_SIMS);
    }

    public static double impliedVolatility(PathDependentOption option, double spot,
                                           double riskFreeRate, double marketPrice,
                                           DividendSchedule dividends, int sims) {
        validateMcInputs(option, spot, marketPrice, sims);
        DividendSchedule divs = dividends == null ? DividendSchedule.NONE : dividends;
        long seed = ThreadLocalRandom.current().nextLong();

        SigmaPricer pricer = s ->
                MonteCarloEngine.priceSeeded(option, spot, riskFreeRate, s, sims, seed, divs).getPrice();

        return solveWithCrn(pricer, marketPrice);
    }


    // ============================================================
    //  American — LSM with CRN
    // ============================================================

    public static double impliedVolatility(AmericanOption option, double spot,
                                           double riskFreeRate, double marketPrice) {
        return impliedVolatility(option, spot, riskFreeRate, marketPrice,
                                 DividendSchedule.NONE, DEFAULT_LSM_PATHS);
    }

    public static double impliedVolatility(AmericanOption option, double spot,
                                           double riskFreeRate, double marketPrice,
                                           DividendSchedule dividends) {
        return impliedVolatility(option, spot, riskFreeRate, marketPrice, dividends, DEFAULT_LSM_PATHS);
    }

    public static double impliedVolatility(AmericanOption option, double spot,
                                           double riskFreeRate, double marketPrice,
                                           DividendSchedule dividends, int paths) {
        if(option == null) throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0)      throw new IllegalArgumentException("Spot must be positive");
        if(marketPrice <= 0) throw new IllegalArgumentException("Market price must be positive");
        if(paths <= 0)     throw new IllegalArgumentException("Paths must be positive");
        DividendSchedule divs = dividends == null ? DividendSchedule.NONE : dividends;
        long seed = ThreadLocalRandom.current().nextLong();

        SigmaPricer pricer = s ->
                LongstaffSchwartzEngine.priceSeeded(option, spot, riskFreeRate, s, paths, seed, divs).getPrice();

        return solveWithCrn(pricer, marketPrice);
    }


    // ============================================================
    //  Generic dispatch
    // ============================================================

    public static double impliedVolatility(Option option, double spot, double r,
                                           double marketPrice, DividendSchedule dividends) {
        if(option instanceof EuropeanOption eu)        return impliedVolatility(eu, spot, r, marketPrice, dividends);
        if(option instanceof PathDependentOption pd)   return impliedVolatility(pd, spot, r, marketPrice, dividends);
        if(option instanceof AmericanOption am)        return impliedVolatility(am, spot, r, marketPrice, dividends);
        throw new IllegalArgumentException("Unsupported option type: " + option.getClass().getSimpleName());
    }


    // ============================================================
    //  Core: CRN-based solver (used by both MC and LSM paths)
    // ============================================================

    @FunctionalInterface
    private interface SigmaPricer { double price(double sigma); }


    private static double solveWithCrn(SigmaPricer pricer, double marketPrice) {

        // 1. Bracket check at the extremes.
        double pLo = pricer.price(SIGMA_LOWER);
        double pHi = pricer.price(SIGMA_UPPER);
        double fLo = pLo - marketPrice;
        double fHi = pHi - marketPrice;

        // Most reasonable options are monotone non-decreasing in σ over [SIGMA_LOWER,
        // SIGMA_UPPER]. If the target lies outside that, refuse with a helpful message.
        if(fLo > 0 && fHi > 0) {
            throw new IllegalArgumentException(String.format(
                    "Market price %.4f is below the σ→0 price %.4f — no implied vol exists",
                    marketPrice, pLo));
        }
        if(fLo < 0 && fHi < 0) {
            throw new IllegalArgumentException(String.format(
                    "Market price %.4f is above the price at σ=%.2f (%.4f) — no implied vol exists in [%.3f, %.2f]",
                    marketPrice, SIGMA_UPPER, pHi, SIGMA_LOWER, SIGMA_UPPER));
        }

        // 2. Newton-Raphson with CRN finite-difference vega.
        //    Initial guess: Brenner-Subrahmanyam scaled by p/(p+something) as a soft prior.
        double sigma = 0.2;
        double dV = 0.005;
        for(int i = 0; i < MAX_NEWTON; i++) {
            double price = pricer.price(sigma);
            double err   = price - marketPrice;
            if(Math.abs(err) < TOL) return sigma;

            double pUp = pricer.price(sigma + dV);
            double vega = (pUp - price) / dV;
            if(Math.abs(vega) < 1e-8) break;

            double next = sigma - err / vega;
            if(!Double.isFinite(next) || next <= SIGMA_LOWER || next >= SIGMA_UPPER) break;
            if(Math.abs(next - sigma) > 1.0) break;
            sigma = next;
        }

        // 3. Bisection fallback. Requires fLo and fHi to bracket zero.
        return bisect(pricer, marketPrice, SIGMA_LOWER, SIGMA_UPPER, sigma);
    }


    private static double bisect(SigmaPricer pricer, double marketPrice,
                                 double lo, double hi, double fallback) {
        double fLo = pricer.price(lo) - marketPrice;
        double fHi = pricer.price(hi) - marketPrice;
        if(fLo * fHi > 0) return fallback;

        double mid = fallback;
        for(int i = 0; i < MAX_BISECT; i++) {
            mid = 0.5 * (lo + hi);
            double f = pricer.price(mid) - marketPrice;
            if(Math.abs(f) < TOL || (hi - lo) < 1e-10) return mid;
            if(f * fLo < 0) { hi = mid; }
            else            { lo = mid; fLo = f; }
        }
        return mid;
    }


    private static void validateMcInputs(Object opt, double spot, double price, int sims) {
        if(opt == null)    throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0)      throw new IllegalArgumentException("Spot must be positive");
        if(price <= 0)     throw new IllegalArgumentException("Market price must be positive");
        if(sims <= 0)      throw new IllegalArgumentException("Sims must be positive");
    }
}

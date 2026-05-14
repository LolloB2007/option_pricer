package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.Option;


/**
 * Implied volatility solver for European options under Black-Scholes,
 * with optional continuous yield + discrete dividends.
 *
 * Strategy: Newton-Raphson using BS vega; falls back to bisection if vega
 * goes flat (deep ITM/OTM) or the Newton step misbehaves. Validates the
 * target price against the no-arbitrage bounds before solving.
 *
 * Only European options are supported here — implied vol for path-dependent
 * or American options would require iterating a slow MC pricer.
 *
 * @author lorenzobarbagelata
 */
public final class ImpliedVolatility {

    private static final double TOL = 1.0e-8;
    private static final int    MAX_NEWTON = 80;
    private static final int    MAX_BISECT = 200;

    private static final double SIGMA_LOWER = 1.0e-6;
    private static final double SIGMA_UPPER = 5.0;

    private ImpliedVolatility() {}


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

        // No-arbitrage bounds — outside these no σ exists.
        double lower, upper;
        if(option.isCall()) { lower = Math.max(discS - discK, 0.0); upper = discS; }
        else                { lower = Math.max(discK - discS, 0.0); upper = discK; }
        if(marketPrice < lower - 1e-9 || marketPrice > upper + 1e-9) {
            throw new IllegalArgumentException(String.format(
                    "Market price %.4f is outside the no-arbitrage band [%.4f, %.4f]",
                    marketPrice, lower, upper));
        }

        // Brenner-Subrahmanyam closed-form initial guess.
        double sigma = Math.sqrt(2.0 * Math.PI / T) * marketPrice / discS;
        if(!Double.isFinite(sigma) || sigma <= SIGMA_LOWER) sigma = 0.2;
        if(sigma > SIGMA_UPPER) sigma = SIGMA_UPPER * 0.5;

        // ----- Newton-Raphson on σ ----- //
        double sigmaNewton = sigma;
        for(int i = 0; i < MAX_NEWTON; i++) {
            double price = BlackScholesEngine.price(option, spot, riskFreeRate, sigmaNewton, dividends).getPrice();
            double err   = price - marketPrice;
            if(Math.abs(err) < TOL) return sigmaNewton;

            Greeks g = BlackScholesEngine.greeks(option, spot, riskFreeRate, sigmaNewton, dividends);
            // greeks.vega is per 1% bump → un-scale to per 1.0 absolute.
            double vegaPerUnit = g.vega() / 0.01;
            if(vegaPerUnit < 1e-10) break;

            double step = err / vegaPerUnit;
            double next = sigmaNewton - step;
            // Damp if Newton wants to leap out of plausible range.
            if(!Double.isFinite(next) || next <= SIGMA_LOWER || next >= SIGMA_UPPER) break;
            if(Math.abs(next - sigmaNewton) > 1.0) break;
            sigmaNewton = next;
        }

        // ----- Bisection fallback ----- //
        double lo = SIGMA_LOWER, hi = SIGMA_UPPER;
        double fLo = BlackScholesEngine.price(option, spot, riskFreeRate, lo, dividends).getPrice() - marketPrice;
        double fHi = BlackScholesEngine.price(option, spot, riskFreeRate, hi, dividends).getPrice() - marketPrice;
        if(fLo * fHi > 0) {
            // Bracket failed — return best Newton estimate, even if imperfect.
            return sigmaNewton;
        }
        double mid = sigmaNewton;
        for(int i = 0; i < MAX_BISECT; i++) {
            mid = 0.5 * (lo + hi);
            double f = BlackScholesEngine.price(option, spot, riskFreeRate, mid, dividends).getPrice() - marketPrice;
            if(Math.abs(f) < TOL || (hi - lo) < 1e-12) return mid;
            if(f * fLo < 0) { hi = mid; fHi = f; }
            else            { lo = mid; fLo = f; }
        }
        return mid;
    }


    /** Convenience overload that accepts a generic Option (must be European). */
    public static double impliedVolatility(Option option, double spot, double r,
                                           double marketPrice, DividendSchedule dividends) {
        if(option instanceof EuropeanOption eu) {
            return impliedVolatility(eu, spot, r, marketPrice, dividends);
        }
        throw new IllegalArgumentException("Implied volatility solver only supports European options");
    }
}

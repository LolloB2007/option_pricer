package everything.optionpricer.pricing;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.AsianOption;
import everything.optionpricer.model.BarrierOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.LookbackOption;
import everything.optionpricer.model.Option;
import everything.optionpricer.model.PathDependentOption;

import java.util.concurrent.ThreadLocalRandom;


/**
 * Greeks for every option type.
 *
 *  - European: closed-form (delegates to {@link BlackScholesEngine#greeks}).
 *  - Path-dependent and American: central finite differences with common
 *    random numbers — every bumped revaluation uses the exact same Brownian
 *    paths via the {@code priceSeeded} entry points, so the noise stays in
 *    the bump rather than the simulation. Without CRN, MC gamma is essentially
 *    unusable.
 *
 * Output is in practitioner conventions (see {@link Greeks}).
 *
 * @author lorenzobarbagelata
 */
public final class GreeksCalculator {

    /** Lower sim count by default — Greeks need 8 revaluations per call. */
    private static final int DEFAULT_GREEKS_SIMS = 50_000;
    private static final int DEFAULT_GREEKS_LSM_PATHS = 25_000;

    // Bumps tuned to balance truncation error vs MC noise.
    private static final double BUMP_S_REL = 0.01;   // 1% of spot
    private static final double BUMP_VOL    = 0.005; // absolute σ bump
    private static final double BUMP_RATE   = 1e-4;  // 1bp
    private static final double BUMP_T_DAYS = 1.0;   // 1 calendar day

    private GreeksCalculator() {}


    // ============================================================
    //  European — closed form
    // ============================================================

    public static Greeks compute(EuropeanOption option, double spot, double r, double sigma) {
        return BlackScholesEngine.greeks(option, spot, r, sigma);
    }

    public static Greeks compute(EuropeanOption option, double spot, double r, double sigma,
                                 DividendSchedule dividends) {
        return BlackScholesEngine.greeks(option, spot, r, sigma, dividends);
    }


    // ============================================================
    //  Path-dependent (Asian/Barrier/Lookback) — CRN finite differences
    // ============================================================

    public static Greeks compute(PathDependentOption option, double spot, double r, double sigma) {
        return compute(option, spot, r, sigma, DEFAULT_GREEKS_SIMS, DividendSchedule.NONE);
    }

    public static Greeks compute(PathDependentOption option, double spot, double r, double sigma, int sims) {
        return compute(option, spot, r, sigma, sims, DividendSchedule.NONE);
    }

    public static Greeks compute(PathDependentOption option, double spot, double r, double sigma,
                                 DividendSchedule dividends) {
        return compute(option, spot, r, sigma, DEFAULT_GREEKS_SIMS, dividends);
    }

    public static Greeks compute(PathDependentOption option, double spot, double r, double sigma,
                                 int sims, DividendSchedule dividends) {
        if(dividends == null) dividends = DividendSchedule.NONE;
        long seed = ThreadLocalRandom.current().nextLong();
        double T = option.getTimeToExpiry();

        double dS = BUMP_S_REL * spot;
        double dV = BUMP_VOL;
        double dR = BUMP_RATE;
        double dT = Math.min(BUMP_T_DAYS / 365.0, T * 0.5);

        DividendSchedule d = dividends;
        double p0  = MonteCarloEngine.priceSeeded(option, spot,      r,      sigma,      sims, seed, d).getPrice();
        double pSp = MonteCarloEngine.priceSeeded(option, spot + dS, r,      sigma,      sims, seed, d).getPrice();
        double pSm = MonteCarloEngine.priceSeeded(option, spot - dS, r,      sigma,      sims, seed, d).getPrice();
        double pVp = MonteCarloEngine.priceSeeded(option, spot,      r,      sigma + dV, sims, seed, d).getPrice();
        double pVm = MonteCarloEngine.priceSeeded(option, spot,      r,      sigma - dV, sims, seed, d).getPrice();
        double pRp = MonteCarloEngine.priceSeeded(option, spot,      r + dR, sigma,      sims, seed, d).getPrice();
        double pRm = MonteCarloEngine.priceSeeded(option, spot,      r - dR, sigma,      sims, seed, d).getPrice();
        double pTm = MonteCarloEngine.priceSeeded(withTimeToExpiry(option, T - dT),
                                                  spot, r, sigma, sims, seed, d).getPrice();

        return assemble(p0, pSp, pSm, pVp, pVm, pRp, pRm, pTm, dS, dV, dR, dT);
    }


    // ============================================================
    //  American — CRN finite differences via LSM
    // ============================================================

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma) {
        return compute(option, spot, r, sigma, DEFAULT_GREEKS_LSM_PATHS, DividendSchedule.NONE);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma, int paths) {
        return compute(option, spot, r, sigma, paths, DividendSchedule.NONE);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma,
                                 DividendSchedule dividends) {
        return compute(option, spot, r, sigma, DEFAULT_GREEKS_LSM_PATHS, dividends);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma,
                                 int paths, DividendSchedule dividends) {
        if(dividends == null) dividends = DividendSchedule.NONE;
        long seed = ThreadLocalRandom.current().nextLong();
        double T = option.getTimeToExpiry();

        double dS = BUMP_S_REL * spot;
        double dV = BUMP_VOL;
        double dR = BUMP_RATE;
        double dT = Math.min(BUMP_T_DAYS / 365.0, T * 0.5);

        DividendSchedule d = dividends;
        double p0  = LongstaffSchwartzEngine.priceSeeded(option, spot,      r,      sigma,      paths, seed, d).getPrice();
        double pSp = LongstaffSchwartzEngine.priceSeeded(option, spot + dS, r,      sigma,      paths, seed, d).getPrice();
        double pSm = LongstaffSchwartzEngine.priceSeeded(option, spot - dS, r,      sigma,      paths, seed, d).getPrice();
        double pVp = LongstaffSchwartzEngine.priceSeeded(option, spot,      r,      sigma + dV, paths, seed, d).getPrice();
        double pVm = LongstaffSchwartzEngine.priceSeeded(option, spot,      r,      sigma - dV, paths, seed, d).getPrice();
        double pRp = LongstaffSchwartzEngine.priceSeeded(option, spot,      r + dR, sigma,      paths, seed, d).getPrice();
        double pRm = LongstaffSchwartzEngine.priceSeeded(option, spot,      r - dR, sigma,      paths, seed, d).getPrice();

        AmericanOption optMinusT = new AmericanOption(
                option.getStrikePrice(), T - dT, option.getOptionType(), option.getExerciseDates());
        double pTm = LongstaffSchwartzEngine.priceSeeded(optMinusT, spot, r, sigma, paths, seed, d).getPrice();

        return assemble(p0, pSp, pSm, pVp, pVm, pRp, pRm, pTm, dS, dV, dR, dT);
    }


    // ============================================================
    //  Generic dispatch — useful for code that holds an Option ref
    // ============================================================

    public static Greeks compute(Option option, double spot, double r, double sigma) {
        return compute(option, spot, r, sigma, DividendSchedule.NONE);
    }

    public static Greeks compute(Option option, double spot, double r, double sigma,
                                 DividendSchedule dividends) {
        if(option instanceof EuropeanOption eu)     return compute(eu, spot, r, sigma, dividends);
        if(option instanceof PathDependentOption p) return compute(p,  spot, r, sigma, dividends);
        if(option instanceof AmericanOption am)     return compute(am, spot, r, sigma, dividends);
        throw new IllegalArgumentException("Unsupported option type: " + option.getClass().getSimpleName());
    }


    // ============================================================
    //  Helpers
    // ============================================================

    private static Greeks assemble(double p0, double pSp, double pSm,
                                   double pVp, double pVm, double pRp, double pRm, double pTm,
                                   double dS, double dV, double dR, double dT) {

        double delta = (pSp - pSm) / (2.0 * dS);
        double gamma = (pSp - 2.0 * p0 + pSm) / (dS * dS);
        double vega  = (pVp - pVm) / (2.0 * dV);
        double rho   = (pRp - pRm) / (2.0 * dR);
        // theta = ∂P/∂t. With time advancing, T shrinks, so theta = -∂P/∂T.
        // One-sided difference using P(T - dT): theta ≈ -(P0 - P(T-dT))/dT = (P(T-dT) - P0)/dT.
        double theta = (pTm - p0) / dT;

        return new Greeks(
                delta,
                gamma,
                vega  * 0.01,
                theta / 365.0,
                rho   * 0.01);
    }


    /** Rebuild a path-dependent option with a different time-to-expiry. */
    private static PathDependentOption withTimeToExpiry(PathDependentOption opt, double newT) {
        if(opt instanceof AsianOption a) {
            return new AsianOption(a.getStrikePrice(), newT, a.getOptionType(),
                                   a.getTimeSteps(), a.isDiscreteMonitoring(),
                                   a.isArithmeticAverage());
        }
        if(opt instanceof BarrierOption b) {
            return new BarrierOption(b.getStrikePrice(), newT, b.getOptionType(),
                                     b.getTimeSteps(), b.isDiscreteMonitoring(),
                                     b.getBarrier(), b.isUpBarrier(), b.isInBarrier());
        }
        if(opt instanceof LookbackOption l) {
            return new LookbackOption(l.getStrikePrice(), newT, l.getOptionType(),
                                      l.getTimeSteps(), l.isDiscreteMonitoring(),
                                      l.isFixedStrike());
        }
        throw new IllegalArgumentException("Unknown path-dependent option: " + opt.getClass().getSimpleName());
    }
}

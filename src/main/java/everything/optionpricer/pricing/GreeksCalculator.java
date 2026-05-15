package everything.optionpricer.pricing;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.AsianOption;
import everything.optionpricer.model.BarrierOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.HestonParams;
import everything.optionpricer.model.LookbackOption;
import everything.optionpricer.model.Option;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PathDependentOption;

import java.util.concurrent.ThreadLocalRandom;


/**
 * Greeks for every option type, dispatched by pricing model.
 *
 *  - Black-Scholes: closed-form.
 *  - Binomial / PDE: deterministic-pricer finite differences (no CRN
 *    needed — the underlying engines are smooth and deterministic).
 *  - Heston: finite differences on the Heston pricer; vega is reported
 *    in the practitioner convention by bumping σ₀ = √v₀.
 *  - Monte Carlo / LSM (path-dependent and American): CRN-seeded
 *    finite differences — every revaluation uses the same Brownian
 *    increments.
 *  - Auto: trimmed mean of the σ-based model Greeks
 *    ({BS, Binomial, PDE} for European; {LSM, Binomial, PDE} for
 *    American).
 *
 * @author lorenzobarbagelata
 */
public final class GreeksCalculator {

    private static final int DEFAULT_GREEKS_SIMS = 50_000;
    private static final int DEFAULT_GREEKS_LSM_PATHS = 25_000;

    private static final double BUMP_S_REL = 0.01;
    private static final double BUMP_VOL   = 0.005;
    private static final double BUMP_RATE  = 1e-4;
    private static final double BUMP_T_DAYS = 1.0;

    private GreeksCalculator() {}


    // ============================================================
    //  European
    // ============================================================

    public static Greeks compute(EuropeanOption option, double spot, double r, double sigma) {
        return compute(option, spot, r, sigma, DividendSchedule.NONE, PricingModel.BS, null);
    }

    public static Greeks compute(EuropeanOption option, double spot, double r, double sigma,
                                 DividendSchedule dividends) {
        return compute(option, spot, r, sigma, dividends, PricingModel.BS, null);
    }

    public static Greeks compute(EuropeanOption option, double spot, double r, double sigma,
                                 DividendSchedule dividends, PricingModel model, HestonParams heston) {
        if(dividends == null) dividends = DividendSchedule.NONE;
        final DividendSchedule divs = dividends;
        final double T = option.getTimeToExpiry();
        final OptionType type = option.getOptionType();
        final double K = option.getStrikePrice();

        switch(model) {
            case BS:
                return BlackScholesEngine.greeks(option, spot, r, sigma, divs);

            case BINOMIAL:
                return finiteDiffGreeks(
                        (s, v, rr, t) -> BinomialEngine.price(
                                EuropeanOption.of(type, K, t), s, rr, v, divs).getPrice(),
                        spot, r, sigma, T);

            case PDE:
                return finiteDiffGreeks(
                        (s, v, rr, t) -> FiniteDifferenceEngine.price(
                                EuropeanOption.of(type, K, t), s, rr, v, divs).getPrice(),
                        spot, r, sigma, T);

            case HESTON:
                if(heston == null) throw new IllegalArgumentException("Heston Greeks require Heston params");
                return hestonGreeks(option, spot, r, heston, divs);

            case AUTO: {
                Greeks bs  = BlackScholesEngine.greeks(option, spot, r, sigma, divs);
                Greeks bin = compute(option, spot, r, sigma, divs, PricingModel.BINOMIAL, null);
                Greeks pde = compute(option, spot, r, sigma, divs, PricingModel.PDE, null);
                return trimmedGreeks(bs, bin, pde);
            }

            default:
                throw new IllegalArgumentException("Model " + model + " is not applicable to European Greeks");
        }
    }


    // ============================================================
    //  Path-dependent (Asian/Barrier/Lookback) — MC with CRN
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
        DividendSchedule d = dividends;

        double dS = BUMP_S_REL * spot;
        double dV = BUMP_VOL;
        double dR = BUMP_RATE;
        double dT = Math.min(BUMP_T_DAYS / 365.0, T * 0.5);

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
    //  American — dispatched by model
    // ============================================================

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma) {
        return compute(option, spot, r, sigma, DEFAULT_GREEKS_LSM_PATHS, DividendSchedule.NONE, PricingModel.LSM);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma, int paths) {
        return compute(option, spot, r, sigma, paths, DividendSchedule.NONE, PricingModel.LSM);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma,
                                 DividendSchedule dividends) {
        return compute(option, spot, r, sigma, DEFAULT_GREEKS_LSM_PATHS, dividends, PricingModel.LSM);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma,
                                 int paths, DividendSchedule dividends) {
        return compute(option, spot, r, sigma, paths, dividends, PricingModel.LSM);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma,
                                 DividendSchedule dividends, PricingModel model) {
        return compute(option, spot, r, sigma, DEFAULT_GREEKS_LSM_PATHS, dividends, model);
    }

    public static Greeks compute(AmericanOption option, double spot, double r, double sigma,
                                 int paths, DividendSchedule dividends, PricingModel model) {
        if(dividends == null) dividends = DividendSchedule.NONE;
        final DividendSchedule divs = dividends;
        final double T = option.getTimeToExpiry();
        final OptionType type = option.getOptionType();
        final double K = option.getStrikePrice();
        final int dates = option.getExerciseDates();

        switch(model) {
            case LSM: {
                long seed = ThreadLocalRandom.current().nextLong();
                double dS = BUMP_S_REL * spot;
                double dV = BUMP_VOL;
                double dR = BUMP_RATE;
                double dT = Math.min(BUMP_T_DAYS / 365.0, T * 0.5);

                double p0  = LongstaffSchwartzEngine.priceSeeded(option, spot,      r,      sigma,      paths, seed, divs).getPrice();
                double pSp = LongstaffSchwartzEngine.priceSeeded(option, spot + dS, r,      sigma,      paths, seed, divs).getPrice();
                double pSm = LongstaffSchwartzEngine.priceSeeded(option, spot - dS, r,      sigma,      paths, seed, divs).getPrice();
                double pVp = LongstaffSchwartzEngine.priceSeeded(option, spot,      r,      sigma + dV, paths, seed, divs).getPrice();
                double pVm = LongstaffSchwartzEngine.priceSeeded(option, spot,      r,      sigma - dV, paths, seed, divs).getPrice();
                double pRp = LongstaffSchwartzEngine.priceSeeded(option, spot,      r + dR, sigma,      paths, seed, divs).getPrice();
                double pRm = LongstaffSchwartzEngine.priceSeeded(option, spot,      r - dR, sigma,      paths, seed, divs).getPrice();
                AmericanOption optMinusT = new AmericanOption(K, T - dT, type, dates);
                double pTm = LongstaffSchwartzEngine.priceSeeded(optMinusT, spot, r, sigma, paths, seed, divs).getPrice();
                return assemble(p0, pSp, pSm, pVp, pVm, pRp, pRm, pTm, dS, dV, dR, dT);
            }

            case BINOMIAL:
                return finiteDiffGreeks(
                        (s, v, rr, t) -> BinomialEngine.price(
                                new AmericanOption(K, t, type, dates), s, rr, v, divs).getPrice(),
                        spot, r, sigma, T);

            case PDE:
                return finiteDiffGreeks(
                        (s, v, rr, t) -> FiniteDifferenceEngine.price(
                                new AmericanOption(K, t, type, dates), s, rr, v, divs).getPrice(),
                        spot, r, sigma, T);

            case AUTO: {
                Greeks lsm = compute(option, spot, r, sigma, paths, divs, PricingModel.LSM);
                Greeks bin = compute(option, spot, r, sigma, paths, divs, PricingModel.BINOMIAL);
                Greeks pde = compute(option, spot, r, sigma, paths, divs, PricingModel.PDE);
                return trimmedGreeks(lsm, bin, pde);
            }

            default:
                throw new IllegalArgumentException("Model " + model + " is not applicable to American Greeks");
        }
    }


    // ============================================================
    //  Generic dispatch
    // ============================================================

    public static Greeks compute(Option option, double spot, double r, double sigma) {
        return compute(option, spot, r, sigma, DividendSchedule.NONE);
    }

    public static Greeks compute(Option option, double spot, double r, double sigma,
                                 DividendSchedule dividends) {
        if(option instanceof EuropeanOption eu)        return compute(eu, spot, r, sigma, dividends);
        if(option instanceof PathDependentOption pd)   return compute(pd, spot, r, sigma, dividends);
        if(option instanceof AmericanOption am)        return compute(am, spot, r, sigma, dividends);
        throw new IllegalArgumentException("Unsupported option type: " + option.getClass().getSimpleName());
    }


    // ============================================================
    //  Helpers
    // ============================================================

    @FunctionalInterface
    private interface FullPricer {
        double price(double spot, double sigma, double rate, double timeToExpiry);
    }


    /**
     * Central finite-difference Greeks against an arbitrary deterministic
     * pricer. Used for Binomial / PDE on European and American types.
     */
    private static Greeks finiteDiffGreeks(FullPricer pricer, double spot, double r,
                                           double sigma, double T) {
        double dS = BUMP_S_REL * spot;
        double dV = BUMP_VOL;
        double dR = BUMP_RATE;
        double dT = Math.min(BUMP_T_DAYS / 365.0, T * 0.5);

        double p0  = pricer.price(spot,      sigma,      r,      T);
        double pSp = pricer.price(spot + dS, sigma,      r,      T);
        double pSm = pricer.price(spot - dS, sigma,      r,      T);
        double pVp = pricer.price(spot,      sigma + dV, r,      T);
        double pVm = pricer.price(spot,      sigma - dV, r,      T);
        double pRp = pricer.price(spot,      sigma,      r + dR, T);
        double pRm = pricer.price(spot,      sigma,      r - dR, T);
        double pTm = pricer.price(spot,      sigma,      r,      T - dT);
        return assemble(p0, pSp, pSm, pVp, pVm, pRp, pRm, pTm, dS, dV, dR, dT);
    }


    /**
     * Heston-flavoured FD Greeks. Vega is reported per 1% σ-bump, where
     * σ ≡ √v₀; we bump σ ± dV and translate to a bumped v₀.
     */
    private static Greeks hestonGreeks(EuropeanOption option, double spot, double r,
                                       HestonParams h, DividendSchedule divs) {
        double T = option.getTimeToExpiry();
        OptionType type = option.getOptionType();
        double K = option.getStrikePrice();

        double dS = BUMP_S_REL * spot;
        double dV = BUMP_VOL;
        double dR = BUMP_RATE;
        double dT = Math.min(BUMP_T_DAYS / 365.0, T * 0.5);

        double sigma0 = Math.sqrt(h.v0());
        double v0Up   = (sigma0 + dV) * (sigma0 + dV);
        double v0Dn   = Math.max(1e-10, (sigma0 - dV) * (sigma0 - dV));
        HestonParams hUp = new HestonParams(v0Up, h.kappa(), h.theta(), h.xi(), h.rho());
        HestonParams hDn = new HestonParams(v0Dn, h.kappa(), h.theta(), h.xi(), h.rho());

        double p0  = HestonEngine.price(option, spot,      r,      h,   divs).getPrice();
        double pSp = HestonEngine.price(option, spot + dS, r,      h,   divs).getPrice();
        double pSm = HestonEngine.price(option, spot - dS, r,      h,   divs).getPrice();
        double pVp = HestonEngine.price(option, spot,      r,      hUp, divs).getPrice();
        double pVm = HestonEngine.price(option, spot,      r,      hDn, divs).getPrice();
        double pRp = HestonEngine.price(option, spot,      r + dR, h,   divs).getPrice();
        double pRm = HestonEngine.price(option, spot,      r - dR, h,   divs).getPrice();
        EuropeanOption optMinusT = EuropeanOption.of(type, K, T - dT);
        double pTm = HestonEngine.price(optMinusT, spot, r, h, divs).getPrice();

        return assemble(p0, pSp, pSm, pVp, pVm, pRp, pRm, pTm, dS, dV, dR, dT);
    }


    private static Greeks assemble(double p0, double pSp, double pSm,
                                   double pVp, double pVm, double pRp, double pRm, double pTm,
                                   double dS, double dV, double dR, double dT) {

        double delta = (pSp - pSm) / (2.0 * dS);
        double gamma = (pSp - 2.0 * p0 + pSm) / (dS * dS);
        double vega  = (pVp - pVm) / (2.0 * dV);
        double rho   = (pRp - pRm) / (2.0 * dR);
        double theta = (pTm - p0) / dT;

        return new Greeks(delta, gamma, vega * 0.01, theta / 365.0, rho * 0.01);
    }


    /** Trimmed mean of three Greeks records, per component. */
    private static Greeks trimmedGreeks(Greeks a, Greeks b, Greeks c) {
        return new Greeks(
                trimMid(a.delta(), b.delta(), c.delta()),
                trimMid(a.gamma(), b.gamma(), c.gamma()),
                trimMid(a.vega(),  b.vega(),  c.vega()),
                trimMid(a.theta(), b.theta(), c.theta()),
                trimMid(a.rho(),   b.rho(),   c.rho()));
    }

    /** Drop min and max of three values, return the remaining one. */
    private static double trimMid(double x, double y, double z) {
        double max = Math.max(x, Math.max(y, z));
        double min = Math.min(x, Math.min(y, z));
        return x + y + z - max - min;
    }


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

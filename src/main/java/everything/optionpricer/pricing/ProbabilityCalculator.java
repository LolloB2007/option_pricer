package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.util.NormalDistribution;


/**
 * Closed-form probability calculations under Black-Scholes / GBM.
 *
 *   probItm(S, K, r, σ, T, q, isCall)
 *       Risk-neutral probability the option finishes in the money — the
 *       N(d2) (call) or N(-d2) (put) of Black-Scholes.
 *
 *   probTouch(S, B, r, σ, T, q)
 *       Probability the underlying touches barrier B at any time in [0, T]
 *       under GBM. Standard formula:
 *         μ = (r - q - ½σ²)·T,   σ_T = σ·√T,   k = ln(B/S),
 *         P_touch(B>S) = N((k-μ)/σ_T · (-1)) + (B/S)^(2μ/σ²)·N((-k-μ)/σ_T)
 *       (mirrored for down barriers). For S already past the barrier we
 *       return 1.0.
 *
 * Convenient for strategy filters ("only sell condors where short strike
 * has < 30% touch probability before expiry").
 *
 * @author lorenzobarbagelata
 */
public final class ProbabilityCalculator {

    private ProbabilityCalculator() {}


    /**
     * Risk-neutral probability the option finishes in the money at maturity.
     *
     * For a call: P(S_T > K) = N(d2).
     * For a put : P(S_T < K) = N(-d2).
     */
    public static double probItm(double spot, double strike, double r, double sigma,
                                 double timeToExpiry, DividendSchedule dividends, boolean isCall) {
        validate(spot, strike, sigma, timeToExpiry);
        if(dividends == null) dividends = DividendSchedule.NONE;
        double q = dividends.continuousYield();
        double Seff = spot - dividends.presentValueBefore(timeToExpiry, r);
        if(Seff <= 0) return isCall ? 0.0 : 1.0;

        double sqrtT = Math.sqrt(timeToExpiry);
        double d2 = (Math.log(Seff / strike) + (r - q - 0.5 * sigma * sigma) * timeToExpiry)
                  / (sigma * sqrtT);
        return isCall ? NormalDistribution.cdf(d2) : NormalDistribution.cdf(-d2);
    }


    /**
     * Probability the underlying touches barrier {@code B} at any point in
     * {@code [0, T]} under GBM. For an up barrier ({@code B > S}) the
     * standard reflection-principle formula:
     * <pre>
     *   P = N((ln(S/B) + μT) / σ√T) + (B/S)^{2μ/σ²} · N((ln(S/B) - μT) / σ√T)
     * </pre>
     * where {@code μ = r - q - ½σ²}. Mirrored for down barriers.
     */
    public static double probTouch(double spot, double barrier, double r, double sigma,
                                   double timeToExpiry, DividendSchedule dividends) {
        validate(spot, sigma, timeToExpiry);
        if(barrier <= 0) throw new IllegalArgumentException("Barrier must be positive");
        if(dividends == null) dividends = DividendSchedule.NONE;
        double q = dividends.continuousYield();
        double Seff = spot - dividends.presentValueBefore(timeToExpiry, r);
        if(Seff <= 0) return 1.0;
        boolean up = barrier > Seff;
        if(up ? Seff >= barrier : Seff <= barrier) return 1.0;

        double mu = r - q - 0.5 * sigma * sigma;
        double sigmaT = sigma * Math.sqrt(timeToExpiry);
        // Work in s = ln(S/B). For up-barrier s < 0; for down s > 0.
        double s = Math.log(Seff / barrier);

        // Use the symmetric form so both cases share code.
        // P_touch = N((s + μT)/σ√T) + (B/S)^(2μ/σ²) · N((s - μT)/σ√T)  for up
        //          (for down barriers `s` and `μT` swap sign; same formula
        //          works with absolute values plugged in symmetrically.)
        double scale = Math.pow(barrier / Seff, 2.0 * mu / (sigma * sigma));
        if(up) {
            double a = NormalDistribution.cdf((s + mu * timeToExpiry) / sigmaT);
            double b = NormalDistribution.cdf((s - mu * timeToExpiry) / sigmaT);
            return a + scale * b;
        } else {
            // Down barrier: reflect.
            double a = NormalDistribution.cdf((-s - mu * timeToExpiry) / sigmaT);
            double b = NormalDistribution.cdf((-s + mu * timeToExpiry) / sigmaT);
            double scaleDn = Math.pow(barrier / Seff, 2.0 * mu / (sigma * sigma));
            return a + scaleDn * b;
        }
    }


    private static void validate(double spot, double sigma, double T) {
        if(spot <= 0)  throw new IllegalArgumentException("Spot must be positive");
        if(sigma <= 0) throw new IllegalArgumentException("Volatility must be positive");
        if(T    <= 0)  throw new IllegalArgumentException("Time-to-expiry must be positive");
    }

    private static void validate(double spot, double strike, double sigma, double T) {
        validate(spot, sigma, T);
        if(strike <= 0) throw new IllegalArgumentException("Strike must be positive");
    }
}

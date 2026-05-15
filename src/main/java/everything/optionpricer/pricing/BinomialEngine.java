package everything.optionpricer.pricing;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.Option;
import everything.optionpricer.model.PricingResult;


/**
 * Cox-Ross-Rubinstein binomial tree pricer. Handles European and American
 * call / put with continuous dividend yield and (via the escrowed model)
 * discrete cash dividends.
 *
 * Convergence: ~O(1/N) for European, ~O(1/N) for American (well-known
 * oscillating convergence). Default {@code N = 1000} steps gives ≤ 1bp
 * agreement with Black-Scholes for typical inputs.
 *
 * @author lorenzobarbagelata
 */
public final class BinomialEngine {

    private static final int DEFAULT_STEPS = 1000;

    private BinomialEngine() {}


    // ----- European ----- //

    public static PricingResult price(EuropeanOption opt, double spot, double r, double sigma) {
        return price(opt, spot, r, sigma, DividendSchedule.NONE, DEFAULT_STEPS);
    }

    public static PricingResult price(EuropeanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends) {
        return price(opt, spot, r, sigma, dividends, DEFAULT_STEPS);
    }

    public static PricingResult price(EuropeanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends, int steps) {
        return crr(opt, spot, r, sigma, dividends, steps, false);
    }


    // ----- American ----- //

    public static PricingResult price(AmericanOption opt, double spot, double r, double sigma) {
        return price(opt, spot, r, sigma, DividendSchedule.NONE, DEFAULT_STEPS);
    }

    public static PricingResult price(AmericanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends) {
        return price(opt, spot, r, sigma, dividends, DEFAULT_STEPS);
    }

    public static PricingResult price(AmericanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends, int steps) {
        return crr(opt, spot, r, sigma, dividends, steps, true);
    }


    // ----- Implementation ----- //

    private static PricingResult crr(Option option, double spot, double r, double sigma,
                                     DividendSchedule dividends, int N, boolean american) {

        validate(option, spot, sigma, N);
        if(dividends == null) dividends = DividendSchedule.NONE;

        double K = option.getStrikePrice();
        double T = option.getTimeToExpiry();
        double q = dividends.continuousYield();
        int    sign = option.isCall() ? 1 : -1;

        // Escrowed model — subtract PV of discrete divs from spot.
        double Seff = spot - dividends.presentValueBefore(T, r);
        if(Seff <= 0)
            throw new IllegalArgumentException("Spot minus PV of discrete dividends is non-positive");

        double dt   = T / N;
        double u    = Math.exp(sigma * Math.sqrt(dt));
        double d    = 1.0 / u;
        double p    = (Math.exp((r - q) * dt) - d) / (u - d);
        double disc = Math.exp(-r * dt);

        if(p < 0 || p > 1) {
            // Risk-neutral measure broken — usually means dt is too large.
            // Bail out cleanly.
            throw new IllegalArgumentException(
                    "Binomial probability out of [0,1] — try a smaller σ or more steps");
        }

        // Precompute S at terminal nodes by walking u/d once.
        double[] v = new double[N + 1];
        double s = Seff * Math.pow(d, N);
        double udd = u / d;
        for(int i = N; i >= 0; i--) {
            v[N - i] = Math.max(sign * (s - K), 0.0);
            s *= udd;
        }

        // Backward induction.
        // Convention: v[i] in layer L is the price at S = u^i · d^(L-i) · Seff
        // (i = up-move count). From (L, i):  UP → (L+1, i+1) = v[i+1] before update;
        //                                    DOWN → (L+1, i)   = v[i]   before update.
        //
        // For the American early-exercise sweep we need S at i=0 of every layer,
        // which is Seff · d^step. As we walk back from step=N-1 to step=0, this
        // base only needs to multiply by u each iteration — no Math.pow inside.
        double walkerBase = Seff * Math.pow(d, N - 1); // base for step = N-1
        for(int step = N - 1; step >= 0; step--) {
            for(int i = 0; i <= step; i++) {
                v[i] = disc * (p * v[i + 1] + (1 - p) * v[i]);
            }
            if(american) {
                double walker = walkerBase;
                for(int i = 0; i <= step; i++) {
                    double intrinsic = Math.max(sign * (walker - K), 0.0);
                    if(intrinsic > v[i]) v[i] = intrinsic;
                    walker *= udd;
                }
            }
            walkerBase *= u; // d^step → d^(step-1) = d^step · u
        }

        return new PricingResult(v[0]);
    }


    private static void validate(Option option, double spot, double sigma, int steps) {
        if(option == null)   throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0)        throw new IllegalArgumentException("Spot must be positive");
        if(sigma <= 0)       throw new IllegalArgumentException("Volatility must be positive");
        if(steps < 1)        throw new IllegalArgumentException("Steps must be ≥ 1");
    }
}

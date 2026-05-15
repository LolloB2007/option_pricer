package everything.optionpricer.pricing;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.Option;
import everything.optionpricer.model.PricingResult;


/**
 * Crank-Nicolson finite-difference solver of the Black-Scholes PDE
 *   ∂V/∂t + ½ σ² S² ∂²V/∂S² + (r - q) S ∂V/∂S - r V = 0
 * on a uniform grid in S, marched backward from maturity to today.
 *
 * Supports European call / put and American call / put (via the standard
 * `V := max(V, intrinsic)` projection at each time slice). Discrete cash
 * dividends are handled by the escrowed model (subtract PV from spot).
 *
 * Tridiagonal system solved by the Thomas algorithm — O(N_S · N_T).
 *
 * @author lorenzobarbagelata
 */
public final class FiniteDifferenceEngine {

    private static final int DEFAULT_S_STEPS = 400;
    private static final int DEFAULT_T_STEPS = 400;

    private FiniteDifferenceEngine() {}


    // ----- European ----- //

    public static PricingResult price(EuropeanOption opt, double spot, double r, double sigma) {
        return price(opt, spot, r, sigma, DividendSchedule.NONE, DEFAULT_S_STEPS, DEFAULT_T_STEPS);
    }

    public static PricingResult price(EuropeanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends) {
        return price(opt, spot, r, sigma, dividends, DEFAULT_S_STEPS, DEFAULT_T_STEPS);
    }

    public static PricingResult price(EuropeanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends, int nS, int nT) {
        return solve(opt, spot, r, sigma, dividends, nS, nT, false);
    }


    // ----- American ----- //

    public static PricingResult price(AmericanOption opt, double spot, double r, double sigma) {
        return price(opt, spot, r, sigma, DividendSchedule.NONE, DEFAULT_S_STEPS, DEFAULT_T_STEPS);
    }

    public static PricingResult price(AmericanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends) {
        return price(opt, spot, r, sigma, dividends, DEFAULT_S_STEPS, DEFAULT_T_STEPS);
    }

    public static PricingResult price(AmericanOption opt, double spot, double r, double sigma,
                                      DividendSchedule dividends, int nS, int nT) {
        return solve(opt, spot, r, sigma, dividends, nS, nT, true);
    }


    // ----- Implementation ----- //

    private static PricingResult solve(Option option, double spot, double r, double sigma,
                                       DividendSchedule dividends, int nS, int nT,
                                       boolean american) {

        validate(option, spot, sigma, nS, nT);
        if(dividends == null) dividends = DividendSchedule.NONE;

        double K = option.getStrikePrice();
        double T = option.getTimeToExpiry();
        double q = dividends.continuousYield();
        boolean isCall = option.isCall();
        int sign = isCall ? 1 : -1;

        double Seff = spot - dividends.presentValueBefore(T, r);
        if(Seff <= 0)
            throw new IllegalArgumentException("Spot minus PV of discrete dividends is non-positive");

        // Grid in S — span 4× the relevant scale so boundary effects don't reach S0.
        double Smax = 4.0 * Math.max(Seff, K);
        double dS = Smax / nS;
        double dt = T / nT;

        double[] V = new double[nS + 1];
        // Terminal payoff.
        for(int i = 0; i <= nS; i++) {
            double si = i * dS;
            V[i] = Math.max(sign * (si - K), 0.0);
        }

        // Tridiagonal coefficients (independent of t under constant σ).
        // Crank-Nicolson: ½ implicit + ½ explicit.
        //   a_i = ¼ dt ( σ² i² - (r-q) i )
        //   b_i = -½ dt ( σ² i² + r )
        //   c_i = ¼ dt ( σ² i² + (r-q) i )
        // Implicit step:   (1 - b_i) V_i^n  - a_i V_{i-1}^n  - c_i V_{i+1}^n  = RHS
        // Explicit step:   a_i V_{i-1}^{n+1} + (1 + b_i) V_i^{n+1} + c_i V_{i+1}^{n+1}
        double[] a = new double[nS + 1];
        double[] b = new double[nS + 1];
        double[] c = new double[nS + 1];
        for(int i = 1; i < nS; i++) {
            double i2 = i * i;
            a[i] = 0.25 * dt * (sigma * sigma * i2 - (r - q) * i);
            b[i] = -0.5  * dt * (sigma * sigma * i2 + r);
            c[i] = 0.25 * dt * (sigma * sigma * i2 + (r - q) * i);
        }

        double[] rhs   = new double[nS + 1];
        double[] alpha = new double[nS + 1];
        double[] beta  = new double[nS + 1];
        double[] gamma = new double[nS + 1];
        // Per-step Thomas-solve scratch — hoisted out of the time loop.
        double[] cp    = new double[nS + 1];
        double[] dp    = new double[nS + 1];
        // Intrinsic per gridpoint — same every step.
        double[] intrinsic = american ? new double[nS + 1] : null;
        for(int i = 1; i < nS; i++) {
            alpha[i] = -a[i];
            beta[i]  = 1.0 - b[i];
            gamma[i] = -c[i];
            if(american) intrinsic[i] = Math.max(sign * (i * dS - K), 0.0);
        }

        // Boundary values: walk e^{-r·τ} and e^{-q·τ} by their per-step factor
        // instead of calling Math.exp 4× per time step. τ = (nT − n)·dt.
        double expRdt = Math.exp(-r * dt);
        double expQdt = Math.exp(-q * dt);
        // discR[k] = e^{-r·k·dt}; discQ[k] = e^{-q·k·dt}. Built incrementally below.
        double discR = 1.0;  // e^{-r·0}
        double discQ = 1.0;  // e^{-q·0}
        // After the n-th iteration (n = nT-1 down to 0), τThis = (nT-n)·dt,
        // τNext = (nT-n-1)·dt = τThis - dt. We track discR/discQ as the
        // "Next" factors and advance them BEFORE using as "This" inside the loop.
        double discRNext = 1.0;
        double discQNext = 1.0;

        for(int n = nT - 1; n >= 0; n--) {
            // Advance discount factors: this-step τ = next-step τ + dt.
            double discRThis = discRNext * expRdt;
            double discQThis = discQNext * expQdt;

            double v0Next, vMaxNext, v0This, vMaxThis;
            if(isCall) {
                v0Next   = 0.0;
                v0This   = 0.0;
                vMaxNext = Smax * discQNext - K * discRNext;
                vMaxThis = Smax * discQThis - K * discRThis;
            } else {
                v0Next   = K * discRNext;
                v0This   = K * discRThis;
                vMaxNext = 0.0;
                vMaxThis = 0.0;
            }
            V[0]  = v0Next;
            V[nS] = vMaxNext;

            // RHS: B V^{n+1}
            for(int i = 1; i < nS; i++) {
                rhs[i] = a[i] * V[i - 1] + (1.0 + b[i]) * V[i] + c[i] * V[i + 1];
            }
            rhs[1]      += a[1]      * v0This;
            rhs[nS - 1] += c[nS - 1] * vMaxThis;

            // Thomas algorithm — buffers reused across time steps.
            cp[1] = gamma[1] / beta[1];
            dp[1] = rhs[1]   / beta[1];
            for(int i = 2; i < nS; i++) {
                double m = beta[i] - alpha[i] * cp[i - 1];
                cp[i] = gamma[i] / m;
                dp[i] = (rhs[i] - alpha[i] * dp[i - 1]) / m;
            }
            V[nS - 1] = dp[nS - 1];
            for(int i = nS - 2; i >= 1; i--) {
                V[i] = dp[i] - cp[i] * V[i + 1];
            }
            V[0]  = v0This;
            V[nS] = vMaxThis;

            if(american) {
                for(int i = 1; i < nS; i++) {
                    if(intrinsic[i] > V[i]) V[i] = intrinsic[i];
                }
            }

            // Slide the discount factors forward for the next iteration.
            discRNext = discRThis;
            discQNext = discQThis;
        }

        // Linear interpolation back to Seff.
        double idx = Seff / dS;
        int i0 = (int) Math.floor(idx);
        if(i0 < 0) i0 = 0;
        if(i0 > nS - 1) i0 = nS - 1;
        double frac = idx - i0;
        double price = V[i0] * (1 - frac) + V[i0 + 1] * frac;

        return new PricingResult(price);
    }


    private static void validate(Option option, double spot, double sigma, int nS, int nT) {
        if(option == null) throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0)      throw new IllegalArgumentException("Spot must be positive");
        if(sigma <= 0)     throw new IllegalArgumentException("Volatility must be positive");
        if(nS < 10)        throw new IllegalArgumentException("Grid too small (nS < 10)");
        if(nT < 10)        throw new IllegalArgumentException("Grid too small (nT < 10)");
    }
}

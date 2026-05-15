package everything.optionpricer.model;


/**
 * Parameters for the Heston (1993) stochastic-volatility model.
 *
 *   dS = (r - q) S dt + √v · S dW₁
 *   dv = κ(θ - v) dt + ξ · √v   dW₂        with corr(dW₁, dW₂) = ρ
 *
 *  - v0    initial instantaneous variance (NOT volatility; v0 = σ₀²)
 *  - kappa mean-reversion speed of variance
 *  - theta long-run mean variance (long-run σ²)
 *  - xi    "vol of vol" — diffusion coefficient on the variance process
 *  - rho   correlation between the spot and variance Brownian drivers,
 *          typically negative (leverage effect)
 *
 * @author lorenzobarbagelata
 */
public record HestonParams(double v0, double kappa, double theta, double xi, double rho) {

    public HestonParams {
        if(v0    <  0) throw new IllegalArgumentException("v0 must be non-negative");
        if(kappa <= 0) throw new IllegalArgumentException("kappa must be positive");
        if(theta <  0) throw new IllegalArgumentException("theta must be non-negative");
        if(xi    <  0) throw new IllegalArgumentException("xi must be non-negative");
        if(rho   < -1 || rho > 1) throw new IllegalArgumentException("rho must be in [-1, 1]");
    }
}

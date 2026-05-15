package everything.optionpricer.pricing;


/**
 * Pricing-model selector exposed in the GUI and the API.
 *
 *  - {@code AUTO}      trimmed mean of the applicable σ-based models
 *                      (BS+Binomial+PDE for European; LSM+Binomial+PDE for
 *                      American). Drops the smallest and largest before
 *                      averaging — degenerates to the median for 3 models.
 *  - {@code BS}        closed-form Black-Scholes (European only).
 *  - {@code MC}        Monte Carlo (path-dependent options).
 *  - {@code LSM}       Longstaff-Schwartz (American).
 *  - {@code BINOMIAL}  Cox-Ross-Rubinstein binomial tree.
 *  - {@code PDE}       Crank-Nicolson finite-difference PDE solver.
 *  - {@code HESTON}    Heston stochastic-vol model via Fourier inversion
 *                      (European only). Requires Heston parameters.
 *
 * @author lorenzobarbagelata
 */
public enum PricingModel {
    AUTO, BS, MC, LSM, BINOMIAL, PDE, HESTON
}

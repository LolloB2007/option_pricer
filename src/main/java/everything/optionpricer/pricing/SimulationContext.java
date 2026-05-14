package everything.optionpricer.pricing;


/**
 * Per-step constants shared with each {@link PathAccumulator}. Lets
 * continuous-monitoring accumulators carry out Brownian-bridge corrections
 * without the engine needing to know whether the accumulator uses them.
 *
 * @author lorenzobarbagelata
 */
public final class SimulationContext {

    /** Step size in years. */
    public final double dt;
    /** GBM volatility. */
    public final double sigma;
    /** {@code sigma^2 * dt} — pre-multiplied because the bridge math needs it. */
    public final double sigmaSqDt;

    public SimulationContext(double dt, double sigma) {
        this.dt = dt;
        this.sigma = sigma;
        this.sigmaSqDt = sigma * sigma * dt;
    }
}

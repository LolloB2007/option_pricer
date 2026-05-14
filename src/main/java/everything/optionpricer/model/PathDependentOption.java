package everything.optionpricer.model;

import everything.optionpricer.pricing.PathAccumulator;
import everything.optionpricer.pricing.SimulationContext;


/**
 * Abstract base for path-dependent options.
 * @author lorenzobarbagelata
 */
public abstract class PathDependentOption extends Option {

    private final int timeSteps;
    private final boolean discreteMonitoring;


    protected PathDependentOption(double strikePrice, double timeToExpiry, OptionType type,
                                  int timeSteps, boolean discreteMonitoring) {

        super(strikePrice, timeToExpiry, type);

        if(timeSteps <= 1)
            throw new IllegalArgumentException("Please enter valid time steps (>=2)");

        this.timeSteps = timeSteps;
        this.discreteMonitoring = discreteMonitoring;
    }


    public int getTimeSteps() {
        return timeSteps;
    }

    public boolean isDiscreteMonitoring() {
        return discreteMonitoring;
    }


    /**
     * Build a fresh, stateful accumulator for one Monte Carlo simulation.
     * The {@link SimulationContext} carries per-step constants the
     * continuous-monitoring variants need (Brownian-bridge math).
     */
    public abstract PathAccumulator newAccumulator(SimulationContext ctx);
}

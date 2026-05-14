package everything.optionpricer.model;

import everything.optionpricer.pricing.PathAccumulator;
import everything.optionpricer.pricing.SimulationContext;


/**
 * Barrier option (up/down, in/out).
 *
 * Discrete monitoring checks for a barrier hit only at observed sample
 * points. Continuous monitoring uses the closed-form Brownian-bridge
 * crossing probability between observations — a lower-variance estimator
 * than naive sampling.
 *
 * @author lorenzobarbagelata
 */
public class BarrierOption extends PathDependentOption {

    private final double barrier;
    private final double logBarrier;
    private final boolean upBarrier;
    private final boolean inBarrier;


    public BarrierOption(double strikePrice, double timeToExpiry, OptionType type,
                         int timeSteps, boolean discreteMonitoring,
                         double barrier, boolean upBarrier, boolean inBarrier) {
        super(strikePrice, timeToExpiry, type, timeSteps, discreteMonitoring);

        if(barrier <= 0)
            throw new IllegalArgumentException("Please enter valid barrier");

        this.barrier = barrier;
        this.logBarrier = Math.log(barrier);
        this.upBarrier = upBarrier;
        this.inBarrier = inBarrier;
    }


    public double getBarrier()    { return barrier; }
    public boolean isUpBarrier()  { return upBarrier; }
    public boolean isInBarrier()  { return inBarrier; }


    @Override
    public PathAccumulator newAccumulator(SimulationContext ctx) {
        return isDiscreteMonitoring() ? new DiscreteAcc() : new ContinuousAcc(ctx.sigmaSqDt);
    }


    // ----- Discrete monitoring: check only sample points. ----- //
    private final class DiscreteAcc implements PathAccumulator {
        private boolean hit;
        private double lastLogPrice;

        @Override public void accumulate(double price, double logPrice) {
            lastLogPrice = logPrice;
            if(!hit && (upBarrier ? logPrice >= logBarrier : logPrice <= logBarrier))
                hit = true;
        }

        @Override public double payoff() {
            boolean alive = inBarrier ? hit : !hit;
            if(!alive) return 0.0;
            return Math.max(getSign() * (Math.exp(lastLogPrice) - getStrikePrice()), 0.0);
        }

        @Override public void reset() { hit = false; lastLogPrice = 0.0; }

        @Override public boolean isDone() { return !inBarrier && hit; }

        @Override public boolean needsPrice() { return false; }
    }


    // ----- Continuous monitoring: Brownian-bridge analytic survival. ----- //
    // Survival per segment (no crossing) given log endpoints x_i, x_{i+1}:
    //   up:   if max(x_i, x_{i+1}) >= logB → survival = 0; else 1 − exp(−2(logB − x_i)(logB − x_{i+1}) / σ²Δt)
    //   down: if min(x_i, x_{i+1}) <= logB → survival = 0; else 1 − exp(−2(x_i − logB)(x_{i+1} − logB) / σ²Δt)
    // Final OUT payoff = totalSurvival × vanilla(S_T); IN payoff = (1 − totalSurvival) × vanilla(S_T).
    private final class ContinuousAcc implements PathAccumulator {
        private final double sigmaSqDt;
        private double logSurvival;
        private double prevLogPrice;
        private double lastLogPrice;
        private int count;
        private boolean knockedOut;

        ContinuousAcc(double sigmaSqDt) {
            this.sigmaSqDt = sigmaSqDt;
        }

        @Override public void accumulate(double price, double logPrice) {
            if(count == 0) {
                // First observation — check endpoint and seed.
                if(upBarrier ? logPrice >= logBarrier : logPrice <= logBarrier)
                    knockedOut = true;
                prevLogPrice = logPrice;
                lastLogPrice = logPrice;
                count = 1;
                return;
            }

            if(!knockedOut) {
                if(upBarrier ? logPrice >= logBarrier : logPrice <= logBarrier) {
                    knockedOut = true;
                } else {
                    double a = upBarrier ? (logBarrier - prevLogPrice) : (prevLogPrice - logBarrier);
                    double b = upBarrier ? (logBarrier - logPrice)     : (logPrice     - logBarrier);
                    // survival = 1 - exp(-2ab/sigmaSqDt); log survival accumulates.
                    double surv = 1.0 - Math.exp(-2.0 * a * b / sigmaSqDt);
                    if(surv <= 0.0) knockedOut = true;
                    else            logSurvival += Math.log(surv);
                }
            }

            prevLogPrice = logPrice;
            lastLogPrice = logPrice;
            count++;
        }

        @Override public double payoff() {
            double totalSurv = knockedOut ? 0.0 : Math.exp(logSurvival);
            double weight    = inBarrier ? (1.0 - totalSurv) : totalSurv;
            if(weight <= 0.0) return 0.0;

            double vanilla = Math.max(getSign() * (Math.exp(lastLogPrice) - getStrikePrice()), 0.0);
            return weight * vanilla;
        }

        @Override public void reset() {
            logSurvival = 0.0;
            prevLogPrice = 0.0;
            lastLogPrice = 0.0;
            count = 0;
            knockedOut = false;
        }

        // OUT only — once knocked out, the survival weight is locked at 0.
        @Override public boolean isDone() { return !inBarrier && knockedOut; }

        @Override public boolean needsPrice() { return false; }
    }
}

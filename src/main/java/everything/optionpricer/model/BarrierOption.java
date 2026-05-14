package everything.optionpricer.model;

import everything.optionpricer.pricing.PathAccumulator;


/**
 * Barrier option (up/down, in/out).
 * @author lorenzobarbagelata
 */
public class BarrierOption extends PathDependentOption {

    private final double barrier;
    private final double logBarrier; // precomputed; log(B) doesn't change across sims
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
    public PathAccumulator newAccumulator() {
        return new Acc();
    }


    // Hit-check is monotonic in log-price, and the payoff only needs the final
    // price (computed via Math.exp once at the end) — so the engine can skip
    // Math.exp for every intermediate step.
    private final class Acc implements PathAccumulator {
        private boolean hit;
        private double lastLogPrice;

        @Override
        public void accumulate(double price, double logPrice) {
            lastLogPrice = logPrice;
            if(!hit) {
                if(upBarrier ? logPrice >= logBarrier : logPrice <= logBarrier)
                    hit = true;
            }
        }

        @Override
        public double payoff() {
            boolean alive = inBarrier ? hit : !hit;
            if(!alive)
                return 0.0;

            double finalPrice = Math.exp(lastLogPrice);
            return Math.max(getSign() * (finalPrice - getStrikePrice()), 0.0);
        }

        @Override
        public void reset() {
            hit = false;
            lastLogPrice = 0.0;
        }

        // OUT options: once knocked out, payoff is locked at 0 — skip the rest of the path.
        @Override
        public boolean isDone() {
            return !inBarrier && hit;
        }

        @Override
        public boolean needsPrice() {
            return false;
        }
    }
}

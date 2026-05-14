package everything.optionpricer.model;

import everything.optionpricer.pricing.PathAccumulator;


/**
 * Lookback option (fixed-strike or floating-strike).
 * @author lorenzobarbagelata
 */
public class LookbackOption extends PathDependentOption {

    private final boolean fixedStrike;


    public LookbackOption(double strikePrice, double timeToExpiry, OptionType type,
                          int timeSteps, boolean discreteMonitoring, boolean fixedStrike) {
        super(strikePrice, timeToExpiry, type, timeSteps, discreteMonitoring);
        this.fixedStrike = fixedStrike;
    }


    public boolean isFixedStrike() {
        return fixedStrike;
    }


    @Override
    public PathAccumulator newAccumulator() {
        return new Acc();
    }


    // min/max/last are monotonic in log-price — track them in log-space, exp
    // only at payoff time (at most twice). Saves a Math.exp per step.
    private final class Acc implements PathAccumulator {
        private double logMin = Double.POSITIVE_INFINITY;
        private double logMax = Double.NEGATIVE_INFINITY;
        private double logLast;

        @Override
        public void accumulate(double price, double logPrice) {
            if(logPrice < logMin) logMin = logPrice;
            if(logPrice > logMax) logMax = logPrice;
            logLast = logPrice;
        }

        @Override
        public double payoff() {
            if(fixedStrike) {
                return isCall()
                        ? Math.max(Math.exp(logMax) - getStrikePrice(), 0.0)
                        : Math.max(getStrikePrice() - Math.exp(logMin), 0.0);
            }
            double last = Math.exp(logLast);
            return isCall()
                    ? Math.max(last - Math.exp(logMin), 0.0)
                    : Math.max(Math.exp(logMax) - last, 0.0);
        }

        @Override
        public void reset() {
            logMin = Double.POSITIVE_INFINITY;
            logMax = Double.NEGATIVE_INFINITY;
            logLast = 0.0;
        }

        @Override
        public boolean needsPrice() {
            return false;
        }
    }
}

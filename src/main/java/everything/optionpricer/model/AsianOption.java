package everything.optionpricer.model;

import everything.optionpricer.pricing.PathAccumulator;


/**
 * Asian option (arithmetic or geometric average).
 * @author lorenzobarbagelata
 */
public class AsianOption extends PathDependentOption {

    private final boolean arithmeticAverage;


    public AsianOption(double strikePrice, double timeToExpiry, OptionType type,
                       int timeSteps, boolean discreteMonitoring, boolean arithmeticAverage) {
        super(strikePrice, timeToExpiry, type, timeSteps, discreteMonitoring);
        this.arithmeticAverage = arithmeticAverage;
    }


    public boolean isArithmeticAverage() {
        return arithmeticAverage;
    }


    @Override
    public PathAccumulator newAccumulator() {
        return arithmeticAverage ? new ArithmeticAcc() : new GeometricAcc();
    }


    // Arithmetic Asian: needs prices in linear space — payoff is mean(price) - K.
    private final class ArithmeticAcc implements PathAccumulator {
        private double sum;
        private int count;

        @Override
        public void accumulate(double price, double logPrice) {
            sum += price;
            count++;
        }

        @Override
        public double payoff() {
            return Math.max(getSign() * (sum / count - getStrikePrice()), 0.0);
        }

        @Override
        public void reset() {
            sum = 0.0;
            count = 0;
        }
    }


    // Geometric Asian works entirely in log-space; engine can skip per-step Math.exp.
    private final class GeometricAcc implements PathAccumulator {
        private double lnSum;
        private int count;

        @Override
        public void accumulate(double price, double logPrice) {
            lnSum += logPrice;
            count++;
        }

        @Override
        public double payoff() {
            double avg = Math.exp(lnSum / count);
            return Math.max(getSign() * (avg - getStrikePrice()), 0.0);
        }

        @Override
        public void reset() {
            lnSum = 0.0;
            count = 0;
        }

        @Override
        public boolean needsPrice() {
            return false;
        }
    }
}

package everything.optionpricer.model;

import everything.optionpricer.pricing.PathAccumulator;
import everything.optionpricer.pricing.SimulationContext;


/**
 * Asian option (arithmetic or geometric average).
 *
 * Discrete monitoring averages the observed sample points; continuous
 * monitoring approximates the time integral (continuous arithmetic /
 * geometric mean) via the trapezoidal rule.
 *
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
    public PathAccumulator newAccumulator(SimulationContext ctx) {
        boolean discrete = isDiscreteMonitoring();
        if(arithmeticAverage) {
            return discrete ? new ArithmeticDiscreteAcc() : new ArithmeticTrapezoidAcc();
        }
        return discrete ? new GeometricDiscreteAcc() : new GeometricTrapezoidAcc();
    }


    // ----- Discrete monitoring: flat average over observation points. ----- //

    private final class ArithmeticDiscreteAcc implements PathAccumulator {
        private double sum;
        private int count;

        @Override public void accumulate(double price, double logPrice) { sum += price; count++; }

        @Override public double payoff() {
            return Math.max(getSign() * (sum / count - getStrikePrice()), 0.0);
        }

        @Override public void reset() { sum = 0.0; count = 0; }
    }

    private final class GeometricDiscreteAcc implements PathAccumulator {
        private double lnSum;
        private int count;

        @Override public void accumulate(double price, double logPrice) { lnSum += logPrice; count++; }

        @Override public double payoff() {
            double avg = Math.exp(lnSum / count);
            return Math.max(getSign() * (avg - getStrikePrice()), 0.0);
        }

        @Override public void reset() { lnSum = 0.0; count = 0; }

        @Override public boolean needsPrice() { return false; }
    }


    // ----- Continuous monitoring: trapezoidal approximation to (1/T) ∫ … dt. ----- //
    // Trapezoidal mean over the path = (S0/2 + S1 + … + S_{N-1} + S_N/2) / N
    // where N = (number of observations - 1) = number of intervals.

    private final class ArithmeticTrapezoidAcc implements PathAccumulator {
        private double sum;     // running total of S_k (counted with weight 1)
        private double first;
        private double last;
        private int count;

        @Override public void accumulate(double price, double logPrice) {
            if(count == 0) first = price;
            last = price;
            sum += price;
            count++;
        }

        @Override public double payoff() {
            // trap sum = sum − (first + last)/2; intervals = count − 1
            double trapSum = sum - 0.5 * (first + last);
            double avg = trapSum / (count - 1);
            return Math.max(getSign() * (avg - getStrikePrice()), 0.0);
        }

        @Override public void reset() { sum = 0.0; first = 0.0; last = 0.0; count = 0; }
    }

    private final class GeometricTrapezoidAcc implements PathAccumulator {
        private double lnSum;
        private double lnFirst;
        private double lnLast;
        private int count;

        @Override public void accumulate(double price, double logPrice) {
            if(count == 0) lnFirst = logPrice;
            lnLast = logPrice;
            lnSum += logPrice;
            count++;
        }

        @Override public double payoff() {
            double trapSum = lnSum - 0.5 * (lnFirst + lnLast);
            double avg = Math.exp(trapSum / (count - 1));
            return Math.max(getSign() * (avg - getStrikePrice()), 0.0);
        }

        @Override public void reset() { lnSum = 0.0; lnFirst = 0.0; lnLast = 0.0; count = 0; }

        @Override public boolean needsPrice() { return false; }
    }
}

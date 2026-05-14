package everything.optionpricer.model;

import everything.optionpricer.pricing.PathAccumulator;
import everything.optionpricer.pricing.SimulationContext;

import java.util.concurrent.ThreadLocalRandom;


/**
 * Lookback option (fixed-strike or floating-strike).
 *
 * Discrete monitoring tracks min/max over sample points. Continuous
 * monitoring inserts the conditional Brownian-bridge extrema between each
 * pair of observations (one extra uniform per step for max and one for
 * min). Without the bridge correction discretely-sampled max/min are
 * biased low/high.
 *
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
    public PathAccumulator newAccumulator(SimulationContext ctx) {
        return isDiscreteMonitoring() ? new DiscreteAcc() : new ContinuousAcc(ctx.sigmaSqDt);
    }


    // ----- Discrete monitoring: min/max over observation points. ----- //
    private final class DiscreteAcc implements PathAccumulator {
        private double logMin = Double.POSITIVE_INFINITY;
        private double logMax = Double.NEGATIVE_INFINITY;
        private double logLast;

        @Override public void accumulate(double price, double logPrice) {
            if(logPrice < logMin) logMin = logPrice;
            if(logPrice > logMax) logMax = logPrice;
            logLast = logPrice;
        }

        @Override public double payoff() {
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

        @Override public void reset() {
            logMin = Double.POSITIVE_INFINITY;
            logMax = Double.NEGATIVE_INFINITY;
            logLast = 0.0;
        }

        @Override public boolean needsPrice() { return false; }
    }


    // ----- Continuous monitoring: Brownian-bridge max/min per segment. ----- //
    // Conditional max of a bridge on [t, t+dt] with log endpoints (x, y),
    // variance σ²Δt, given U ~ Uniform(0,1):
    //   M = (x + y + sqrt((y-x)² − 2σ²Δt · log U)) / 2
    // Conditional min (independent V):
    //   m = (x + y − sqrt((y-x)² − 2σ²Δt · log V)) / 2
    // (Using log U is fine because U∈(0,1) ⇒ log U ≤ 0 ⇒ radicand ≥ (y-x)².)
    private final class ContinuousAcc implements PathAccumulator {
        private final double sigmaSqDt;
        private double logMin = Double.POSITIVE_INFINITY;
        private double logMax = Double.NEGATIVE_INFINITY;
        private double prevLogPrice;
        private double logLast;
        private int count;

        ContinuousAcc(double sigmaSqDt) {
            this.sigmaSqDt = sigmaSqDt;
        }

        @Override public void accumulate(double price, double logPrice) {
            if(logPrice < logMin) logMin = logPrice;
            if(logPrice > logMax) logMax = logPrice;
            logLast = logPrice;

            if(count > 0) {
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                double dx = logPrice - prevLogPrice;
                double dx2 = dx * dx;

                // Avoid log(0): nextDouble() is [0, 1) so guard the lower bound.
                double u = rng.nextDouble();
                if(u <= 0.0) u = Double.MIN_NORMAL;
                double v = rng.nextDouble();
                if(v <= 0.0) v = Double.MIN_NORMAL;

                double sumEnds = prevLogPrice + logPrice;
                double radMax  = Math.sqrt(dx2 - 2.0 * sigmaSqDt * Math.log(u));
                double radMin  = Math.sqrt(dx2 - 2.0 * sigmaSqDt * Math.log(v));
                double bridgeMax = 0.5 * (sumEnds + radMax);
                double bridgeMin = 0.5 * (sumEnds - radMin);

                if(bridgeMax > logMax) logMax = bridgeMax;
                if(bridgeMin < logMin) logMin = bridgeMin;
            }

            prevLogPrice = logPrice;
            count++;
        }

        @Override public double payoff() {
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

        @Override public void reset() {
            logMin = Double.POSITIVE_INFINITY;
            logMax = Double.NEGATIVE_INFINITY;
            prevLogPrice = 0.0;
            logLast = 0.0;
            count = 0;
        }

        @Override public boolean needsPrice() { return false; }
    }
}

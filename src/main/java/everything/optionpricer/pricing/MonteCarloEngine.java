package everything.optionpricer.pricing;

import everything.optionpricer.model.PathDependentOption;
import everything.optionpricer.model.PricingResult;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


/**
 * Monte Carlo pricer for path-dependent options under GBM.
 *
 * Performance choices:
 *  - antithetic variates: each Gaussian draw drives a (z, -z) pair, halving
 *    RNG calls and roughly halving estimator variance;
 *  - work is sliced into one batch per core, each worker reuses two
 *    accumulators via {@code reset()} — no per-simulation allocation;
 *  - per-step constants (dt, drift, diff) are computed once;
 *  - the path is propagated in log-space; engine skips {@code Math.exp} on
 *    every step when the accumulator reports {@code needsPrice() == false};
 *  - accumulators may signal early termination via {@code isDone()} (e.g.
 *    knocked-out OUT barriers);
 *  - RNG is {@link ThreadLocalRandom}, no cross-thread contention.
 *
 * @author lorenzobarbagelata
 */
public final class MonteCarloEngine {

    private static final int DEFAULT_SIMULATIONS = 100_000;

    private MonteCarloEngine() {}


    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, double volatility) {
        return price(option, spot, riskFreeRate, volatility, DEFAULT_SIMULATIONS);
    }


    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, double volatility, int sims) {

        validateInputs(option, spot, riskFreeRate, volatility, sims);

        final int tS = option.getTimeSteps();
        final double T = option.getTimeToExpiry();

        // tS observation points cover [0, T] inclusively.
        final double dt    = T / (tS - 1);
        final double drift = (riskFreeRate - 0.5 * volatility * volatility) * dt;
        final double diff  = volatility * Math.sqrt(dt);
        final double logSpot = Math.log(spot);
        final SimulationContext ctx = new SimulationContext(dt, volatility);

        // Antithetic: run pairs of paths (z, -z). Round up so the effective
        // sample count is at least `sims`.
        final int pairs = (sims + 1) / 2;
        final int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        final int batchSize   = (pairs + parallelism - 1) / parallelism;

        double sum = IntStream.range(0, parallelism).parallel().mapToDouble(t -> {
            int start = t * batchSize;
            int end   = Math.min(pairs, start + batchSize);
            if(start >= end) return 0.0;
            return runBatch(option, ctx, spot, logSpot, drift, diff, tS, end - start);
        }).sum();

        double averagePayoff = sum / (2.0 * pairs);
        double price = Math.exp(-riskFreeRate * T) * averagePayoff;
        return new PricingResult(price);
    }


    /**
     * Run {@code pairCount} antithetic simulation pairs. Allocates two
     * accumulators once and reuses them via {@link PathAccumulator#reset()}.
     */
    private static double runBatch(PathDependentOption option, SimulationContext ctx,
                                   double spot, double logSpot,
                                   double drift, double diff, int tS, int pairCount) {

        final PathAccumulator accPos = option.newAccumulator(ctx);
        final PathAccumulator accNeg = option.newAccumulator(ctx);
        final boolean needsPrice = accPos.needsPrice();
        final ThreadLocalRandom rng = ThreadLocalRandom.current();

        double sum = 0.0;

        for(int p = 0; p < pairCount; p++) {
            accPos.reset();
            accNeg.reset();
            accPos.accumulate(spot, logSpot);
            accNeg.accumulate(spot, logSpot);

            double logPos = logSpot;
            double logNeg = logSpot;

            for(int i = 1; i < tS; i++) {
                if(accPos.isDone() && accNeg.isDone()) break;

                double z = rng.nextGaussian();
                double dz = diff * z;
                logPos += drift + dz;
                logNeg += drift - dz;

                double pricePos = needsPrice ? Math.exp(logPos) : 0.0;
                double priceNeg = needsPrice ? Math.exp(logNeg) : 0.0;

                accPos.accumulate(pricePos, logPos);
                accNeg.accumulate(priceNeg, logNeg);
            }

            sum += accPos.payoff() + accNeg.payoff();
        }

        return sum;
    }


    private static void validateInputs(PathDependentOption option, double spot,
                                       double riskFreeRate, double volatility, int sims) {

        if(option == null)
            throw new IllegalArgumentException("Option cannot be null");

        if(spot <= 0.0)
            throw new IllegalArgumentException("Please enter valid current price");

        if(riskFreeRate >= 1.0 || riskFreeRate <= -0.2)
            throw new IllegalArgumentException("Please enter valid risk-free rate");

        if(volatility >= 5.0 || volatility <= 0.0)
            throw new IllegalArgumentException("Please enter valid volatility");

        if(sims <= 0)
            throw new IllegalArgumentException("Please enter valid number of simulations");
    }
}

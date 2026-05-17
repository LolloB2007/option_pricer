package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.PathDependentOption;
import everything.optionpricer.model.PricingResult;

import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


/**
 * Monte Carlo pricer for path-dependent options under GBM, with continuous
 * yield + discrete dividend support.
 *
 * @author lorenzobarbagelata
 */
public final class MonteCarloEngine {

    private static final int DEFAULT_SIMULATIONS = 100_000;

    /** Floor when a discrete dividend would push the path non-positive. */
    private static final double MIN_PRICE = 1e-9;

    private MonteCarloEngine() {}


    // ============================================================
    //  Unseeded entry points
    // ============================================================

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, double volatility) {
        return price(option, spot, riskFreeRate, volatility, DEFAULT_SIMULATIONS, DividendSchedule.NONE);
    }

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, double volatility, int sims) {
        return price(option, spot, riskFreeRate, volatility, sims, DividendSchedule.NONE);
    }

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, double volatility,
                                      DividendSchedule dividends) {
        return price(option, spot, riskFreeRate, volatility, DEFAULT_SIMULATIONS, dividends);
    }

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, double volatility,
                                      int sims, DividendSchedule dividends) {
        return run(option, spot, riskFreeRate, volatility, sims, dividends, /*seed*/ null);
    }


    // ============================================================
    //  Seeded entry points — for common-random-numbers Greeks
    // ============================================================

    public static PricingResult priceSeeded(PathDependentOption option, double spot,
                                            double riskFreeRate, double volatility,
                                            int sims, long seed) {
        return run(option, spot, riskFreeRate, volatility, sims, DividendSchedule.NONE, seed);
    }

    public static PricingResult priceSeeded(PathDependentOption option, double spot,
                                            double riskFreeRate, double volatility,
                                            int sims, long seed, DividendSchedule dividends) {
        return run(option, spot, riskFreeRate, volatility, sims, dividends, seed);
    }


    // ============================================================
    //  Core
    // ============================================================

    private static PricingResult run(PathDependentOption option, double spot,
                                     double riskFreeRate, double volatility,
                                     int sims, DividendSchedule dividends, Long seed) {

        validateInputs(option, spot, riskFreeRate, volatility, sims);
        if(dividends == null) dividends = DividendSchedule.NONE;

        final int tS = option.getTimeSteps();
        final double T = option.getTimeToExpiry();
        final double q = dividends.continuousYield();

        final double dt    = T / (tS - 1);
        final double drift = (riskFreeRate - q - 0.5 * volatility * volatility) * dt;
        final double diff  = volatility * Math.sqrt(dt);
        final double logSpot = Math.log(spot);
        final SimulationContext ctx = new SimulationContext(dt, volatility);

        // Precompute per-step discrete dividend drops.
        final double[] stepDivs = dividends.stepwiseAmounts(dt, tS);
        final boolean hasDiscreteDivs = dividends.hasDiscrete();

        final int pairs = (sims + 1) / 2;
        final int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        final int batchSize   = (pairs + parallelism - 1) / parallelism;
        final boolean seeded = (seed != null);
        final long seedVal = seeded ? seed : 0L;
        final DividendSchedule divsFinal = dividends; // unused inside lambda but kept for clarity

        double sum = IntStream.range(0, parallelism).parallel().mapToDouble(t -> {
            int start = t * batchSize;
            int end   = Math.min(pairs, start + batchSize);
            if(start >= end) return 0.0;
            return seeded
                    ? runBatchSeeded(option, ctx, spot, logSpot, drift, diff, tS,
                                     stepDivs, hasDiscreteDivs, start, end - start, seedVal)
                    : runBatch(option, ctx, spot, logSpot, drift, diff, tS,
                               stepDivs, hasDiscreteDivs, end - start);
        }).sum();

        double averagePayoff = sum / (2.0 * pairs);
        return new PricingResult(Math.exp(-riskFreeRate * T) * averagePayoff);
    }


    private static double runBatch(PathDependentOption option, SimulationContext ctx,
                                   double spot, double logSpot,
                                   double drift, double diff, int tS,
                                   double[] stepDivs, boolean hasDiscreteDivs,
                                   int pairCount) {

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

                double pricePos, priceNeg;
                if(hasDiscreteDivs && stepDivs[i] != 0.0) {
                    // Linear-space step needed: drop by the discrete amount, refresh log.
                    pricePos = Math.exp(logPos) - stepDivs[i];
                    priceNeg = Math.exp(logNeg) - stepDivs[i];
                    if(pricePos < MIN_PRICE) pricePos = MIN_PRICE;
                    if(priceNeg < MIN_PRICE) priceNeg = MIN_PRICE;
                    logPos = Math.log(pricePos);
                    logNeg = Math.log(priceNeg);
                } else {
                    pricePos = needsPrice ? Math.exp(logPos) : 0.0;
                    priceNeg = needsPrice ? Math.exp(logNeg) : 0.0;
                }

                accPos.accumulate(pricePos, logPos);
                accNeg.accumulate(priceNeg, logNeg);
            }

            sum += accPos.payoff() + accNeg.payoff();
        }

        return sum;
    }


    private static double runBatchSeeded(PathDependentOption option, SimulationContext ctx,
                                         double spot, double logSpot,
                                         double drift, double diff, int tS,
                                         double[] stepDivs, boolean hasDiscreteDivs,
                                         int pairStart, int pairCount, long seed) {

        final PathAccumulator accPos = option.newAccumulator(ctx);
        final PathAccumulator accNeg = option.newAccumulator(ctx);
        final boolean needsPrice = accPos.needsPrice();

        double sum = 0.0;

        for(int p = 0; p < pairCount; p++) {
            SplittableRandom rng = new SplittableRandom(mixSeed(seed, pairStart + p));

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

                double pricePos, priceNeg;
                if(hasDiscreteDivs && stepDivs[i] != 0.0) {
                    pricePos = Math.exp(logPos) - stepDivs[i];
                    priceNeg = Math.exp(logNeg) - stepDivs[i];
                    if(pricePos < MIN_PRICE) pricePos = MIN_PRICE;
                    if(priceNeg < MIN_PRICE) priceNeg = MIN_PRICE;
                    logPos = Math.log(pricePos);
                    logNeg = Math.log(priceNeg);
                } else {
                    pricePos = needsPrice ? Math.exp(logPos) : 0.0;
                    priceNeg = needsPrice ? Math.exp(logNeg) : 0.0;
                }

                accPos.accumulate(pricePos, logPos);
                accNeg.accumulate(priceNeg, logNeg);
            }

            sum += accPos.payoff() + accNeg.payoff();
        }

        return sum;
    }


    /** Deterministic 64-bit mix of (seed, index) for per-simulation RNG seeding. */
    public static long mixSeed(long seed, int index) {
        long h = seed ^ ((long) index * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 32);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return h;
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

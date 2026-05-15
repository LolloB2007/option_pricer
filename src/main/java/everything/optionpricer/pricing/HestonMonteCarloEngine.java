package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.HestonParams;
import everything.optionpricer.model.PathDependentOption;
import everything.optionpricer.model.PricingResult;

import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


/**
 * Monte Carlo pricer for path-dependent options under the Heston (1993)
 * stochastic-volatility model:
 *
 *   dS = (r − q) S dt + √v · S dW₁
 *   dv = κ(θ − v) dt + ξ √v        dW₂            corr(dW₁, dW₂) = ρ
 *
 * Discretisation: full-truncation Euler — every step floors {@code v} at
 * zero to keep the variance process valid without resorting to a more
 * elaborate scheme. With reasonable parameters and {@code timeSteps ≥ 252}
 * this matches Heston-Fourier closed-form prices to within typical MC
 * standard error.
 *
 * Performance choices mirror {@link MonteCarloEngine}:
 *  - antithetic variates on both Brownian drivers;
 *  - parallel chunked batches sized to {@link Runtime#availableProcessors()};
 *  - {@code ThreadLocalRandom} unseeded path / {@link SplittableRandom}
 *    per-pair seeded path.
 *
 * Caveat: continuous-monitoring accumulators (Barrier / Lookback) apply
 * Brownian-bridge corrections assuming constant volatility. Under Heston
 * the local variance moves, so those corrections become an approximation
 * (using {@code σ ≈ √θ}). For Heston path-dependent options prefer
 * discrete monitoring with a fine time grid.
 *
 * @author lorenzobarbagelata
 */
public final class HestonMonteCarloEngine {

    private static final int DEFAULT_SIMULATIONS = 100_000;
    private static final double MIN_PRICE = 1e-9;

    private HestonMonteCarloEngine() {}


    // ----- Unseeded entry points ----- //

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, HestonParams heston) {
        return price(option, spot, riskFreeRate, heston, DEFAULT_SIMULATIONS, DividendSchedule.NONE);
    }

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, HestonParams heston, int sims) {
        return price(option, spot, riskFreeRate, heston, sims, DividendSchedule.NONE);
    }

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, HestonParams heston,
                                      DividendSchedule dividends) {
        return price(option, spot, riskFreeRate, heston, DEFAULT_SIMULATIONS, dividends);
    }

    public static PricingResult price(PathDependentOption option, double spot,
                                      double riskFreeRate, HestonParams heston,
                                      int sims, DividendSchedule dividends) {
        return run(option, spot, riskFreeRate, heston, sims, dividends, /*seed*/ null);
    }


    // ----- Seeded entry point ----- //

    public static PricingResult priceSeeded(PathDependentOption option, double spot,
                                            double riskFreeRate, HestonParams heston,
                                            int sims, long seed, DividendSchedule dividends) {
        return run(option, spot, riskFreeRate, heston, sims, dividends, seed);
    }


    // ----- Core ----- //

    private static PricingResult run(PathDependentOption option, double spot, double r,
                                     HestonParams h, int sims,
                                     DividendSchedule dividends, Long seed) {

        validate(option, spot, h, sims);
        if(dividends == null) dividends = DividendSchedule.NONE;
        final DividendSchedule divs = dividends;

        final int tS = option.getTimeSteps();
        final double T = option.getTimeToExpiry();
        final double q = divs.continuousYield();

        final double dt = T / (tS - 1);
        final double sqrtDt = Math.sqrt(dt);
        final double drift = (r - q) * dt;
        final double logSpot = Math.log(spot);

        final double v0    = h.v0();
        final double kappa = h.kappa();
        final double theta = h.theta();
        final double xi    = h.xi();
        final double rho   = h.rho();
        final double sqrt1mRho2 = Math.sqrt(Math.max(0.0, 1.0 - rho * rho));

        // For continuous-monitoring accumulators (Barrier / Lookback bridge math)
        // we approximate the local variance with the long-run mean θ.
        final SimulationContext ctx = new SimulationContext(dt, Math.sqrt(Math.max(theta, 1e-12)));

        final double[] stepDivs = divs.stepwiseAmounts(dt, tS);
        final boolean hasDiscreteDivs = divs.hasDiscrete();

        final int pairs = (sims + 1) / 2;
        final int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        final int batchSize = (pairs + parallelism - 1) / parallelism;
        final boolean seeded = (seed != null);
        final long seedVal = seeded ? seed : 0L;

        double sum = IntStream.range(0, parallelism).parallel().mapToDouble(t -> {
            int start = t * batchSize;
            int end   = Math.min(pairs, start + batchSize);
            if(start >= end) return 0.0;
            return runBatch(option, ctx,
                            spot, logSpot, drift, sqrtDt, tS,
                            v0, kappa, theta, xi, rho, sqrt1mRho2,
                            stepDivs, hasDiscreteDivs,
                            start, end - start, seeded, seedVal);
        }).sum();

        double averagePayoff = sum / (2.0 * pairs);
        return new PricingResult(Math.exp(-r * T) * averagePayoff);
    }


    private static double runBatch(PathDependentOption option, SimulationContext ctx,
                                   double spot, double logSpot, double drift, double sqrtDt, int tS,
                                   double v0, double kappa, double theta, double xi,
                                   double rho, double sqrt1mRho2,
                                   double[] stepDivs, boolean hasDiscreteDivs,
                                   int pairStart, int pairCount, boolean seeded, long seed) {

        final PathAccumulator accPos = option.newAccumulator(ctx);
        final PathAccumulator accNeg = option.newAccumulator(ctx);
        final double dt = ctx.dt;
        final ThreadLocalRandom tlr = seeded ? null : ThreadLocalRandom.current();

        double sum = 0.0;
        for(int p = 0; p < pairCount; p++) {
            accPos.reset();
            accNeg.reset();
            accPos.accumulate(spot, logSpot);
            accNeg.accumulate(spot, logSpot);

            double logPos = logSpot, logNeg = logSpot;
            double vPos = v0, vNeg = v0;

            // Per-pair seeded RNG if requested (CRN); otherwise share the thread-local one.
            SplittableRandom srng = seeded
                    ? new SplittableRandom(MonteCarloEngine.mixSeed(seed, pairStart + p))
                    : null;

            for(int i = 1; i < tS; i++) {
                if(accPos.isDone() && accNeg.isDone()) break;

                double z1 = seeded ? srng.nextGaussian() : tlr.nextGaussian();
                double z2 = seeded ? srng.nextGaussian() : tlr.nextGaussian();

                double zCorr = rho * z1 + sqrt1mRho2 * z2;          // for vPos
                double zCorrAnti = -zCorr;                          // antithetic-mirror

                double sqrtVPos = Math.sqrt(Math.max(vPos, 0.0));
                double sqrtVNeg = Math.sqrt(Math.max(vNeg, 0.0));

                // logS update with full-truncation v_t in the drift correction.
                logPos += drift - 0.5 * Math.max(vPos, 0.0) * dt + sqrtVPos * sqrtDt * z1;
                logNeg += drift - 0.5 * Math.max(vNeg, 0.0) * dt + sqrtVNeg * sqrtDt * (-z1);

                // v update (full-truncation Euler).
                vPos = Math.max(0.0, vPos + kappa * (theta - vPos) * dt + xi * sqrtVPos * sqrtDt * zCorr);
                vNeg = Math.max(0.0, vNeg + kappa * (theta - vNeg) * dt + xi * sqrtVNeg * sqrtDt * zCorrAnti);

                double pricePos = Math.exp(logPos);
                double priceNeg = Math.exp(logNeg);

                if(hasDiscreteDivs && stepDivs[i] != 0.0) {
                    pricePos -= stepDivs[i]; if(pricePos < MIN_PRICE) pricePos = MIN_PRICE;
                    priceNeg -= stepDivs[i]; if(priceNeg < MIN_PRICE) priceNeg = MIN_PRICE;
                    logPos = Math.log(pricePos);
                    logNeg = Math.log(priceNeg);
                }

                accPos.accumulate(pricePos, logPos);
                accNeg.accumulate(priceNeg, logNeg);
            }

            sum += accPos.payoff() + accNeg.payoff();
        }
        return sum;
    }


    private static void validate(PathDependentOption option, double spot, HestonParams h, int sims) {
        if(option == null) throw new IllegalArgumentException("Option cannot be null");
        if(h == null)      throw new IllegalArgumentException("Heston params cannot be null");
        if(spot <= 0)      throw new IllegalArgumentException("Spot must be positive");
        if(sims <= 0)      throw new IllegalArgumentException("Sims must be positive");
    }
}

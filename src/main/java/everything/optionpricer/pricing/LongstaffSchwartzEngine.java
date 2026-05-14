package everything.optionpricer.pricing;

import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.PricingResult;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


/**
 * Longstaff–Schwartz Monte Carlo pricer for American (Bermudan) options.
 *
 * Algorithm: simulate {@code paths} GBM paths through {@code exerciseDates}
 * points; iterate backward from maturity; at each exercise date, regress
 * the discounted future cashflow of the in-the-money paths on the
 * polynomial basis {1, S, S²}; compare the regressed continuation value to
 * intrinsic exercise and update each path's exercise time accordingly.
 * Discount each path's chosen cashflow back to t=0 and average.
 *
 * Implementation notes:
 *  - antithetic variates: paths come in (+z, −z) pairs;
 *  - forward simulation is parallelised over worker threads;
 *  - the backward regression pass is sequential but cheap (O(P) per date);
 *  - the 3×3 normal-equations system is solved by Cramer's rule.
 *
 * @author lorenzobarbagelata
 */
public final class LongstaffSchwartzEngine {

    private static final int DEFAULT_PATHS = 50_000;

    private LongstaffSchwartzEngine() {}


    public static PricingResult price(AmericanOption option, double spot,
                                      double riskFreeRate, double volatility) {
        return price(option, spot, riskFreeRate, volatility, DEFAULT_PATHS);
    }


    public static PricingResult price(AmericanOption option, double spot,
                                      double riskFreeRate, double volatility, int paths) {

        validateInputs(option, spot, riskFreeRate, volatility, paths);

        final int N = option.getExerciseDates();
        final double T = option.getTimeToExpiry();
        final double K = option.getStrikePrice();
        final int sign = option.getSign();

        final double dt    = T / (N - 1);
        final double drift = (riskFreeRate - 0.5 * volatility * volatility) * dt;
        final double diff  = volatility * Math.sqrt(dt);
        final double discountStep = Math.exp(-riskFreeRate * dt);

        // Round up to an even path count for antithetic pairing.
        final int pairs = (paths + 1) / 2;
        final int totalPaths = pairs * 2;

        // Forward simulation: S[i][j] = price at date j on path i.
        final double[][] S = new double[totalPaths][N];

        final int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        final int batchSize   = (pairs + parallelism - 1) / parallelism;

        IntStream.range(0, parallelism).parallel().forEach(t -> {
            int start = t * batchSize;
            int end   = Math.min(pairs, start + batchSize);
            if(start >= end) return;
            simulatePaths(S, start, end, spot, drift, diff, N);
        });

        // Backward induction: choose exercise time per path.
        // cashflow[i] = payoff at exerciseTime[i].
        double[] cashflow = new double[totalPaths];
        int[]    exerciseTime = new int[totalPaths];

        for(int i = 0; i < totalPaths; i++) {
            cashflow[i] = Math.max(sign * (S[i][N - 1] - K), 0.0);
            exerciseTime[i] = N - 1;
        }

        // Scratch buffers for the regression pass.
        double[] x = new double[totalPaths];
        double[] y = new double[totalPaths];
        int[]    idx = new int[totalPaths];

        // Skip t=0 (no exercise decision at inception) and t=N-1 (terminal).
        for(int t = N - 2; t >= 1; t--) {
            int itm = 0;
            for(int i = 0; i < totalPaths; i++) {
                double Sit = S[i][t];
                double intrinsic = Math.max(sign * (Sit - K), 0.0);
                if(intrinsic > 0.0) {
                    x[itm] = Sit;
                    // Discounted cashflow from exercise time back to t.
                    int gap = exerciseTime[i] - t;
                    double pv = cashflow[i];
                    for(int g = 0; g < gap; g++) pv *= discountStep;
                    y[itm] = pv;
                    idx[itm] = i;
                    itm++;
                }
            }

            if(itm < 3) continue; // not enough in-the-money paths for the regression

            double[] coef = solveQuadraticNormalEquations(x, y, itm);
            if(coef == null) continue; // singular system — leave decisions unchanged

            double a = coef[0], b = coef[1], c = coef[2];

            for(int j = 0; j < itm; j++) {
                double sj = x[j];
                double continuation = a + sj * (b + c * sj);
                double exercise = Math.max(sign * (sj - K), 0.0);
                if(exercise > continuation) {
                    int i = idx[j];
                    cashflow[i] = exercise;
                    exerciseTime[i] = t;
                }
            }
        }

        // Discount each path's chosen cashflow back to t=0 and average.
        double sum = 0.0;
        for(int i = 0; i < totalPaths; i++) {
            double pv = cashflow[i];
            for(int g = 0; g < exerciseTime[i]; g++) pv *= discountStep;
            sum += pv;
        }
        double price = sum / totalPaths;

        return new PricingResult(price);
    }


    private static void simulatePaths(double[][] S, int pairStart, int pairEnd,
                                      double spot, double drift, double diff, int N) {

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for(int p = pairStart; p < pairEnd; p++) {
            int posIdx = 2 * p;
            int negIdx = 2 * p + 1;

            S[posIdx][0] = spot;
            S[negIdx][0] = spot;

            double logPos = Math.log(spot);
            double logNeg = logPos;

            for(int j = 1; j < N; j++) {
                double z = rng.nextGaussian();
                double dz = diff * z;
                logPos += drift + dz;
                logNeg += drift - dz;
                S[posIdx][j] = Math.exp(logPos);
                S[negIdx][j] = Math.exp(logNeg);
            }
        }
    }


    /**
     * Solve the 3×3 normal-equations system for the least-squares fit of
     * y ≈ a + b·x + c·x² using the first {@code n} entries of {@code x},
     * {@code y}. Returns {a, b, c} or {@code null} if the system is singular.
     */
    private static double[] solveQuadraticNormalEquations(double[] x, double[] y, int n) {

        double s0 = n;
        double s1 = 0, s2 = 0, s3 = 0, s4 = 0;
        double t0 = 0, t1 = 0, t2 = 0;

        for(int i = 0; i < n; i++) {
            double xi = x[i];
            double xi2 = xi * xi;
            double yi = y[i];
            s1 += xi;
            s2 += xi2;
            s3 += xi2 * xi;
            s4 += xi2 * xi2;
            t0 += yi;
            t1 += yi * xi;
            t2 += yi * xi2;
        }

        // Matrix M = [[s0,s1,s2],[s1,s2,s3],[s2,s3,s4]]; rhs = [t0,t1,t2].
        double det =
                s0 * (s2 * s4 - s3 * s3)
              - s1 * (s1 * s4 - s3 * s2)
              + s2 * (s1 * s3 - s2 * s2);

        if(Math.abs(det) < 1e-12) return null;

        double det0 =
                t0 * (s2 * s4 - s3 * s3)
              - s1 * (t1 * s4 - s3 * t2)
              + s2 * (t1 * s3 - s2 * t2);

        double det1 =
                s0 * (t1 * s4 - t2 * s3)
              - t0 * (s1 * s4 - s3 * s2)
              + s2 * (s1 * t2 - t1 * s2);

        double det2 =
                s0 * (s2 * t2 - s3 * t1)
              - s1 * (s1 * t2 - t1 * s2)
              + t0 * (s1 * s3 - s2 * s2);

        return new double[] { det0 / det, det1 / det, det2 / det };
    }


    private static void validateInputs(AmericanOption option, double spot,
                                       double riskFreeRate, double volatility, int paths) {

        if(option == null)
            throw new IllegalArgumentException("Option cannot be null");

        if(spot <= 0.0)
            throw new IllegalArgumentException("Please enter valid current price");

        if(riskFreeRate >= 1.0 || riskFreeRate <= -0.2)
            throw new IllegalArgumentException("Please enter valid risk-free rate");

        if(volatility >= 5.0 || volatility <= 0.0)
            throw new IllegalArgumentException("Please enter valid volatility");

        if(paths <= 0)
            throw new IllegalArgumentException("Please enter valid number of paths");
    }
}

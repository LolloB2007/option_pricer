package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.HestonParams;
import everything.optionpricer.model.OptionType;

import java.util.List;


/**
 * Heston calibration via Nelder-Mead simplex. Given a set of European
 * market quotes, solve for the five Heston parameters that minimise the
 * sum of squared price errors against {@link HestonEngine}.
 *
 * The bot use case: client uploads a chain of market prices once per
 * session and gets back {@code (v0, κ, θ, ξ, ρ)} it can plug into
 * {@code model=HESTON} on subsequent pricing calls.
 *
 * No gradients (Heston price has none in closed form) — Nelder-Mead is
 * the standard fit. Constraints applied by squashing through transforms:
 *   v0, κ, θ, ξ &gt; 0  via {@code exp}
 *   ρ ∈ (-1, 1)         via {@code tanh}
 *
 * @author lorenzobarbagelata
 */
public final class HestonCalibrator {

    private static final int    DEFAULT_MAX_ITER = 400;
    private static final double DEFAULT_TOL      = 1.0e-6;

    public record Quote(double strike, double timeToExpiry, OptionType type, double marketPrice) {}

    public record Result(HestonParams params, double rmse, int iterations, boolean converged) {}

    private HestonCalibrator() {}


    public static Result calibrate(List<Quote> quotes, double spot, double rate) {
        return calibrate(quotes, spot, rate, DividendSchedule.NONE, null, DEFAULT_MAX_ITER, DEFAULT_TOL);
    }


    public static Result calibrate(List<Quote> quotes, double spot, double rate,
                                   DividendSchedule dividends, HestonParams initialGuess,
                                   int maxIterations, double tolerance) {
        if(quotes == null || quotes.isEmpty())
            throw new IllegalArgumentException("Need at least one quote to calibrate");
        if(spot <= 0)
            throw new IllegalArgumentException("Spot must be positive");
        if(dividends == null) dividends = DividendSchedule.NONE;

        // Sensible starting params.
        HestonParams start = initialGuess != null
                ? initialGuess
                : new HestonParams(0.04, 2.0, 0.04, 0.30, -0.5);
        double[] x0 = pack(start);

        double[][] simplex = new double[6][5];
        simplex[0] = x0.clone();
        for(int i = 0; i < 5; i++) {
            simplex[i + 1] = x0.clone();
            simplex[i + 1][i] += 0.5;   // initial simplex step (in transformed coordinates)
        }
        double[] values = new double[6];
        for(int i = 0; i < 6; i++) values[i] = objective(simplex[i], quotes, spot, rate, dividends);

        final double alpha = 1.0;   // reflection
        final double gamma = 2.0;   // expansion
        final double rhoR  = 0.5;   // contraction
        final double sigmaS = 0.5;  // shrink

        int iters = 0;
        boolean converged = false;
        for(iters = 0; iters < maxIterations; iters++) {
            // Sort vertices by objective.
            sortSimplex(simplex, values);

            // Convergence: range of values across the simplex small enough.
            if(values[5] - values[0] < tolerance) { converged = true; break; }

            // Centroid of all but worst.
            double[] centroid = new double[5];
            for(int i = 0; i < 5; i++) for(int j = 0; j < 5; j++) centroid[j] += simplex[i][j];
            for(int j = 0; j < 5; j++) centroid[j] /= 5.0;

            // Reflect worst.
            double[] worst = simplex[5];
            double[] xr = combine(centroid, worst, 1 + alpha, -alpha);
            double fr = objective(xr, quotes, spot, rate, dividends);
            if(fr >= values[0] && fr < values[4]) {
                simplex[5] = xr; values[5] = fr;
                continue;
            }
            // Expansion.
            if(fr < values[0]) {
                double[] xe = combine(centroid, worst, 1 + alpha * gamma, -alpha * gamma);
                double fe = objective(xe, quotes, spot, rate, dividends);
                simplex[5] = fe < fr ? xe : xr;
                values[5]  = fe < fr ? fe : fr;
                continue;
            }
            // Contraction.
            double[] xc = combine(centroid, worst, 1 - rhoR, rhoR);
            double fc = objective(xc, quotes, spot, rate, dividends);
            if(fc < values[5]) {
                simplex[5] = xc; values[5] = fc;
                continue;
            }
            // Shrink toward best.
            double[] best = simplex[0];
            for(int i = 1; i < 6; i++) {
                for(int j = 0; j < 5; j++) {
                    simplex[i][j] = best[j] + sigmaS * (simplex[i][j] - best[j]);
                }
                values[i] = objective(simplex[i], quotes, spot, rate, dividends);
            }
        }

        sortSimplex(simplex, values);
        HestonParams fitted = unpack(simplex[0]);
        double sse = values[0];
        double rmse = Math.sqrt(sse / quotes.size());
        return new Result(fitted, rmse, iters, converged);
    }


    /** Sum of squared price errors. */
    private static double objective(double[] x, List<Quote> quotes, double spot,
                                    double rate, DividendSchedule divs) {
        HestonParams h;
        try { h = unpack(x); }
        catch(IllegalArgumentException e) { return 1e18; }

        double sse = 0.0;
        for(Quote q : quotes) {
            try {
                EuropeanOption opt = EuropeanOption.of(q.type, q.strike, q.timeToExpiry);
                double model = HestonEngine.price(opt, spot, rate, h, divs).getPrice();
                double err = model - q.marketPrice;
                sse += err * err;
            } catch(Exception ex) {
                return 1e18;
            }
        }
        return sse;
    }


    // ----- Param packing: keep the Nelder-Mead in unconstrained R^5 ----- //

    private static double[] pack(HestonParams h) {
        return new double[]{
                Math.log(Math.max(h.v0(),    1e-8)),
                Math.log(Math.max(h.kappa(), 1e-8)),
                Math.log(Math.max(h.theta(), 1e-8)),
                Math.log(Math.max(h.xi(),    1e-8)),
                atanh(clamp(h.rho(), -0.999, 0.999))
        };
    }

    private static HestonParams unpack(double[] x) {
        double v0    = Math.exp(x[0]);
        double kappa = Math.exp(x[1]);
        double theta = Math.exp(x[2]);
        double xi    = Math.exp(x[3]);
        double rho   = Math.tanh(x[4]);
        return new HestonParams(v0, kappa, theta, xi, rho);
    }

    private static double[] combine(double[] centroid, double[] worst, double a, double b) {
        double[] out = new double[5];
        for(int i = 0; i < 5; i++) out[i] = a * centroid[i] + b * worst[i];
        return out;
    }

    private static void sortSimplex(double[][] simplex, double[] values) {
        // Insertion sort on 6 elements — cheap.
        for(int i = 1; i < 6; i++) {
            for(int j = i; j > 0 && values[j - 1] > values[j]; j--) {
                double tv = values[j - 1]; values[j - 1] = values[j]; values[j] = tv;
                double[] ts = simplex[j - 1]; simplex[j - 1] = simplex[j]; simplex[j] = ts;
            }
        }
    }

    private static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}

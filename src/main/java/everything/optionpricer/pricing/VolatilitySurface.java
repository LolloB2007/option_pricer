package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.OptionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;


/**
 * Implied-volatility surface fitted from a set of European market quotes.
 *
 * The fitter inverts each quote under Black-Scholes (with the supplied
 * rate and dividend schedule) to recover its implied vol, then organises
 * the points by expiry slice. {@link #volAt(double, double)} interpolates
 * linearly in strike within each bracketing slice and linearly in time
 * between slices, with flat extrapolation outside the convex hull of the
 * fitted points.
 *
 * This is the minimal practical "fitter" — it captures both term
 * structure (different slices per expiry) and skew (varying σ per strike
 * inside a slice) without committing to a particular parametric form
 * (SVI / SABR / Heston calibration), which would tie the surface to a
 * specific model.
 *
 * @author lorenzobarbagelata
 */
public final class VolatilitySurface {

    /** A single market quote. */
    public record Quote(double strike, double timeToExpiry, OptionType type, double marketPrice) {}

    /** A fitted surface point. */
    public record Point(double strike, double timeToExpiry, double impliedVolatility) {}

    /** Quotes that could not be inverted (price outside the no-arb band, etc.). */
    public record Failed(Quote quote, String reason) {}


    /** One per-expiry slice — sorted by strike. */
    private static final class Slice {
        final double[] strikes;
        final double[] vols;
        Slice(double[] strikes, double[] vols) {
            this.strikes = strikes;
            this.vols = vols;
        }
    }

    private final double[] expiries; // sorted ascending
    private final Slice[] slices;    // parallel to expiries
    private final List<Point>  points;
    private final List<Failed> failures;


    private VolatilitySurface(double[] expiries, Slice[] slices,
                              List<Point> points, List<Failed> failures) {
        this.expiries = expiries;
        this.slices = slices;
        this.points = List.copyOf(points);
        this.failures = List.copyOf(failures);
    }


    // ----- Fitting ----- //

    public static VolatilitySurface fit(List<Quote> quotes, double spot, double rate) {
        return fit(quotes, spot, rate, DividendSchedule.NONE);
    }

    public static VolatilitySurface fit(List<Quote> quotes, double spot, double rate,
                                        DividendSchedule dividends) {
        if(quotes == null || quotes.isEmpty())
            throw new IllegalArgumentException("Need at least one quote");
        if(spot <= 0) throw new IllegalArgumentException("Spot must be positive");
        if(dividends == null) dividends = DividendSchedule.NONE;

        // Group by expiry. TreeMap sorts the expiries naturally.
        TreeMap<Double, List<Point>> byExpiry = new TreeMap<>();
        List<Point>  flatPoints = new ArrayList<>(quotes.size());
        List<Failed> failures   = new ArrayList<>();

        for(Quote q : quotes) {
            try {
                if(q.strike <= 0 || q.timeToExpiry <= 0)
                    throw new IllegalArgumentException("strike and timeToExpiry must be positive");
                EuropeanOption opt = EuropeanOption.of(q.type, q.strike, q.timeToExpiry);
                double iv = ImpliedVolatility.impliedVolatility(opt, spot, rate, q.marketPrice, dividends);
                Point pt = new Point(q.strike, q.timeToExpiry, iv);
                byExpiry.computeIfAbsent(q.timeToExpiry, k -> new ArrayList<>()).add(pt);
                flatPoints.add(pt);
            } catch(IllegalArgumentException ex) {
                failures.add(new Failed(q, ex.getMessage() == null ? "could not invert" : ex.getMessage()));
            }
        }

        if(byExpiry.isEmpty()) {
            // All quotes rejected.
            return new VolatilitySurface(new double[0], new Slice[0], flatPoints, failures);
        }

        double[] expiries = byExpiry.keySet().stream().mapToDouble(Double::doubleValue).toArray();
        Slice[] slices = new Slice[expiries.length];
        int i = 0;
        for(var entry : byExpiry.entrySet()) {
            List<Point> ps = entry.getValue();
            ps.sort(Comparator.comparingDouble(Point::strike));
            double[] strikes = new double[ps.size()];
            double[] vols    = new double[ps.size()];
            for(int j = 0; j < ps.size(); j++) {
                strikes[j] = ps.get(j).strike();
                vols[j]    = ps.get(j).impliedVolatility();
            }
            slices[i++] = new Slice(strikes, vols);
        }
        return new VolatilitySurface(expiries, slices, flatPoints, failures);
    }


    // ----- Queries ----- //

    public List<Point>  points()   { return points; }
    public List<Failed> failures() { return failures; }
    public double[]     expiries() { return expiries.clone(); }

    public boolean isEmpty() { return expiries.length == 0; }


    /**
     * Look up the implied vol at the given strike / expiry.
     *
     * Linear interpolation in strike inside each bracketing slice and
     * linear interpolation in T between slices. Flat extrapolation at
     * the boundaries.
     */
    public double volAt(double strike, double timeToExpiry) {
        if(expiries.length == 0)
            throw new IllegalStateException("Surface is empty");

        // Locate bracketing expiry slices.
        int hi = Arrays.binarySearch(expiries, timeToExpiry);
        if(hi >= 0) return interpInSlice(slices[hi], strike);
        int insertion = -hi - 1; // index of first expiry strictly greater than T

        if(insertion == 0)                 return interpInSlice(slices[0], strike);
        if(insertion == expiries.length)   return interpInSlice(slices[expiries.length - 1], strike);

        Slice  loSlice = slices[insertion - 1];
        Slice  hiSlice = slices[insertion];
        double loT = expiries[insertion - 1];
        double hiT = expiries[insertion];
        double vLo = interpInSlice(loSlice, strike);
        double vHi = interpInSlice(hiSlice, strike);
        double w = (timeToExpiry - loT) / (hiT - loT);
        return vLo + (vHi - vLo) * w;
    }


    private static double interpInSlice(Slice s, double strike) {
        int n = s.strikes.length;
        if(n == 0) throw new IllegalStateException("Empty slice");
        if(n == 1) return s.vols[0];
        if(strike <= s.strikes[0])      return s.vols[0];
        if(strike >= s.strikes[n - 1])  return s.vols[n - 1];
        int hi = Arrays.binarySearch(s.strikes, strike);
        if(hi >= 0) return s.vols[hi];
        int insertion = -hi - 1;
        double k0 = s.strikes[insertion - 1];
        double k1 = s.strikes[insertion];
        double v0 = s.vols[insertion - 1];
        double v1 = s.vols[insertion];
        double w = (strike - k0) / (k1 - k0);
        return v0 + (v1 - v0) * w;
    }
}

package everything.optionpricer.model;

import java.util.Arrays;


/**
 * Underlying dividend assumptions. Supports both:
 *   - a continuous dividend yield {@code q} (annual, in decimal), and
 *   - a list of discrete cash dividends paid at specific times.
 *
 * The two can be combined.
 *
 * Discrete-dividend convention (escrowed model): for closed-form
 * Black-Scholes the engine subtracts the present value of discrete
 * dividends from spot before pricing. For Monte Carlo / LSM the engine
 * drops the path by the dividend amount at the corresponding step.
 *
 * @author lorenzobarbagelata
 */
public final class DividendSchedule {

    private static final double[] EMPTY = new double[0];

    /** No dividends — continuous yield 0, no discrete payments. */
    public static final DividendSchedule NONE = new DividendSchedule(0.0, EMPTY, EMPTY);

    private final double continuousYield;
    private final double[] times;   // sorted ascending
    private final double[] amounts; // parallel to times


    public DividendSchedule(double continuousYield, double[] times, double[] amounts) {
        if(continuousYield < 0.0 || continuousYield >= 1.0)
            throw new IllegalArgumentException("continuous yield must be in [0, 1)");
        if(times == null) times = EMPTY;
        if(amounts == null) amounts = EMPTY;
        if(times.length != amounts.length)
            throw new IllegalArgumentException("dividend times and amounts must have equal length");

        // Defensive copy + sort by time so internal logic can binary-search etc.
        double[] t = times.clone();
        double[] a = amounts.clone();
        // Simple insertion sort — these arrays are tiny.
        for(int i = 1; i < t.length; i++) {
            double tk = t[i], ak = a[i];
            int j = i - 1;
            while(j >= 0 && t[j] > tk) { t[j + 1] = t[j]; a[j + 1] = a[j]; j--; }
            t[j + 1] = tk; a[j + 1] = ak;
        }
        for(int i = 0; i < t.length; i++) {
            if(t[i] <= 0) throw new IllegalArgumentException("dividend times must be positive");
            if(a[i] <  0) throw new IllegalArgumentException("dividend amounts must be non-negative");
        }

        this.continuousYield = continuousYield;
        this.times   = t;
        this.amounts = a;
    }


    public static DividendSchedule continuous(double yield) {
        return new DividendSchedule(yield, EMPTY, EMPTY);
    }

    public static DividendSchedule discrete(double[] times, double[] amounts) {
        return new DividendSchedule(0.0, times, amounts);
    }


    public double continuousYield() { return continuousYield; }
    public int    discreteCount()   { return times.length; }
    public double time(int i)       { return times[i]; }
    public double amount(int i)     { return amounts[i]; }
    public boolean hasDiscrete()    { return times.length > 0; }
    public boolean isNone()         { return continuousYield == 0.0 && times.length == 0; }


    /**
     * Present value of all discrete dividends paid strictly before {@code upTo},
     * discounted at the risk-free rate {@code r}.
     */
    public double presentValueBefore(double upTo, double r) {
        double sum = 0.0;
        for(int i = 0; i < times.length; i++) {
            if(times[i] < upTo) sum += amounts[i] * Math.exp(-r * times[i]);
            else break; // sorted
        }
        return sum;
    }


    /**
     * Return an array of length {@code stepCount + 1} where entry {@code i}
     * holds the sum of any discrete dividends paid in the half-open interval
     * {@code (stepDt·(i-1), stepDt·i]}. Index 0 is always 0 (initial sample).
     * Used by Monte Carlo to drop the path at the right steps without
     * scanning the dividend list every step.
     */
    public double[] stepwiseAmounts(double dt, int stepCount) {
        double[] perStep = new double[stepCount];
        if(times.length == 0) return perStep;
        for(int i = 0; i < times.length; i++) {
            double tau = times[i];
            // step index j such that (j-1)*dt < tau <= j*dt   →   j = ceil(tau/dt)
            int j = (int) Math.ceil(tau / dt - 1e-12);
            if(j < 1) j = 1;
            if(j >= stepCount) continue; // dividend after maturity — ignored
            perStep[j] += amounts[i];
        }
        return perStep;
    }


    @Override
    public String toString() {
        return "DividendSchedule{q=" + continuousYield + ", discrete=" + Arrays.toString(times) + "/" + Arrays.toString(amounts) + "}";
    }
}

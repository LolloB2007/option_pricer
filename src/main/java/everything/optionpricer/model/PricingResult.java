package everything.optionpricer.model;


/**
 * Result of an option pricing.
 *
 * Carries the price and, for Monte-Carlo-style estimators, the
 * estimator's standard error (so the bot can size positions
 * accordingly) and the number of paths consumed.
 *
 * Closed-form pricers (BS, Binomial, PDE, Heston-Fourier) return
 * {@code stdError = null} and {@code paths = null}.
 *
 * @author lorenzobarbagelata
 */
public class PricingResult {

    private final double price;
    private final Double stdError; // null for closed-form pricers
    private final Integer paths;   // null for closed-form pricers


    public PricingResult(double price) {
        this(price, null, null);
    }

    public PricingResult(double price, double stdError, int paths) {
        this(price, Double.valueOf(stdError), Integer.valueOf(paths));
    }

    private PricingResult(double price, Double stdError, Integer paths) {
        this.price = price;
        this.stdError = stdError;
        this.paths = paths;
    }


    public double getPrice() {
        return price;
    }

    /** Estimator standard error in price units, or {@code null} for closed-form. */
    public Double getStdError() {
        return stdError;
    }

    /** Number of simulated paths consumed, or {@code null} for closed-form. */
    public Integer getPaths() {
        return paths;
    }


    @Override
    public String toString() {
        if(stdError == null) return String.format("Price: %.3f", price);
        return String.format("Price: %.3f ± %.4f (n=%d)", price, stdError, paths);
    }
}

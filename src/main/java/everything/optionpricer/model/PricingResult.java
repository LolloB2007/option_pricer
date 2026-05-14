package everything.optionpricer.model;


/**
 * Result of an option pricing.
 * @author lorenzobarbagelata
 */
public class PricingResult {

    private final double price;

    public PricingResult(double price) {
        this.price = price;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return String.format("Price: %.3f", price);
    }
}

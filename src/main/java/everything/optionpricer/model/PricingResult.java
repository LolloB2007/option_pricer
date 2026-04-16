package everything.optionpricer.model;


/**
 * Data regarding pricing of given option
 * @author lorenzobarbagelata
 */
public class PricingResult {
    
    private final double price;
    
    public PricingResult(double price) {
        this.price = price;
    }
    
    public double getPrice() {
        return this.price;
    }
    
}

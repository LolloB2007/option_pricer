package everything.optionpricer.model;


/**
 * Data regarding pricing of given option
 * @author lorenzobarbagelata
 */
public class PricingResult {
    
    private final double price;
    
    
    /**
     * Constructor. Used by BlackScholesEngine
     * @param price 
     */
    public PricingResult(double price) {
        this.price = price;
    }
    
    
    /**
     * Getter method for price
     * @return double
     */
    public double getPrice() {
        return this.price;
    }
    
    public String toString() {
        return "Price: " + price;
    }
    
}

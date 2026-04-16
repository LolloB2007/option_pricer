package everything.optionpricer.model;

import static everything.optionpricer.model.OptionType.*;
import everything.optionpricer.pricing.*;


/**
 * Data regarding pricing of given option
 * @author lorenzobarbagelata
 */
public class PricingResult {
    
    private Option option; //timeToExpiry and strikePrice contained in here. Use getters if needed
    private double riskFreeRate;
    private double volatility;
    private double currentPrice;
    
    
    /**
     * Not sure how this constructor is going to be used
     * @param option
     * @param r
     * @param v
     * @param p 
     */
    public PricingResult(Option option, double r, double v, double p) {
        this.option = option;
        this.riskFreeRate = r;
        this.volatility = v;
        this.currentPrice = p;
    }
    
    
    /**
     * Method that prices the option depending on CALL or PUT
     * @return double
     */
    public double price() {
        
        OptionType type = option.getOptionType();
        
        if(type == CALL) {
            double d1 = BlackScholesEngine.d1(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry());
            double d2 = (d1 - (volatility*Math.sqrt(option.getTimeToExpiry())));
            
            return BlackScholesEngine.cost(currentPrice, option.getStrikePrice(), riskFreeRate, option.getTimeToExpiry(), d1, d2, option.getOptionType());
        }
        
        if(type == PUT) {
            double d1 = BlackScholesEngine.d1(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry());
            double d2 = (d1 - (volatility*Math.sqrt(option.getTimeToExpiry())));
            
            return BlackScholesEngine.cost(currentPrice, option.getStrikePrice(), riskFreeRate, option.getTimeToExpiry(), d1, d2, option.getOptionType());
        }
        
        return 0.0; //in case optiontype not in enum, even though error should be thrown
    }
    
}

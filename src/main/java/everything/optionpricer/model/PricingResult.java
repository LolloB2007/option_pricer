package everything.optionpricer.model;

import static everything.optionpricer.model.OptionType.CALL;
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
    
    public PricingResult(Option option, double r, double v, double p) {
        this.option = option;
        this.riskFreeRate = r;
        this.volatility = v;
        this.currentPrice = p;
    }
    
    public double price() {
        
        OptionType type = option.getOptionType();
        
        if(type == CALL) {
            double d1 = BlackScholesEngine.d1(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry());
        }
        
        return 0.0; //just to prevent IDE error from being annoying
    }
    
}

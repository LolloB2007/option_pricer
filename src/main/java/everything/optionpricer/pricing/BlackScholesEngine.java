package everything.optionpricer.pricing;

import everything.optionpricer.model.Option;
import everything.optionpricer.model.OptionType;
import static everything.optionpricer.model.OptionType.*;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.util.NormalDistribution;


/**
 * Engine for computation using BlackScholes
 * @author lorenzobarbagelata
 */
public class BlackScholesEngine {
    
    
    /**
     * Calculates d1 parameter for pricing
     * @param S
     * @param K
     * @param r
     * @param v
     * @param t
     * @return double
     */
    private static double d1(double S, double K, double r, double v, double t)
    {
        double num = (Math.log(S/K) + ((r + (0.5)*Math.pow(v, 2))*(t)));
        double denom = (v * Math.sqrt(t));
        
        return (num/denom);
    }
    
    
    /**
     * Calculates d2 parameter for pricing
     * @param S
     * @param K
     * @param r
     * @param v
     * @param t
     * @return 
     */
    private static double d2(double S, double K, double r, double v, double t)
    {
        return (d1(S, K, r, v, t) - (v*Math.sqrt(t)));
    }
    
    
    /**
     * Method that returns current price of option. Assists "price"
     * @param S
     * @param K
     * @param r
     * @param t
     * @param d1
     * @param d2
     * @return 
     */
    private static double cost(double S, double K, double r, double t, double d1, double d2, OptionType type) {
        
        if(type == CALL) {
            double partOne = S * NormalDistribution.cdf(d1);
            double partTwo = K * Math.pow(Math.E, (-r * t)) * NormalDistribution.cdf(d2);

            return partOne - partTwo;
        }
        
        if(type == PUT) {
            return (cost(S, K, r, t, d1, d2, CALL) - S + (K*Math.exp(-r*t)));
        }
        
        return 0.0; //if type is not in Enum, though error should be thrown
    }
    
    
    /**
     * Method that returns price of option depending on the type
     * @param option
     * @param currentPrice
     * @param riskFreeRate
     * @param volatility
     * @return PricingResult
     */
    public static PricingResult price(Option option, double currentPrice, double riskFreeRate, double volatility)
    {
        OptionType type = option.getOptionType();
        double price = 0.0;
        
        if(type == CALL) {
            double d1 = BlackScholesEngine.d1(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry());
            double d2 = (d1 - (volatility*Math.sqrt(option.getTimeToExpiry())));
            
            price = BlackScholesEngine.cost(currentPrice, option.getStrikePrice(), riskFreeRate, option.getTimeToExpiry(), 
                    d1(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry()), 
                    d2(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry()), 
                    option.getOptionType());
        }
        
        if(type == PUT) {
            double d1 = BlackScholesEngine.d1(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry());
            double d2 = (d1 - (volatility*Math.sqrt(option.getTimeToExpiry())));
            
            price = BlackScholesEngine.cost(currentPrice, option.getStrikePrice(), riskFreeRate, option.getTimeToExpiry(), 
                    d1(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry()), 
                    d2(currentPrice, option.getStrikePrice(), riskFreeRate, volatility, option.getTimeToExpiry()), 
                    option.getOptionType());
        }
        
        return new PricingResult(price);
    }
}

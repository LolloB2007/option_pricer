package everything.optionpricer.pricing;

import everything.optionpricer.model.OptionType;
import static everything.optionpricer.model.OptionType.CALL;
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
    public static double d1(double S, double K, double r, double v, double t)
    {
        double num = (Math.log(S/K) + ((r + (1/2)*Math.pow(v, 2))*(t)));
        double denom = (v * Math.sqrt(t));
        
        return (num/denom);
    }
    
    
    /**
     * Method that returns current price of option
     * @param S
     * @param K
     * @param r
     * @param t
     * @param d1
     * @param d2
     * @return 
     */
    public static double cost(double S, double K, double r, double t, double d1, double d2, OptionType type) {
        
        if(type == CALL) {
            double partOne = S * NormalDistribution.cdf(d1);
            double partTwo = K * Math.pow(Math.E, (-r * t)) * NormalDistribution.cdf(d2);

            return partOne - partTwo;
        }
        
        return 0.0; //temporarily added
    }
}

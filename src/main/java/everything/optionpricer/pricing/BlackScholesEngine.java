package everything.optionpricer.pricing;


/**
 * Engine for computation using BlackScholes
 * @author lorenzobarbagelata
 */
public class BlackScholesEngine {
    
    public static double d1(double S, double K, double r, double v, double t)
    {
        double num = (Math.log(S/K) + ((r + (1/2)*Math.pow(v, 2))*(t)));
        double denom = (v * Math.sqrt(t));
        
        return (num/denom);
    }
}

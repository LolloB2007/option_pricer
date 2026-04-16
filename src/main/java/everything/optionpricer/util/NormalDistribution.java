package everything.optionpricer.util;


/**
 * Assistant for standard Normal Distribution calculations
 * Will definitely be used with Black Scholes, maybe for future expansions of project
 * @author lorenzobarbagelata
 */
public final class NormalDistribution {
    
    private static final double ONE_OVER_SQRT_2PI = 1 / (Math.sqrt(2 * Math.PI));
    private static final double SQRT_2 = Math.sqrt(2);
    
    
    /**
     * Returns standard normal distribution pdf at x
     * @param x
     * @return double
     */
    public static double pdf(double x) {
        
        return ONE_OVER_SQRT_2PI * Math.exp(-0.5 * x * x);
    }
    
    
    /**
     * Returns standard normal distribution cdf at x
     * Uses Abramowitz-Stegun style approximation
     * @param x
     * @return double
     */
    public static double cdf(double x) {
        
        if(x > 8.0)
            return 1.0;
        
        if(x < -8.0)
            return 0.0;
        
        double absX = Math.abs(x);
        double temp = 1.0 / (1.0 + 0.2316419 * absX);
        
        double poly = temp * (0.319381530 +
                temp * (-0.356563782 +
                temp * (1.781477937 +
                temp * (-1.821255978 +
                temp * 1.330274429)))
                );
        
        double approx = 1.0 - (pdf(absX) * poly);
        
        return x >= 0.0 ? approx : (1.0 - approx);
    }
    
    
    /**
     * Returns complement of cdf(x)
     * @param x
     * @return double
     */
    public static double cdfComplement(double x) {
        
        return 1.0 - cdf(x);
    }
    
    
    /**
     * Returns inverse cdf of p. Uses mix of Schmeiser and Shore approximations for calculation
     * @param p
     * @return double
     */
    public static double cdfInverse(double p) {
        
        if(0.5 <= p && p < 0.958) {
            return ((Math.pow(p, 0.135) - Math.pow(1-p, 0.135))/0.1975); //Schmeiser
        } else if (p >= 0.958) { 
            return (-5.531*(Math.pow((1-p)/p, 0.1193)-1)); //Shore
        } else {
            return (-1.0 * cdfInverse(1-p)); //Recursive call for negative case
        }
    }
    
}

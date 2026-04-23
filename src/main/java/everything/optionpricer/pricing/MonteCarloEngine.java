package everything.optionpricer.pricing;

import everything.optionpricer.model.*;
import everything.optionpricer.util.*;

/**
 * Engine for computation using Monte Carlo simulations
 * @author lorenzobarbagelata
 */
public class MonteCarloEngine {

    private static final int DEFAULT_SIMULATIONS = 100000;
    
    
    /**
     * Caller method for pricing
     * @param option
     * @param currentPrice
     * @param riskFreeRate
     * @param volatility
     * @return PricingResult
     */
    public static PricingResult price(PathDependentOption option, double currentPrice, double riskFreeRate, double volatility) {
        return price(option, currentPrice, riskFreeRate, volatility, DEFAULT_SIMULATIONS);
    }
    
    
    /**
     * Actual pricing computation
     * @param o
     * @param S
     * @param r
     * @param v
     * @param sims
     * @return PricingResult
     */
    private static PricingResult price(PathDependentOption o, double S, double r, double v, int sims) {
        //some code. will do shortly, prolly tomorrow
    }
}

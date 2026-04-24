package everything.optionpricer.pricing;

import everything.optionpricer.model.*;
import everything.optionpricer.util.*;
import java.util.Random;

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
        
        validateInputs(o, S, r, v);
        
        Random rng = new Random();
        
        double payoffSum = 0.0;
        double timeToExpiry = o.getTimeToExpiry();
        int steps = o.getTimeSteps();
        
        for(int i = 0; i<sims; i++) {
            LinkedList paths = simulatePath(o, S, r, v, sims, rng);
            
            payoffSum = payoffSum + o.payoff(paths);
        }
        
        double averagePayoff = payoffSum / sims;
        double price = discount(averagePayoff, r, timeToExpiry);
        
        return new PricingResult(price);
    }

    
    /**
     * Validates inputs given by user
     * @param o
     * @param S
     * @param r
     * @param v 
     */
    private static void validateInputs(PathDependentOption o, double S, double r, double v) {
        
        if (o == null) {
            throw new IllegalArgumentException("Option cannot be null");
        }

        if (S <= 0.0) {
            throw new IllegalArgumentException("Please enter valid current price");
        }

        if (r >= 1 || r <= 0.0) {
            throw new IllegalArgumentException("Please enter valid risk-free rate");
        }

        if (v >= 5.0 || v <= 0.0) {
            throw new IllegalArgumentException("Please enter valid volatility");
        }

    }
    
    
    /**
     * Calculates discount with compound discount rate
     * @param averagePayoff
     * @param r
     * @param timeToExpiry
     * @return 
     */
    private static double discount(double averagePayoff, double r, double timeToExpiry) {
        return (Math.exp(-r * timeToExpiry) * averagePayoff);
    }

    
    /**
     * The place where magic happens
     * @param o
     * @param S
     * @param r
     * @param v
     * @param sims
     * @param rng
     * @return 
     */
    private static LinkedList simulatePath(PathDependentOption o, double S, double r, double v, int sims, Random rng) {
        //the magic still needs to be researched
    }

    
    

    
}

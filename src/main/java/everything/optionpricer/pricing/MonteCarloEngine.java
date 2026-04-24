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
        
        validateInputs(o, S, r, v, sims);
        
        double payoffSum = 0.0;
        double timeToExpiry = o.getTimeToExpiry();
        
        for(int i = 0; i<sims; i++) {
            double[] paths = simulatePath(o, S, r, v);
            
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
     * @param sims
     */
    private static void validateInputs(PathDependentOption o, double S, double r, double v, int sims) {
        
        if (o == null) {
            throw new IllegalArgumentException("Option cannot be null");
        }

        if (S <= 0.0) {
            throw new IllegalArgumentException("Please enter valid current price");
        }

        if (r >= 1 || r <= -0.2) {
            throw new IllegalArgumentException("Please enter valid risk-free rate");
        }

        if (v >= 5.0 || v <= 0.0) {
            throw new IllegalArgumentException("Please enter valid volatility");
        }
        
        if(sims <= 0) {
            throw new IllegalArgumentException("Please enter valid number of simulations");
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
     * @return LinkedList
     */
    private static double[] simulatePath(PathDependentOption o, double S, double r, double v) {
        
        int tS = o.getTimeSteps();
        double[] path = new double[tS];
        
        double ttE = o.getTimeToExpiry();
        
        double deltaT = dt(ttE, tS);
        double drift = drift(r, v, deltaT);
        double diff = diffusion(v, deltaT);
        
        double price = S;
        path[0] = price;
        
        for(int i = 1; i<tS; i++) {
            double z = NormalDistribution.sampleStandardNormal();
            price = price * Math.exp(drift + diff*z);
            path[i] = price;
        }
        
        return path;
    }

    
    /**
     * Calculates delta time
     * @param ttE
     * @param tS
     * @return double
     */
    private static double dt(double ttE, double tS) {
        return (ttE / tS);
    }

    
    /**
     * Calculates drift correction term
     * @param r
     * @param v
     * @param deltaT
     * @return double
     */
    private static double drift(double r, double v, double deltaT) {
        return ((r - (0.5 * v * v)) * deltaT);
    }

    
    /**
     * Calculates diffusion term
     * @param v
     * @param deltaT
     * @return double
     */
    private static double diffusion(double v, double deltaT) {
        return (v * Math.sqrt(deltaT));
    }
    
}

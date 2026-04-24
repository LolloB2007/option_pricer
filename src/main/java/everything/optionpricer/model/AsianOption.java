package everything.optionpricer.model;

import everything.optionpricer.util.LinkedList;

/**
 * implementation of asian options
 * @author lorenzobarbagelata
 */
public class AsianOption extends PathDependentOption {
    
    private boolean arithmeticAverage;
    
    
    /**
     * Constructor for option
     * @param sP --> strike price
     * @param tE --> time to expiry
     * @param type --> option type
     * @param tS --> time steps
     * @param dM --> discrete monitoring
     * @param arithmeticAverage --> 
     */
    public AsianOption(double sP, double tE, OptionType type, int tS, boolean dM, boolean arithmeticAverage) {
        super(sP, tE, type, tS, dM);
        
        this.arithmeticAverage = arithmeticAverage;
    }

    
    /**
     * Getter for arithmetic average
     * @return boolean
     */
    public boolean isArithmeticAverage() {
        return arithmeticAverage;
    }
    

    /**
     * Returns payoff of option
     * @param path
     * @return double
     */
    @Override
    public double payoff(LinkedList path) {
        double average;
        
        if(this.arithmeticAverage) {
            average = computeArithmeticAverage(path);
        } else {
            average = computeGeometricAverage(path);
        }
        
        if(this.isCall()) {
            return Math.max(average - this.getStrikePrice(), 0.0);
        } else {
            return Math.max(this.getStrikePrice() - average, 0.0);
        }
    }

    
    /**
     * Computes arithmetic average of prices in path
     * Helper to payoff
     * @param path
     * @return double
     */
    private double computeArithmeticAverage(LinkedList path) {
        int length = path.size();
        double total = 0.0;
        
        for(int i = 0; i<length; i++) {
            total += (double) path.get(i);
        }
        
        return (total/length);
    }

    
    /**
     * Computes geometric average of prices in path
     * Uses log addition to avoid issues with numbers getting too big for java
     * Helper to payoff
     * @param path
     * @return double
     */
    private double computeGeometricAverage(LinkedList path) {
        int length = path.size();
        double lnSum = 0.0;
        
        for(int i = 0; i<length; i++) {
            lnSum = Math.log((double) path.get(i));
        }
        
        return (Math.exp(lnSum/length));
    }
}

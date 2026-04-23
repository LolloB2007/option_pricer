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
    public double payoff(LinkedList<Double> path) {
        return 0.0;
    }
}

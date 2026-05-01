package everything.optionpricer.model;

/**
 * implementation of lookback options
 * @author lorenzobarbagelata
 */
public class LookbackOption extends PathDependentOption {

    private final boolean fixedStrike;
    
    
    /**
     * Constructor for LookbackOption
     * @param sP
     * @param tE
     * @param type
     * @param tS
     * @param discreteMonitoring
     * @param fixedStrike 
     */
    public LookbackOption(double sP, double tE, OptionType type, int tS, boolean discreteMonitoring, boolean fixedStrike) {
        super(sP, tE, type, tS, discreteMonitoring);
        this.fixedStrike = fixedStrike;
    }
    
    
    /**
     * Getter for fixedStrike
     * @return boolean
     */
    public boolean isFixedStrike() {
        return fixedStrike;
    }
    
    
    /**
     * Calculates payoff for Monte Carlo steps
     * @param path
     * @return double
     */
    @Override
    public double payoff(double[] path) {
        double pathMax = findMax(path);
        double pathMin = findMin(path);
        double finalPrice = path[path.length - 1];
        
        if(fixedStrike) {
            if(isCall()) {
                return Math.max(pathMax - getStrikePrice(), 0);
            } else {
                return Math.max(getStrikePrice() - pathMin, 0);
            }
        } else {
            if(isCall()) {
                return Math.max(finalPrice - pathMin, 0);
            } else {
                return Math.max(pathMax - finalPrice, 0);
            }
        }
    }

    
    /**
     * Helper method that finds max in a path
     * @param path
     * @return double
     */
    private double findMax(double[] path) {
        double max = path[0];
        
        for(int i = 1; i<path.length; i++) {
            if(path[i] > max)
                max = path[i];
        }
        
        return max;
    }

    
    /**
     * Helper method that finds min in a path
     * @param path
     * @return 
     */
    private double findMin(double[] path) {
        double min = path[0];
        
        for(int i = 1; i<path.length; i++) {
            if(path[i] < min)
                min = path[i];
        }
        
        return min;
    }
    
}

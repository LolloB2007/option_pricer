package everything.optionpricer.model;

import everything.optionpricer.util.LinkedList;

/**
 * abstract implementation of path-dependent options
 * @author lorenzobarbagelata
 */
public abstract class PathDependentOption extends Option {
    
    private final int timeSteps;
    private final Boolean discreteMonitoring;
    
    
    /**
     * Constructor for general purpose path dependent options with discrete/continuous parameter
     * @param sP
     * @param tE
     * @param type
     * @param timeSteps
     * @param isDiscreteMonitoring 
     */
    protected PathDependentOption(double sP, double tE, OptionType type, int timeSteps, boolean isDiscreteMonitoring) {
        
        super(sP, tE, type);
        
        if(timeSteps<=0)
            throw new IllegalArgumentException("Please enter valid time steps");
        
        this.timeSteps = timeSteps;
        this.discreteMonitoring = isDiscreteMonitoring;
    }
    
    
    /**
     * Constructor for general purpose path dependent options without discrete/continuous parameter
     * @param sP
     * @param tE
     * @param type
     * @param timeSteps 
     */
    protected PathDependentOption(double sP, double tE, OptionType type, int timeSteps) {
        
        super(sP, tE, type);
        
        if(timeSteps<=0)
            throw new IllegalArgumentException("Please enter valid time steps");
        
        this.timeSteps = timeSteps;
        discreteMonitoring = null;
    }

    
    /**
     * Getter for time steps
     * @return int
     */
    public int getTimeSteps() {
        return timeSteps;
    }

    
    /**
     * Getter for discreteMonitoring
     * @return boolean
     */
    public Boolean isDiscreteMonitoring() {
        if(discreteMonitoring==null)
            return null;
        
        return discreteMonitoring;
    }
    
    
    /**
     * Declares payoff method
     * Subclasses will be using this
     * @param path
     * @return double
     */
    public abstract double payoff(LinkedList<Double> path);
    
}

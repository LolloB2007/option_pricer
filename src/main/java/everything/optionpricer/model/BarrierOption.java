package everything.optionpricer.model;

/**
 * implementation of barrier options
 * @author lorenzobarbagelata
 */
public class BarrierOption extends PathDependentOption {
    
    private final double barrier;
    private final boolean upBarrier;
    private final boolean inBarrier;
    
    
    /**
     * Constructor for BarrierOption
     * @param strikePrice
     * @param timeToExpiry
     * @param type
     * @param timeSteps
     * @param discreteMonitoring
     * @param barrier
     * @param upBarrier
     * @param inBarrier 
     */
    public BarrierOption(double strikePrice, double timeToExpiry, OptionType type, int timeSteps, boolean discreteMonitoring, double barrier, boolean upBarrier, boolean inBarrier) {
        super(strikePrice, timeToExpiry, type, timeSteps, discreteMonitoring);
        this.barrier = barrier;
        this.upBarrier = upBarrier;
        this.inBarrier = inBarrier;
    }

    
    /**
     * Getter for barrier
     * @return double
     */
    public double getBarrier() {
        return barrier;
    }

    
    /**
     * Getter for upBarrier
     * @return boolean
     */
    public boolean isUpBarrier() {
        return upBarrier;
    }
    
    
    /**
     * Getter for inBarrier
     * @return double
     */
    public boolean isInBarrier() {
        return inBarrier;
    }
    

    /**
     * Calculates payoff of function
     * @param path
     * @return double
     */
    @Override
    public double payoff(double[] path) {
        boolean isHit = checkBarrierHit(path);
        
        boolean stillActive;
        
        if(inBarrier) {
            stillActive = isHit;
        } else {
            stillActive = !isHit;
        }
        
        if(!stillActive)
            return 0.0;
        
        double finalPrice = path[path.length - 1];
        
        if(isCall())
            return Math.max(finalPrice - getStrikePrice(), 0.0);
            
        return Math.max(getStrikePrice() - finalPrice, 0.0);
    }

    
    /**
     * Helper to payoff
     * @param path
     * @return boolean
     */
    private boolean checkBarrierHit(double[] path) {
        
        for(double point : path) {
            if(upBarrier) {
                if(point >= barrier)
                    return true;
            } else {
                if(point <= barrier) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
}

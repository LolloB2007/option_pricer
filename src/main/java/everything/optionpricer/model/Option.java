package everything.optionpricer.model;

import static everything.optionpricer.model.OptionType.CALL;


/**
 * implementation of Options.
 * Holds K, T, and OptionType
 * @author lorenzobarbagelata
 */
public abstract class Option {
    
    private final double strikePrice;
    private final double timeToExpiry; //in years
    private final OptionType type;
    
    
    /**
     * Constructor for Option, accessed through call and put methods
     * @param sP
     * @param tE
     * @param t 
     */
    protected Option(double sP, double tE, OptionType t) {
        
        if(sP <= 0)
            throw new IllegalArgumentException("Please enter valid strike price");
        
        if(tE <= 0)
            throw new IllegalArgumentException("Please enter valid time to expiry");
        
        
        strikePrice = sP;
        timeToExpiry = tE;
        type = t;
    }
    
    
    /**
     * Getter for strike price
     * @return double
     */
    public double getStrikePrice() {
        return this.strikePrice;
    }
    
   
    /**
     * Getter for time to expiry
     * @return 
     */
    public double getTimeToExpiry() {
        return this.timeToExpiry;
    }
    
    
    /**
     * Getter for OptionType
     * @return OptionType
     */
    public OptionType getOptionType() {
        return this.type;
    }
    
    
    /**
     * Returns whether Option is call
     * @return boolean
     */
    public boolean isCall() {
        return this.type == CALL ? true : false;
    }
    
    
    /**
     * Returns whether Option is put
     * @return boolean
     */
    public boolean isPut() {
        return !isCall();
    }
    
    
    /**
     * Returns OptionType sign
     * @return int
     */
    public int getSign()
    {
        return this.type.sign();
    }
}

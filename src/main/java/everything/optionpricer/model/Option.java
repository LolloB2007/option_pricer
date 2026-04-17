package everything.optionpricer.model;

import static everything.optionpricer.model.OptionType.CALL;


/**
 * implementation of Options.
 * Holds K, T, and OptionType
 * @author lorenzobarbagelata
 */
public class Option {
    
    private final double strikePrice;
    private final double timeToExpiry; //in years
    private final OptionType type;
    
    
    /**
     * Constructor for Option, accessed through call and put methods
     * @param sP
     * @param tE
     * @param t 
     */
    private Option(double sP, double tE, OptionType t) {
        
        if(sP <= 0)
            throw new IllegalArgumentException("Please enter valid strike price");
        
        if(tE <= 0)
            throw new IllegalArgumentException("Please enter valid time to expiry");
        
        
        strikePrice = sP;
        timeToExpiry = tE;
        type = t;
    }
    
    
    /**
     * Empty constructor needed for text-based user input
     */
    public Option()
    {
        strikePrice = 0;
        timeToExpiry = 0;
        type = null;
    }
    
    
    /**
     * Creates call option taking strike price and time to expiry in years
     * @param sP
     * @param tE
     * @return Option
     */
    public static Option call(double sP, double tE) {
        return new Option(sP, tE, OptionType.CALL);
    }
    
    
    /**
     * Creates put option taking strike price and time to expiry in years
     * @param sP
     * @param tE
     * @return Option
     */
    public static Option put(double sP, double tE) {
        return new Option(sP, tE, OptionType.PUT);
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

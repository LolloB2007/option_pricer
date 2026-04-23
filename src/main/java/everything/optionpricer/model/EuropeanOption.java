package everything.optionpricer.model;

import static everything.optionpricer.model.OptionType.*;

/**
 * implementation of European options
 * @author lorenzobarbagelata
 */
public class EuropeanOption extends Option {
    
    
    /**
     * Constructor. Needed for static generation methods
     * @param sP
     * @param tE
     * @param type 
     */
    private EuropeanOption(double sP, double tE, OptionType type) {
        super(sP, tE, type);
    }
    
    
    /**
     * Creates call option taking strike price and time to expiry in years
     * @param sP
     * @param tE
     * @return Option
     */
    public static EuropeanOption call(double sP, double tE) {
        return new EuropeanOption(sP, tE, CALL);
    }
    
    
    /**
     * Creates put option taking strike price and time to expiry in years
     * @param sP
     * @param tE
     * @return Option
     */
    public static EuropeanOption put(double sP, double tE) {
        return new EuropeanOption(sP, tE, PUT);
    }
}

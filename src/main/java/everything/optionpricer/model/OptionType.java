package everything.optionpricer.model;


/**
 * enum class --> put or call
 * @author lorenzobarbagelata
 */
public enum OptionType {
    
    CALL, PUT;
    
    public boolean isCall() {return this == CALL;}
    public boolean isPut() {return this == PUT;}
    
    
    /**
     * Assigns numerical value to OptionType.
     * If positive, call; if negative, put
     * @return int
     */
    public int sign() {
        if(this == CALL) {
            return 1;
        }
        
        return -1;
    }
}

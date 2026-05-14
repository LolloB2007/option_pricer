package everything.optionpricer.model;


/**
 * Option type: CALL or PUT.
 * @author lorenzobarbagelata
 */
public enum OptionType {

    CALL, PUT;

    public boolean isCall() { return this == CALL; }
    public boolean isPut()  { return this == PUT;  }

    /**
     * +1 for CALL, -1 for PUT.
     */
    public int sign() {
        return this == CALL ? 1 : -1;
    }
}

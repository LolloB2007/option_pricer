package everything.optionpricer.model;

import static everything.optionpricer.model.OptionType.CALL;
import static everything.optionpricer.model.OptionType.PUT;


/**
 * European option (no early exercise, terminal payoff only).
 * @author lorenzobarbagelata
 */
public class EuropeanOption extends Option {

    private EuropeanOption(double strikePrice, double timeToExpiry, OptionType type) {
        super(strikePrice, timeToExpiry, type);
    }

    public static EuropeanOption call(double strikePrice, double timeToExpiry) {
        return new EuropeanOption(strikePrice, timeToExpiry, CALL);
    }

    public static EuropeanOption put(double strikePrice, double timeToExpiry) {
        return new EuropeanOption(strikePrice, timeToExpiry, PUT);
    }

    public static EuropeanOption of(OptionType type, double strikePrice, double timeToExpiry) {
        return new EuropeanOption(strikePrice, timeToExpiry, type);
    }
}

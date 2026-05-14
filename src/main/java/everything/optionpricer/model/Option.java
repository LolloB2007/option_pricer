package everything.optionpricer.model;

import static everything.optionpricer.model.OptionType.CALL;


/**
 * Base class for options. Holds K, T, and OptionType.
 * @author lorenzobarbagelata
 */
public abstract class Option {

    private final double strikePrice;
    private final double timeToExpiry; //in years
    private final OptionType type;


    protected Option(double strikePrice, double timeToExpiry, OptionType type) {

        if(strikePrice <= 0)
            throw new IllegalArgumentException("Please enter valid strike price");

        if(timeToExpiry <= 0)
            throw new IllegalArgumentException("Please enter valid time to expiry");

        this.strikePrice = strikePrice;
        this.timeToExpiry = timeToExpiry;
        this.type = type;
    }


    public double getStrikePrice() {
        return strikePrice;
    }

    public double getTimeToExpiry() {
        return timeToExpiry;
    }

    public OptionType getOptionType() {
        return type;
    }

    public boolean isCall() {
        return type == CALL;
    }

    public boolean isPut() {
        return !isCall();
    }

    public int getSign() {
        return type.sign();
    }
}

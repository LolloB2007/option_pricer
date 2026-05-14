package everything.optionpricer.pricing;

import everything.optionpricer.model.Option;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.util.NormalDistribution;


/**
 * Closed-form Black–Scholes pricer for European options.
 * @author lorenzobarbagelata
 */
public final class BlackScholesEngine {

    private BlackScholesEngine() {}


    public static PricingResult price(Option option, double spot, double riskFreeRate, double volatility) {

        if(option == null)
            throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0.0)
            throw new IllegalArgumentException("Please enter valid spot price");
        if(volatility <= 0.0)
            throw new IllegalArgumentException("Please enter valid volatility");

        double K = option.getStrikePrice();
        double t = option.getTimeToExpiry();
        double sqrtT = Math.sqrt(t);

        double d1 = (Math.log(spot / K) + (riskFreeRate + 0.5 * volatility * volatility) * t)
                  / (volatility * sqrtT);
        double d2 = d1 - volatility * sqrtT;

        double discountedK = K * Math.exp(-riskFreeRate * t);

        // Call value; put recovered by put-call parity: P = C - S + K e^{-rT}.
        double call = spot * NormalDistribution.cdf(d1) - discountedK * NormalDistribution.cdf(d2);

        double price = option.getOptionType() == OptionType.CALL
                ? call
                : call - spot + discountedK;

        return new PricingResult(price);
    }
}

package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.Option;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.util.NormalDistribution;


/**
 * Closed-form Black-Scholes pricer for European options.
 *
 * Supports continuous dividend yield {@code q} and discrete cash dividends
 * via {@link DividendSchedule}. Discrete dividends are handled with the
 * escrowed model: their present value is subtracted from spot before
 * pricing.
 *
 * @author lorenzobarbagelata
 */
public final class BlackScholesEngine {

    private BlackScholesEngine() {}


    // ----- price ----- //

    public static PricingResult price(Option option, double spot, double riskFreeRate, double volatility) {
        return price(option, spot, riskFreeRate, volatility, DividendSchedule.NONE);
    }


    public static PricingResult price(Option option, double spot, double riskFreeRate,
                                      double volatility, DividendSchedule dividends) {
        validateInputs(option, spot, volatility);
        if(dividends == null) dividends = DividendSchedule.NONE;

        double T = option.getTimeToExpiry();
        double K = option.getStrikePrice();
        double q = dividends.continuousYield();

        // Escrow discrete dividends.
        double Seff = spot - dividends.presentValueBefore(T, riskFreeRate);
        if(Seff <= 0)
            throw new IllegalArgumentException("Spot minus PV of discrete dividends is non-positive");

        double sqrtT = Math.sqrt(T);
        double d1 = (Math.log(Seff / K) + (riskFreeRate - q + 0.5 * volatility * volatility) * T)
                  / (volatility * sqrtT);
        double d2 = d1 - volatility * sqrtT;

        double discS = Seff * Math.exp(-q * T);
        double discK = K    * Math.exp(-riskFreeRate * T);

        double call = discS * NormalDistribution.cdf(d1) - discK * NormalDistribution.cdf(d2);
        double price = option.getOptionType() == OptionType.CALL
                ? call
                : call - discS + discK;     // put-call parity (dividend-adjusted)

        return new PricingResult(price);
    }


    // ----- greeks ----- //

    public static Greeks greeks(Option option, double spot, double riskFreeRate, double volatility) {
        return greeks(option, spot, riskFreeRate, volatility, DividendSchedule.NONE);
    }


    public static Greeks greeks(Option option, double spot, double riskFreeRate,
                                double volatility, DividendSchedule dividends) {
        validateInputs(option, spot, volatility);
        if(dividends == null) dividends = DividendSchedule.NONE;

        double T = option.getTimeToExpiry();
        double K = option.getStrikePrice();
        double q = dividends.continuousYield();

        double Seff = spot - dividends.presentValueBefore(T, riskFreeRate);
        if(Seff <= 0)
            throw new IllegalArgumentException("Spot minus PV of discrete dividends is non-positive");

        double sqrtT = Math.sqrt(T);
        double d1 = (Math.log(Seff / K) + (riskFreeRate - q + 0.5 * volatility * volatility) * T)
                  / (volatility * sqrtT);
        double d2 = d1 - volatility * sqrtT;

        double pdfD1 = NormalDistribution.pdf(d1);
        double cdfD1 = NormalDistribution.cdf(d1);
        double cdfD2 = NormalDistribution.cdf(d2);
        double discS = Seff * Math.exp(-q * T);
        double discK = K    * Math.exp(-riskFreeRate * T);

        boolean isCall = option.getOptionType() == OptionType.CALL;

        double deltaRaw, thetaRaw, rhoRaw;
        if(isCall) {
            deltaRaw = Math.exp(-q * T) * cdfD1;
            thetaRaw = -discS * pdfD1 * volatility / (2.0 * sqrtT)
                       + q * discS * cdfD1
                       - riskFreeRate * discK * cdfD2;
            rhoRaw   = T * discK * cdfD2;
        } else {
            deltaRaw = -Math.exp(-q * T) * NormalDistribution.cdf(-d1);
            thetaRaw = -discS * pdfD1 * volatility / (2.0 * sqrtT)
                       - q * discS * NormalDistribution.cdf(-d1)
                       + riskFreeRate * discK * NormalDistribution.cdf(-d2);
            rhoRaw   = -T * discK * NormalDistribution.cdf(-d2);
        }
        double gammaRaw = Math.exp(-q * T) * pdfD1 / (Seff * volatility * sqrtT);
        double vegaRaw  = discS * pdfD1 * sqrtT;

        return new Greeks(
                deltaRaw,
                gammaRaw,
                vegaRaw  * 0.01,
                thetaRaw / 365.0,
                rhoRaw   * 0.01);
    }


    private static void validateInputs(Option option, double spot, double volatility) {
        if(option == null)
            throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0.0)
            throw new IllegalArgumentException("Please enter valid spot price");
        if(volatility <= 0.0)
            throw new IllegalArgumentException("Please enter valid volatility");
    }
}

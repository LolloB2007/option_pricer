package everything.optionpricer.util;

import java.util.concurrent.ThreadLocalRandom;


/**
 * Standard Normal distribution helpers (pdf, cdf, inverse cdf, sampling).
 * @author lorenzobarbagelata
 */
public final class NormalDistribution {

    private static final double ONE_OVER_SQRT_2PI = 1.0 / Math.sqrt(2.0 * Math.PI);

    private NormalDistribution() {}


    /**
     * Standard normal pdf at x.
     */
    public static double pdf(double x) {
        return ONE_OVER_SQRT_2PI * Math.exp(-0.5 * x * x);
    }


    /**
     * Standard normal cdf at x (Abramowitz–Stegun approximation).
     */
    public static double cdf(double x) {

        if(x >  8.0) return 1.0;
        if(x < -8.0) return 0.0;

        double absX = Math.abs(x);
        double t = 1.0 / (1.0 + 0.2316419 * absX);

        double poly = t * (0.319381530 +
                      t * (-0.356563782 +
                      t * (1.781477937 +
                      t * (-1.821255978 +
                      t *  1.330274429))));

        double approx = 1.0 - pdf(absX) * poly;
        return x >= 0.0 ? approx : 1.0 - approx;
    }


    public static double cdfComplement(double x) {
        return 1.0 - cdf(x);
    }


    /**
     * Approximate inverse standard-normal cdf (Schmeiser / Shore blend).
     */
    public static double cdfInverse(double p) {

        if(p <= 0.0 || p >= 1.0)
            throw new IllegalArgumentException("p must lie in (0, 1)");

        if(p < 0.5)
            return -cdfInverse(1.0 - p);

        if(p < 0.958)
            return (Math.pow(p, 0.135) - Math.pow(1.0 - p, 0.135)) / 0.1975; // Schmeiser

        return -5.531 * (Math.pow((1.0 - p) / p, 0.1193) - 1.0); // Shore
    }


    /**
     * Sample from the standard normal using the current thread's RNG.
     * Uses ThreadLocalRandom so parallel Monte Carlo simulations do not
     * contend on a shared synchronized generator.
     */
    public static double sampleStandardNormal() {
        return ThreadLocalRandom.current().nextGaussian();
    }
}

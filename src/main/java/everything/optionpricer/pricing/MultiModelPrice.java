package everything.optionpricer.pricing;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Aggregates prices across multiple models and returns a trimmed mean —
 * the "mean of the two middle outputs" as soon as you have four contributors,
 * the median for three.
 *
 * Concretely: drop the smallest and largest values, average the rest.
 *
 * @author lorenzobarbagelata
 */
public final class MultiModelPrice {

    private MultiModelPrice() {}


    /** Trimmed mean of {@code values} — drops min and max, averages the rest. */
    public static double trimmedMean(double[] values) {
        if(values == null || values.length == 0)
            throw new IllegalArgumentException("Need at least one value");
        if(values.length <= 2) {
            // No trimming possible — fall back to simple average.
            double s = 0;
            for(double v : values) s += v;
            return s / values.length;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        // Drop index 0 (min) and index n-1 (max).
        double sum = 0;
        for(int i = 1; i < sorted.length - 1; i++) sum += sorted[i];
        return sum / (sorted.length - 2);
    }


    /**
     * Result of an Auto-mode pricing: the trimmed-mean price plus the
     * per-model contributions, preserved in insertion order so the GUI /
     * API can show how each engine voted.
     */
    public record Aggregated(double price, Map<PricingModel, Double> contributions) {}


    /** Builder that collects model→price pairs and folds them into an Aggregated. */
    public static final class Builder {
        private final Map<PricingModel, Double> contributions = new LinkedHashMap<>();

        public Builder add(PricingModel model, double price) {
            contributions.put(model, price);
            return this;
        }

        public Aggregated build() {
            double[] values = contributions.values().stream().mapToDouble(Double::doubleValue).toArray();
            return new Aggregated(trimmedMean(values), Map.copyOf(contributions));
        }
    }


    public static Builder builder() {
        return new Builder();
    }
}

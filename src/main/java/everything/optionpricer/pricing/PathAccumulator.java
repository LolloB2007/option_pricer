package everything.optionpricer.pricing;


/**
 * Streaming payoff accumulator. The Monte Carlo engine feeds observations one
 * at a time and queries the payoff at the end — no full path array required.
 *
 * Instances are stateful and not thread-safe; reuse them via {@link #reset()}
 * across simulations on the same thread.
 *
 * Both price and log-price are passed at each step so each option type can
 * use whichever is natural. Implementations that work in log-space should
 * return {@code false} from {@link #needsPrice()} — the engine then skips a
 * {@code Math.exp} per step.
 *
 * @author lorenzobarbagelata
 */
public interface PathAccumulator {

    /**
     * Feed the next observed underlying price along the simulated path.
     * The engine guarantees {@code logPrice == Math.log(price)} when
     * {@link #needsPrice()} returns {@code true}; otherwise {@code price}
     * may be passed as 0 and only {@code logPrice} is meaningful.
     */
    void accumulate(double price, double logPrice);

    /** Final payoff once the path has been fully consumed. */
    double payoff();

    /** Clear all state so this accumulator can be reused for a new simulation. */
    void reset();

    /**
     * True if no further observation can change the payoff (e.g. an OUT
     * barrier has been knocked out). Lets the engine skip the rest of the path.
     */
    default boolean isDone() { return false; }

    /**
     * Whether {@code accumulate} needs the linear-space price. If {@code false},
     * the engine omits the per-step {@code Math.exp} call and passes 0.
     */
    default boolean needsPrice() { return true; }
}

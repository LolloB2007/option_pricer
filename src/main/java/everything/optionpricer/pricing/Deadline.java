package everything.optionpricer.pricing;


/**
 * Thread-local cooperative deadline. The API layer sets it when a
 * request specifies {@code maxLatencyMs}; long-running engines
 * (Monte Carlo, LSM, Heston-MC, Nelder-Mead calibration) check it at
 * batch / iteration boundaries and throw {@link DeadlineExceededException}
 * if it has elapsed. The wrap() middleware maps the exception to a
 * structured error with code {@code DEADLINE_EXCEEDED}.
 *
 * Why thread-local: keeps engine signatures untouched. Each request
 * runs on one thread; the deadline scopes naturally to the handler.
 *
 * @author lorenzobarbagelata
 */
public final class Deadline {

    private static final ThreadLocal<Long> deadlineNs = new ThreadLocal<>();


    private Deadline() {}


    /** Set a deadline {@code millis} from now on the current thread. */
    public static void setFromNow(long millis) {
        deadlineNs.set(System.nanoTime() + millis * 1_000_000L);
    }


    /** Clear the deadline. Always call this in a {@code finally} block. */
    public static void clear() {
        deadlineNs.remove();
    }


    /** True if a deadline is set and has elapsed. */
    public static boolean exceeded() {
        Long d = deadlineNs.get();
        return d != null && System.nanoTime() > d;
    }


    /**
     * The currently configured deadline in nanos, or 0 if none. Engines
     * capture this at the start of their entry method so they can
     * propagate the value into parallel ForkJoinPool workers (which
     * don't inherit the request thread's ThreadLocal).
     */
    public static long peekNs() {
        Long d = deadlineNs.get();
        return d == null ? 0L : d;
    }


    /**
     * Throw if {@code deadlineNs > 0} and {@code System.nanoTime()} is
     * past it. Engines call this at simulation-batch boundaries on
     * parallel workers using the value captured by {@link #peekNs()}.
     */
    public static void checkpointAt(long deadlineNs) {
        if(deadlineNs > 0 && System.nanoTime() > deadlineNs) {
            throw new DeadlineExceededException();
        }
    }


    /**
     * Throw {@link DeadlineExceededException} if the request thread's
     * deadline has elapsed. Only safe to call on the request thread
     * itself — parallel workers must use {@link #checkpointAt(long)}.
     */
    public static void checkpoint() {
        if(exceeded()) throw new DeadlineExceededException();
    }


    public static final class DeadlineExceededException extends RuntimeException {
        public DeadlineExceededException() { super("deadline exceeded"); }
    }
}

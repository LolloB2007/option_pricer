package everything.optionpricer.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;


/**
 * Hand-rolled Prometheus metrics — no client library. Just enough to
 * power dashboards and alerting via the {@code /metrics} endpoint:
 *
 *   pricer_requests_total{endpoint, status}     counter
 *   pricer_request_seconds{endpoint}            histogram (buckets + sum + count)
 *   pricer_in_flight_requests                   gauge
 *   pricer_started_at_unixtime_seconds          gauge (set once at startup)
 *
 * @author lorenzobarbagelata
 */
public final class Metrics {

    // Per-endpoint × per-status request counters.
    private static final Map<String, LongAdder> requestCounts = new ConcurrentHashMap<>();
    // Per-endpoint latency: a fixed bucket histogram.
    private static final Map<String, Histogram>  latencies     = new ConcurrentHashMap<>();
    // Currently-executing requests.
    private static final AtomicLong inFlight = new AtomicLong();
    // Process start time.
    private static final long startedAtMillis = System.currentTimeMillis();


    private Metrics() {}


    public static void onRequestStarted() {
        inFlight.incrementAndGet();
    }

    public static void onRequestFinished(String endpoint, int status, long durationNanos) {
        inFlight.decrementAndGet();
        String key = endpoint + "|" + status;
        requestCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        latencies.computeIfAbsent(endpoint, k -> new Histogram()).observe(durationNanos / 1_000_000_000.0);
    }


    /** Render in Prometheus text exposition format. */
    public static String exposition() {
        StringBuilder b = new StringBuilder(4096);

        b.append("# HELP pricer_started_at_unixtime_seconds Process start time (unix seconds).\n");
        b.append("# TYPE pricer_started_at_unixtime_seconds gauge\n");
        b.append("pricer_started_at_unixtime_seconds ").append(startedAtMillis / 1000.0).append('\n');

        b.append("# HELP pricer_in_flight_requests Currently-executing HTTP requests.\n");
        b.append("# TYPE pricer_in_flight_requests gauge\n");
        b.append("pricer_in_flight_requests ").append(inFlight.get()).append('\n');

        b.append("# HELP pricer_requests_total Total HTTP requests by endpoint and status.\n");
        b.append("# TYPE pricer_requests_total counter\n");
        for(var e : requestCounts.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            String endpoint = parts[0];
            String status   = parts[1];
            b.append("pricer_requests_total{endpoint=\"").append(escape(endpoint))
             .append("\",status=\"").append(status).append("\"} ").append(e.getValue().sum()).append('\n');
        }

        b.append("# HELP pricer_request_seconds Request latency in seconds.\n");
        b.append("# TYPE pricer_request_seconds histogram\n");
        for(var e : latencies.entrySet()) {
            String endpoint = e.getKey();
            e.getValue().write(b, endpoint);
        }

        return b.toString();
    }


    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    /**
     * Tiny fixed-bucket histogram. Buckets cover the typical pricing range
     * (sub-ms to multi-second). Concurrent updates via {@link LongAdder}.
     */
    private static final class Histogram {
        private static final double[] BUCKETS = {
                0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, Double.POSITIVE_INFINITY
        };

        private final LongAdder[] counts = new LongAdder[BUCKETS.length];
        private final LongAdder   sumMicros = new LongAdder();
        private final LongAdder   count     = new LongAdder();

        Histogram() {
            for(int i = 0; i < counts.length; i++) counts[i] = new LongAdder();
        }

        void observe(double seconds) {
            count.increment();
            sumMicros.add((long) (seconds * 1_000_000));
            for(int i = 0; i < BUCKETS.length; i++) {
                if(seconds <= BUCKETS[i]) {
                    counts[i].increment();
                    // Cumulative buckets: also increment all higher ones.
                    for(int j = i + 1; j < BUCKETS.length; j++) counts[j].increment();
                    return;
                }
            }
        }

        void write(StringBuilder b, String endpoint) {
            for(int i = 0; i < BUCKETS.length; i++) {
                String upper = (BUCKETS[i] == Double.POSITIVE_INFINITY) ? "+Inf" : Double.toString(BUCKETS[i]);
                b.append("pricer_request_seconds_bucket{endpoint=\"").append(escape(endpoint))
                 .append("\",le=\"").append(upper).append("\"} ").append(counts[i].sum()).append('\n');
            }
            b.append("pricer_request_seconds_count{endpoint=\"").append(escape(endpoint)).append("\"} ")
             .append(count.sum()).append('\n');
            b.append("pricer_request_seconds_sum{endpoint=\"").append(escape(endpoint)).append("\"} ")
             .append(sumMicros.sum() / 1_000_000.0).append('\n');
        }
    }


    /** Used for endpoint labels — collapses path-with-IDs into a stable label if needed. */
    public static String labelFor(String requestPath) {
        return Objects.requireNonNullElse(requestPath, "unknown");
    }
}

package everything.optionpricer.api;


/**
 * Minimal structured (JSON-line) request log. One line per completed
 * request — easy to grep, easy to parse, easy to ingest into ELK/Loki/
 * CloudWatch.
 *
 * Shape:
 *   {"ts":"2026-05-16T10:23:45.123Z","reqId":"abc12345","method":"POST","path":"/v1/price/european","status":200,"latencyMs":2}
 *
 * @author lorenzobarbagelata
 */
public final class RequestLogger {

    private RequestLogger() {}


    public static void log(String requestId, String method, String path, int status, long latencyNanos) {
        long ms = latencyNanos / 1_000_000;
        String line = "{\"ts\":\"" + java.time.Instant.now() + "\""
                + ",\"reqId\":\"" + escape(requestId) + "\""
                + ",\"method\":\""  + escape(method) + "\""
                + ",\"path\":\""    + escape(path) + "\""
                + ",\"status\":"    + status
                + ",\"latencyMs\":" + ms + "}";
        System.out.println(line);
    }


    public static String newId() {
        return Long.toHexString(System.nanoTime() ^ Thread.currentThread().getId());
    }


    private static String escape(String s) {
        if(s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 4);
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch(c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> b.append(c);
            }
        }
        return b.toString();
    }
}

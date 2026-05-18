package everything.optionpricer.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;


/**
 * Hand-rolled binary wire format for the {@code /v1/grid/*} hot path.
 *
 * Motivation: at chain-pricing scale (200 strikes × 10ms-per-pricing)
 * the JSON encode/decode dominates the per-request overhead. JSON-on-
 * keep-alive HTTP is fine for everything else, but the grid endpoints
 * exchange large uniform payloads where binary really pays.
 *
 * Schemaless on the wire — readers must know the method discriminator
 * up front (one endpoint == one method == one shape). Little-endian
 * doubles match the IEEE-754 layout the JVM uses natively.
 *
 * Opt-in: clients send {@code Content-Type: application/x-pricer-v1}.
 * The same MIME is returned on the response. JSON stays the default
 * for backward compat and for endpoints with variable-shape payloads
 * (batch, fit, calibrate, etc.) where a schemaless binary buys little.
 *
 * Wire format (all multi-byte values little-endian, no padding):
 * <pre>
 *   Request:
 *     uint32   magic       = 'OPB1' (LE 0x31_42_50_4F)
 *     uint8    method      'E','A','M','B','L'
 *     float64  spot
 *     float64  rate
 *     float64  volatility
 *     float64  timeToExpiry
 *     float64  continuousYield        (0 if none)
 *     int32    timeSteps              (0 if not applicable, e.g. European)
 *     int32    simulations            (0 = use engine default)
 *     int64    seed                   (0 = no seed / non-reproducible)
 *     uint8    flags                  bit0 = discreteMonitoring, bit1 = arithmeticAverage,
 *                                     bit2 = upBarrier, bit3 = inBarrier, bit4 = fixedStrike,
 *                                     bit5 = useAntithetic
 *     // Method-specific block:
 *     // Barrier:      float64 barrier
 *     // American:     int32   exerciseDates
 *     int32    contractCount
 *     contractCount × { uint8 type ('C'|'P'), float64 strike }
 *
 *   Response:
 *     uint32   magic       = 'OPB1'
 *     int32    resultCount
 *     resultCount × { uint8 type, float64 strike, float64 price }
 * </pre>
 *
 * Discrete dividends are intentionally omitted from the binary path —
 * they're rare in the chain-pricing flow this format is optimised for.
 * Callers needing them should use the JSON path.
 *
 * @author lorenzobarbagelata
 */
final class Binary {

    public static final String MIME = "application/x-pricer-v1";

    private static final int MAGIC = 0x31425030 | ('1' << 24);
    // 'O' 'P' 'B' '1' little-endian: 0x31_42_50_4F. The constant above
    // is constructed at runtime to keep the source readable.
    private static final int MAGIC_BYTES = bytesToInt((byte)'O', (byte)'P', (byte)'B', (byte)'1');

    private Binary() {}


    static boolean wantsBinary(HttpExchange ex) {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if(ct == null) return false;
        return ct.toLowerCase().startsWith(MIME);
    }


    // ============================================================
    //  Decoders — populate the existing record DTOs so the grid
    //  computation code is unchanged.
    // ============================================================

    static Grid.EuropeanGridRequest readEuropeanGrid(HttpExchange ex) throws IOException {
        ByteBuffer in = readAll(ex);
        readMagic(in);
        char method = (char) (in.get() & 0xff);
        requireMethod(method, 'E');
        Header h = readHeader(in);
        // No method-specific block.
        Grid.Contract[] contracts = readContracts(in);
        ApiServer.DividendsDto divs = wrapYield(h.continuousYield);
        return new Grid.EuropeanGridRequest(h.spot, h.rate, h.volatility, h.timeToExpiry,
                                            divs, contracts);
    }

    static Grid.AmericanGridRequest readAmericanGrid(HttpExchange ex) throws IOException {
        ByteBuffer in = readAll(ex);
        readMagic(in);
        char method = (char) (in.get() & 0xff);
        requireMethod(method, 'M');
        Header h = readHeader(in);
        int exerciseDates = in.getInt();
        Grid.Contract[] contracts = readContracts(in);
        ApiServer.DividendsDto divs = wrapYield(h.continuousYield);
        Integer sims = h.simulations == 0 ? null : h.simulations;
        return new Grid.AmericanGridRequest(h.spot, h.rate, h.volatility, h.timeToExpiry,
                                            exerciseDates, sims, divs, contracts);
    }

    static Grid.PathGridRequest readAsianGrid(HttpExchange ex) throws IOException {
        ByteBuffer in = readAll(ex);
        readMagic(in);
        char method = (char) (in.get() & 0xff);
        requireMethod(method, 'A');
        Header h = readHeader(in);
        Grid.Contract[] contracts = readContracts(in);
        return makePathGrid(h, contracts, /*arith*/ (h.flags & 0b00000010) != 0,
                            /*barrier*/ null, /*up*/ null, /*in*/ null, /*fixed*/ null);
    }

    static Grid.PathGridRequest readBarrierGrid(HttpExchange ex) throws IOException {
        ByteBuffer in = readAll(ex);
        readMagic(in);
        char method = (char) (in.get() & 0xff);
        requireMethod(method, 'B');
        Header h = readHeader(in);
        double barrier = in.getDouble();
        Grid.Contract[] contracts = readContracts(in);
        boolean upB = (h.flags & 0b00000100) != 0;
        boolean inB = (h.flags & 0b00001000) != 0;
        return makePathGrid(h, contracts, /*arith*/ null, barrier, upB, inB, null);
    }

    static Grid.PathGridRequest readLookbackGrid(HttpExchange ex) throws IOException {
        ByteBuffer in = readAll(ex);
        readMagic(in);
        char method = (char) (in.get() & 0xff);
        requireMethod(method, 'L');
        Header h = readHeader(in);
        Grid.Contract[] contracts = readContracts(in);
        boolean fixed = (h.flags & 0b00010000) != 0;
        return makePathGrid(h, contracts, null, null, null, null, fixed);
    }


    private record Header(double spot, double rate, double volatility, double timeToExpiry,
                          double continuousYield, int timeSteps, int simulations,
                          long seed, int flags) {}


    private static Header readHeader(ByteBuffer in) {
        double spot = in.getDouble();
        double rate = in.getDouble();
        double vol  = in.getDouble();
        double t    = in.getDouble();
        double q    = in.getDouble();
        int  ts     = in.getInt();
        int  sims   = in.getInt();
        long seed   = in.getLong();
        int  flags  = in.get() & 0xff;
        return new Header(spot, rate, vol, t, q, ts, sims, seed, flags);
    }


    private static Grid.Contract[] readContracts(ByteBuffer in) {
        int n = in.getInt();
        if(n < 0 || n > 100_000)
            throw ApiException.badRequest("binary: implausible contract count " + n);
        Grid.Contract[] out = new Grid.Contract[n];
        for(int i = 0; i < n; i++) {
            char t = (char)(in.get() & 0xff);
            if(t != 'C' && t != 'P')
                throw ApiException.badField("contracts[" + i + "].type",
                        "binary: must be 'C' or 'P'");
            double k = in.getDouble();
            out[i] = new Grid.Contract(t == 'C' ? "CALL" : "PUT", k);
        }
        return out;
    }


    private static Grid.PathGridRequest makePathGrid(Header h, Grid.Contract[] contracts,
                                                     Boolean arith, Double barrier,
                                                     Boolean up, Boolean inB, Boolean fixed) {
        Integer sims = h.simulations == 0 ? null : h.simulations;
        Long seed    = h.seed == 0L ? null : h.seed;
        boolean useAnti = (h.flags & 0b00100000) != 0;
        boolean discrete = (h.flags & 0b00000001) != 0;
        return new Grid.PathGridRequest(
                h.spot, h.rate, h.volatility, h.timeToExpiry,
                h.timeSteps, discrete,
                arith, barrier, up, inB, fixed,
                sims, seed, useAnti ? Boolean.TRUE : null,
                wrapYield(h.continuousYield), contracts);
    }


    private static ApiServer.DividendsDto wrapYield(double q) {
        return q == 0.0 ? null : new ApiServer.DividendsDto(q, null);
    }


    // ============================================================
    //  Encoder — write the grid response
    // ============================================================

    static void writeGridResponse(HttpExchange ex, Grid.GridResult[] results) throws IOException {
        // 4 (magic) + 4 (count) + N × (1 + 8 + 8) bytes.
        int n = results.length;
        ByteBuffer buf = ByteBuffer.allocate(8 + n * 17).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC_BYTES);
        buf.putInt(n);
        for(Grid.GridResult r : results) {
            buf.put((byte) (r.type().equalsIgnoreCase("CALL") ? 'C' : 'P'));
            buf.putDouble(r.strike());
            buf.putDouble(r.price());
        }
        byte[] body = buf.array();
        ex.getResponseHeaders().set("Content-Type", MIME);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }


    // ============================================================
    //  Helpers
    // ============================================================

    private static ByteBuffer readAll(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            byte[] bytes = is.readAllBytes();
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private static void readMagic(ByteBuffer in) {
        if(in.remaining() < 5)
            throw ApiException.malformedJson("binary: payload too short");
        int magic = in.getInt();
        if(magic != MAGIC_BYTES)
            throw ApiException.malformedJson("binary: wrong magic bytes (expected OPB1)");
    }

    private static void requireMethod(char actual, char expected) {
        if(actual != expected) {
            throw ApiException.badField("method",
                    "binary: wrong method discriminator (got '" + actual + "', want '" + expected + "')");
        }
    }

    private static int bytesToInt(byte b0, byte b1, byte b2, byte b3) {
        return ((b0 & 0xff)) | ((b1 & 0xff) << 8) | ((b2 & 0xff) << 16) | ((b3 & 0xff) << 24);
    }
}

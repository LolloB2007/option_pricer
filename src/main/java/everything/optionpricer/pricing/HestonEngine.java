package everything.optionpricer.pricing;

import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.HestonParams;
import everything.optionpricer.model.PricingResult;

import java.util.stream.IntStream;


/**
 * Heston (1993) stochastic-volatility pricer for European options.
 *
 *   C = S e^{-qT} P₁ - K e^{-rT} P₂
 *
 * with characteristic functions evaluated in the "Little Heston Trap"
 * form (Albrecher et al. 2007) to avoid the branch-cut discontinuities of
 * the original Heston paper.
 *
 * Performance choices:
 *  - the two integrands for P₁ and P₂ are evaluated together at each node
 *    (single midpoint sweep, shared {@code e^{-i u ln K}} factor and
 *    per-call constants pre-baked into a context);
 *  - the {@link N_NODES}-node sweep is parallelised across cores.
 *
 * @author lorenzobarbagelata
 */
public final class HestonEngine {

    private static final double U_MAX = 100.0;
    private static final int    N_NODES = 256;

    private HestonEngine() {}


    public static PricingResult price(EuropeanOption option, double spot, double r,
                                      HestonParams params) {
        return price(option, spot, r, params, DividendSchedule.NONE);
    }


    public static PricingResult price(EuropeanOption option, double spot, double r,
                                      HestonParams params, DividendSchedule dividends) {

        if(option == null) throw new IllegalArgumentException("Option cannot be null");
        if(spot <= 0)      throw new IllegalArgumentException("Spot must be positive");
        if(params == null) throw new IllegalArgumentException("Heston params cannot be null");
        if(dividends == null) dividends = DividendSchedule.NONE;

        double K = option.getStrikePrice();
        double T = option.getTimeToExpiry();
        double q = dividends.continuousYield();
        double Seff = spot - dividends.presentValueBefore(T, r);
        if(Seff <= 0)
            throw new IllegalArgumentException("Spot minus PV of discrete dividends is non-positive");

        Ctx ctx = new Ctx(Seff, K, T, r, q, params);

        double du = U_MAX / N_NODES;

        // Parallel midpoint integration, evaluating P₁ and P₂ integrands jointly.
        double[] sums = IntStream.range(0, N_NODES).parallel()
                .mapToObj(i -> {
                    double u = (i + 0.5) * du;
                    return integrandPair(u, ctx);
                })
                .reduce(new double[]{0.0, 0.0}, (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});

        double P1 = 0.5 + (sums[0] * du) / Math.PI;
        double P2 = 0.5 + (sums[1] * du) / Math.PI;

        double discS = Seff * Math.exp(-q * T);
        double discK = K    * Math.exp(-r * T);

        double call = discS * P1 - discK * P2;
        double price = option.isCall() ? call : call - discS + discK;

        return new PricingResult(price);
    }


    /**
     * Per-call constants shared across every integration node.
     */
    private static final class Ctx {
        final double K, T, r, q;
        final double v0, kappa, theta, xi, rho;
        final double xi2;
        final double kappaTheta;
        final double b1, b2;           // b_j for j = 1, 2
        final double rhoXi;            // ρ ξ
        final double lnS;              // ln S
        final double lnK;              // ln K
        final double drift;            // (r - q)

        Ctx(double S, double K, double T, double r, double q, HestonParams h) {
            this.K = K; this.T = T; this.r = r; this.q = q;
            this.v0    = h.v0();
            this.kappa = h.kappa();
            this.theta = h.theta();
            this.xi    = h.xi();
            this.rho   = h.rho();
            this.xi2        = xi * xi;
            this.kappaTheta = kappa * theta;
            this.rhoXi      = rho * xi;
            this.b1         = kappa - rhoXi;
            this.b2         = kappa;
            this.lnS        = Math.log(S);
            this.lnK        = Math.log(K);
            this.drift      = r - q;
        }
    }


    /**
     * Compute Re[ e^{-i u ln K} · φ_j(u) / (i u) ] for both j = 1 and j = 2.
     * Sharing the e^{-i u ln K} factor and per-call constants between the two
     * integrals halves the bookkeeping per node.
     */
    private static double[] integrandPair(double u, Ctx ctx) {
        // e^{-i u ln K} = cos(-u·lnK) + i·sin(-u·lnK)
        double demodCos = Math.cos(-u * ctx.lnK);
        double demodSin = Math.sin(-u * ctx.lnK);

        double r1 = singleIntegrand(1, u, ctx, demodCos, demodSin);
        double r2 = singleIntegrand(2, u, ctx, demodCos, demodSin);
        return new double[] { r1, r2 };
    }


    private static double singleIntegrand(int j, double u, Ctx ctx,
                                          double demodCos, double demodSin) {
        double[] phi = characteristicFunction(j, u, ctx);
        double phiRe = phi[0], phiIm = phi[1];
        // numerator = e^{-i u ln K} · φ
        double numRe = phiRe * demodCos - phiIm * demodSin;
        double numIm = phiRe * demodSin + phiIm * demodCos;
        // divide by (i u):  (a + i b) / (i u) = (b - i a) / u  → real part = b/u
        return numIm / u;
    }


    /**
     * φ_j(u) — Heston characteristic function in the Little-Trap form.
     * Returns [re, im].
     */
    private static double[] characteristicFunction(int j, double u, Ctx ctx) {
        double bj = (j == 1) ? ctx.b1 : ctx.b2;
        double uj = (j == 1) ?  0.5   : -0.5;

        // (ρξ i u - b)
        double rxiu_Re = -bj;
        double rxiu_Im = ctx.rhoXi * u;
        // Square
        double sq_Re = rxiu_Re * rxiu_Re - rxiu_Im * rxiu_Im;
        double sq_Im = 2.0 * rxiu_Re * rxiu_Im;
        // Subtract ξ²(2 u_j i u - u²)
        double sub_Re = -ctx.xi2 * u * u;
        double sub_Im =  2.0 * ctx.xi2 * uj * u;
        double d2_Re = sq_Re - sub_Re;
        double d2_Im = sq_Im - sub_Im;
        // d = sqrt of complex (principal branch)
        double d_Re, d_Im;
        {
            double mag = Math.hypot(d2_Re, d2_Im);
            double arg = Math.atan2(d2_Im, d2_Re) * 0.5;
            double rd = Math.sqrt(mag);
            d_Re = rd * Math.cos(arg);
            d_Im = rd * Math.sin(arg);
        }

        // Little-Trap: g = (b - ρξ i u - d) / (b - ρξ i u + d)
        double bMinusRxiu_Re = -rxiu_Re;
        double bMinusRxiu_Im = -rxiu_Im;
        double gNum_Re = bMinusRxiu_Re - d_Re;
        double gNum_Im = bMinusRxiu_Im - d_Im;
        double gDen_Re = bMinusRxiu_Re + d_Re;
        double gDen_Im = bMinusRxiu_Im + d_Im;
        double denMag2 = gDen_Re * gDen_Re + gDen_Im * gDen_Im;
        double g_Re = (gNum_Re * gDen_Re + gNum_Im * gDen_Im) / denMag2;
        double g_Im = (gNum_Im * gDen_Re - gNum_Re * gDen_Im) / denMag2;

        // e^{-dT}
        double mdT_Re = -d_Re * ctx.T;
        double mdT_Im = -d_Im * ctx.T;
        double e_mdT_factor = Math.exp(mdT_Re);
        double e_mdT_Re = e_mdT_factor * Math.cos(mdT_Im);
        double e_mdT_Im = e_mdT_factor * Math.sin(mdT_Im);

        // (1 - g·e^{-dT})
        double geMdT_Re = g_Re * e_mdT_Re - g_Im * e_mdT_Im;
        double geMdT_Im = g_Re * e_mdT_Im + g_Im * e_mdT_Re;
        double oneMinusGe_Re = 1.0 - geMdT_Re;
        double oneMinusGe_Im = -geMdT_Im;

        // (1 - g)
        double oneMinusG_Re = 1.0 - g_Re;
        double oneMinusG_Im = -g_Im;

        // ratio = (1 - g e^{-dT}) / (1 - g);  log of ratio
        double omgMag2 = oneMinusG_Re * oneMinusG_Re + oneMinusG_Im * oneMinusG_Im;
        double ratio_Re = (oneMinusGe_Re * oneMinusG_Re + oneMinusGe_Im * oneMinusG_Im) / omgMag2;
        double ratio_Im = (oneMinusGe_Im * oneMinusG_Re - oneMinusGe_Re * oneMinusG_Im) / omgMag2;
        double logRatio_Re = 0.5 * Math.log(ratio_Re * ratio_Re + ratio_Im * ratio_Im);
        double logRatio_Im = Math.atan2(ratio_Im, ratio_Re);

        // C = (r - q) i u T + (κθ / ξ²) · [(b - ρξ i u - d) T - 2 log(ratio)]
        double coef = ctx.kappaTheta / ctx.xi2;
        double termA_Re = gNum_Re * ctx.T;
        double termA_Im = gNum_Im * ctx.T;
        double termAll_Re = termA_Re - 2.0 * logRatio_Re;
        double termAll_Im = termA_Im - 2.0 * logRatio_Im;
        double C_Re = coef * termAll_Re;
        double C_Im = coef * termAll_Im + ctx.drift * u * ctx.T;

        // D = (b - ρξ i u - d) / ξ² · (1 - e^{-dT}) / (1 - g e^{-dT})
        double oneMinusE_Re = 1.0 - e_mdT_Re;
        double oneMinusE_Im = -e_mdT_Im;
        double oMgeMag2 = oneMinusGe_Re * oneMinusGe_Re + oneMinusGe_Im * oneMinusGe_Im;
        double frac_Re = (oneMinusE_Re * oneMinusGe_Re + oneMinusE_Im * oneMinusGe_Im) / oMgeMag2;
        double frac_Im = (oneMinusE_Im * oneMinusGe_Re - oneMinusE_Re * oneMinusGe_Im) / oMgeMag2;
        double pre_Re = gNum_Re / ctx.xi2;
        double pre_Im = gNum_Im / ctx.xi2;
        double D_Re = pre_Re * frac_Re - pre_Im * frac_Im;
        double D_Im = pre_Re * frac_Im + pre_Im * frac_Re;

        // φ_j(u) = exp(C + D·v0 + i u lnS)
        double exp_Re = C_Re + D_Re * ctx.v0;
        double exp_Im = C_Im + D_Im * ctx.v0 + u * ctx.lnS;
        double expFactor = Math.exp(exp_Re);
        double phi_Re = expFactor * Math.cos(exp_Im);
        double phi_Im = expFactor * Math.sin(exp_Im);

        return new double[] { phi_Re, phi_Im };
    }
}

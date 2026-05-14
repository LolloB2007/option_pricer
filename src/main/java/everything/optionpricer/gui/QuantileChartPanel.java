package everything.optionpricer.gui;

import everything.optionpricer.model.OptionType;
import everything.optionpricer.util.NormalDistribution;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;


/**
 * Plots the 5th / 50th / 95th percentile of the option's value over time.
 *
 * At each grid time t, compute the analytic percentiles of S_t under GBM
 * and value a European call/put at strike K with remaining time (T − t)
 * via Black–Scholes. Since BS is monotone in S the V-percentile is the
 * corresponding S-percentile (sense flipped for puts).
 *
 * Exact for European options. For the path-dependent / American methods
 * on the Monte Carlo tab this is a European-equivalent reference scenario
 * at percentile S_t (it does not incorporate path history or early
 * exercise).
 *
 * @author lorenzobarbagelata
 */
public class QuantileChartPanel extends JPanel {

    private static final double Z_05 = -1.6448536269514722;
    private static final double Z_95 =  1.6448536269514722;

    private static final double T_MIN = 1.0e-9;

    private static final int POINTS = 240;
    private static final int MARGIN_LEFT   = 56;
    private static final int MARGIN_RIGHT  = 18;
    private static final int MARGIN_TOP    = 28;
    private static final int MARGIN_BOTTOM = 38;

    private static final int CHART_ARC = 14;

    private double spot, strike, rate, volatility, timeToExpiry;
    private boolean isCall;
    private boolean ready;


    public QuantileChartPanel() {
        setPreferredSize(new Dimension(440, 240));
        setOpaque(false);
    }


    public void setInputs(double spot, double strike, double rate, double volatility,
                          double timeToExpiry, OptionType type) {
        this.spot         = spot;
        this.strike       = strike;
        this.rate         = rate;
        this.volatility   = volatility;
        this.timeToExpiry = timeToExpiry;
        this.isCall       = (type == OptionType.CALL);
        this.ready        = spot > 0 && strike > 0 && volatility > 0 && timeToExpiry > 0;
        repaint();
    }


    public void clear() {
        this.ready = false;
        repaint();
    }


    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth(), h = getHeight();

            // Soft rounded surface for the whole panel.
            Color surface = new Color(0x21232B);
            Color border  = new Color(0x2E3140);
            g.setColor(surface);
            g.fillRoundRect(0, 0, w, h, CHART_ARC, CHART_ARC);
            g.setColor(border);
            g.drawRoundRect(0, 0, w - 1, h - 1, CHART_ARC, CHART_ARC);

            int x0 = MARGIN_LEFT, y0 = MARGIN_TOP;
            int plotW = w - MARGIN_LEFT - MARGIN_RIGHT;
            int plotH = h - MARGIN_TOP - MARGIN_BOTTOM;
            if(plotW <= 10 || plotH <= 10) return;

            // Title at top-left of the surface.
            g.setColor(new Color(0x9094A8));
            g.setFont(getFont().deriveFont(11.5f));
            g.drawString("Option value · 5 / 50 / 95% bands", 16, 18);

            if(!ready) {
                String msg = "Price an option to see the 5 / 50 / 95% bands";
                g.setColor(new Color(0x6B6F80));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(msg, x0 + (plotW - fm.stringWidth(msg)) / 2, y0 + plotH / 2);
                return;
            }

            // Clip drawing to the inner plot region so series don't bleed over the labels.
            Shape oldClip = g.getClip();

            // Build the three option-value series.
            double[] ts = new double[POINTS];
            double[] v05 = new double[POINTS];
            double[] v50 = new double[POINTS];
            double[] v95 = new double[POINTS];

            double drift = rate - 0.5 * volatility * volatility;
            double yMin = 0.0;
            double yMax = Double.NEGATIVE_INFINITY;

            for(int i = 0; i < POINTS; i++) {
                double t = timeToExpiry * i / (POINTS - 1);
                ts[i] = t;
                double mu = Math.log(spot) + drift * t;
                double sd = volatility * Math.sqrt(t);

                double sLow  = Math.exp(mu + Z_05 * sd);
                double sMid  = Math.exp(mu);
                double sHigh = Math.exp(mu + Z_95 * sd);

                double remaining = timeToExpiry - t;
                double vLow  = blackScholes(sLow,  remaining);
                double vMid  = blackScholes(sMid,  remaining);
                double vHigh = blackScholes(sHigh, remaining);

                if(isCall) { v05[i] = vLow;  v50[i] = vMid; v95[i] = vHigh; }
                else       { v05[i] = vHigh; v50[i] = vMid; v95[i] = vLow;  }

                if(v95[i] > yMax) yMax = v95[i];
                if(v50[i] > yMax) yMax = v50[i];
                if(v05[i] > yMax) yMax = v05[i];
            }

            if(yMax <= 0) yMax = 1.0;
            yMax *= 1.08;

            // Soft horizontal gridlines + tick labels.
            g.setFont(getFont().deriveFont(11f));
            FontMetrics fm = g.getFontMetrics();
            int ticks = 5;
            for(int i = 0; i <= ticks; i++) {
                double v = yMin + (yMax - yMin) * i / ticks;
                int y = y0 + plotH - (int) Math.round(plotH * i / (double) ticks);
                g.setColor(new Color(255, 255, 255, 18));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(x0, y, x0 + plotW, y);
                g.setColor(new Color(0x9094A8));
                String lbl = String.format("%.2f", v);
                g.drawString(lbl, x0 - fm.stringWidth(lbl) - 6, y + fm.getAscent() / 2 - 2);
            }

            // Time-axis labels at start and end of the plot.
            g.setColor(new Color(0x9094A8));
            g.drawString("t = 0", x0, y0 + plotH + fm.getAscent() + 4);
            String tEnd = String.format("T = %.2fy", timeToExpiry);
            g.drawString(tEnd, x0 + plotW - fm.stringWidth(tEnd), y0 + plotH + fm.getAscent() + 4);

            // Clip series to the plot rect (rounded for a soft edge).
            g.setClip(new RoundRectangle2D.Double(x0, y0, plotW, plotH, 8, 8));

            // Translucent band between p5 and p95 — the "uncertainty band".
            Path2D band = new Path2D.Double();
            double tMin = ts[0], tMax = ts[ts.length - 1];
            // Top edge: p95 left → right
            for(int i = 0; i < POINTS; i++) {
                double x = x0 + (ts[i] - tMin) / (tMax - tMin) * plotW;
                double y = y0 + plotH - (v95[i] - yMin) / (yMax - yMin) * plotH;
                if(i == 0) band.moveTo(x, y); else band.lineTo(x, y);
            }
            // Bottom edge: p5 right → left
            for(int i = POINTS - 1; i >= 0; i--) {
                double x = x0 + (ts[i] - tMin) / (tMax - tMin) * plotW;
                double y = y0 + plotH - (v05[i] - yMin) / (yMax - yMin) * plotH;
                band.lineTo(x, y);
            }
            band.closePath();
            g.setColor(new Color(0x7C5CFF & 0x00FFFFFF | (28 << 24), true));
            g.fill(band);

            // Three series — outer first then median on top.
            drawSeries(g, ts, v95, new Color(0x60A5FA), x0, y0, plotW, plotH, yMin, yMax, 1.6f);
            drawSeries(g, ts, v05, new Color(0xFB923C), x0, y0, plotW, plotH, yMin, yMax, 1.6f);
            drawSeries(g, ts, v50, new Color(0x4ADE80), x0, y0, plotW, plotH, yMin, yMax, 2.2f);

            g.setClip(oldClip);

            // Legend (top-right inside the surface).
            drawLegend(g, w - MARGIN_RIGHT - 4, 14);
        } finally {
            g.dispose();
        }
    }


    private double blackScholes(double S, double tau) {
        if(tau < T_MIN || S <= 0) {
            return Math.max((isCall ? 1.0 : -1.0) * (S - strike), 0.0);
        }
        double sqrtTau = Math.sqrt(tau);
        double d1 = (Math.log(S / strike) + (rate + 0.5 * volatility * volatility) * tau)
                  / (volatility * sqrtTau);
        double d2 = d1 - volatility * sqrtTau;
        double discountedK = strike * Math.exp(-rate * tau);
        double call = S * NormalDistribution.cdf(d1) - discountedK * NormalDistribution.cdf(d2);
        return isCall ? call : call - S + discountedK;
    }


    private static void drawSeries(Graphics2D g, double[] ts, double[] ys, Color col,
                                   int x0, int y0, int plotW, int plotH,
                                   double yMin, double yMax, float stroke) {
        g.setColor(col);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double tMin = ts[0], tMax = ts[ts.length - 1];
        Path2D p = new Path2D.Double();
        for(int i = 0; i < ts.length; i++) {
            double x = x0 + (ts[i] - tMin) / (tMax - tMin) * plotW;
            double y = y0 + plotH - (ys[i] - yMin) / (yMax - yMin) * plotH;
            if(i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        g.draw(p);
    }


    /** Right-aligned legend that grows leftward from x. */
    private static void drawLegend(Graphics2D g, int xRight, int yBaseline) {
        g.setFont(g.getFont().deriveFont(11.5f));
        FontMetrics fm = g.getFontMetrics();
        int x = xRight;
        x = legendEntry(g, fm, x, yBaseline, new Color(0xFB923C), "5%");
        x = legendEntry(g, fm, x - 12, yBaseline, new Color(0x4ADE80), "median");
        x = legendEntry(g, fm, x - 12, yBaseline, new Color(0x60A5FA), "95%");
    }

    private static int legendEntry(Graphics2D g, FontMetrics fm, int xRight, int y, Color col, String text) {
        int textW = fm.stringWidth(text);
        int swatchW = 14;
        int gap = 6;
        int totalW = swatchW + gap + textW;
        int xLeft = xRight - totalW;

        g.setColor(col);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(xLeft, y, xLeft + swatchW, y);

        g.setColor(new Color(0xC9CDDB));
        g.drawString(text, xLeft + swatchW + gap, y + fm.getAscent() / 2 - 1);

        return xLeft;
    }
}

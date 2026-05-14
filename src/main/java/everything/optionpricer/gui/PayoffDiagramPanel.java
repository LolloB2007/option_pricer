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
import java.awt.geom.Path2D;


/**
 * Payoff diagram. Plots two curves vs underlying price:
 *  - Intrinsic at maturity (the hockey stick): max(±(S − K), 0).
 *  - Current option value vs spot (smooth BS curve).
 *
 * Includes a vertical guide at the configured spot S₀ and an "S₀" / "K"
 * tick marker on the x-axis.
 *
 * Exact for European options. For path-dependent and American methods the
 * "current value" curve is the European-equivalent reference (the same
 * caveat as the percentile chart).
 *
 * @author lorenzobarbagelata
 */
public class PayoffDiagramPanel extends JPanel {

    private static final int POINTS = 240;
    private static final int MARGIN_LEFT   = 56;
    private static final int MARGIN_RIGHT  = 18;
    private static final int MARGIN_TOP    = 28;
    private static final int MARGIN_BOTTOM = 38;
    private static final int CHART_ARC = 14;
    private static final double T_MIN = 1.0e-9;

    private static final Color SURFACE = new Color(0x21232B);
    private static final Color BORDER  = new Color(0x2E3140);
    private static final Color INTRINSIC_COL = new Color(0xC9CDDB);   // muted grey-white
    private static final Color VALUE_COL     = new Color(0x7C5CFF);   // accent violet
    private static final Color SPOT_GUIDE    = new Color(0x4ADE80);   // green

    private double spot, strike, rate, volatility, timeToExpiry;
    private boolean isCall;
    private boolean ready;


    public PayoffDiagramPanel() {
        setPreferredSize(new Dimension(440, 220));
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
            g.setColor(SURFACE);
            g.fillRoundRect(0, 0, w, h, CHART_ARC, CHART_ARC);
            g.setColor(BORDER);
            g.drawRoundRect(0, 0, w - 1, h - 1, CHART_ARC, CHART_ARC);

            int x0 = MARGIN_LEFT, y0 = MARGIN_TOP;
            int pw = w - MARGIN_LEFT - MARGIN_RIGHT;
            int ph = h - MARGIN_TOP - MARGIN_BOTTOM;
            if(pw <= 10 || ph <= 10) return;

            g.setColor(new Color(0x9094A8));
            g.setFont(getFont().deriveFont(11.5f));
            g.drawString("Payoff diagram", 16, 18);

            if(!ready) {
                String msg = "Price an option to see its payoff diagram";
                g.setColor(new Color(0x6B6F80));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(msg, x0 + (pw - fm.stringWidth(msg)) / 2, y0 + ph / 2);
                return;
            }

            double sMin = Math.max(0.01, Math.min(spot, strike) * 0.5);
            double sMax = Math.max(spot, strike) * 1.6;

            double[] xs   = new double[POINTS];
            double[] intr = new double[POINTS];
            double[] val  = new double[POINTS];
            double yMax = 0;

            for(int i = 0; i < POINTS; i++) {
                double s = sMin + (sMax - sMin) * i / (POINTS - 1);
                xs[i] = s;
                intr[i] = Math.max((isCall ? 1.0 : -1.0) * (s - strike), 0.0);
                val[i]  = blackScholes(s, timeToExpiry);
                if(intr[i] > yMax) yMax = intr[i];
                if(val[i]  > yMax) yMax = val[i];
            }
            double yMin = 0.0;
            yMax = Math.max(yMax, 1.0) * 1.10;

            paintGrid(g, x0, y0, pw, ph, sMin, sMax, yMin, yMax);

            // Filled area below the BS value curve — soft accent tint.
            Path2D fill = new Path2D.Double();
            fill.moveTo(x0, y0 + ph);
            for(int i = 0; i < POINTS; i++) {
                double sx = x0 + (xs[i] - sMin) / (sMax - sMin) * pw;
                double sy = y0 + ph - (val[i] - yMin) / (yMax - yMin) * ph;
                fill.lineTo(sx, sy);
            }
            fill.lineTo(x0 + pw, y0 + ph);
            fill.closePath();
            g.setColor(new Color(0x7C5CFF & 0x00FFFFFF | (28 << 24), true));
            g.fill(fill);

            // Intrinsic — dashed, muted.
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                       1f, new float[] { 6f, 5f }, 0f));
            g.setColor(INTRINSIC_COL);
            drawCurve(g, xs, intr, x0, y0, pw, ph, sMin, sMax, yMin, yMax);

            // Current value — solid accent.
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(VALUE_COL);
            drawCurve(g, xs, val, x0, y0, pw, ph, sMin, sMax, yMin, yMax);

            paintMarkers(g, x0, y0, pw, ph, sMin, sMax);
            paintLegend(g, w - MARGIN_RIGHT - 4, 14);
        } finally {
            g.dispose();
        }
    }


    private void paintGrid(Graphics2D g, int x0, int y0, int pw, int ph,
                           double sMin, double sMax, double yMin, double yMax) {
        g.setFont(getFont().deriveFont(11f));
        FontMetrics fm = g.getFontMetrics();
        int yticks = 4;
        for(int i = 0; i <= yticks; i++) {
            double v = yMin + (yMax - yMin) * i / yticks;
            int y = y0 + ph - (int) Math.round(ph * i / (double) yticks);
            g.setColor(new Color(255, 255, 255, 18));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(x0, y, x0 + pw, y);
            g.setColor(new Color(0x9094A8));
            String lbl = String.format("%.2f", v);
            g.drawString(lbl, x0 - fm.stringWidth(lbl) - 6, y + fm.getAscent() / 2 - 2);
        }
        // X-axis labels (start, mid, end).
        g.setColor(new Color(0x9094A8));
        int yTextLine = y0 + ph + fm.getAscent() + 4;
        g.drawString(String.format("%.2f", sMin), x0, yTextLine);
        String mid = String.format("%.2f", (sMin + sMax) / 2);
        g.drawString(mid, x0 + pw / 2 - fm.stringWidth(mid) / 2, yTextLine);
        String hi  = String.format("%.2f", sMax);
        g.drawString(hi, x0 + pw - fm.stringWidth(hi), yTextLine);
    }


    private void paintMarkers(Graphics2D g, int x0, int y0, int pw, int ph,
                              double sMin, double sMax) {
        // Vertical guide at spot S0.
        int xSpot = x0 + (int) Math.round((spot - sMin) / (sMax - sMin) * pw);
        if(xSpot >= x0 && xSpot <= x0 + pw) {
            g.setColor(new Color(0x4ADE80 & 0x00FFFFFF | (90 << 24), true));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                       1f, new float[] { 4f, 4f }, 0f));
            g.drawLine(xSpot, y0, xSpot, y0 + ph);

            g.setColor(SPOT_GUIDE);
            g.setFont(getFont().deriveFont(11f));
            FontMetrics fm = g.getFontMetrics();
            String lbl = String.format("S₀ = %.2f", spot);
            int lx = Math.min(xSpot + 6, x0 + pw - fm.stringWidth(lbl) - 2);
            g.drawString(lbl, lx, y0 + fm.getAscent() + 2);
        }

        // Strike tick on x-axis.
        int xStrike = x0 + (int) Math.round((strike - sMin) / (sMax - sMin) * pw);
        if(xStrike >= x0 && xStrike <= x0 + pw) {
            g.setColor(new Color(0xB8BCC9));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(xStrike, y0 + ph - 3, xStrike, y0 + ph + 3);
            g.setFont(getFont().deriveFont(11f));
            FontMetrics fm = g.getFontMetrics();
            String lbl = String.format("K = %.2f", strike);
            int lx = xStrike - fm.stringWidth(lbl) / 2;
            lx = Math.max(x0 + 2, Math.min(lx, x0 + pw - fm.stringWidth(lbl) - 2));
            g.drawString(lbl, lx, y0 + ph + fm.getAscent() + 18);
        }
    }


    private static void drawCurve(Graphics2D g, double[] xs, double[] ys,
                                  int x0, int y0, int pw, int ph,
                                  double sMin, double sMax, double yMin, double yMax) {
        Path2D p = new Path2D.Double();
        for(int i = 0; i < xs.length; i++) {
            double x = x0 + (xs[i] - sMin) / (sMax - sMin) * pw;
            double y = y0 + ph - (ys[i] - yMin) / (yMax - yMin) * ph;
            if(i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        g.draw(p);
    }


    private void paintLegend(Graphics2D g, int xRight, int yBaseline) {
        g.setFont(g.getFont().deriveFont(11.5f));
        FontMetrics fm = g.getFontMetrics();
        int x = xRight;
        x = legendEntry(g, fm, x,      yBaseline, INTRINSIC_COL, "payoff at T",   true);
            legendEntry(g, fm, x - 14, yBaseline, VALUE_COL,     "value today",   false);
    }


    private static int legendEntry(Graphics2D g, FontMetrics fm, int xRight, int y,
                                   Color col, String text, boolean dashed) {
        int textW = fm.stringWidth(text);
        int swatchW = 16, gap = 6;
        int xLeft = xRight - (swatchW + gap + textW);
        g.setColor(col);
        if(dashed) {
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                       1f, new float[] { 4f, 3f }, 0f));
        } else {
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        g.drawLine(xLeft, y, xLeft + swatchW, y);
        g.setColor(new Color(0xC9CDDB));
        g.drawString(text, xLeft + swatchW + gap, y + fm.getAscent() / 2 - 1);
        return xLeft;
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
}

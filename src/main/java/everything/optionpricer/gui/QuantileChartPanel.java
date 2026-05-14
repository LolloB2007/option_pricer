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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;


/**
 * Plots the 5th / 50th / 95th percentile of the option's value over time,
 * with hover crosshair + tooltip readout.
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

    private static final Color CHART_HIGH = new Color(0x60A5FA); // blue (95%)
    private static final Color CHART_MID  = new Color(0x4ADE80); // green (median)
    private static final Color CHART_LOW  = new Color(0xFB923C); // orange (5%)
    private static final Color SURFACE    = new Color(0x21232B);
    private static final Color BORDER     = new Color(0x2E3140);

    private double spot, strike, rate, volatility, timeToExpiry;
    private boolean isCall;
    private boolean ready;

    // Cached per-pricing so hover redraws are cheap.
    private double[] ts, v05, v50, v95;
    private double yMin, yMax;

    // Live hover state.
    private Integer hoverX;


    public QuantileChartPanel() {
        setPreferredSize(new Dimension(440, 240));
        setOpaque(false);

        MouseMotionAdapter motion = new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getX()); }
            @Override public void mouseDragged(MouseEvent e) { updateHover(e.getX()); }
        };
        addMouseMotionListener(motion);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) {
                if(hoverX != null) { hoverX = null; repaint(); }
            }
        });
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
        if(ready) recomputeSeries();
        repaint();
    }


    public void clear() {
        this.ready = false;
        this.hoverX = null;
        repaint();
    }


    private int plotX() { return MARGIN_LEFT; }
    private int plotY() { return MARGIN_TOP; }
    private int plotW() { return getWidth()  - MARGIN_LEFT - MARGIN_RIGHT; }
    private int plotH() { return getHeight() - MARGIN_TOP  - MARGIN_BOTTOM; }


    private void updateHover(int mouseX) {
        if(!ready) return;
        int px = plotX(), pw = plotW();
        if(pw <= 0) return;
        Integer next = (mouseX >= px && mouseX <= px + pw) ? mouseX : null;
        if(!java.util.Objects.equals(next, hoverX)) {
            hoverX = next;
            repaint();
        }
    }


    private void recomputeSeries() {
        ts  = new double[POINTS];
        v05 = new double[POINTS];
        v50 = new double[POINTS];
        v95 = new double[POINTS];

        double drift = rate - 0.5 * volatility * volatility;
        yMin = 0.0;
        yMax = Double.NEGATIVE_INFINITY;

        for(int i = 0; i < POINTS; i++) {
            double t = timeToExpiry * i / (POINTS - 1);
            ts[i] = t;
            double[] v = optionValues(t, drift);
            v05[i] = v[0]; v50[i] = v[1]; v95[i] = v[2];
            if(v05[i] > yMax) yMax = v05[i];
            if(v50[i] > yMax) yMax = v50[i];
            if(v95[i] > yMax) yMax = v95[i];
        }
        if(yMax <= 0) yMax = 1.0;
        yMax *= 1.08;
    }


    /** Returns {v5, v50, v95} option values at time t. */
    private double[] optionValues(double t, double drift) {
        double mu = Math.log(spot) + drift * t;
        double sd = volatility * Math.sqrt(t);

        double sLow  = Math.exp(mu + Z_05 * sd);
        double sMid  = Math.exp(mu);
        double sHigh = Math.exp(mu + Z_95 * sd);

        double remaining = timeToExpiry - t;
        double vLow  = blackScholes(sLow,  remaining);
        double vMid  = blackScholes(sMid,  remaining);
        double vHigh = blackScholes(sHigh, remaining);

        // Calls: V increases with S, so V(sHigh) is the 95% line.
        // Puts:  V decreases with S, so V(sLow)  is the 95% line.
        return isCall ? new double[]{vLow, vMid, vHigh}
                      : new double[]{vHigh, vMid, vLow};
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

            int x0 = plotX(), y0 = plotY(), pw = plotW(), ph = plotH();
            if(pw <= 10 || ph <= 10) return;

            g.setColor(new Color(0x9094A8));
            g.setFont(getFont().deriveFont(11.5f));
            g.drawString("Option value · 5 / 50 / 95% bands", 16, 18);

            if(!ready) {
                String msg = "Price an option to see the 5 / 50 / 95% bands";
                g.setColor(new Color(0x6B6F80));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(msg, x0 + (pw - fm.stringWidth(msg)) / 2, y0 + ph / 2);
                return;
            }

            paintGrid(g, x0, y0, pw, ph);
            paintBandAndSeries(g, x0, y0, pw, ph);
            paintLegend(g, w - MARGIN_RIGHT - 4, 14);

            if(hoverX != null) {
                paintHover(g, x0, y0, pw, ph);
            }
        } finally {
            g.dispose();
        }
    }


    private void paintGrid(Graphics2D g, int x0, int y0, int pw, int ph) {
        g.setFont(getFont().deriveFont(11f));
        FontMetrics fm = g.getFontMetrics();
        int ticks = 5;
        for(int i = 0; i <= ticks; i++) {
            double v = yMin + (yMax - yMin) * i / ticks;
            int y = y0 + ph - (int) Math.round(ph * i / (double) ticks);
            g.setColor(new Color(255, 255, 255, 18));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(x0, y, x0 + pw, y);
            g.setColor(new Color(0x9094A8));
            String lbl = String.format("%.2f", v);
            g.drawString(lbl, x0 - fm.stringWidth(lbl) - 6, y + fm.getAscent() / 2 - 2);
        }
        g.setColor(new Color(0x9094A8));
        g.drawString("t = 0", x0, y0 + ph + fm.getAscent() + 4);
        String tEnd = String.format("T = %.2fy", timeToExpiry);
        g.drawString(tEnd, x0 + pw - fm.stringWidth(tEnd), y0 + ph + fm.getAscent() + 4);
    }


    private void paintBandAndSeries(Graphics2D g, int x0, int y0, int pw, int ph) {
        Shape oldClip = g.getClip();
        g.setClip(new RoundRectangle2D.Double(x0, y0, pw, ph, 8, 8));

        // Translucent uncertainty band between p5 and p95.
        double tMin = ts[0], tMax = ts[ts.length - 1];
        Path2D band = new Path2D.Double();
        for(int i = 0; i < POINTS; i++) {
            double x = x0 + (ts[i] - tMin) / (tMax - tMin) * pw;
            double y = y0 + ph - (v95[i] - yMin) / (yMax - yMin) * ph;
            if(i == 0) band.moveTo(x, y); else band.lineTo(x, y);
        }
        for(int i = POINTS - 1; i >= 0; i--) {
            double x = x0 + (ts[i] - tMin) / (tMax - tMin) * pw;
            double y = y0 + ph - (v05[i] - yMin) / (yMax - yMin) * ph;
            band.lineTo(x, y);
        }
        band.closePath();
        g.setColor(new Color(0x7C5CFF & 0x00FFFFFF | (28 << 24), true));
        g.fill(band);

        drawSeries(g, v95, CHART_HIGH, x0, y0, pw, ph, 1.6f);
        drawSeries(g, v05, CHART_LOW,  x0, y0, pw, ph, 1.6f);
        drawSeries(g, v50, CHART_MID,  x0, y0, pw, ph, 2.2f);

        g.setClip(oldClip);
    }


    private void paintHover(Graphics2D g, int x0, int y0, int pw, int ph) {
        int mx = Math.max(x0, Math.min(x0 + pw, hoverX));
        double tMin = ts[0], tMax = ts[ts.length - 1];
        double t = tMin + (mx - x0) / (double) pw * (tMax - tMin);

        // Analytic readouts at the exact hover-t — no array snapping.
        double drift = rate - 0.5 * volatility * volatility;
        double[] v = optionValues(t, drift);
        double vLow = v[0], vMid = v[1], vHigh = v[2];

        int yLow  = (int) Math.round(y0 + ph - (vLow  - yMin) / (yMax - yMin) * ph);
        int yMidI = (int) Math.round(y0 + ph - (vMid  - yMin) / (yMax - yMin) * ph);
        int yHigh = (int) Math.round(y0 + ph - (vHigh - yMin) / (yMax - yMin) * ph);

        // Crosshair vertical line.
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 70));
        g.drawLine(mx, y0, mx, y0 + ph);

        // Dots at the three intersections.
        drawDot(g, CHART_HIGH, mx, yHigh);
        drawDot(g, CHART_MID,  mx, yMidI);
        drawDot(g, CHART_LOW,  mx, yLow);

        // Tooltip card.
        String tLine = String.format("t = %.2f y", t);
        String h95   = String.format("%.3f", vHigh);
        String h50   = String.format("%.3f", vMid);
        String h05   = String.format("%.3f", vLow);

        Color textCol  = new Color(0xE8E9F0);
        Color mutedCol = new Color(0x9094A8);
        g.setFont(getFont().deriveFont(11.5f));
        FontMetrics fm = g.getFontMetrics();
        int lh = fm.getHeight();
        int padX = 12, padY = 10, dotSize = 6, dotGap = 8, labelGap = 6;
        int labelMaxW = Math.max(fm.stringWidth("95%"), Math.max(fm.stringWidth("median"), fm.stringWidth("5%")));
        int valueMaxW = Math.max(fm.stringWidth(h95), Math.max(fm.stringWidth(h50), fm.stringWidth(h05)));
        int rowW = dotSize + dotGap + labelMaxW + labelGap + valueMaxW;
        int titleW = fm.stringWidth(tLine);
        int boxW = Math.max(rowW, titleW) + 2 * padX;
        int boxH = 4 * lh + 2 * padY + 2; // title + 3 rows + a little extra

        int boxX = mx + 12;
        if(boxX + boxW > x0 + pw) boxX = mx - 12 - boxW;
        if(boxX < x0) boxX = x0 + 4;
        int boxY = y0 + 10;

        g.setColor(new Color(0x171821));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        g.setColor(new Color(0x7C5CFF & 0x00FFFFFF | (140 << 24), true));
        g.drawRoundRect(boxX, boxY, boxW - 1, boxH - 1, 10, 10);

        int textX = boxX + padX;
        int yLine = boxY + padY + fm.getAscent();

        g.setColor(mutedCol);
        g.drawString(tLine, textX, yLine);
        yLine += lh + 2;

        // Three rows: dot, label, right-aligned value.
        int valueRight = boxX + boxW - padX;
        int rowGap = (lh - dotSize) / 2;
        yLine = drawTooltipRow(g, fm, textX, yLine, valueRight, rowGap, CHART_HIGH, "95%",    h95, textCol);
        yLine = drawTooltipRow(g, fm, textX, yLine, valueRight, rowGap, CHART_MID,  "median", h50, textCol);
                drawTooltipRow(g, fm, textX, yLine, valueRight, rowGap, CHART_LOW,  "5%",     h05, textCol);
    }


    private static int drawTooltipRow(Graphics2D g, FontMetrics fm, int textX, int yBaseline, int valueRight,
                                      int rowGap, Color dotCol, String label, String value, Color textCol) {
        int dotSize = 6;
        g.setColor(dotCol);
        g.fillOval(textX, yBaseline - fm.getAscent() + rowGap, dotSize, dotSize);
        g.setColor(new Color(0xB8BCC9));
        g.drawString(label, textX + dotSize + 8, yBaseline);
        g.setColor(textCol);
        int valueW = fm.stringWidth(value);
        g.drawString(value, valueRight - valueW, yBaseline);
        return yBaseline + fm.getHeight();
    }


    private static void drawDot(Graphics2D g, Color col, int cx, int cy) {
        g.setColor(new Color(0x171821));
        g.fillOval(cx - 4, cy - 4, 8, 8);
        g.setColor(col);
        g.fillOval(cx - 3, cy - 3, 6, 6);
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


    private void drawSeries(Graphics2D g, double[] ys, Color col,
                            int x0, int y0, int plotW, int plotH, float stroke) {
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


    private void paintLegend(Graphics2D g, int xRight, int yBaseline) {
        g.setFont(g.getFont().deriveFont(11.5f));
        FontMetrics fm = g.getFontMetrics();
        int x = xRight;
        x = legendEntry(g, fm, x,        yBaseline, CHART_LOW,  "5%");
        x = legendEntry(g, fm, x - 12,   yBaseline, CHART_MID,  "median");
            legendEntry(g, fm, x - 12,   yBaseline, CHART_HIGH, "95%");
    }


    private static int legendEntry(Graphics2D g, FontMetrics fm, int xRight, int y, Color col, String text) {
        int textW = fm.stringWidth(text);
        int swatchW = 14, gap = 6;
        int xLeft = xRight - (swatchW + gap + textW);
        g.setColor(col);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(xLeft, y, xLeft + swatchW, y);
        g.setColor(new Color(0xC9CDDB));
        g.drawString(text, xLeft + swatchW + gap, y + fm.getAscent() / 2 - 1);
        return xLeft;
    }
}

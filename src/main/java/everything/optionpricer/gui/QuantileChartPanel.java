package everything.optionpricer.gui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;


/**
 * Plots the analytic 5th, 50th and 95th percentile lines of {@code S_t}
 * under geometric Brownian motion.
 *
 * Closed form: log S_t ~ N(log S0 + (r − ½σ²)t, σ²t), so
 *   S_t at percentile α  =  S0 · exp((r − ½σ²)·t + σ·√t · z_α)
 * No simulation needed.
 *
 * @author lorenzobarbagelata
 */
public class QuantileChartPanel extends JPanel {

    // Inverse standard-normal cdf at 5% and 95% (95% confidence band).
    private static final double Z_05 = -1.6448536269514722;
    private static final double Z_95 =  1.6448536269514722;

    private static final int POINTS = 200;
    private static final int MARGIN_LEFT   = 56;
    private static final int MARGIN_RIGHT  = 16;
    private static final int MARGIN_TOP    = 18;
    private static final int MARGIN_BOTTOM = 36;

    private double spot;
    private double rate;
    private double volatility;
    private double timeToExpiry;
    private boolean ready;


    public QuantileChartPanel() {
        setPreferredSize(new Dimension(420, 220));
    }


    /** Update the inputs and repaint. */
    public void setInputs(double spot, double rate, double volatility, double timeToExpiry) {
        this.spot = spot;
        this.rate = rate;
        this.volatility = volatility;
        this.timeToExpiry = timeToExpiry;
        this.ready = spot > 0 && volatility > 0 && timeToExpiry > 0;
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

            int w = getWidth(), h = getHeight();
            int x0 = MARGIN_LEFT, y0 = MARGIN_TOP;
            int plotW = w - MARGIN_LEFT - MARGIN_RIGHT;
            int plotH = h - MARGIN_TOP - MARGIN_BOTTOM;
            if(plotW <= 10 || plotH <= 10) return;

            // Frame and background.
            g.setColor(getBackground().darker());
            g.fillRect(x0, y0, plotW, plotH);
            g.setColor(getForeground().darker());
            g.drawRect(x0, y0, plotW, plotH);

            if(!ready) {
                String msg = "Enter inputs and price to see the GBM 5/50/95% bands";
                g.setColor(getForeground());
                FontMetrics fm = g.getFontMetrics();
                g.drawString(msg, x0 + (plotW - fm.stringWidth(msg)) / 2, y0 + plotH / 2);
                return;
            }

            // Compute the three percentile series.
            double[] ts = new double[POINTS];
            double[] p5 = new double[POINTS];
            double[] p50 = new double[POINTS];
            double[] p95 = new double[POINTS];

            double drift = rate - 0.5 * volatility * volatility;
            double yMin = spot, yMax = spot;

            for(int i = 0; i < POINTS; i++) {
                double t = timeToExpiry * i / (POINTS - 1);
                ts[i] = t;
                double mu = Math.log(spot) + drift * t;
                double sd = volatility * Math.sqrt(t);
                p50[i] = Math.exp(mu);
                p5[i]  = Math.exp(mu + Z_05 * sd);
                p95[i] = Math.exp(mu + Z_95 * sd);
                if(p5[i]  < yMin) yMin = p5[i];
                if(p95[i] > yMax) yMax = p95[i];
            }

            // Add a little vertical padding.
            double pad = 0.05 * (yMax - yMin);
            yMin -= pad;
            yMax += pad;

            // Axes labels — a few price ticks plus start/end time labels.
            g.setColor(getForeground());
            FontMetrics fm = g.getFontMetrics();
            int ticks = 5;
            for(int i = 0; i <= ticks; i++) {
                double v = yMin + (yMax - yMin) * i / ticks;
                int y = y0 + plotH - (int) Math.round(plotH * i / (double) ticks);
                g.setColor(getForeground().darker());
                g.drawLine(x0, y, x0 + plotW, y);
                g.setColor(getForeground());
                String lbl = String.format("%.2f", v);
                g.drawString(lbl, x0 - fm.stringWidth(lbl) - 4, y + fm.getAscent() / 2 - 2);
            }
            g.drawString("0", x0 - 4, y0 + plotH + fm.getAscent() + 2);
            String tEnd = String.format("T = %.2fy", timeToExpiry);
            g.drawString(tEnd, x0 + plotW - fm.stringWidth(tEnd), y0 + plotH + fm.getAscent() + 2);

            // Plot the three series.
            drawSeries(g, ts, p95, new Color(80, 180, 255),  x0, y0, plotW, plotH, yMin, yMax, 1.5f);
            drawSeries(g, ts, p5,  new Color(255, 130, 80),  x0, y0, plotW, plotH, yMin, yMax, 1.5f);
            drawSeries(g, ts, p50, new Color(120, 220, 140), x0, y0, plotW, plotH, yMin, yMax, 2.0f);

            // Legend.
            int lx = x0 + 10, ly = y0 + 14;
            drawLegendEntry(g, lx,      ly, new Color(80, 180, 255),  "95%");
            drawLegendEntry(g, lx + 60, ly, new Color(120, 220, 140), "median");
            drawLegendEntry(g, lx + 140, ly, new Color(255, 130, 80), "5%");
        } finally {
            g.dispose();
        }
    }


    private static void drawSeries(Graphics2D g, double[] ts, double[] ys, Color col,
                                   int x0, int y0, int plotW, int plotH,
                                   double yMin, double yMax, float stroke) {
        g.setColor(col);
        g.setStroke(new BasicStroke(stroke));
        double tMin = ts[0], tMax = ts[ts.length - 1];
        double prevX = x0, prevY = y0 + plotH - (ys[0] - yMin) / (yMax - yMin) * plotH;
        for(int i = 1; i < ts.length; i++) {
            double x = x0 + (ts[i] - tMin) / (tMax - tMin) * plotW;
            double y = y0 + plotH - (ys[i] - yMin) / (yMax - yMin) * plotH;
            g.draw(new Line2D.Double(prevX, prevY, x, y));
            prevX = x; prevY = y;
        }
    }


    private static void drawLegendEntry(Graphics2D g, int x, int y, Color col, String text) {
        g.setColor(col);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x, y, x + 14, y);
        g.setColor(g.getColor().brighter());
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + 18, y + fm.getAscent() / 2 - 1);
    }
}

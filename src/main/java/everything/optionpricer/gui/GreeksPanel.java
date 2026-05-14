package everything.optionpricer.gui;

import everything.optionpricer.pricing.Greeks;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;


/**
 * Horizontal row of Greek chips: Δ, Γ, ν, Θ, ρ. Each chip is a small
 * rounded label with the Greek's symbol + value.
 *
 * @author lorenzobarbagelata
 */
public class GreeksPanel extends JPanel {

    private final Chip delta = new Chip("Δ", "delta");
    private final Chip gamma = new Chip("Γ", "gamma");
    private final Chip vega  = new Chip("ν", "vega (per 1% σ)");
    private final Chip theta = new Chip("Θ", "theta (per day)");
    private final Chip rho   = new Chip("ρ", "rho (per 1% r)");


    public GreeksPanel() {
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.CENTER, 8, 0));
        add(delta);
        add(gamma);
        add(vega);
        add(theta);
        add(rho);
        clear();
    }


    public void setGreeks(Greeks g) {
        delta.setValue(g.delta());
        gamma.setValue(g.gamma());
        vega.setValue(g.vega());
        theta.setValue(g.theta());
        rho.setValue(g.rho());
    }


    public void setComputing() {
        delta.setPlaceholder();
        gamma.setPlaceholder();
        vega.setPlaceholder();
        theta.setPlaceholder();
        rho.setPlaceholder();
    }


    public void clear() {
        delta.setEmpty();
        gamma.setEmpty();
        vega.setEmpty();
        theta.setEmpty();
        rho.setEmpty();
    }


    private static final class Chip extends JLabel {
        private final String symbol;

        Chip(String symbol, String tooltip) {
            super(symbol);
            this.symbol = symbol;
            setToolTipText(tooltip);
            setOpaque(false);
            setForeground(new Color(0xE8E9F0));
            setFont(getFont().deriveFont(Font.PLAIN, 12.5f));
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            setAlignmentY(Component.CENTER_ALIGNMENT);
        }

        void setValue(double v) {
            setText(symbol + "  " + format(v));
            setForeground(new Color(0xE8E9F0));
        }

        void setPlaceholder() {
            setText(symbol + "  …");
            setForeground(new Color(0x9094A8));
        }

        void setEmpty() {
            setText(symbol + "  —");
            setForeground(new Color(0x6B6F80));
        }

        private static String format(double v) {
            // Auto-format: use sig figs sensibly for both very small and large values.
            double abs = Math.abs(v);
            if(abs == 0.0)               return "0.0000";
            if(abs >= 100)               return String.format("%+.2f", v);
            if(abs >= 1)                 return String.format("%+.4f", v);
            if(abs >= 0.0001)            return String.format("%+.4f", v);
            return String.format("%+.2e", v);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0x2A2C36));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
                g.setColor(new Color(0x3A3C46));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 999, 999);
            } finally {
                g.dispose();
            }
            super.paintComponent(g0);
        }
    }
}

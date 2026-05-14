package everything.optionpricer.gui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;


/**
 * Rounded, slightly-elevated surface used to group form sections. Picks up
 * its colour from the active L&F so it tracks dark/light themes.
 *
 * @author lorenzobarbagelata
 */
public class Card extends JPanel {

    private static final int ARC = 14;


    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
    }


    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill = UIManager.getColor("Panel.background");
            if(fill == null) fill = new Color(0x21232B);
            // Nudge slightly lighter than the page background so the card reads as a surface.
            fill = lighten(fill, 0.06f);

            Color border = UIManager.getColor("Component.borderColor");
            if(border == null) border = new Color(0x2E3140);

            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
            g.setColor(border);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        } finally {
            g.dispose();
        }
        super.paintComponent(g0);
    }


    private static Color lighten(Color c, float fraction) {
        int r = (int) Math.min(255, c.getRed()   + 255 * fraction);
        int g = (int) Math.min(255, c.getGreen() + 255 * fraction);
        int b = (int) Math.min(255, c.getBlue()  + 255 * fraction);
        return new Color(r, g, b);
    }
}

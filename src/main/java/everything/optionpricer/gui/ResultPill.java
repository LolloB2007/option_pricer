package everything.optionpricer.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;


/**
 * Pill-shaped, accent-tinted label used to surface the pricer result.
 *
 * @author lorenzobarbagelata
 */
public class ResultPill extends JLabel {

    public ResultPill() {
        super("", SwingConstants.CENTER);
        setOpaque(false);
        setVisible(false);
        setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        setFont(getFont().deriveFont(Font.BOLD, 14f));
        setForeground(new Color(0xE8E9F0));
    }


    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Tinted fill + thin accent border for a soft "chip" look.
            g.setColor(new Color(0x7C5CFF & 0x00FFFFFF | (40 << 24), true));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
            g.setColor(new Color(0x7C5CFF & 0x00FFFFFF | (120 << 24), true));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 999, 999);
        } finally {
            g.dispose();
        }
        super.paintComponent(g0);
    }
}

package everything.optionpricer.gui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;


/**
 * Centralised colour palette, typography helpers and component styling
 * recipes. Keeps the look-and-feel tweaks out of the panel code.
 *
 * @author lorenzobarbagelata
 */
public final class Theme {

    // Accent + chart palette.
    public static final Color ACCENT          = new Color(0x7C5CFF);
    public static final Color ACCENT_HOVER    = new Color(0x8E72FF);
    public static final Color ACCENT_PRESSED  = new Color(0x6B4FE3);
    public static final Color ACCENT_SOFT     = new Color(0x7C5CFF & 0x00FFFFFF | (32 << 24), true);

    public static final Color CHART_HIGH = new Color(0x60A5FA); // blue
    public static final Color CHART_MID  = new Color(0x4ADE80); // green
    public static final Color CHART_LOW  = new Color(0xFB923C); // orange

    public static final Color SUCCESS = new Color(0x4ADE80);

    private Theme() {}


    /** Install global UI defaults. Call once before any window is shown. */
    public static void install() {
        // Rounded corners across the board.
        UIManager.put("Component.arc",        12);
        UIManager.put("Button.arc",          999);
        UIManager.put("Component.focusWidth",   0);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("ScrollBar.thumbArc",  999);
        UIManager.put("ScrollBar.width",      10);
        UIManager.put("ScrollBar.trackArc",  999);

        // Underline-style tabs sit better with the modern look.
        UIManager.put("TabbedPane.tabType",            "underlined");
        UIManager.put("TabbedPane.tabHeight",          40);
        UIManager.put("TabbedPane.tabInsets",          new Insets(8, 22, 8, 22));
        UIManager.put("TabbedPane.showTabSeparators",  false);
        UIManager.put("TabbedPane.underlineColor",     ACCENT);
        UIManager.put("TabbedPane.selectedForeground", new Color(0xE8E9F0));

        // Slightly larger base font for breathing room.
        Font base = UIManager.getFont("defaultFont");
        if(base != null) {
            UIManager.put("defaultFont", base.deriveFont(13f));
        }
    }


    /** Apply the filled-accent style to a primary-action button. */
    public static void stylePrimary(AbstractButton button) {
        button.putClientProperty(FlatClientProperties.STYLE,
                "background: #7C5CFF;" +
                "foreground: #FFFFFF;" +
                "hoverBackground: #8E72FF;" +
                "pressedBackground: #6B4FE3;" +
                "focusedBackground: #8E72FF;" +
                "borderWidth: 0;" +
                "innerFocusWidth: 0;" +
                "focusWidth: 0;");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13.5f));
        button.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
    }


    /** Style a JToggleButton (in a group) as a segmented-control chip. */
    public static void styleSegment(AbstractButton seg) {
        seg.putClientProperty(FlatClientProperties.STYLE,
                "arc: 999;" +
                "background: $Panel.background;" +
                "foreground: #B8BCC9;" +
                "selectedBackground: #7C5CFF;" +
                "selectedForeground: #FFFFFF;" +
                "hoverBackground: #2A2C36;" +
                "borderWidth: 0;" +
                "innerFocusWidth: 0;" +
                "focusWidth: 0;");
        seg.setFocusPainted(false);
        seg.setFont(seg.getFont().deriveFont(Font.PLAIN, 12.5f));
        seg.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
    }


    /** Build an H1-style title label. */
    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE_CLASS, "h1");
        return l;
    }


    /** Build a muted subtitle label. */
    public static JLabel subtitle(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        l.setForeground(new Color(0x9094A8));
        return l;
    }


    /** Build a small label used as a form-row caption. */
    public static JLabel formLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(0xB8BCC9));
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12.5f));
        return l;
    }
}

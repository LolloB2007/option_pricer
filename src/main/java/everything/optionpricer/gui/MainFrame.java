package everything.optionpricer.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;


/**
 * Application window — a tabbed shell hosting the European/Black–Scholes
 * page and the Monte Carlo page.
 *
 * Each tab's content sits inside a {@link JScrollPane} so the panel can
 * stay at its natural height regardless of window size — when the window
 * is too short to fit everything, the user scrolls instead of having
 * sections silently clipped below the viewport.
 *
 * @author lorenzobarbagelata
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("OptionPricer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(BorderFactory.createEmptyBorder(6, 12, 0, 12));
        tabs.addTab("European",    inScrollPane(new EuropeanPanel()));
        tabs.addTab("Monte Carlo", inScrollPane(new MonteCarloPanel()));

        add(tabs);
    }


    /**
     * Wrap a content panel so it can be scrolled vertically when the
     * tab is shorter than the panel needs. Horizontal scroll is
     * disabled — the panel fills the viewport width via MigLayout
     * {@code fillx}.
     */
    private static JScrollPane inScrollPane(JComponent content) {
        JScrollPane sp = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setViewportBorder(null);

        // Inherit the panel background so the scroll pane doesn't show
        // an L&F-default grey gutter on either side of the content.
        sp.setOpaque(false);
        JViewport vp = sp.getViewport();
        vp.setOpaque(false);
        return sp;
    }
}

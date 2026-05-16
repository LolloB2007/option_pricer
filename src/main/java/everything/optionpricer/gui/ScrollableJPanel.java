package everything.optionpricer.gui;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;


/**
 * {@link JPanel} that fills the viewport width when placed inside a
 * {@link javax.swing.JScrollPane}, so MigLayout's {@code fillx} actually
 * stretches the content to the available width instead of leaving the
 * panel at its preferred (narrower) size with empty space beside it.
 *
 * The panel does NOT track viewport height — when the content is taller
 * than the viewport the scroll pane shows a vertical scrollbar.
 *
 * @author lorenzobarbagelata
 */
public class ScrollableJPanel extends JPanel implements Scrollable {

    public ScrollableJPanel(LayoutManager layout) {
        super(layout);
    }


    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(64, visible.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

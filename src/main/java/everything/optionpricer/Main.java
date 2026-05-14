package everything.optionpricer;

import com.formdev.flatlaf.FlatDarkLaf;
import everything.optionpricer.gui.MainFrame;
import everything.optionpricer.gui.Theme;
import javax.swing.SwingUtilities;


/**
 * Application entry point.
 * @author lorenzobarbagelata
 */
public class Main {

    public static void main(String[] args) {

        FlatDarkLaf.setup();
        Theme.install();

        SwingUtilities.invokeLater(() -> {
            MainFrame main = new MainFrame();
            main.setSize(620, 820);
            main.setLocationRelativeTo(null);
            main.setVisible(true);
        });
    }
}

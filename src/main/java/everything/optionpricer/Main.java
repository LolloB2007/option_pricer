package everything.optionpricer;

import com.formdev.flatlaf.FlatDarkLaf;
import everything.optionpricer.gui.MainFrame;
import javax.swing.SwingUtilities;

/**
 * entry point
 * @author lorenzobarbagelata
 */
public class Main {

    public static void main(String[] args) {
        
        FlatDarkLaf.setup();
        
        SwingUtilities.invokeLater( () -> {
            MainFrame main = new MainFrame();
            main.setSize(400, 500);
            main.setLocationRelativeTo(null);
            main.setVisible(true);
        });
        
    }
}

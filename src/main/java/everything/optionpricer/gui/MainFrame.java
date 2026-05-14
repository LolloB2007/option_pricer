package everything.optionpricer.gui;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;


/**
 * Application window — a tabbed shell hosting the European/Black–Scholes
 * page and the Monte Carlo page.
 *
 * @author lorenzobarbagelata
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("OptionPricer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("European (Black–Scholes)", new EuropeanPanel());
        tabs.addTab("Monte Carlo",              new MonteCarloPanel());

        add(tabs);
    }
}

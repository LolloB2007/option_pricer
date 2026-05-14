package everything.optionpricer;

import com.formdev.flatlaf.FlatDarkLaf;
import everything.optionpricer.api.ApiServer;
import everything.optionpricer.gui.MainFrame;
import everything.optionpricer.gui.Theme;

import javax.swing.SwingUtilities;
import java.io.IOException;


/**
 * Application entry point. Two modes:
 *   - default: launch the Swing GUI.
 *   - {@code --api [port]}: start the HTTP/JSON pricing API (default port 8080).
 *
 * @author lorenzobarbagelata
 */
public class Main {

    public static void main(String[] args) {
        if(args.length > 0 && "--api".equalsIgnoreCase(args[0])) {
            int port = 8080;
            if(args.length > 1) {
                try {
                    port = Integer.parseInt(args[1]);
                } catch(NumberFormatException e) {
                    System.err.println("invalid port: " + args[1]);
                    System.exit(1);
                }
            }
            try {
                ApiServer.start(port);
                System.out.println("OptionPricer API listening on http://localhost:" + port);
                // Keep the JVM alive; the server uses daemon-like executor threads otherwise.
                Thread.currentThread().join();
            } catch(IOException e) {
                System.err.println("failed to start API: " + e.getMessage());
                System.exit(1);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        FlatDarkLaf.setup();
        Theme.install();

        SwingUtilities.invokeLater(() -> {
            MainFrame main = new MainFrame();
            main.setSize(660, 1040);
            main.setLocationRelativeTo(null);
            main.setVisible(true);
        });
    }
}

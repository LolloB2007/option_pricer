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
            String token = System.getenv("OPTIONPRICER_API_TOKEN");
            // Parse remaining args order-independently.
            for(int i = 1; i < args.length; i++) {
                if("--token".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    token = args[++i];
                } else {
                    try {
                        port = Integer.parseInt(args[i]);
                    } catch(NumberFormatException e) {
                        System.err.println("ignored arg: " + args[i]);
                    }
                }
            }
            try {
                ApiServer.start(port, token);
                System.out.println("OptionPricer API listening on http://localhost:" + port);
                if(token != null && !token.isBlank()) {
                    System.out.println("Authentication: ENABLED — bearer token required on every endpoint except /health.");
                } else {
                    System.out.println("Authentication: DISABLED — bind to localhost only or front with a reverse proxy.");
                }
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
            // Restored (non-maximized) size if the user un-maximizes.
            main.setSize(660, 1040);
            main.setLocationRelativeTo(null);
            // Default to a full-screen / maximized window.
            main.setExtendedState(javax.swing.JFrame.MAXIMIZED_BOTH);
            main.setVisible(true);
        });
    }
}

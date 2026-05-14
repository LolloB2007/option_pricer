package everything.optionpricer.gui;

import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.BlackScholesEngine;

import javax.swing.*;
import net.miginfocom.swing.MigLayout;


/**
 * Main application window.
 * @author lorenzobarbagelata
 */
public class MainFrame extends JFrame {

    private static final String INVALID_INPUT_MSG = "Please enter valid values in inputs";

    private final JPanel mainPanel;

    private JComboBox<OptionType> optionTypeCombo;
    private JTextField spotField;
    private JTextField strikeField;
    private JTextField rateField;
    private JTextField volField;
    private JTextField timeField;

    private JButton priceButton;
    private JLabel resultLabel;


    public MainFrame() {
        mainPanel = new JPanel(new MigLayout(
                "fillx, insets 20",
                "[right]12[grow, fill]",
                "[]10[]10[]10[]10[]10[]10[]20[]"
        ));

        componentInit();

        mainPanel.add(new JLabel("Enter % as decimals (6% = 0.06) and time in years", SwingConstants.CENTER),
                "span2, growx, alignxcenter, wrap");

        mainPanel.add(new JLabel("Option Type:"));
        mainPanel.add(optionTypeCombo, "wrap");

        mainPanel.add(new JLabel("Spot Price (S):"));
        mainPanel.add(spotField, "wrap");

        mainPanel.add(new JLabel("Strike Price (K):"));
        mainPanel.add(strikeField, "wrap");

        mainPanel.add(new JLabel("Risk-Free Rate (r):"));
        mainPanel.add(rateField, "wrap");

        mainPanel.add(new JLabel("Volatility (σ):"));
        mainPanel.add(volField, "wrap");

        mainPanel.add(new JLabel("Time to Expiry (T):"));
        mainPanel.add(timeField, "wrap");

        mainPanel.add(priceButton, "span 2, center, wrap");
        mainPanel.add(resultLabel, "span 2, growx, alignx center");

        add(mainPanel);
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());

        spotField   = new JTextField(8);
        strikeField = new JTextField(8);
        rateField   = new JTextField(8);
        volField    = new JTextField(8);
        timeField   = new JTextField(8);

        priceButton = new JButton("Price option");
        priceButton.addActionListener(e -> onButtonClick());

        resultLabel = new JLabel("", SwingConstants.CENTER);
        resultLabel.setVisible(false);
    }


    private void onButtonClick() {

        resultLabel.setVisible(false);

        OptionType type = (OptionType) optionTypeCombo.getSelectedItem();

        double spot, strike, rate, vol, time;
        try {
            spot   = Double.parseDouble(spotField.getText().trim());
            strike = Double.parseDouble(strikeField.getText().trim());
            rate   = Double.parseDouble(rateField.getText().trim());
            vol    = Double.parseDouble(volField.getText().trim());
            time   = Double.parseDouble(timeField.getText().trim());
        } catch(NumberFormatException ex) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        if(!validateInputs(spot, strike, rate, vol, time)) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        try {
            EuropeanOption option = EuropeanOption.of(type, strike, time);
            PricingResult result = BlackScholesEngine.price(option, spot, rate, vol);
            showResult(result.toString());
        } catch(IllegalArgumentException ex) {
            showResult(INVALID_INPUT_MSG);
        }
    }


    private void showResult(String text) {
        resultLabel.setText(text);
        resultLabel.setVisible(true);
    }


    // Sanity bounds; negative rates are allowed (real-world EUR/CHF policy rates have been negative).
    private boolean validateInputs(double spot, double strike, double rate, double vol, double time) {
        if(spot   <= 0)            return false;
        if(strike <= 0)            return false;
        if(rate   <= -0.2 || rate >= 1.0) return false;
        if(vol    <=  0.0 || vol  >= 5.0) return false;
        if(time   <= 0)            return false;
        return true;
    }
}

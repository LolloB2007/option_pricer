package everything.optionpricer.gui;

import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.BlackScholesEngine;

import javax.swing.*;
import net.miginfocom.swing.MigLayout;


/**
 * European-option pricing page: Black–Scholes closed form.
 *
 * @author lorenzobarbagelata
 */
public class EuropeanPanel extends JPanel {

    private static final String INVALID_INPUT_MSG = "Please enter valid values in inputs";

    private JComboBox<OptionType> optionTypeCombo;
    private JTextField spotField, strikeField, rateField, volField, timeField;
    private JButton priceButton;
    private JLabel resultLabel;


    public EuropeanPanel() {
        setLayout(new MigLayout(
                "fillx, insets 20",
                "[right]12[grow, fill]",
                "[]10[]10[]10[]10[]10[]10[]20[]"
        ));

        componentInit();

        add(new JLabel("Enter % as decimals (6% = 0.06) and time in years", SwingConstants.CENTER),
                "span2, growx, alignxcenter, wrap");

        add(new JLabel("Option Type:"));        add(optionTypeCombo, "wrap");
        add(new JLabel("Spot Price (S):"));     add(spotField,       "wrap");
        add(new JLabel("Strike Price (K):"));   add(strikeField,     "wrap");
        add(new JLabel("Risk-Free Rate (r):")); add(rateField,       "wrap");
        add(new JLabel("Volatility (σ):"));     add(volField,        "wrap");
        add(new JLabel("Time to Expiry (T):")); add(timeField,       "wrap");

        add(priceButton, "span 2, center, wrap");
        add(resultLabel, "span 2, growx, alignx center");
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());
        spotField   = new JTextField(8);
        strikeField = new JTextField(8);
        rateField   = new JTextField(8);
        volField    = new JTextField(8);
        timeField   = new JTextField(8);

        priceButton = new JButton("Price option");
        priceButton.addActionListener(e -> onPrice());

        resultLabel = new JLabel("", SwingConstants.CENTER);
        resultLabel.setVisible(false);
    }


    private void onPrice() {
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

        if(spot <= 0 || strike <= 0 || time <= 0
                || rate <= -0.2 || rate >= 1.0
                || vol  <=  0.0 || vol  >= 5.0) {
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
}

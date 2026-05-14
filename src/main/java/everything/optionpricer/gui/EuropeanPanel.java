package everything.optionpricer.gui;

import com.formdev.flatlaf.FlatClientProperties;
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
    private ResultPill resultLabel;


    public EuropeanPanel() {
        setLayout(new MigLayout("fillx, insets 24 28 24 28", "[grow, fill]", "[]14[]18[]14[]"));
        componentInit();

        add(Theme.title("European Option"), "wrap");
        add(Theme.subtitle("Closed-form Black–Scholes pricer"), "wrap, gapbottom 6");

        add(buildInputs(), "growx, wrap");

        add(priceButton, "alignx center, wrap, gaptop 4");
        add(resultLabel, "alignx center, wrap, gaptop 6");
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());

        spotField   = field("e.g. 100");
        strikeField = field("e.g. 100");
        rateField   = field("e.g. 0.05");
        volField    = field("e.g. 0.20");
        timeField   = field("e.g. 1.00");

        priceButton = new JButton("Price option");
        Theme.stylePrimary(priceButton);
        priceButton.addActionListener(e -> onPrice());

        resultLabel = new ResultPill();
    }


    private Card buildInputs() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill]",
                "[]10[]10[]10[]10[]10[]"
        ));
        card.add(Theme.formLabel("Option type"));  card.add(optionTypeCombo, "wrap");
        card.add(Theme.formLabel("Spot (S)"));     card.add(spotField,       "wrap");
        card.add(Theme.formLabel("Strike (K)"));   card.add(strikeField,     "wrap");
        card.add(Theme.formLabel("Rate (r)"));     card.add(rateField,       "wrap");
        card.add(Theme.formLabel("Volatility (σ)")); card.add(volField,      "wrap");
        card.add(Theme.formLabel("Time (T, yrs)")); card.add(timeField,      "wrap");
        return card;
    }


    private static JTextField field(String placeholder) {
        JTextField f = new JTextField(10);
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        return f;
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

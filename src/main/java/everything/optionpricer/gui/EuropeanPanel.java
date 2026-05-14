package everything.optionpricer.gui;

import com.formdev.flatlaf.FlatClientProperties;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.BlackScholesEngine;
import everything.optionpricer.pricing.Greeks;
import everything.optionpricer.pricing.GreeksCalculator;
import everything.optionpricer.pricing.ImpliedVolatility;

import javax.swing.*;
import net.miginfocom.swing.MigLayout;


/**
 * European-option pricing page: Black–Scholes closed form, with Greeks and a
 * payoff diagram.
 *
 * @author lorenzobarbagelata
 */
public class EuropeanPanel extends JPanel {

    private static final String INVALID_INPUT_MSG = "Please enter valid values in inputs";

    private JComboBox<OptionType> optionTypeCombo;
    private JTextField spotField, strikeField, rateField, volField, timeField, divYieldField;
    private JButton priceButton;
    private ResultPill resultLabel;
    private GreeksPanel greeksPanel;
    private PayoffDiagramPanel payoffChart;

    // Implied volatility sub-form
    private JTextField marketPriceField;
    private JButton    solveIvButton;
    private JLabel     ivResultLabel;


    public EuropeanPanel() {
        setLayout(new MigLayout(
                "fillx, insets 24 28 24 28",
                "[grow, fill]",
                "[]10[]16[]12[]8[]10[]10[]"));
        componentInit();

        add(Theme.title("European Option"), "wrap");
        add(Theme.subtitle("Closed-form Black–Scholes pricer"), "wrap, gapbottom 6");

        add(buildInputs(), "growx, wrap");

        add(priceButton,  "alignx center, wrap, gaptop 4");
        add(resultLabel,  "alignx center, wrap, gaptop 4");
        add(greeksPanel,  "growx, wrap, gaptop 2");
        add(payoffChart,  "grow, push, gaptop 6");
        add(buildIvSection(), "growx, wrap, gaptop 8");
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());

        spotField     = field("e.g. 100");
        strikeField   = field("e.g. 100");
        rateField     = field("e.g. 0.05");
        volField      = field("e.g. 0.20");
        timeField     = field("e.g. 1.00");
        divYieldField = field("0.00");

        priceButton = new JButton("Price option");
        Theme.stylePrimary(priceButton);
        priceButton.addActionListener(e -> onPrice());

        resultLabel  = new ResultPill();
        greeksPanel  = new GreeksPanel();
        payoffChart  = new PayoffDiagramPanel();

        marketPriceField = field("market price");
        solveIvButton    = new JButton("Solve σ");
        Theme.stylePrimary(solveIvButton);
        solveIvButton.addActionListener(e -> onSolveIv());
        ivResultLabel    = new JLabel(" ");
        ivResultLabel.setForeground(new java.awt.Color(0xB8BCC9));
    }


    private Card buildInputs() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill]",
                "[]10[]10[]10[]10[]10[]10[]"));
        card.add(Theme.formLabel("Option type"));    card.add(optionTypeCombo, "wrap");
        card.add(Theme.formLabel("Spot (S)"));       card.add(spotField,       "wrap");
        card.add(Theme.formLabel("Strike (K)"));     card.add(strikeField,     "wrap");
        card.add(Theme.formLabel("Rate (r)"));       card.add(rateField,       "wrap");
        card.add(Theme.formLabel("Volatility (σ)")); card.add(volField,        "wrap");
        card.add(Theme.formLabel("Time (T, yrs)"));  card.add(timeField,       "wrap");
        card.add(Theme.formLabel("Div yield (q)"));  card.add(divYieldField,   "wrap");
        return card;
    }


    private Card buildIvSection() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill][80!]",
                "[]6[]"));
        JLabel title = Theme.subtitle("Implied volatility from a market price");
        card.add(title, "span 3, wrap");
        card.add(Theme.formLabel("Market price"));
        card.add(marketPriceField, "growx");
        card.add(solveIvButton, "growy, wrap");
        card.add(ivResultLabel, "span 3, growx, alignx center, gaptop 2");
        return card;
    }


    private static JTextField field(String placeholder) {
        JTextField f = new JTextField(10);
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        return f;
    }


    private void onPrice() {
        resultLabel.setVisible(false);
        greeksPanel.clear();
        payoffChart.clear();

        OptionType type = (OptionType) optionTypeCombo.getSelectedItem();
        double spot, strike, rate, vol, time, divYield;
        try {
            spot     = Double.parseDouble(spotField.getText().trim());
            strike   = Double.parseDouble(strikeField.getText().trim());
            rate     = Double.parseDouble(rateField.getText().trim());
            vol      = Double.parseDouble(volField.getText().trim());
            time     = Double.parseDouble(timeField.getText().trim());
            divYield = parseDoubleOrZero(divYieldField.getText());
        } catch(NumberFormatException ex) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        if(spot <= 0 || strike <= 0 || time <= 0
                || rate <= -0.2 || rate >= 1.0
                || vol  <=  0.0 || vol  >= 5.0
                || divYield < 0  || divYield >= 1.0) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        DividendSchedule divs = divYield == 0.0
                ? DividendSchedule.NONE
                : DividendSchedule.continuous(divYield);

        try {
            EuropeanOption option = EuropeanOption.of(type, strike, time);
            PricingResult result = BlackScholesEngine.price(option, spot, rate, vol, divs);
            showResult(result.toString());

            Greeks g = GreeksCalculator.compute(option, spot, rate, vol, divs);
            greeksPanel.setGreeks(g);

            payoffChart.setInputs(spot, strike, rate, vol, time, type);
        } catch(IllegalArgumentException ex) {
            showResult(INVALID_INPUT_MSG);
        }
    }


    private void onSolveIv() {
        OptionType type = (OptionType) optionTypeCombo.getSelectedItem();
        double spot, strike, rate, time, divYield, marketPrice;
        try {
            spot        = Double.parseDouble(spotField.getText().trim());
            strike      = Double.parseDouble(strikeField.getText().trim());
            rate        = Double.parseDouble(rateField.getText().trim());
            time        = Double.parseDouble(timeField.getText().trim());
            divYield    = parseDoubleOrZero(divYieldField.getText());
            marketPrice = Double.parseDouble(marketPriceField.getText().trim());
        } catch(NumberFormatException ex) {
            ivResultLabel.setForeground(new java.awt.Color(0xFB923C));
            ivResultLabel.setText("Enter spot, strike, rate, time, and a numeric market price");
            return;
        }

        DividendSchedule divs = divYield == 0.0
                ? DividendSchedule.NONE
                : DividendSchedule.continuous(divYield);

        try {
            EuropeanOption option = EuropeanOption.of(type, strike, time);
            double iv = ImpliedVolatility.impliedVolatility(option, spot, rate, marketPrice, divs);
            ivResultLabel.setForeground(new java.awt.Color(0xB8BCC9));
            ivResultLabel.setText(String.format("Implied σ = %.4f  (%.2f%%)", iv, iv * 100.0));
        } catch(IllegalArgumentException ex) {
            ivResultLabel.setForeground(new java.awt.Color(0xFB923C));
            ivResultLabel.setText(ex.getMessage());
        }
    }


    private static double parseDoubleOrZero(String s) {
        if(s == null) return 0.0;
        String t = s.trim();
        if(t.isEmpty()) return 0.0;
        return Double.parseDouble(t);
    }


    private void showResult(String text) {
        resultLabel.setText(text);
        resultLabel.setVisible(true);
    }
}

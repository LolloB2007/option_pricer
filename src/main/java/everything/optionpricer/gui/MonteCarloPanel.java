package everything.optionpricer.gui;

import com.formdev.flatlaf.FlatClientProperties;
import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.AsianOption;
import everything.optionpricer.model.BarrierOption;
import everything.optionpricer.model.LookbackOption;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.LongstaffSchwartzEngine;
import everything.optionpricer.pricing.MonteCarloEngine;

import javax.swing.*;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import net.miginfocom.swing.MigLayout;


/**
 * Monte Carlo pricing page — Asian, Barrier, Lookback (path-dependent MC)
 * plus American (Longstaff–Schwartz).
 *
 * @author lorenzobarbagelata
 */
public class MonteCarloPanel extends JPanel {

    private static final String INVALID_INPUT_MSG = "Please enter valid values in inputs";

    private static final String CARD_ASIAN    = "ASIAN";
    private static final String CARD_BARRIER  = "BARRIER";
    private static final String CARD_LOOKBACK = "LOOKBACK";
    private static final String CARD_AMERICAN = "AMERICAN";

    // Common inputs
    private JComboBox<OptionType> optionTypeCombo;
    private JTextField spotField, strikeField, rateField, volField, timeField, stepsField;

    // Method picker (segmented)
    private JToggleButton segAsian, segBarrier, segLookback, segAmerican;

    // Asian-specific
    private JComboBox<String> asianAverageCombo;
    private JComboBox<String> asianMonitoringCombo;

    // Barrier-specific
    private JTextField barrierField;
    private JComboBox<String> barrierDirCombo;
    private JComboBox<String> barrierInOutCombo;
    private JComboBox<String> barrierMonitoringCombo;

    // Lookback-specific
    private JComboBox<String> lookbackStrikeCombo;
    private JComboBox<String> lookbackMonitoringCombo;

    // American-specific
    private JTextField americanExerciseField;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private JButton priceButton;
    private ResultPill resultLabel;
    private QuantileChartPanel chart;


    public MonteCarloPanel() {
        setLayout(new MigLayout(
                "fillx, insets 24 28 24 28",
                "[grow, fill]",
                "[]14[]14[]14[]10[]10[]14[]"
        ));
        componentInit();

        add(Theme.title("Monte Carlo"), "wrap");
        add(Theme.subtitle("Path-dependent and American options"), "wrap, gapbottom 4");

        add(buildSegmented(), "growx, wrap");
        add(buildCommonInputs(), "growx, wrap");
        add(cardPanel, "growx, wrap");

        add(priceButton, "alignx center, wrap, gaptop 2");
        add(resultLabel, "alignx center, wrap, gaptop 2");
        add(chart, "grow, push, gaptop 8");
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());

        spotField   = field("100");
        strikeField = field("100");
        rateField   = field("0.05");
        volField    = field("0.20");
        timeField   = field("1.00");
        stepsField  = field("252");

        // Segmented method picker
        ButtonGroup grp = new ButtonGroup();
        segAsian    = makeSegment("Asian",    grp, CARD_ASIAN);
        segBarrier  = makeSegment("Barrier",  grp, CARD_BARRIER);
        segLookback = makeSegment("Lookback", grp, CARD_LOOKBACK);
        segAmerican = makeSegment("American", grp, CARD_AMERICAN);
        segAsian.setSelected(true);

        asianAverageCombo    = new JComboBox<>(new String[] { "Arithmetic", "Geometric" });
        asianMonitoringCombo = new JComboBox<>(new String[] { "Discrete", "Continuous" });

        barrierField           = field("120");
        barrierDirCombo        = new JComboBox<>(new String[] { "Up", "Down" });
        barrierInOutCombo      = new JComboBox<>(new String[] { "Out", "In" });
        barrierMonitoringCombo = new JComboBox<>(new String[] { "Discrete", "Continuous" });

        lookbackStrikeCombo     = new JComboBox<>(new String[] { "Fixed strike", "Floating strike" });
        lookbackMonitoringCombo = new JComboBox<>(new String[] { "Discrete", "Continuous" });

        americanExerciseField = field("50");

        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.setOpaque(false);
        cardPanel.add(buildAsianCard(),    CARD_ASIAN);
        cardPanel.add(buildBarrierCard(),  CARD_BARRIER);
        cardPanel.add(buildLookbackCard(), CARD_LOOKBACK);
        cardPanel.add(buildAmericanCard(), CARD_AMERICAN);

        priceButton = new JButton("Price option");
        Theme.stylePrimary(priceButton);
        priceButton.addActionListener(e -> onPrice());

        resultLabel = new ResultPill();
        chart       = new QuantileChartPanel();
    }


    private JToggleButton makeSegment(String label, ButtonGroup grp, String cardKey) {
        JToggleButton b = new JToggleButton(label);
        Theme.styleSegment(b);
        grp.add(b);
        b.addActionListener(e -> cardLayout.show(cardPanel, cardKey));
        return b;
    }


    private JPanel buildSegmented() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(segAsian);
        row.add(segBarrier);
        row.add(segLookback);
        row.add(segAmerican);
        return row;
    }


    private Card buildCommonInputs() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]",
                "[]10[]10[]10[]"
        ));
        card.add(Theme.formLabel("Option type")); card.add(optionTypeCombo);
        card.add(new JLabel());                   card.add(Theme.formLabel("Time steps"));   card.add(stepsField,   "wrap");
        card.add(Theme.formLabel("Spot (S)"));    card.add(spotField);
        card.add(new JLabel());                   card.add(Theme.formLabel("Strike (K)"));   card.add(strikeField,  "wrap");
        card.add(Theme.formLabel("Rate (r)"));    card.add(rateField);
        card.add(new JLabel());                   card.add(Theme.formLabel("Volatility (σ)")); card.add(volField,    "wrap");
        card.add(Theme.formLabel("Time (T, yrs)")); card.add(timeField, "span 4, wrap");
        return card;
    }


    private Card buildAsianCard() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]",
                "[]10[]"
        ));
        card.add(Theme.formLabel("Average"));    card.add(asianAverageCombo);
        card.add(new JLabel());                  card.add(Theme.formLabel("Monitoring")); card.add(asianMonitoringCombo, "wrap");
        return card;
    }

    private Card buildBarrierCard() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]",
                "[]10[]10[]"
        ));
        card.add(Theme.formLabel("Barrier (B)")); card.add(barrierField);
        card.add(new JLabel());                   card.add(Theme.formLabel("Direction"));  card.add(barrierDirCombo,   "wrap");
        card.add(Theme.formLabel("In / Out"));    card.add(barrierInOutCombo);
        card.add(new JLabel());                   card.add(Theme.formLabel("Monitoring")); card.add(barrierMonitoringCombo, "wrap");
        return card;
    }

    private Card buildLookbackCard() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]",
                "[]"
        ));
        card.add(Theme.formLabel("Strike type")); card.add(lookbackStrikeCombo);
        card.add(new JLabel());                   card.add(Theme.formLabel("Monitoring")); card.add(lookbackMonitoringCombo, "wrap");
        return card;
    }

    private Card buildAmericanCard() {
        Card card = new Card(new MigLayout("fillx, insets 0", "[120!][grow, fill]", "[]"));
        card.add(Theme.formLabel("Exercise dates"));
        card.add(americanExerciseField, "wrap");
        return card;
    }


    private static JTextField field(String placeholder) {
        JTextField f = new JTextField(8);
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        return f;
    }


    private String currentMethod() {
        if(segAsian.isSelected())    return "Asian";
        if(segBarrier.isSelected())  return "Barrier";
        if(segLookback.isSelected()) return "Lookback";
        return "American";
    }


    private void onPrice() {
        resultLabel.setVisible(false);
        chart.clear();

        double spot, strike, rate, vol, time;
        int steps;
        try {
            spot   = Double.parseDouble(spotField.getText().trim());
            strike = Double.parseDouble(strikeField.getText().trim());
            rate   = Double.parseDouble(rateField.getText().trim());
            vol    = Double.parseDouble(volField.getText().trim());
            time   = Double.parseDouble(timeField.getText().trim());
            steps  = Integer.parseInt(stepsField.getText().trim());
        } catch(NumberFormatException ex) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        if(spot <= 0 || strike <= 0 || time <= 0 || steps < 2
                || rate <= -0.2 || rate >= 1.0
                || vol  <=  0.0 || vol  >= 5.0) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        OptionType type = (OptionType) optionTypeCombo.getSelectedItem();
        chart.setInputs(spot, strike, rate, vol, time, type);

        try {
            PricingResult result;
            switch(currentMethod()) {
                case "Asian":    result = priceAsian(type, strike, time, steps, spot, rate, vol);    break;
                case "Barrier":  result = priceBarrier(type, strike, time, steps, spot, rate, vol);  break;
                case "Lookback": result = priceLookback(type, strike, time, steps, spot, rate, vol); break;
                case "American": result = priceAmerican(type, strike, time, spot, rate, vol);        break;
                default: throw new IllegalStateException("unknown method");
            }
            showResult(result.toString());
        } catch(IllegalArgumentException ex) {
            showResult(INVALID_INPUT_MSG);
        }
    }


    private PricingResult priceAsian(OptionType type, double strike, double time, int steps,
                                     double spot, double rate, double vol) {
        boolean discrete = "Discrete".equals(asianMonitoringCombo.getSelectedItem());
        boolean arithmetic = "Arithmetic".equals(asianAverageCombo.getSelectedItem());
        AsianOption opt = new AsianOption(strike, time, type, steps, discrete, arithmetic);
        return MonteCarloEngine.price(opt, spot, rate, vol);
    }

    private PricingResult priceBarrier(OptionType type, double strike, double time, int steps,
                                       double spot, double rate, double vol) {
        double barrier  = Double.parseDouble(barrierField.getText().trim());
        boolean up      = "Up".equals(barrierDirCombo.getSelectedItem());
        boolean in      = "In".equals(barrierInOutCombo.getSelectedItem());
        boolean discrete = "Discrete".equals(barrierMonitoringCombo.getSelectedItem());
        BarrierOption opt = new BarrierOption(strike, time, type, steps, discrete, barrier, up, in);
        return MonteCarloEngine.price(opt, spot, rate, vol);
    }

    private PricingResult priceLookback(OptionType type, double strike, double time, int steps,
                                        double spot, double rate, double vol) {
        boolean fixedStrike = "Fixed strike".equals(lookbackStrikeCombo.getSelectedItem());
        boolean discrete    = "Discrete".equals(lookbackMonitoringCombo.getSelectedItem());
        LookbackOption opt = new LookbackOption(strike, time, type, steps, discrete, fixedStrike);
        return MonteCarloEngine.price(opt, spot, rate, vol);
    }

    private PricingResult priceAmerican(OptionType type, double strike, double time,
                                        double spot, double rate, double vol) {
        int dates = Integer.parseInt(americanExerciseField.getText().trim());
        AmericanOption opt = new AmericanOption(strike, time, type, dates);
        return LongstaffSchwartzEngine.price(opt, spot, rate, vol);
    }


    private void showResult(String text) {
        resultLabel.setText(text);
        resultLabel.setVisible(true);
    }
}

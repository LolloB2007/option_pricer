package everything.optionpricer.gui;

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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import net.miginfocom.swing.MigLayout;


/**
 * Monte Carlo pricing page — Asian, Barrier, Lookback (path-dependent MC)
 * plus American (Longstaff–Schwartz). Common GBM inputs at the top, a
 * method-specific card panel in the middle, and the underlying-price
 * 5/50/95% percentile chart underneath.
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
    private JComboBox<String> methodCombo;
    private JTextField spotField, strikeField, rateField, volField, timeField, stepsField;

    // Asian-specific
    private JComboBox<String> asianAverageCombo;     // arithmetic / geometric
    private JComboBox<String> asianMonitoringCombo;  // discrete / continuous

    // Barrier-specific
    private JTextField barrierField;
    private JComboBox<String> barrierDirCombo;       // up / down
    private JComboBox<String> barrierInOutCombo;     // in / out
    private JComboBox<String> barrierMonitoringCombo;

    // Lookback-specific
    private JComboBox<String> lookbackStrikeCombo;   // fixed / floating
    private JComboBox<String> lookbackMonitoringCombo;

    // American-specific
    private JTextField americanExerciseField;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private JButton priceButton;
    private JLabel resultLabel;
    private QuantileChartPanel chart;


    public MonteCarloPanel() {
        setLayout(new MigLayout(
                "fillx, insets 14",
                "[right]10[grow, fill]",
                ""
        ));

        componentInit();

        add(new JLabel("Monte Carlo pricer — % as decimals, time in years",
                SwingConstants.CENTER), "span 2, growx, wrap, gapbottom 6");

        add(new JLabel("Method:"));         add(methodCombo,      "wrap");
        add(new JLabel("Option Type:"));    add(optionTypeCombo,  "wrap");
        add(new JLabel("Spot Price (S):"));     add(spotField,    "wrap");
        add(new JLabel("Strike Price (K):"));   add(strikeField,  "wrap");
        add(new JLabel("Risk-Free Rate (r):")); add(rateField,    "wrap");
        add(new JLabel("Volatility (σ):"));     add(volField,     "wrap");
        add(new JLabel("Time to Expiry (T):")); add(timeField,    "wrap");
        add(new JLabel("Time Steps:"));         add(stepsField,   "wrap, gapbottom 8");

        add(cardPanel, "span 2, growx, wrap, gapbottom 8");
        add(priceButton, "span 2, center, wrap");
        add(resultLabel, "span 2, growx, alignx center, wrap, gaptop 4");
        add(chart, "span 2, grow, push, gaptop 8");
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());
        methodCombo = new JComboBox<>(new String[] { "Asian", "Barrier", "Lookback", "American" });
        methodCombo.addActionListener(e -> cardLayout.show(cardPanel, currentCardKey()));

        spotField   = new JTextField("100", 8);
        strikeField = new JTextField("100", 8);
        rateField   = new JTextField("0.05", 8);
        volField    = new JTextField("0.20", 8);
        timeField   = new JTextField("1.00", 8);
        stepsField  = new JTextField("252", 8);

        // Asian card
        asianAverageCombo    = new JComboBox<>(new String[] { "Arithmetic", "Geometric" });
        asianMonitoringCombo = new JComboBox<>(new String[] { "Discrete", "Continuous" });

        // Barrier card
        barrierField           = new JTextField("120", 8);
        barrierDirCombo        = new JComboBox<>(new String[] { "Up", "Down" });
        barrierInOutCombo      = new JComboBox<>(new String[] { "Out", "In" });
        barrierMonitoringCombo = new JComboBox<>(new String[] { "Discrete", "Continuous" });

        // Lookback card
        lookbackStrikeCombo     = new JComboBox<>(new String[] { "Fixed strike", "Floating strike" });
        lookbackMonitoringCombo = new JComboBox<>(new String[] { "Discrete", "Continuous" });

        // American card
        americanExerciseField = new JTextField("50", 8);

        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.add(buildAsianCard(),    CARD_ASIAN);
        cardPanel.add(buildBarrierCard(),  CARD_BARRIER);
        cardPanel.add(buildLookbackCard(), CARD_LOOKBACK);
        cardPanel.add(buildAmericanCard(), CARD_AMERICAN);

        priceButton = new JButton("Price option");
        priceButton.addActionListener(e -> onPrice());

        resultLabel = new JLabel("", SwingConstants.CENTER);
        resultLabel.setVisible(false);

        chart = new QuantileChartPanel();
    }


    // ----- Cards ----- //

    private JPanel buildAsianCard() {
        JPanel p = newCard();
        addRow(p, 0, "Average:",    asianAverageCombo);
        addRow(p, 1, "Monitoring:", asianMonitoringCombo);
        return p;
    }

    private JPanel buildBarrierCard() {
        JPanel p = newCard();
        addRow(p, 0, "Barrier (B):", barrierField);
        addRow(p, 1, "Direction:",   barrierDirCombo);
        addRow(p, 2, "In/Out:",      barrierInOutCombo);
        addRow(p, 3, "Monitoring:",  barrierMonitoringCombo);
        return p;
    }

    private JPanel buildLookbackCard() {
        JPanel p = newCard();
        addRow(p, 0, "Strike type:", lookbackStrikeCombo);
        addRow(p, 1, "Monitoring:",  lookbackMonitoringCombo);
        return p;
    }

    private JPanel buildAmericanCard() {
        JPanel p = newCard();
        addRow(p, 0, "Exercise dates:", americanExerciseField);
        return p;
    }

    private static JPanel newCard() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Method parameters"));
        return p;
    }

    private static void addRow(JPanel parent, int row, String label, java.awt.Component field) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row; lc.anchor = GridBagConstraints.LINE_END;
        lc.insets = new java.awt.Insets(3, 6, 3, 6);
        parent.add(new JLabel(label), lc);
        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row; fc.anchor = GridBagConstraints.LINE_START;
        fc.fill = GridBagConstraints.HORIZONTAL; fc.weightx = 1.0;
        fc.insets = new java.awt.Insets(3, 0, 3, 6);
        parent.add(field, fc);
    }


    private String currentCardKey() {
        switch((String) methodCombo.getSelectedItem()) {
            case "Asian":    return CARD_ASIAN;
            case "Barrier":  return CARD_BARRIER;
            case "Lookback": return CARD_LOOKBACK;
            case "American": return CARD_AMERICAN;
            default:         return CARD_ASIAN;
        }
    }


    // ----- Pricing ----- //

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

        // Update the chart regardless of which option type — it visualises the
        // underlying's distribution, not the option payoff.
        chart.setInputs(spot, rate, vol, time);

        try {
            PricingResult result;
            switch((String) methodCombo.getSelectedItem()) {
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

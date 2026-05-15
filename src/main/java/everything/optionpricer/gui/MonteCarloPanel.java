package everything.optionpricer.gui;

import com.formdev.flatlaf.FlatClientProperties;
import everything.optionpricer.model.AmericanOption;
import everything.optionpricer.model.AsianOption;
import everything.optionpricer.model.BarrierOption;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.LookbackOption;
import everything.optionpricer.model.Option;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PathDependentOption;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.BinomialEngine;
import everything.optionpricer.pricing.FiniteDifferenceEngine;
import everything.optionpricer.pricing.Greeks;
import everything.optionpricer.pricing.GreeksCalculator;
import everything.optionpricer.pricing.ImpliedVolatility;
import everything.optionpricer.pricing.LongstaffSchwartzEngine;
import everything.optionpricer.pricing.MonteCarloEngine;
import everything.optionpricer.pricing.MultiModelPrice;
import everything.optionpricer.pricing.PricingModel;

import javax.swing.*;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import net.miginfocom.swing.MigLayout;


/**
 * Monte Carlo pricing page — Asian, Barrier, Lookback (path-dependent MC)
 * plus American (Longstaff–Schwartz). Includes Greeks via CRN finite
 * differences and a payoff diagram.
 *
 * @author lorenzobarbagelata
 */
public class MonteCarloPanel extends JPanel {

    private static final String INVALID_INPUT_MSG = "Please enter valid values in inputs";

    private static final String CARD_ASIAN    = "ASIAN";
    private static final String CARD_BARRIER  = "BARRIER";
    private static final String CARD_LOOKBACK = "LOOKBACK";
    private static final String CARD_AMERICAN = "AMERICAN";

    private JComboBox<OptionType> optionTypeCombo;
    private JTextField spotField, strikeField, rateField, volField, timeField, stepsField, divYieldField;

    private JToggleButton segAsian, segBarrier, segLookback, segAmerican;

    private JComboBox<String> asianAverageCombo, asianMonitoringCombo;
    private JTextField barrierField;
    private JComboBox<String> barrierDirCombo, barrierInOutCombo, barrierMonitoringCombo;
    private JComboBox<String> lookbackStrikeCombo, lookbackMonitoringCombo;
    private JTextField americanExerciseField;
    private JComboBox<String> americanModelCombo;

    private JLabel modelDetailLabel;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private JButton priceButton;
    private ResultPill resultLabel;
    private GreeksPanel greeksPanel;
    private QuantileChartPanel quantileChart;
    private PayoffDiagramPanel payoffChart;

    // Implied vol sub-section
    private JTextField marketPriceField;
    private JButton    solveIvButton;
    private JLabel     ivResultLabel;


    public MonteCarloPanel() {
        setLayout(new MigLayout(
                "fillx, insets 24 28 24 28",
                "[grow, fill]",
                "[]14[]14[]14[]10[]10[]8[]14[]10[]"
        ));
        componentInit();

        add(Theme.title("Monte Carlo"), "wrap");
        add(Theme.subtitle("Path-dependent and American options"), "wrap, gapbottom 4");

        add(buildSegmented(),    "growx, wrap");
        add(buildCommonInputs(), "growx, wrap");
        add(cardPanel,           "growx, wrap");

        add(priceButton,       "alignx center, wrap, gaptop 2");
        add(resultLabel,       "alignx center, wrap, gaptop 2");
        add(modelDetailLabel,  "alignx center, wrap, gaptop 2");
        add(greeksPanel,       "growx, wrap, gaptop 2");
        add(quantileChart, "grow, push, gaptop 8");
        add(payoffChart,   "grow, push, gaptop 8");
        add(buildIvSection(), "growx, wrap, gaptop 8");
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());

        spotField     = field("100");
        strikeField   = field("100");
        rateField     = field("0.05");
        volField      = field("0.20");
        timeField     = field("1.00");
        stepsField    = field("252");
        divYieldField = field("0.00");

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
        americanModelCombo    = new JComboBox<>(new String[] { "Auto", "Longstaff-Schwartz", "Binomial", "PDE" });

        modelDetailLabel = new JLabel(" ");
        modelDetailLabel.setForeground(new java.awt.Color(0x9094A8));
        modelDetailLabel.setFont(modelDetailLabel.getFont().deriveFont(11.5f));

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

        resultLabel   = new ResultPill();
        greeksPanel   = new GreeksPanel();
        quantileChart = new QuantileChartPanel();
        payoffChart   = new PayoffDiagramPanel();

        marketPriceField = field("market price");
        solveIvButton    = new JButton("Solve σ");
        Theme.stylePrimary(solveIvButton);
        solveIvButton.addActionListener(e -> onSolveIv());
        ivResultLabel    = new JLabel(" ");
        ivResultLabel.setForeground(new java.awt.Color(0xB8BCC9));
    }


    private Card buildIvSection() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill][80!]",
                "[]6[]"));
        card.add(Theme.subtitle("Implied volatility from a market price"), "span 3, wrap");
        card.add(Theme.formLabel("Market price"));
        card.add(marketPriceField, "growx");
        card.add(solveIvButton, "growy, wrap");
        card.add(ivResultLabel, "span 3, growx, alignx center, gaptop 2");
        return card;
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
                "[]10[]10[]10[]10[]"
        ));
        card.add(Theme.formLabel("Option type")); card.add(optionTypeCombo);
        card.add(new JLabel());                   card.add(Theme.formLabel("Time steps"));     card.add(stepsField,   "wrap");
        card.add(Theme.formLabel("Spot (S)"));    card.add(spotField);
        card.add(new JLabel());                   card.add(Theme.formLabel("Strike (K)"));     card.add(strikeField,  "wrap");
        card.add(Theme.formLabel("Rate (r)"));    card.add(rateField);
        card.add(new JLabel());                   card.add(Theme.formLabel("Volatility (σ)")); card.add(volField,     "wrap");
        card.add(Theme.formLabel("Time (T, yrs)")); card.add(timeField);
        card.add(new JLabel());                   card.add(Theme.formLabel("Div yield (q)"));  card.add(divYieldField, "wrap");
        return card;
    }

    private Card buildAsianCard() {
        Card card = new Card(new MigLayout("fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]", "[]10[]"));
        card.add(Theme.formLabel("Average"));    card.add(asianAverageCombo);
        card.add(new JLabel());                  card.add(Theme.formLabel("Monitoring")); card.add(asianMonitoringCombo, "wrap");
        return card;
    }

    private Card buildBarrierCard() {
        Card card = new Card(new MigLayout("fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]", "[]10[]10[]"));
        card.add(Theme.formLabel("Barrier (B)")); card.add(barrierField);
        card.add(new JLabel());                   card.add(Theme.formLabel("Direction"));  card.add(barrierDirCombo,        "wrap");
        card.add(Theme.formLabel("In / Out"));    card.add(barrierInOutCombo);
        card.add(new JLabel());                   card.add(Theme.formLabel("Monitoring")); card.add(barrierMonitoringCombo, "wrap");
        return card;
    }

    private Card buildLookbackCard() {
        Card card = new Card(new MigLayout("fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]", "[]"));
        card.add(Theme.formLabel("Strike type")); card.add(lookbackStrikeCombo);
        card.add(new JLabel());                   card.add(Theme.formLabel("Monitoring")); card.add(lookbackMonitoringCombo, "wrap");
        return card;
    }

    private Card buildAmericanCard() {
        Card card = new Card(new MigLayout("fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]", "[]10[]"));
        card.add(Theme.formLabel("Exercise dates")); card.add(americanExerciseField);
        card.add(new JLabel());                      card.add(Theme.formLabel("Model")); card.add(americanModelCombo, "wrap");
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
        modelDetailLabel.setText(" ");
        greeksPanel.clear();
        quantileChart.clear();
        payoffChart.clear();

        double spot, strike, rate, vol, time, divYield;
        int steps;
        try {
            spot     = Double.parseDouble(spotField.getText().trim());
            strike   = Double.parseDouble(strikeField.getText().trim());
            rate     = Double.parseDouble(rateField.getText().trim());
            vol      = Double.parseDouble(volField.getText().trim());
            time     = Double.parseDouble(timeField.getText().trim());
            steps    = Integer.parseInt(stepsField.getText().trim());
            divYield = parseDoubleOrZero(divYieldField.getText());
        } catch(NumberFormatException ex) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        if(spot <= 0 || strike <= 0 || time <= 0 || steps < 2
                || rate <= -0.2 || rate >= 1.0
                || vol  <=  0.0 || vol  >= 5.0
                || divYield < 0  || divYield >= 1.0) {
            showResult(INVALID_INPUT_MSG);
            return;
        }

        OptionType type = (OptionType) optionTypeCombo.getSelectedItem();
        DividendSchedule divs = divYield == 0.0
                ? DividendSchedule.NONE
                : DividendSchedule.continuous(divYield);

        quantileChart.setInputs(spot, strike, rate, vol, time, type);
        payoffChart.setInputs(spot, strike, rate, vol, time, type);

        try {
            PricingResult result;
            Option pricedOption;
            switch(currentMethod()) {
                case "Asian": {
                    PathDependentOption opt = buildAsian(type, strike, time, steps);
                    result = MonteCarloEngine.price(opt, spot, rate, vol, divs);
                    pricedOption = opt;
                    break;
                }
                case "Barrier": {
                    PathDependentOption opt = buildBarrier(type, strike, time, steps);
                    result = MonteCarloEngine.price(opt, spot, rate, vol, divs);
                    pricedOption = opt;
                    break;
                }
                case "Lookback": {
                    PathDependentOption opt = buildLookback(type, strike, time, steps);
                    result = MonteCarloEngine.price(opt, spot, rate, vol, divs);
                    pricedOption = opt;
                    break;
                }
                case "American": {
                    AmericanOption opt = buildAmerican(type, strike, time);
                    result = priceAmericanByModel(opt, spot, rate, vol, divs);
                    pricedOption = opt;
                    break;
                }
                default: throw new IllegalStateException("unknown method");
            }
            showResult(result.toString());
            computeGreeksAsync(pricedOption, spot, rate, vol, divs);
        } catch(IllegalArgumentException ex) {
            showResult(INVALID_INPUT_MSG);
        }
    }


    /**
     * MC Greeks need ~8 re-prices — run them off the EDT so the UI stays
     * responsive. Show "…" placeholders while computing. For American the
     * chosen model is honoured.
     */
    private void computeGreeksAsync(Option option, double spot, double rate, double vol,
                                    DividendSchedule dividends) {
        greeksPanel.setComputing();
        priceButton.setEnabled(false);

        final PricingModel americanModel = (option instanceof AmericanOption) ? parseAmericanModel() : null;

        new SwingWorker<Greeks, Void>() {
            @Override protected Greeks doInBackground() {
                if(option instanceof AmericanOption am) {
                    return GreeksCalculator.compute(am, spot, rate, vol, dividends, americanModel);
                }
                return GreeksCalculator.compute(option, spot, rate, vol, dividends);
            }
            @Override protected void done() {
                priceButton.setEnabled(true);
                try {
                    greeksPanel.setGreeks(get());
                } catch(Exception e) {
                    greeksPanel.clear();
                }
            }
        }.execute();
    }


    private PricingModel parseAmericanModel() {
        return switch((String) americanModelCombo.getSelectedItem()) {
            case "Longstaff-Schwartz" -> PricingModel.LSM;
            case "Binomial"           -> PricingModel.BINOMIAL;
            case "PDE"                -> PricingModel.PDE;
            default                   -> PricingModel.AUTO;
        };
    }


    private static double parseDoubleOrZero(String s) {
        if(s == null) return 0.0;
        String t = s.trim();
        if(t.isEmpty()) return 0.0;
        return Double.parseDouble(t);
    }


    private void onSolveIv() {
        double spot, strike, rate, time, divYield, marketPrice;
        int steps;
        try {
            spot        = Double.parseDouble(spotField.getText().trim());
            strike      = Double.parseDouble(strikeField.getText().trim());
            rate        = Double.parseDouble(rateField.getText().trim());
            time        = Double.parseDouble(timeField.getText().trim());
            steps       = Integer.parseInt(stepsField.getText().trim());
            divYield    = parseDoubleOrZero(divYieldField.getText());
            marketPrice = Double.parseDouble(marketPriceField.getText().trim());
        } catch(NumberFormatException ex) {
            ivResultLabel.setForeground(new java.awt.Color(0xFB923C));
            ivResultLabel.setText("Fill the common inputs and a numeric market price");
            return;
        }

        OptionType type = (OptionType) optionTypeCombo.getSelectedItem();
        DividendSchedule divs = divYield == 0.0
                ? DividendSchedule.NONE
                : DividendSchedule.continuous(divYield);

        Option option;
        try {
            option = switch(currentMethod()) {
                case "Asian"    -> buildAsian(type, strike, time, steps);
                case "Barrier"  -> buildBarrier(type, strike, time, steps);
                case "Lookback" -> buildLookback(type, strike, time, steps);
                case "American" -> buildAmerican(type, strike, time);
                default         -> throw new IllegalStateException("unknown method");
            };
        } catch(IllegalArgumentException ex) {
            ivResultLabel.setForeground(new java.awt.Color(0xFB923C));
            ivResultLabel.setText("Invalid inputs for " + currentMethod());
            return;
        }

        // MC IV is slow (1-2s) — run off the EDT.
        ivResultLabel.setForeground(new java.awt.Color(0x9094A8));
        ivResultLabel.setText("Solving σ …");
        solveIvButton.setEnabled(false);

        final PricingModel americanModel = (option instanceof AmericanOption) ? parseAmericanModel() : null;

        new SwingWorker<Double, Void>() {
            @Override protected Double doInBackground() {
                if(option instanceof AmericanOption am) {
                    return ImpliedVolatility.impliedVolatility(am, spot, rate, marketPrice, divs, americanModel);
                }
                return ImpliedVolatility.impliedVolatility(option, spot, rate, marketPrice, divs);
            }
            @Override protected void done() {
                solveIvButton.setEnabled(true);
                try {
                    double iv = get();
                    ivResultLabel.setForeground(new java.awt.Color(0xB8BCC9));
                    ivResultLabel.setText(String.format("Implied σ = %.4f  (%.2f%%)", iv, iv * 100.0));
                } catch(Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    ivResultLabel.setForeground(new java.awt.Color(0xFB923C));
                    ivResultLabel.setText(cause.getMessage() != null ? cause.getMessage() : "Failed");
                }
            }
        }.execute();
    }


    private AsianOption buildAsian(OptionType type, double strike, double time, int steps) {
        boolean discrete = "Discrete".equals(asianMonitoringCombo.getSelectedItem());
        boolean arithmetic = "Arithmetic".equals(asianAverageCombo.getSelectedItem());
        return new AsianOption(strike, time, type, steps, discrete, arithmetic);
    }

    private BarrierOption buildBarrier(OptionType type, double strike, double time, int steps) {
        double barrier   = Double.parseDouble(barrierField.getText().trim());
        boolean up       = "Up".equals(barrierDirCombo.getSelectedItem());
        boolean in       = "In".equals(barrierInOutCombo.getSelectedItem());
        boolean discrete = "Discrete".equals(barrierMonitoringCombo.getSelectedItem());
        return new BarrierOption(strike, time, type, steps, discrete, barrier, up, in);
    }

    private LookbackOption buildLookback(OptionType type, double strike, double time, int steps) {
        boolean fixedStrike = "Fixed strike".equals(lookbackStrikeCombo.getSelectedItem());
        boolean discrete    = "Discrete".equals(lookbackMonitoringCombo.getSelectedItem());
        return new LookbackOption(strike, time, type, steps, discrete, fixedStrike);
    }

    private AmericanOption buildAmerican(OptionType type, double strike, double time) {
        int dates = Integer.parseInt(americanExerciseField.getText().trim());
        return new AmericanOption(strike, time, type, dates);
    }


    private PricingResult priceAmericanByModel(AmericanOption opt, double spot, double rate, double vol,
                                               DividendSchedule divs) {
        String choice = (String) americanModelCombo.getSelectedItem();
        switch(choice) {
            case "Longstaff-Schwartz": {
                modelDetailLabel.setText("Model: Longstaff-Schwartz (50k paths)");
                return LongstaffSchwartzEngine.price(opt, spot, rate, vol, divs);
            }
            case "Binomial": {
                modelDetailLabel.setText("Model: Binomial (CRR, 1000 steps)");
                return BinomialEngine.price(opt, spot, rate, vol, divs);
            }
            case "PDE": {
                modelDetailLabel.setText("Model: Crank-Nicolson PDE");
                return FiniteDifferenceEngine.price(opt, spot, rate, vol, divs);
            }
            default: { // Auto
                double lsm = LongstaffSchwartzEngine.price(opt, spot, rate, vol, divs).getPrice();
                double bin = BinomialEngine.price(opt, spot, rate, vol, divs).getPrice();
                double pde = FiniteDifferenceEngine.price(opt, spot, rate, vol, divs).getPrice();
                MultiModelPrice.Aggregated agg = MultiModelPrice.builder()
                        .add(PricingModel.LSM, lsm)
                        .add(PricingModel.BINOMIAL, bin)
                        .add(PricingModel.PDE, pde)
                        .build();
                modelDetailLabel.setText(String.format("Auto · LSM=%.4f · Bin=%.4f · PDE=%.4f", lsm, bin, pde));
                return new PricingResult(agg.price());
            }
        }
    }


    private void showResult(String text) {
        resultLabel.setText(text);
        resultLabel.setVisible(true);
    }
}

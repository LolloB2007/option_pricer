package everything.optionpricer.gui;

import com.formdev.flatlaf.FlatClientProperties;
import everything.optionpricer.model.DividendSchedule;
import everything.optionpricer.model.EuropeanOption;
import everything.optionpricer.model.HestonParams;
import everything.optionpricer.model.OptionType;
import everything.optionpricer.model.PricingResult;
import everything.optionpricer.pricing.BinomialEngine;
import everything.optionpricer.pricing.BlackScholesEngine;
import everything.optionpricer.pricing.FiniteDifferenceEngine;
import everything.optionpricer.pricing.Greeks;
import everything.optionpricer.pricing.GreeksCalculator;
import everything.optionpricer.pricing.HestonEngine;
import everything.optionpricer.pricing.ImpliedVolatility;
import everything.optionpricer.pricing.MultiModelPrice;
import everything.optionpricer.pricing.PricingModel;

import java.awt.CardLayout;

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
    private JComboBox<String>     modelCombo;
    private JTextField spotField, strikeField, rateField, volField, timeField, divYieldField;
    private JTextField hV0Field, hKappaField, hThetaField, hXiField, hRhoField;
    private CardLayout hestonCards;
    private JPanel hestonCardsHost;
    private JLabel modelDetailLabel;
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
        add(hestonCardsHost, "growx, wrap, gaptop 4");

        add(priceButton,    "alignx center, wrap, gaptop 4");
        add(resultLabel,    "alignx center, wrap, gaptop 4");
        add(modelDetailLabel, "alignx center, wrap, gaptop 2");
        add(greeksPanel,    "growx, wrap, gaptop 2");
        add(payoffChart,    "grow, push, gaptop 6");
        add(buildIvSection(), "growx, wrap, gaptop 8");
    }


    private void componentInit() {
        optionTypeCombo = new JComboBox<>(OptionType.values());
        modelCombo      = new JComboBox<>(new String[] { "Auto", "Black-Scholes", "Binomial", "PDE", "Heston" });
        modelCombo.addActionListener(e -> updateHestonVisibility());

        spotField     = field("e.g. 100");
        strikeField   = field("e.g. 100");
        rateField     = field("e.g. 0.05");
        volField      = field("e.g. 0.20");
        timeField     = field("e.g. 1.00");
        divYieldField = field("0.00");

        hV0Field    = field("0.04");
        hKappaField = field("2.0");
        hThetaField = field("0.04");
        hXiField    = field("0.30");
        hRhoField   = field("-0.7");

        hestonCards = new CardLayout();
        hestonCardsHost = new JPanel(hestonCards);
        hestonCardsHost.setOpaque(false);
        hestonCardsHost.add(new JPanel() {{ setOpaque(false); }}, "EMPTY");
        hestonCardsHost.add(buildHestonCard(), "HESTON");

        priceButton = new JButton("Price option");
        Theme.stylePrimary(priceButton);
        priceButton.addActionListener(e -> onPrice());

        resultLabel       = new ResultPill();
        modelDetailLabel  = new JLabel(" ");
        modelDetailLabel.setForeground(new java.awt.Color(0x9094A8));
        modelDetailLabel.setFont(modelDetailLabel.getFont().deriveFont(11.5f));
        greeksPanel  = new GreeksPanel();
        payoffChart  = new PayoffDiagramPanel();

        marketPriceField = field("market price");
        solveIvButton    = new JButton("Solve σ");
        Theme.stylePrimary(solveIvButton);
        solveIvButton.addActionListener(e -> onSolveIv());
        ivResultLabel    = new JLabel(" ");
        ivResultLabel.setForeground(new java.awt.Color(0xB8BCC9));
    }


    private void updateHestonVisibility() {
        hestonCards.show(hestonCardsHost, "Heston".equals(modelCombo.getSelectedItem()) ? "HESTON" : "EMPTY");
        hestonCardsHost.revalidate();
        hestonCardsHost.repaint();
    }


    private Card buildInputs() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill]",
                "[]10[]10[]10[]10[]10[]10[]10[]10[]"));
        card.add(Theme.formLabel("Option type"));    card.add(optionTypeCombo, "wrap");
        card.add(Theme.formLabel("Model"));          card.add(modelCombo,      "wrap");
        card.add(Theme.formLabel("Spot (S)"));       card.add(spotField,       "wrap");
        card.add(Theme.formLabel("Strike (K)"));     card.add(strikeField,     "wrap");
        card.add(Theme.formLabel("Rate (r)"));       card.add(rateField,       "wrap");
        card.add(Theme.formLabel("Volatility (σ)")); card.add(volField,        "wrap");
        card.add(Theme.formLabel("Time (T, yrs)"));  card.add(timeField,       "wrap");
        card.add(Theme.formLabel("Div yield (q)"));  card.add(divYieldField,   "wrap");
        return card;
    }


    private Card buildHestonCard() {
        Card card = new Card(new MigLayout(
                "fillx, insets 0",
                "[120!][grow, fill][20!][120!][grow, fill]",
                "[]6[]6[]"));
        card.add(Theme.subtitle("Heston parameters"), "span 5, wrap");
        card.add(Theme.formLabel("v₀ (init var)"));  card.add(hV0Field);
        card.add(new JLabel());                       card.add(Theme.formLabel("κ (mean rev)")); card.add(hKappaField, "wrap");
        card.add(Theme.formLabel("θ (long var)"));   card.add(hThetaField);
        card.add(new JLabel());                       card.add(Theme.formLabel("ξ (vol of vol)")); card.add(hXiField, "wrap");
        card.add(Theme.formLabel("ρ (correlation)")); card.add(hRhoField, "wrap");
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
        modelDetailLabel.setText(" ");
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

        PricingModel model = parseSelectedModel();

        try {
            EuropeanOption option = EuropeanOption.of(type, strike, time);
            double price;
            String detail = "";

            switch(model) {
                case BS -> {
                    price = BlackScholesEngine.price(option, spot, rate, vol, divs).getPrice();
                    detail = "Model: Black-Scholes";
                }
                case BINOMIAL -> {
                    price = BinomialEngine.price(option, spot, rate, vol, divs).getPrice();
                    detail = "Model: Binomial (CRR, 1000 steps)";
                }
                case PDE -> {
                    price = FiniteDifferenceEngine.price(option, spot, rate, vol, divs).getPrice();
                    detail = "Model: Crank-Nicolson PDE";
                }
                case HESTON -> {
                    HestonParams hp = parseHestonParams();
                    price = HestonEngine.price(option, spot, rate, hp, divs).getPrice();
                    detail = "Model: Heston (Fourier)";
                }
                case AUTO -> {
                    double bs  = BlackScholesEngine.price(option, spot, rate, vol, divs).getPrice();
                    double bin = BinomialEngine.price(option, spot, rate, vol, divs).getPrice();
                    double pde = FiniteDifferenceEngine.price(option, spot, rate, vol, divs).getPrice();
                    MultiModelPrice.Aggregated agg = MultiModelPrice.builder()
                            .add(PricingModel.BS, bs)
                            .add(PricingModel.BINOMIAL, bin)
                            .add(PricingModel.PDE, pde)
                            .build();
                    price = agg.price();
                    detail = String.format("Auto · BS=%.4f · Bin=%.4f · PDE=%.4f", bs, bin, pde);
                }
                default -> throw new IllegalArgumentException("Unsupported model: " + model);
            }

            showResult(String.format("Price: %.3f", price));
            modelDetailLabel.setText(detail);

            // Greeks are always closed-form for European (independent of selected pricer).
            Greeks g = GreeksCalculator.compute(option, spot, rate, vol, divs);
            greeksPanel.setGreeks(g);

            payoffChart.setInputs(spot, strike, rate, vol, time, type);
        } catch(IllegalArgumentException ex) {
            showResult(ex.getMessage() != null ? ex.getMessage() : INVALID_INPUT_MSG);
        }
    }


    private PricingModel parseSelectedModel() {
        return switch((String) modelCombo.getSelectedItem()) {
            case "Black-Scholes" -> PricingModel.BS;
            case "Binomial"      -> PricingModel.BINOMIAL;
            case "PDE"           -> PricingModel.PDE;
            case "Heston"        -> PricingModel.HESTON;
            default              -> PricingModel.AUTO;
        };
    }


    private HestonParams parseHestonParams() {
        try {
            return new HestonParams(
                    Double.parseDouble(hV0Field.getText().trim()),
                    Double.parseDouble(hKappaField.getText().trim()),
                    Double.parseDouble(hThetaField.getText().trim()),
                    Double.parseDouble(hXiField.getText().trim()),
                    Double.parseDouble(hRhoField.getText().trim()));
        } catch(NumberFormatException ex) {
            throw new IllegalArgumentException("Heston params must all be numeric");
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

package everything.optionpricer.gui;

import everything.optionpricer.model.*;
import everything.optionpricer.pricing.BlackScholesEngine;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;


/**
 * special case of JFrame. Entry point of gui
 * @author lorenzobarbagelata
 */
public class MainFrame extends JFrame {
    
    private JPanel MainPanel;
    
    private JComboBox optionTypeCombo;
    private JTextField spotField;
    private JTextField strikeField;
    private JTextField rateField;
    private JTextField volField;
    private JTextField timeField;
    
    private JButton priceButton;
    private JLabel resultLabel;
    
    
    /**
     * Constructor for MainFrame
     */
    public MainFrame() {
        MainPanel = new JPanel(new MigLayout(
                "fillx, insets 20",
                "[right]12[grow, fill]",
                "[]10[]10[]10[]10[]10[]20[]"
        ));
        
        componentInit();
        
        MainPanel.add(new JLabel("Option Type:"));
        MainPanel.add(optionTypeCombo, "wrap");

        MainPanel.add(new JLabel("Spot Price (S):"));
        MainPanel.add(spotField, "wrap");

        MainPanel.add(new JLabel("Strike Price (K):"));
        MainPanel.add(strikeField, "wrap");

        MainPanel.add(new JLabel("Risk-Free Rate (r), decimal form:"));
        MainPanel.add(rateField, "wrap");

        MainPanel.add(new JLabel("Volatility (σ), decimal form:"));
        MainPanel.add(volField, "wrap");

        MainPanel.add(new JLabel("Time to Expiry (T):"));
        MainPanel.add(timeField, "wrap");

        MainPanel.add(priceButton, "span 2, center, wrap");
        MainPanel.add(resultLabel, "span 2, growx, alignx center");
        
        this.add(MainPanel);
    }
    
    
    /**
     * Method that initialises all components of MainFrame
     */
    private void componentInit() {
        optionTypeInit();
        spotFieldInit();
        strikeFieldInit();
        rateFieldInit();
        volFieldInit();
        timeFieldInit();
        priceButtonInit();
        resultLabelInit();
    }
    
    
    /**
     * Initialises optionType JComboBox
     */
    private void optionTypeInit() {
        String[] options = {"Call", "Put"};
        optionTypeCombo = new JComboBox(options);
        optionTypeCombo.setVisible(true);
    }
    
    
    /**
     * Initialises spotField JTextField
     */
    private void spotFieldInit() {
        spotField = new JTextField(8);
        spotField.setVisible(true);
    }
    
    
    /**
     * Initialises strikeField JTextField
     */
    private void strikeFieldInit() {
        strikeField = new JTextField(8);
        strikeField.setVisible(true);
    }
    
    
    /**
     * Initialises rateField JTextField
     */
    private void rateFieldInit() {
        rateField = new JTextField(8);
        rateField.setVisible(true);
    }
    
    
    /**
     * Initialises volField JTextField
     */
    private void volFieldInit() {
        volField = new JTextField(8);
        volField.setVisible(true);
    }
    
    
    /**
     * Initialises timeField JTextField
     */
    private void timeFieldInit() {
        timeField = new JTextField(8);
        timeField.setVisible(true);
    }
    
    
    /**
     * Initialises priceButton JButton
     */
    private void priceButtonInit() {
        priceButton = new JButton("Price option");
        priceButton.setVisible(true);
        
        priceButton.addActionListener(e -> {
            onButtonClick();
        });
    }
    
    
    /**
     * Initialises resultLabel JLabel and sets its visibility to false
     */
    private void resultLabelInit() {
        resultLabel = new JLabel("", SwingConstants.CENTER);
        resultLabel.setVisible(false);
    }
    
    
    /**
     * Collects data from JTextFields + OptionType, sends to Engine, and displays result
     */
    private void onButtonClick() {
        String optionType = (String) optionTypeCombo.getSelectedItem();
        
        double spotPrice = Double.parseDouble(spotField.getText());
        double strikePrice = Double.parseDouble(strikeField.getText());
        double rate = Double.parseDouble(rateField.getText());
        double volatility = Double.parseDouble(volField.getText());
        double time = Double.parseDouble(timeField.getText());
        
        Option current;
        
        if(optionType.toUpperCase().equals("CALL")) {
            current = Option.call(strikePrice, time);
        } else {
            current = Option.put(strikePrice, time);
        }
        
        PricingResult price = BlackScholesEngine.price(current, spotPrice, rate, volatility);
        
        resultLabel.setText(price.toString());
        resultLabel.setVisible(true);
    }
    
}

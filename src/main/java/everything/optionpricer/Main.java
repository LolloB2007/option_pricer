package everything.optionpricer;


import everything.optionpricer.model.*;
import everything.optionpricer.pricing.*;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * entry point
 * @author lorenzobarbagelata
 */
public class Main {

    public static void main(String[] args) {
        
        Scanner input = new Scanner(System.in);
        
        System.out.println("=== Black-Scholes European Option Pricer ===");
        System.out.println("Enter rates and volatility as decimals:");
        System.out.println("ex. 6% --> 0.06; 15% --> 0.15");
        System.out.println("");
        
        try{
            System.out.print("Option type (CALL/PUT): ");
            String type = input.nextLine().trim().toUpperCase();

            System.out.print("Spot price (S): ");
            double s = input.nextDouble();

            System.out.print("Strike price (K): ");
            double k = input.nextDouble();

            System.out.print("Risk-free rate (r): ");
            double r = input.nextDouble();

            System.out.print("Volatility (sigma): ");
            double v = input.nextDouble();

            System.out.print("Time to expire in years (T): ");
            double t = input.nextDouble();

            Option option = new Option();

            if(type.equals("CALL")) {
                option = Option.call(k, t);
            } else if(type.equals("PUT")) {
                option = Option.put(k, t);
            } else {
                throw new IllegalArgumentException("Please enter a valid European Option type");
            }
            
            if(t<=0) {
                throw new IllegalArgumentException("Please enter a valid time to expiry");
            }

            PricingResult price = BlackScholesEngine.price(option, s, r, v);

            System.out.println();
            System.out.println("=== Result ===");
            System.out.print(price);
        } catch(InputMismatchException e) {
            System.out.println("Please enter a valid value");
        }
    }
}

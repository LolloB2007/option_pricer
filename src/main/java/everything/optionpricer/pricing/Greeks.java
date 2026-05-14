package everything.optionpricer.pricing;


/**
 * The five standard option sensitivities, in practitioner reporting
 * conventions:
 *
 *   delta — change in price per 1.00 change in spot   (dimensionless)
 *   gamma — change in delta per 1.00 change in spot   (1/dollar)
 *   vega  — change in price per 1% absolute vol bump  (× 0.01 of dP/dσ)
 *   theta — change in price per calendar day          (dP/dt ÷ 365)
 *   rho   — change in price per 1% absolute rate bump (× 0.01 of dP/dr)
 *
 * @author lorenzobarbagelata
 */
public record Greeks(double delta, double gamma, double vega, double theta, double rho) {}

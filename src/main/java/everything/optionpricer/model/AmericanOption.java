package everything.optionpricer.model;


/**
 * American option — early-exerciseable at any of {@code exerciseDates}
 * equally-spaced points up to and including {@code timeToExpiry}. Strictly
 * this is a Bermudan approximation; with enough dates it converges to the
 * true American value.
 *
 * @author lorenzobarbagelata
 */
public class AmericanOption extends Option {

    private final int exerciseDates;


    public AmericanOption(double strikePrice, double timeToExpiry, OptionType type, int exerciseDates) {
        super(strikePrice, timeToExpiry, type);

        if(exerciseDates < 2)
            throw new IllegalArgumentException("Need at least 2 exercise dates");

        this.exerciseDates = exerciseDates;
    }


    public int getExerciseDates() {
        return exerciseDates;
    }


    /** Intrinsic value at underlying price S. */
    public double intrinsic(double S) {
        return Math.max(getSign() * (S - getStrikePrice()), 0.0);
    }
}

package oas.dreyka.vortexdread.tornado;

import oas.dreyka.vortexdread.wind.EfScale;

import java.util.random.RandomGenerator;

/**
 * Draws the strength a new tornado will reach.
 *
 * <p>The odds are the real ones. Roughly three in five tornadoes never get past EF0, a quarter more
 * stop at EF1, and an EF5 happens about once in a thousand. A mod that rolls uniformly across the six
 * ratings makes the violent one ordinary, and once it is ordinary it stops being frightening.
 */
public final class RatingRoll {
    private RatingRoll() {
    }

    /** Share of tornadoes that reach each rating and no further, EF0 first. */
    private static final double[] SHARE = {0.600, 0.280, 0.080, 0.030, 0.008, 0.002};

    private static final double[] CUMULATIVE = cumulative();

    private static double[] cumulative() {
        double[] out = new double[SHARE.length];
        double running = 0.0;
        for (int i = 0; i < SHARE.length; i++) {
            running += SHARE[i];
            out[i] = running;
        }
        out[out.length - 1] = 1.0;
        return out;
    }

    /**
     * Rolls a rating, then a gust inside that rating's band.
     *
     * @param bias positive makes strong tornadoes common, negative keeps the sky to weak ones, zero
     *             follows the real distribution
     * @return the peak three-second gust in metres per second
     */
    public static float drawPeakWind(RandomGenerator random, double bias) {
        return windInside(rollRating(random, bias), random);
    }

    /** Rolls the rating alone, with the same bias rule. */
    public static EfScale rollRating(RandomGenerator random, double bias) {
        double u = skew(random.nextDouble(), bias);
        EfScale[] all = EfScale.values();
        for (int i = 0; i < CUMULATIVE.length; i++) {
            if (u < CUMULATIVE[i]) {
                return all[i];
            }
        }
        return EfScale.EF5;
    }

    /**
     * A gust somewhere inside a rating's band. EF5 has no ceiling in reality, so it is given the width
     * of the band below it, which puts the worst case near a hundred and ten metres a second.
     */
    public static float windInside(EfScale rating, RandomGenerator random) {
        EfScale[] all = EfScale.values();
        float floor = rating.minWind();
        float ceiling = rating.ordinal() + 1 < all.length
                ? all[rating.ordinal() + 1].minWind()
                : floor + (floor - all[rating.ordinal() - 1].minWind()) * 1.5f;
        return floor + (float) random.nextDouble() * (ceiling - floor);
    }

    /**
     * Bends a uniform draw toward one end. Monotone, so the ordering of the ratings survives, and it
     * leaves the real distribution alone at zero.
     */
    private static double skew(double u, double bias) {
        if (bias == 0.0) {
            return u;
        }
        return Math.pow(u, Math.exp(-bias));
    }
}

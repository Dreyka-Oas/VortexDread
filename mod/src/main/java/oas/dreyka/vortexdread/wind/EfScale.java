package oas.dreyka.vortexdread.wind;

/**
 * The Enhanced Fujita rating, keyed on the three-second gust in metres per second.
 *
 * <p>The floors are the real ones: 29, 38, 50, 61, 75 and 90 m/s. A Minecraft block is one metre and a
 * tick is a twentieth of a second, so a wind speed here needs no conversion beyond dividing by 20 to
 * reach blocks per tick.
 *
 * <p>The rating is derived from the wind rather than stored beside it, the way a real survey derives
 * it from the damage. Nothing in the mod carries an EF number as its own state.
 */
public enum EfScale {
    EF0(29.0f),
    EF1(38.0f),
    EF2(50.0f),
    EF3(61.0f),
    EF4(75.0f),
    EF5(90.0f);

    private final float minWind;

    EfScale(float minWind) {
        this.minWind = minWind;
    }

    /** Lowest three-second gust, in metres per second, that still rates at this level. */
    public float minWind() {
        return minWind;
    }

    /** The number a player reads, 0 through 5. */
    public int number() {
        return ordinal();
    }

    /** Translation key for the rating, as shown in a warning or a command reply. */
    public String translationKey() {
        return "vortexdread.rating." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Rates a gust. Anything under the EF0 floor still rates EF0: below that threshold there is no
     * tornado to rate, and the caller decides whether one exists at all.
     */
    public static EfScale fromWind(float metresPerSecond) {
        EfScale[] all = values();
        for (int i = all.length - 1; i > 0; i--) {
            if (metresPerSecond >= all[i].minWind) {
                return all[i];
            }
        }
        return EF0;
    }
}

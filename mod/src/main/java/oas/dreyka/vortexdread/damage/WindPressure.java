package oas.dreyka.vortexdread.damage;

/**
 * What a given wind is able to take apart.
 *
 * <p>The whole destruction model comes down to one comparison: the dynamic pressure the wind carries
 * against the resistance the block offers. Power in a wind goes with the cube of its speed, and the
 * damage scale is built on exactly that, which is why an EF4 is not twice an EF2 but many times it.
 *
 * <p>Calibrated against the vanilla resistance table rather than against pascals, because that table is
 * what a player has intuition about. An EF0 strips leaves, glass and plants. An EF2 takes the dirt and
 * the sand out from under a house. An EF4 takes the stone walls. An EF5 takes iron. Nothing takes
 * obsidian, and nothing ever takes bedrock.
 */
public final class WindPressure {
    private WindPressure() {
    }

    /** Density of air at sea level, in kilograms per cubic metre. */
    public static final double AIR_DENSITY = 1.225;

    /** Reference wind: the floor of EF0, where damage starts existing at all. */
    private static final double REFERENCE_WIND = 29.0;

    /** Resistance an EF0 wind can just about beat. Leaves are 0.2 and glass is 0.3. */
    private static final double REFERENCE_CAPACITY = 0.35;

    /** Nothing above this resistance is ever taken, whatever the wind. Obsidian sits at 1200. */
    public static final double NEVER_BREAKS = 900.0;

    /** Dynamic pressure of a wind, in pascals. Reported to addons and to the debug screen. */
    public static double dynamicPressure(double windSpeed) {
        return 0.5 * AIR_DENSITY * windSpeed * windSpeed;
    }

    /**
     * The highest block resistance this wind can pull out of the ground.
     *
     * @param scale the server's multiplier, one for the calibration above
     */
    public static double resistanceCapacity(double windSpeed, double scale) {
        if (windSpeed <= 0.0 || scale <= 0.0) {
            return 0.0;
        }
        double ratio = windSpeed / REFERENCE_WIND;
        return REFERENCE_CAPACITY * ratio * ratio * ratio * scale;
    }

    /** Whether a block of that resistance comes out under that wind. */
    public static boolean breaks(double resistance, double windSpeed, double scale) {
        if (resistance >= NEVER_BREAKS || resistance < 0.0) {
            return false;
        }
        return resistanceCapacity(windSpeed, scale) >= resistance;
    }

    /**
     * The wind needed to take a block of that resistance, which is what a warning or a wiki page wants
     * rather than the comparison itself.
     */
    public static double windNeededFor(double resistance, double scale) {
        if (resistance <= 0.0) {
            return 0.0;
        }
        if (resistance >= NEVER_BREAKS || scale <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return REFERENCE_WIND * Math.cbrt(resistance / (REFERENCE_CAPACITY * scale));
    }

    /**
     * Whether a block that has just come out is light enough to be carried rather than simply dropped.
     * A wind that only just beat the block has nothing left to lift it with.
     */
    public static boolean carries(double resistance, double windSpeed, double scale) {
        if (!breaks(resistance, windSpeed, scale)) {
            return false;
        }
        return resistanceCapacity(windSpeed, scale) >= resistance * 2.5;
    }

    /**
     * Terminal velocity of a block of that resistance in still air, in blocks per second. Heavier
     * material falls faster and is thrown less far, which is what sorts the debris field by weight the
     * way a real one is sorted.
     */
    public static double terminalVelocity(double resistance) {
        double clamped = Math.max(0.05, Math.min(resistance, 60.0));
        return 12.0 + 28.0 * Math.cbrt(clamped / 60.0);
    }
}

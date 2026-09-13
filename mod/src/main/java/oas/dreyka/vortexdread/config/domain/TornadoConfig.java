package oas.dreyka.vortexdread.config.domain;

/** The shape and the life of one funnel, once the sky has decided to make one. */
public final class TornadoConfig {
    private TornadoConfig() {
    }

    /** Core radius of the weakest tornado, in blocks. Everything else scales off this. */
    public static double coreRadiusAtEf0 = 6.0;

    /**
     * How fast the core widens with the wind. Larger tornadoes really are stronger on average, and
     * this exponent is what turns the wind speed into the width a player sees.
     */
    public static double radiusGrowthExponent = 1.5;

    /** Shortest life, in ticks. Most tornadoes last a couple of minutes and nothing more. */
    public static int lifespanMinTicks = 2400;

    /** Longest life, in ticks. The rare long tracked one runs for a quarter of an hour. */
    public static int lifespanMaxTicks = 14400;

    /** How fast the system travels over the ground, in blocks per second. */
    public static double travelSpeed = 8.0;

    /** How far the track may wander from its heading, in degrees per second. */
    public static double trackWander = 1.6;

    /** Amplitude of the turbulence riding on the analytic field, as a fraction of the local wind. */
    public static double turbulence = 0.22;

    /**
     * Height of the cloud deck above sea level, in blocks.
     *
     * <p>Absolute rather than measured from the ground under the funnel, because that is what a cloud
     * base is: one altitude over the whole storm, with the land rising and falling under it. Measured
     * from the ground instead, a funnel standing on a mountain would push its wall cloud through the
     * deck the sky is already drawing, which is the one mistake that cannot be hidden.
     *
     * <p>The default is where Photon puts its cumulus layer at stock settings, so the two meet. A pack
     * set to another altitude, or no pack at all, wants this moved with it.
     */
    public static double cloudBaseAltitude = 183.0;

    /** Shortest funnel worth drawing, in blocks, for a tornado standing high enough to have no room. */
    private static final double MINIMUM_FUNNEL = 40.0;

    /** How tall the column is for a funnel whose feet are at this height. */
    public static double funnelHeightAbove(double groundY) {
        return Math.max(MINIMUM_FUNNEL, cloudBaseAltitude - groundY);
    }

    /** Fraction of its life the funnel spends reaching down before it touches the ground. */
    public static double formingFraction = 0.12;

    /** Fraction of its life it spends thinning into a rope and dying. */
    public static double ropeOutFraction = 0.18;
}

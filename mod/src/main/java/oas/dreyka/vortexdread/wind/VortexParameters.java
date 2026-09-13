package oas.dreyka.vortexdread.wind;

/**
 * Everything the wind field needs about one tornado, in world units.
 *
 * <p>Speeds are metres per second, which is blocks per second here. Distances are blocks. The record is
 * immutable and cheap to rebuild: the entity holds its own evolving state and hands out a fresh one
 * each tick rather than letting the sampler read mutable fields across threads.
 *
 * @param centerX      world x of the axis at ground level
 * @param centerZ      world z of the axis at ground level
 * @param groundY      terrain height under the axis, where the corner flow lives
 * @param cloudBaseY   height of the wall cloud the funnel hangs from
 * @param coreRadius   radius of the core wall, where the wind peaks
 * @param peakWind     three-second gust at the core wall
 * @param translationX how fast the whole system travels east, per second
 * @param translationZ how fast the whole system travels south, per second
 * @param turbulence   amplitude of the curl noise added on top, 0 for a clean analytic field
 */
public record VortexParameters(
        double centerX,
        double centerZ,
        double groundY,
        double cloudBaseY,
        float coreRadius,
        float peakWind,
        double translationX,
        double translationZ,
        float turbulence) {

    /** Radial inflow as a fraction of the tangential wind, in the shallow layer near the ground. */
    public static final double INFLOW_RATIO = 0.55;

    /** How fast the inflow layer thins out with height, as a multiple of the funnel height. */
    public static final double INFLOW_DECAY = 6.0;

    /**
     * Decay exponent of the tangential wind outside the core. A textbook Rankine vortex uses 1, which
     * falls off faster than anything measured: real tornadoes sit between 0.5 and 0.8.
     */
    public static final double OUTER_DECAY = 0.7;

    /** Where the field is cut to zero, as a multiple of the core radius. */
    public static final double INFLUENCE_FACTOR = 8.0;

    /** How much wider the funnel is at the cloud base than at the ground. */
    public static final double FLARE = 3.0;

    /** Peak updraft at the core wall, as a fraction of the tangential peak. */
    public static final double UPDRAFT_FRACTION = 0.6;

    public VortexParameters {
        if (coreRadius <= 0.0f) {
            throw new IllegalArgumentException("core radius must be positive, got " + coreRadius);
        }
        if (cloudBaseY <= groundY) {
            throw new IllegalArgumentException("the cloud base must sit above the ground");
        }
    }

    /** Beyond this distance from the axis the field is exactly zero. */
    public double influenceRadius() {
        return coreRadius * INFLUENCE_FACTOR;
    }

    /** Height of the visible funnel, ground to wall cloud. */
    public double funnelHeight() {
        return cloudBaseY - groundY;
    }

    /** Normalised height, 0 at the ground and 1 at the cloud base, clamped outside. */
    public double heightFraction(double y) {
        double h = (y - groundY) / funnelHeight();
        return h < 0.0 ? 0.0 : (h > 1.0 ? 1.0 : h);
    }

    /**
     * Radius of the condensation funnel at a given height. A tornado is a cone standing on its point:
     * the core radius at the ground, flaring into the wall cloud above.
     */
    public double funnelRadiusAt(double y) {
        double h = heightFraction(y);
        return coreRadius * (1.0 + FLARE * h * h);
    }

    /** The rating this gust would earn from a damage survey. */
    public EfScale rating() {
        return EfScale.fromWind(peakWind);
    }

    /**
     * How far the vortex has hollowed out into two cells, 0 for a single rising column and 1 for a
     * fully hollow core with a downdraft on the axis. Past EF3 a real tornado does this, and the
     * satellite vortices it breaks into are the reason a violent one looks nothing like a strong one.
     */
    public double twoCellFraction() {
        double t = (peakWind - EfScale.EF2.minWind()) / (EfScale.EF4.minWind() - EfScale.EF2.minWind());
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }
}

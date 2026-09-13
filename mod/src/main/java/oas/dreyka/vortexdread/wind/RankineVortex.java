package oas.dreyka.vortexdread.wind;

/**
 * The analytic wind field of one tornado: a modified Rankine vortex with the three things a textbook
 * Rankine leaves out, and which are exactly what a player feels.
 *
 * <p>Solid body rotation inside the core wall, a slower than inverse decay outside it, a shallow
 * converging inflow layer near the ground, an updraft ring at the core wall, a central downdraft once
 * the vortex is violent enough to hollow out, and the translation of the whole system, which makes one
 * flank far worse than the other. No allocation, no branching on anything but geometry.
 *
 * <p>Deterministic and time independent. Turbulence is added on top by {@link TurbulentVortex}, which
 * is where the clock belongs.
 */
public final class RankineVortex {
    private RankineVortex() {
    }

    /** Below this distance from the axis there is no direction to rotate about. */
    private static final double AXIS_EPSILON = 1.0e-6;

    /** How much the surface drag robs from the tangential wind between the ground and the cloud base. */
    private static final double SURFACE_DRAG = 0.2;

    /** Width of the updraft ring, as a fraction of the core radius. */
    private static final double UPDRAFT_RING_WIDTH = 0.9;

    /** Width of the central downdraft, as a fraction of the core radius. */
    private static final double DOWNDRAFT_WIDTH = 0.45;

    /** Strength of the central downdraft at full two-cell development, against the updraft peak. */
    private static final double DOWNDRAFT_STRENGTH = 0.8;

    /** Strength of the surface corner flow, against the updraft peak. */
    private static final double CORNER_FLOW_STRENGTH = 0.8;

    /** Height of the corner flow maximum, as a fraction of the core radius. */
    private static final double CORNER_FLOW_HEIGHT = 0.15;

    /**
     * Writes the wind velocity at a world position into {@code out}, in blocks per second.
     *
     * <p>Outside the influence radius the vector is exactly zero, so a caller can add the samples of
     * several tornadoes together without any of them reaching across the map.
     */
    public static void sample(VortexParameters p, double x, double y, double z, WindVector out) {
        double dx = x - p.centerX();
        double dz = z - p.centerZ();
        double r = Math.sqrt(dx * dx + dz * dz);
        double influence = p.influenceRadius();
        if (r >= influence) {
            out.zero();
            return;
        }

        double fade = outerFade(r, influence);
        double h = p.heightFraction(y);
        double tangential = tangentialSpeed(p, r) * fade * (1.0 - SURFACE_DRAG * h);
        double radial = -VortexParameters.INFLOW_RATIO * tangential * Math.exp(-VortexParameters.INFLOW_DECAY * h);
        double vertical = verticalSpeed(p, r, y - p.groundY(), h) * fade;

        if (r < AXIS_EPSILON) {
            out.set(0.0, vertical, 0.0);
            return;
        }

        // Cyclonic: seen from above with north up, the flow turns anticlockwise, so a point due east of
        // the axis is pushed north. Unit tangent is (dz, -dx) / r, unit radial is (dx, dz) / r.
        double invR = 1.0 / r;
        double tx = dz * invR;
        double tz = -dx * invR;
        double rx = dx * invR;
        double rz = dz * invR;

        out.set(
                tangential * tx + radial * rx + p.translationX() * fade,
                vertical,
                tangential * tz + radial * rz + p.translationZ() * fade);
    }

    /**
     * Tangential wind at a distance from the axis, before the outer fade and the surface drag.
     * Linear inside the core, a slow power law outside it.
     */
    public static double tangentialSpeed(VortexParameters p, double r) {
        double rc = p.coreRadius();
        if (r <= rc) {
            return p.peakWind() * (r / rc);
        }
        return p.peakWind() * Math.pow(rc / r, VortexParameters.OUTER_DECAY);
    }

    /**
     * Vertical wind, in two pieces.
     *
     * <p>Aloft: a rising ring at the core wall, minus a sinking column on the axis whose weight grows
     * with the rating. A weak tornado is one rising tube; a violent one is a hollow ring with a
     * collapse down the middle, which is what breaks it into satellite vortices.
     *
     * <p>At the surface: the corner flow. Air runs inward along the ground, arrives at the core and has
     * nowhere left to go but up, and it turns through that corner in a layer only a fraction of the
     * core radius deep. This is where the vertical wind is fastest in a real tornado, and it is the
     * reason anything on the ground is lofted at all. A model with only the term aloft leaves the
     * debris sitting in the grass while the funnel passes over it.
     */
    private static double verticalSpeed(VortexParameters p, double r, double aboveGround, double h) {
        double rc = p.coreRadius();
        double peak = VortexParameters.UPDRAFT_FRACTION * p.peakWind();

        double ringOffset = (r - rc) / (UPDRAFT_RING_WIDTH * rc);
        double ring = Math.exp(-ringOffset * ringOffset);

        double axisOffset = r / (DOWNDRAFT_WIDTH * rc);
        double axis = Math.exp(-axisOffset * axisOffset) * p.twoCellFraction() * DOWNDRAFT_STRENGTH;

        double aloft = peak * (ring - axis) * verticalProfile(h);
        double corner = peak * CORNER_FLOW_STRENGTH * ring * cornerProfile(aboveGround, rc);
        return aloft + corner;
    }

    /**
     * The corner flow's own vertical profile: nothing right at the surface, since air cannot come out
     * of the ground, a maximum a fraction of the core radius up, and gone well before the funnel's
     * mid height.
     */
    private static double cornerProfile(double aboveGround, double coreRadius) {
        if (aboveGround <= 0.0) {
            return 0.0;
        }
        double peakHeight = CORNER_FLOW_HEIGHT * coreRadius;
        double k = aboveGround / peakHeight;
        return k * Math.exp(1.0 - k);
    }

    /**
     * How the vertical motion varies with height: killed by friction right at the surface, established
     * within the first few percent of the funnel, easing off as the air spreads into the storm above.
     */
    private static double verticalProfile(double h) {
        return (1.0 - Math.exp(-h * 8.0)) * (1.0 - 0.3 * h);
    }

    /**
     * Smooth cut to zero at the influence radius. Without it the outer power law would still be worth a
     * few blocks per second at the edge of the loaded world, and every entity in every chunk would owe
     * a sample.
     */
    private static double outerFade(double r, double influence) {
        double t = r / influence;
        return 1.0 - t * t;
    }
}

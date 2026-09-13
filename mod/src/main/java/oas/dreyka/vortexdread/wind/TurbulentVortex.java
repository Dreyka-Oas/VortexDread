package oas.dreyka.vortexdread.wind;

/**
 * The analytic vortex with turbulence on top, which is the field the rest of the mod actually uses.
 *
 * <p>The clean Rankine field alone reads as a machine: every debris on the same ring keeps the same
 * distance forever, and the funnel wall is a perfect cylinder. Real air does not do that. The noise
 * added here turns with the vortex, at the vortex's own angular rate, so the striations that appear on
 * the funnel wall wind around it the way they do on a photograph rather than crawling across a surface
 * that happens to be rotating underneath them.
 *
 * <p>The amplitude follows the local wind rather than being constant: calm air stays calm, the core
 * wall boils.
 */
public final class TurbulentVortex {
    private TurbulentVortex() {
    }

    /** Size of the largest turbulent structure, as a fraction of the core radius. */
    private static final double STRUCTURE_SCALE = 0.35;

    /** How many octaves of noise. Three carries the eye from the whole wall down to a wisp. */
    private static final int OCTAVES = 3;

    /** How much stronger the turbulence is at the ground than at the cloud base. */
    private static final double GROUND_ROUGHNESS = 2.5;

    /** Below this distance the angular rate is meaningless and the noise is sampled unrotated. */
    private static final double AXIS_EPSILON = 1.0e-3;

    private static final double TAU = Math.PI * 2.0;

    /**
     * Writes the turbulent wind velocity at a world position into {@code out}, in blocks per second.
     *
     * @param phase seconds since the vortex formed, which is what winds the striations round
     */
    public static void sample(VortexParameters p, double phase, double x, double y, double z, WindVector out) {
        RankineVortex.sample(p, x, y, z, out);
        if (p.turbulence() <= 0.0f) {
            return;
        }
        double speed = out.speed();
        if (speed <= 0.0) {
            return;
        }

        double dx = x - p.centerX();
        double dz = z - p.centerZ();
        double r = Math.sqrt(dx * dx + dz * dz);

        // Wind the sample point back by however far the flow has carried it since the vortex formed,
        // so the noise rides the air instead of sitting still in the world.
        double sx = dx;
        double sz = dz;
        if (r > AXIS_EPSILON) {
            double angularRate = RankineVortex.tangentialSpeed(p, r) / r;
            double angle = -(angularRate * phase) % TAU;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            sx = dx * cos - dz * sin;
            sz = dx * sin + dz * cos;
        }
        double sy = y - VortexParameters.UPDRAFT_FRACTION * p.peakWind() * phase * 0.15;

        double frequency = 1.0 / (p.coreRadius() * STRUCTURE_SCALE);
        WindVector noise = SCRATCH.get();
        CurlNoise.curl(sx * frequency, sy * frequency, sz * frequency, noise);

        double h = p.heightFraction(y);
        double roughness = 1.0 + (GROUND_ROUGHNESS - 1.0) * (1.0 - h);
        double amplitude = p.turbulence() * speed * roughness;

        out.add(noise.x * amplitude, noise.y * amplitude, noise.z * amplitude);
    }

    /**
     * One scratch vector per thread. The advection pool, the destruction sweep and the renderer all
     * sample this field, and a fresh vector per sample would allocate once per debris per tick.
     */
    private static final ThreadLocal<WindVector> SCRATCH = ThreadLocal.withInitial(WindVector::new);
}

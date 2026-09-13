package oas.dreyka.vortexdread.wind;

/**
 * Divergence free turbulence, taken as the curl of a noise vector potential.
 *
 * <p>Adding plain noise to a velocity field creates and destroys air: debris piles up in the places
 * where the noise happens to converge, and the eye reads that as a bug long before it reads it as
 * wind. The curl of a potential cannot do that, which is why every fluid look in film uses it.
 *
 * <p>Value noise rather than gradient noise. The field is sampled millions of times per second and the
 * difference between the two is invisible under a vortex that is already turning at eighty metres a
 * second, while the cost is not.
 */
public final class CurlNoise {
    private CurlNoise() {
    }

    /** Offset between the three potential components, so they are not the same field three times. */
    private static final double COMPONENT_OFFSET = 137.17;

    /** Finite difference step for the curl, in the same units as the sample position. */
    private static final double DELTA = 0.5;

    /**
     * Writes the curl of the noise potential at a position into {@code out}. The result is bounded by
     * roughly 1 in each component and has zero divergence up to the finite difference error.
     */
    public static void curl(double x, double y, double z, WindVector out) {
        double inv = 1.0 / (2.0 * DELTA);

        // curl(P) = (dPz/dy - dPy/dz, dPx/dz - dPz/dx, dPy/dx - dPx/dy)
        double dPzDy = (potentialZ(x, y + DELTA, z) - potentialZ(x, y - DELTA, z)) * inv;
        double dPyDz = (potentialY(x, y, z + DELTA) - potentialY(x, y, z - DELTA)) * inv;
        double dPxDz = (potentialX(x, y, z + DELTA) - potentialX(x, y, z - DELTA)) * inv;
        double dPzDx = (potentialZ(x + DELTA, y, z) - potentialZ(x - DELTA, y, z)) * inv;
        double dPyDx = (potentialY(x + DELTA, y, z) - potentialY(x - DELTA, y, z)) * inv;
        double dPxDy = (potentialX(x, y + DELTA, z) - potentialX(x, y - DELTA, z)) * inv;

        out.set(dPzDy - dPyDz, dPxDz - dPzDx, dPyDx - dPxDy);
    }

    private static double potentialX(double x, double y, double z) {
        return noise(x, y, z);
    }

    private static double potentialY(double x, double y, double z) {
        return noise(x + COMPONENT_OFFSET, y + COMPONENT_OFFSET, z - COMPONENT_OFFSET);
    }

    private static double potentialZ(double x, double y, double z) {
        return noise(x - COMPONENT_OFFSET, y + 2.0 * COMPONENT_OFFSET, z + COMPONENT_OFFSET);
    }

    /** Smooth value noise in roughly [-1, 1], one octave. */
    public static double noise(double x, double y, double z) {
        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);
        double xf = smooth(x - xi);
        double yf = smooth(y - yi);
        double zf = smooth(z - zi);

        double c000 = hash(xi, yi, zi);
        double c100 = hash(xi + 1, yi, zi);
        double c010 = hash(xi, yi + 1, zi);
        double c110 = hash(xi + 1, yi + 1, zi);
        double c001 = hash(xi, yi, zi + 1);
        double c101 = hash(xi + 1, yi, zi + 1);
        double c011 = hash(xi, yi + 1, zi + 1);
        double c111 = hash(xi + 1, yi + 1, zi + 1);

        double x00 = c000 + (c100 - c000) * xf;
        double x10 = c010 + (c110 - c010) * xf;
        double x01 = c001 + (c101 - c001) * xf;
        double x11 = c011 + (c111 - c011) * xf;
        double y0 = x00 + (x10 - x00) * yf;
        double y1 = x01 + (x11 - x01) * yf;
        return y0 + (y1 - y0) * zf;
    }

    /**
     * Several octaves of the same noise, each half the amplitude and twice the frequency. Turbulent
     * air has structure at every size, and one octave reads as a lava lamp.
     */
    public static double fbm(double x, double y, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double normaliser = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * noise(x, y, z);
            normaliser += amplitude;
            amplitude *= 0.5;
            x *= 2.0;
            y *= 2.0;
            z *= 2.0;
        }
        return sum / normaliser;
    }

    private static double smooth(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** Integer hash to [-1, 1]. Three odd primes, one final mix, no table to fall out of cache. */
    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 8388608.0 - 1.0;
    }
}

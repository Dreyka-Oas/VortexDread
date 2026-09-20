package oas.dreyka.vortexdread.atmosphere;

/**
 * Smooth deterministic noise, used to perturb the warm damp air entering at the floor.
 *
 * <p>This is the only randomness the simulation has, and everything visible above grows out of it.
 * Ground that warms perfectly evenly produces a uniform sheet with no clouds in it at all, so the noise
 * decides where the thermals are, and the physics decides what becomes of them.
 *
 * <p>Integer arithmetic throughout and no floating point in the hash, because two machines replaying
 * the same sky have to agree cell for cell. The one floating point operation is the final scaling, which
 * is exact in single precision for a value taken out of 24 bits.
 */
public final class ThermalNoise {

    /** Odd 64-bit constants from the SplitMix64 finaliser, which passes the usual avalanche tests. */
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;
    private static final long STEP_X = 0x9E3779B97F4A7C15L;
    private static final long STEP_Z = 0xD2B74407B1CE6E93L;
    private static final long STEP_T = 0xCA5A826395121157L;

    private final long seed;

    public ThermalNoise(long seed) {
        this.seed = seed;
    }

    /**
     * One octave of value noise, in [-1, 1], smooth in space and in time.
     *
     * @param x horizontal position in lattice units; one unit is one thermal across
     * @param z the other horizontal position
     * @param time position along the third axis, which makes the pattern breathe rather than sit still
     */
    public float at(float x, float z, float time) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int t0 = (int) Math.floor(time);
        float fx = fade(x - x0);
        float fz = fade(z - z0);
        float ft = fade(time - t0);

        float n000 = corner(x0, z0, t0);
        float n100 = corner(x0 + 1, z0, t0);
        float n010 = corner(x0, z0 + 1, t0);
        float n110 = corner(x0 + 1, z0 + 1, t0);
        float n001 = corner(x0, z0, t0 + 1);
        float n101 = corner(x0 + 1, z0, t0 + 1);
        float n011 = corner(x0, z0 + 1, t0 + 1);
        float n111 = corner(x0 + 1, z0 + 1, t0 + 1);

        float near = lerp(lerp(n000, n100, fx), lerp(n010, n110, fx), fz);
        float far = lerp(lerp(n001, n101, fx), lerp(n011, n111, fx), fz);
        return lerp(near, far, ft);
    }

    /**
     * Several octaves summed, each half the amplitude and twice the frequency of the one before.
     *
     * <p>One octave gives thermals that are all the same size, which reads as a pattern. Three gives a
     * few large cells with smaller ones inside them, which is what a field of cumulus looks like from
     * underneath.
     */
    public float layered(float x, float z, float time, int octaves) {
        float total = 0.0f;
        float amplitude = 1.0f;
        float normalisation = 0.0f;
        float frequency = 1.0f;
        for (int octave = 0; octave < octaves; octave++) {
            total += amplitude * at(x * frequency, z * frequency, time * frequency);
            normalisation += amplitude;
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }
        return normalisation > 0.0f ? total / normalisation : 0.0f;
    }

    private float corner(int x, int z, int t) {
        long h = seed + x * STEP_X + z * STEP_Z + t * STEP_T;
        h ^= h >>> 30;
        h *= MIX_A;
        h ^= h >>> 27;
        h *= MIX_B;
        h ^= h >>> 31;
        // The top 24 bits, mapped to [-1, 1). Taking the top rather than the bottom matters: the low bits
        // of a multiply-based mixer are the weakest, and the lowest one of all is barely mixed.
        int bits = (int) (h >>> 40);
        return bits * (2.0f / 16777216.0f) - 1.0f;
    }

    /** The quintic fade of Perlin's improved noise: zero first and second derivative at both ends. */
    private static float fade(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

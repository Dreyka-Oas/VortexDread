// The only randomness the simulation has, and everything visible above grows out of it. ThermalNoise.java is
// the twin of this file.
//
// Integer arithmetic throughout and no floating point in the hash, which is what lets it port here with no
// tolerance at all: a 64 bit multiply wraps the same way on every machine ever built, where an exponential
// does not. Unsigned rather than signed only because C leaves signed overflow undefined and Java does not;
// the bits are the same either way.

#define NOISE_MIX_A 0xBF58476D1CE4E5B9UL
#define NOISE_MIX_B 0x94D049BB133111EBUL
#define NOISE_STEP_X 0x9E3779B97F4A7C15UL
#define NOISE_STEP_Z 0xD2B74407B1CE6E93UL
#define NOISE_STEP_T 0xCA5A826395121157UL

inline float noise_corner(ulong seed, int x, int z, int t) {
    ulong h = seed + (ulong)((long) x) * NOISE_STEP_X + (ulong)((long) z) * NOISE_STEP_Z
            + (ulong)((long) t) * NOISE_STEP_T;
    h ^= h >> 30;
    h *= NOISE_MIX_A;
    h ^= h >> 27;
    h *= NOISE_MIX_B;
    h ^= h >> 31;
    // The top 24 bits, mapped to [-1, 1). The low bits of a multiply-based mixer are the weakest.
    int bits = (int) (h >> 40);
    return bits * (2.0f / 16777216.0f) - 1.0f;
}

// The quintic fade of Perlin's improved noise: zero first and second derivative at both ends.
inline float noise_fade(float t) {
    return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
}

// One octave of value noise, in [-1, 1], smooth in space and in time. The third axis is what makes the
// pattern breathe rather than sit still, and a thermal that changed every step would seed nothing.
inline float noise_at(ulong seed, float x, float z, float time) {
    int x0 = (int) floor(x);
    int z0 = (int) floor(z);
    int t0 = (int) floor(time);
    float fx = noise_fade(x - x0);
    float fz = noise_fade(z - z0);
    float ft = noise_fade(time - t0);

    float n000 = noise_corner(seed, x0, z0, t0);
    float n100 = noise_corner(seed, x0 + 1, z0, t0);
    float n010 = noise_corner(seed, x0, z0 + 1, t0);
    float n110 = noise_corner(seed, x0 + 1, z0 + 1, t0);
    float n001 = noise_corner(seed, x0, z0, t0 + 1);
    float n101 = noise_corner(seed, x0 + 1, z0, t0 + 1);
    float n011 = noise_corner(seed, x0, z0 + 1, t0 + 1);
    float n111 = noise_corner(seed, x0 + 1, z0 + 1, t0 + 1);

    // Not named near and far: some vendors' headers still carry those two as empty macros from another era.
    float thisSide = mix_linear(mix_linear(n000, n100, fx), mix_linear(n010, n110, fx), fz);
    float nextSide = mix_linear(mix_linear(n001, n101, fx), mix_linear(n011, n111, fx), fz);
    return mix_linear(thisSide, nextSide, ft);
}

// Several octaves summed, each half the amplitude and twice the frequency of the one before. One octave gives
// thermals that are all the same size, which reads as a pattern. Three gives a few large cells with smaller
// ones inside them, which is what a field of cumulus looks like from underneath.
inline float noise_layered(ulong seed, float x, float z, float time, int octaves) {
    float total = 0.0f;
    float amplitude = 1.0f;
    float normalisation = 0.0f;
    float frequency = 1.0f;
    for (int octave = 0; octave < octaves; octave++) {
        total += amplitude * noise_at(seed, x * frequency, z * frequency, time * frequency);
        normalisation += amplitude;
        amplitude *= 0.5f;
        frequency *= 2.0f;
    }
    return normalisation > 0.0f ? exact_divide(total, normalisation) : 0.0f;
}

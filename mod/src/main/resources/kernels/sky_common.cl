// Geometry, addressing and thermodynamics, shared by every stage below.
//
// The grid sizes arrive as build definitions rather than as arguments, so the compiler folds the index
// arithmetic into constants and the addressing costs one multiply-add instead of three. The program is
// rebuilt when the grid changes, which happens when a server owner edits the config and never again.
//
// Every line here is the same arithmetic as the Java class it came from, in the same order, because the
// processor path is the reference and a difference in the last place is a different sky ten steps later.
// That is also why there is no fast math flag on the build and why FP_CONTRACT is off: a fused
// multiply-add rounds once where the source asks for twice, which is more accurate and is the wrong answer.

#pragma OPENCL FP_CONTRACT OFF

#define SLAB (SIZE_X * SIZE_Z)

// Division, and the one operation on this card that will not agree with the processor on its own.
//
// OpenCL lets a single precision divide be wrong by two and a half units in the last place. That is a fair
// trade for graphics and it is fatal here: measured against Java, which rounds correctly, this card's float
// divide disagrees on a third of every hundred thousand random pairs, by one bit. One bit is all it takes,
// because the fields feed each other: condensing warms, warming lifts, lifting cools, cooling condenses. The
// bit doubles each turn of that loop and the two skies are unrecognisable inside a few simulated minutes.
//
// Two obvious ways out were measured and both are closed. The build option that asks for a correctly rounded
// divide is not advertised by CL_DEVICE_SINGLE_FP_CONFIG here, and the driver accepts it and ignores it rather
// than refusing the build, so asking is worse than useless. Double precision looked like the answer and is
// worse: this card has no fp64 divider, the driver expands one in software, and the result disagreed with Java
// on every single one of two hundred thousand pairs.
//
// So the quotient is corrected rather than requested. The fused multiply is exact by specification on both
// sides, so it recovers the remainder the divide threw away, and one Newton step on that remainder lands on
// the correctly rounded quotient. Measured over a million pairs spanning the whole exponent range, plus the
// divisors this simulation actually uses: not one disagreement.
//
// The guarantee has a floor, and it is the reason for the guard. Both operands and the quotient have to stay
// above about 4e-31, since below that the remainder itself falls into the subnormals, where this card flushes
// to zero and the correction vanishes. Outside that range the raw quotient is returned and the two paths can
// part by two and a half units in the last place of a number smaller than 1e-37, which every field in this
// simulation absorbs whole. Nothing in the physics comes within twenty five orders of magnitude of the floor:
// the fields are temperatures near three hundred, mixing ratios near a hundredth, and speeds under fifty.
inline float exact_divide(float over, float under) {
    float quotient = over / under;
    // Zero, infinity and nan included: for those the divide is already exact, and correcting a signed zero
    // would lose its sign while correcting an infinity would turn it into a nan.
    if (!isnormal(quotient) || !isnormal(over) || !isnormal(under)) {
        return quotient;
    }
    float remainder = fma(-quotient, under, over);
    return quotient + remainder / under;
}

inline int cell_index(int x, int y, int z) {
    return (y * SIZE_Z + z) * SIZE_X + x;
}

// The two horizontal axes tile and the vertical one does not. The clamp is not a safety net: the floor row
// reading itself as its own neighbour below is exactly the no flux condition a wall imposes.
inline int wrapped_index(int x, int y, int z) {
    int wx = x % SIZE_X;
    if (wx < 0) {
        wx += SIZE_X;
    }
    int wz = z % SIZE_Z;
    if (wz < 0) {
        wz += SIZE_Z;
    }
    int cy = y < 0 ? 0 : (y >= SIZE_Y ? SIZE_Y - 1 : y);
    return (cy * SIZE_Z + wz) * SIZE_X + wx;
}

inline int clamped_index(int x, int y, int z) {
    int cx = x < 0 ? 0 : (x >= SIZE_X ? SIZE_X - 1 : x);
    int cy = y < 0 ? 0 : (y >= SIZE_Y ? SIZE_Y - 1 : y);
    int cz = z < 0 ? 0 : (z >= SIZE_Z ? SIZE_Z - 1 : z);
    return (cy * SIZE_Z + cz) * SIZE_X + cx;
}

// Face 0 is the ground and face SIZE_Y is the lid. Neither lets air through and the second has no slot in
// the array at all, so both answer zero here rather than at every call site.
inline float face_velocity_y(global const float* velocityY, int x, int y, int z) {
    if (y <= 0 || y >= SIZE_Y) {
        return 0.0f;
    }
    return velocityY[cell_index(x, y, z)];
}

inline float mix_linear(float a, float b, float t) {
    return a + (b - a) * t;
}

// Trilinear sample wrapping horizontally, which is what the semi-Lagrangian backtrace wants.
inline float sample_periodic(global const float* field, float x, float y, float z) {
    int x0 = (int) floor(x);
    int y0 = (int) floor(y);
    int z0 = (int) floor(z);
    float fx = x - x0;
    float fy = y - y0;
    float fz = z - z0;

    float c00 = mix_linear(field[wrapped_index(x0, y0, z0)], field[wrapped_index(x0 + 1, y0, z0)], fx);
    float c10 = mix_linear(field[wrapped_index(x0, y0, z0 + 1)],
            field[wrapped_index(x0 + 1, y0, z0 + 1)], fx);
    float c01 = mix_linear(field[wrapped_index(x0, y0 + 1, z0)],
            field[wrapped_index(x0 + 1, y0 + 1, z0)], fx);
    float c11 = mix_linear(field[wrapped_index(x0, y0 + 1, z0 + 1)],
            field[wrapped_index(x0 + 1, y0 + 1, z0 + 1)], fx);

    return mix_linear(mix_linear(c00, c10, fz), mix_linear(c01, c11, fz), fy);
}

// The exponential, written out rather than called. The library one is specified only to within four units
// in the last place and each vendor spends that budget differently, so calling it would make the card
// disagree with the processor and with the other vendor's card. PortableMath.java is the twin of this, term
// for term, and says what the disagreement costs.

#define INVERSE_LN2 1.4426950408889634f
#define LN2_HIGH 0.693359375f
#define LN2_LOW -2.12194440e-4f

inline float portable_exp(float x) {
    int power = (int) floor(x * INVERSE_LN2 + 0.5f);
    float remainder = x - power * LN2_HIGH - power * LN2_LOW;

    float series = 1.0f / 5040.0f;
    series = 1.0f / 720.0f + remainder * series;
    series = 1.0f / 120.0f + remainder * series;
    series = 1.0f / 24.0f + remainder * series;
    series = 1.0f / 6.0f + remainder * series;
    series = 0.5f + remainder * series;
    series = 1.0f + remainder * series;
    series = 1.0f + remainder * series;

    return ldexp(series, power);
}

// Thermodynamics.java, the constants and the four functions a kernel needs. The rest of that class runs on
// the host: the Exner function and the pressure are one value per altitude row, computed once and uploaded.

#define GRAVITY 9.80665f
#define HEAT_CAPACITY 1005.0f
#define LATENT_HEAT 2.501e6f
#define KELVIN_OFFSET 273.15f

inline float virtual_potential_temperature(float potentialTemperature, float vapourRatio) {
    return potentialTemperature * (1.0f + 0.61f * vapourRatio);
}

inline float buoyancy_of(float potentialTemperature, float vapourRatio, float cloudWaterRatio,
        float ambientVirtual) {
    float virtualTemperature = virtual_potential_temperature(potentialTemperature, vapourRatio);
    return GRAVITY * (exact_divide(virtualTemperature, ambientVirtual) - 1.0f - cloudWaterRatio);
}

inline float saturation_mixing_ratio(float temperatureCelsius, float pressurePascals) {
    float exponent = exact_divide(17.67f * temperatureCelsius, temperatureCelsius + 243.5f);
    return exact_divide(380.16f * portable_exp(exponent), pressurePascals);
}

inline float saturation_slope(float temperatureCelsius, float saturation) {
    float offset = temperatureCelsius + 243.5f;
    return exact_divide(saturation * 17.67f * 243.5f, offset * offset);
}

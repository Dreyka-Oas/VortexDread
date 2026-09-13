// Debris advection: one work item per piece, one tick of drag against the wind at its own position.
//
// This is the same equation as DebrisMotion, TurbulentVortex, RankineVortex and CurlNoise on the Java
// side, written out again because a kernel cannot call into the JVM. The two are held together by the
// parity test rather than by good intentions: every constant below has a twin in the Java, and moving
// one without the other is what the test exists to catch.
//
// Double precision throughout. The processor path is the reference, it runs in double, and a kernel in
// single would disagree with it in the fourth digit after a handful of ticks, which is a debris field
// that lands somewhere else on a server than on the client watching it.

#pragma OPENCL EXTENSION cl_khr_fp64 : enable

// Contraction turned off, not for speed and not for accuracy: a fused multiply-add is the more accurate
// of the two, and the processor path does not have one. Parity is the property being bought here.
#pragma OPENCL FP_CONTRACT OFF

#define VD_INFLOW_RATIO        0.55
#define VD_INFLOW_DECAY        6.0
#define VD_OUTER_DECAY         0.7
#define VD_INFLUENCE_FACTOR    8.0
#define VD_UPDRAFT_FRACTION    0.6

#define VD_AXIS_EPSILON        1.0e-6
#define VD_SURFACE_DRAG        0.2
#define VD_UPDRAFT_RING_WIDTH  0.9
#define VD_DOWNDRAFT_WIDTH     0.45
#define VD_DOWNDRAFT_STRENGTH  0.8
#define VD_CORNER_FLOW_STRENGTH 0.8
#define VD_CORNER_FLOW_HEIGHT  0.15

#define VD_EF2_WIND            50.0f
#define VD_EF4_WIND            75.0f

#define VD_STRUCTURE_SCALE     0.35
#define VD_GROUND_ROUGHNESS    2.5
#define VD_TURBULENCE_EPSILON  1.0e-3
#define VD_TAU                 6.283185307179586

#define VD_COMPONENT_OFFSET    137.17
#define VD_CURL_DELTA          0.5

#define VD_GRAVITY             32.0
#define VD_MAX_SUBSTEPS        4
#define VD_MIN_TERMINAL        1.0

// ---- Transcendentals, written out rather than taken from the driver ----
//
// The OpenCL specification lets a double exp, log, sin or cos be off by several units in the last
// place, and says nothing at all about what that means in practice: Mesa's rusticl answers all four at
// roughly single precision on this hardware. That is a seventh digit of disagreement with the Java on
// the wind speed itself, which the turbulence term then multiplies by the age of the storm, and the two
// paths end up putting the same plank in visibly different places.
//
// So the four the field needs are written here, each with an argument reduction and a polynomial long
// enough that the remainder is below one unit in the last place. Java's own are within one unit of the
// true value, so the two agree to about two, which is the parity the test holds them to. This costs a
// few dozen multiply-adds per sample on a part that has thousands of lanes idle.

#define VD_LN2_HI  6.93147180369123816490e-01
#define VD_LN2_LO  1.90821492927058770002e-10
#define VD_INV_LN2 1.44269504088896338700e+00
#define VD_SQRT2   1.41421356237309514547e+00

/**
 * Division, refined.
 *
 * Mesa's rusticl answers a double divide on this hardware to about eight digits, which is what a float
 * divide gives and seven short of what the type promises. Every ratio in the field below goes through
 * here instead: two Newton steps on the driver's own reciprocal, each one squaring the error, which
 * lands inside a couple of units in the last place. A driver that already divides properly loses a
 * handful of multiply-adds and nothing else.
 */
inline double vd_div(double a, double b) {
    double y = 1.0 / b;
    y = y * (2.0 - b * y);
    y = y * (2.0 - b * y);
    return a * y;
}

/** Square root, refined the same way and for the same reason. One step, since it starts half as wrong. */
inline double vd_sqrt(double x) {
    if (x <= 0.0) {
        return 0.0;
    }
    double y = sqrt(x);
    return 0.5 * (y + vd_div(x, y));
}

/** 2^k for a whole k, built straight out of the exponent field so it cannot round. */
inline double vd_scale2(int k) {
    return as_double((ulong) (k + 1023) << 52);
}

inline double vd_exp(double x) {
    if (x > 709.0) {
        return INFINITY;
    }
    if (x < -745.0) {
        return 0.0;
    }
    // Cody and Waite: ln 2 split in two so that k times the high half is exact and the remainder keeps
    // every bit it started with. One term short of this and the reduction, not the polynomial, is the
    // error.
    double k = rint(x * VD_INV_LN2);
    double r = (x - k * VD_LN2_HI) - k * VD_LN2_LO;

    // |r| is at most half a ln 2, where the term after the last one below is under 1e-19.
    double p = 1.0 / 87178291200.0;
    p = p * r + 1.0 / 6227020800.0;
    p = p * r + 1.0 / 479001600.0;
    p = p * r + 1.0 / 39916800.0;
    p = p * r + 1.0 / 3628800.0;
    p = p * r + 1.0 / 362880.0;
    p = p * r + 1.0 / 40320.0;
    p = p * r + 1.0 / 5040.0;
    p = p * r + 1.0 / 720.0;
    p = p * r + 1.0 / 120.0;
    p = p * r + 1.0 / 24.0;
    p = p * r + 1.0 / 6.0;
    p = p * r + 0.5;
    p = p * r + 1.0;
    p = p * r + 1.0;
    return p * vd_scale2((int) k);
}

inline double vd_log(double x) {
    if (x <= 0.0) {
        return x == 0.0 ? -INFINITY : NAN;
    }
    ulong bits = as_ulong(x);
    int e = (int) ((bits >> 52) & 0x7FFUL) - 1023;
    double m = as_double((bits & 0x000FFFFFFFFFFFFFUL) | 0x3FF0000000000000UL);
    // Centred on one rather than taken from [1, 2), which halves the series argument and with it the
    // number of terms the tail needs.
    if (m > VD_SQRT2) {
        m *= 0.5;
        e += 1;
    }

    double s = vd_div(m - 1.0, m + 1.0);
    double z = s * s;
    double p = 1.0 / 23.0;
    p = p * z + 1.0 / 21.0;
    p = p * z + 1.0 / 19.0;
    p = p * z + 1.0 / 17.0;
    p = p * z + 1.0 / 15.0;
    p = p * z + 1.0 / 13.0;
    p = p * z + 1.0 / 11.0;
    p = p * z + 1.0 / 9.0;
    p = p * z + 1.0 / 7.0;
    p = p * z + 1.0 / 5.0;
    p = p * z + 1.0 / 3.0;
    p = p * z + 1.0;
    double ef = (double) e;
    return (ef * VD_LN2_HI + 2.0 * s * p) + ef * VD_LN2_LO;
}

#define VD_PIO2_HI 1.57079632679489655800e+00
#define VD_PIO2_MID 6.12323399573676603587e-17
#define VD_PIO2_LO 1.49827585224075007737e-33

/** Sine and cosine of the same angle, since the field never wants one without the other. */
inline double2 vd_sincos(double x) {
    double k = rint(x * (2.0 / M_PI));
    double r = ((x - k * VD_PIO2_HI) - k * VD_PIO2_MID) - k * VD_PIO2_LO;
    double z = r * r;

    double sp = -1.0 / 355687428096000.0;
    sp = sp * z + 1.0 / 1307674368000.0;
    sp = sp * z - 1.0 / 6227020800.0;
    sp = sp * z + 1.0 / 39916800.0;
    sp = sp * z - 1.0 / 362880.0;
    sp = sp * z + 1.0 / 5040.0;
    sp = sp * z - 1.0 / 120.0;
    sp = sp * z + 1.0 / 6.0;
    double sine = r - r * z * sp;

    double cp = -1.0 / 6402373705728000.0;
    cp = cp * z + 1.0 / 20922789888000.0;
    cp = cp * z - 1.0 / 87178291200.0;
    cp = cp * z + 1.0 / 479001600.0;
    cp = cp * z - 1.0 / 3628800.0;
    cp = cp * z + 1.0 / 40320.0;
    cp = cp * z - 1.0 / 720.0;
    cp = cp * z + 1.0 / 24.0;
    cp = cp * z - 0.5;
    double cosine = 1.0 + z * cp;

    // The quadrant the reduction took the angle out of, put back.
    int q = ((int) k) & 3;
    if (q == 1) {
        return (double2)(cosine, -sine);
    }
    if (q == 2) {
        return (double2)(-sine, -cosine);
    }
    if (q == 3) {
        return (double2)(-cosine, sine);
    }
    return (double2)(sine, cosine);
}

// Java truncates toward zero and then steps down for a negative, which is not what any library floor
// does. Reproduced rather than replaced: one lattice cell of disagreement is a different noise value.
inline int vd_floor(double v) {
    int i = (int) v;
    return v < (double) i ? i - 1 : i;
}

inline double vd_hash(int x, int y, int z) {
    uint h = (uint) x * 374761393u + (uint) y * 668265263u + (uint) z * 1274126177u;
    h = (h ^ (h >> 13)) * 1274126177u;
    h ^= h >> 16;
    return (double) (h & 0xFFFFFFu) / 8388608.0 - 1.0;
}

inline double vd_smooth(double t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

inline double vd_noise(double x, double y, double z) {
    int xi = vd_floor(x);
    int yi = vd_floor(y);
    int zi = vd_floor(z);
    double xf = vd_smooth(x - xi);
    double yf = vd_smooth(y - yi);
    double zf = vd_smooth(z - zi);

    double c000 = vd_hash(xi, yi, zi);
    double c100 = vd_hash(xi + 1, yi, zi);
    double c010 = vd_hash(xi, yi + 1, zi);
    double c110 = vd_hash(xi + 1, yi + 1, zi);
    double c001 = vd_hash(xi, yi, zi + 1);
    double c101 = vd_hash(xi + 1, yi, zi + 1);
    double c011 = vd_hash(xi, yi + 1, zi + 1);
    double c111 = vd_hash(xi + 1, yi + 1, zi + 1);

    double x00 = c000 + (c100 - c000) * xf;
    double x10 = c010 + (c110 - c010) * xf;
    double x01 = c001 + (c101 - c001) * xf;
    double x11 = c011 + (c111 - c011) * xf;
    double y0 = x00 + (x10 - x00) * yf;
    double y1 = x01 + (x11 - x01) * yf;
    return y0 + (y1 - y0) * zf;
}

inline double vd_potential_x(double x, double y, double z) {
    return vd_noise(x, y, z);
}

inline double vd_potential_y(double x, double y, double z) {
    return vd_noise(x + VD_COMPONENT_OFFSET, y + VD_COMPONENT_OFFSET, z - VD_COMPONENT_OFFSET);
}

inline double vd_potential_z(double x, double y, double z) {
    return vd_noise(x - VD_COMPONENT_OFFSET, y + 2.0 * VD_COMPONENT_OFFSET, z + VD_COMPONENT_OFFSET);
}

inline double3 vd_curl(double x, double y, double z) {
    double inv = 1.0 / (2.0 * VD_CURL_DELTA);
    double dPzDy = (vd_potential_z(x, y + VD_CURL_DELTA, z) - vd_potential_z(x, y - VD_CURL_DELTA, z)) * inv;
    double dPyDz = (vd_potential_y(x, y, z + VD_CURL_DELTA) - vd_potential_y(x, y, z - VD_CURL_DELTA)) * inv;
    double dPxDz = (vd_potential_x(x, y, z + VD_CURL_DELTA) - vd_potential_x(x, y, z - VD_CURL_DELTA)) * inv;
    double dPzDx = (vd_potential_z(x + VD_CURL_DELTA, y, z) - vd_potential_z(x - VD_CURL_DELTA, y, z)) * inv;
    double dPyDx = (vd_potential_y(x + VD_CURL_DELTA, y, z) - vd_potential_y(x - VD_CURL_DELTA, y, z)) * inv;
    double dPxDy = (vd_potential_x(x, y + VD_CURL_DELTA, z) - vd_potential_x(x, y - VD_CURL_DELTA, z)) * inv;
    return (double3)(dPzDy - dPyDz, dPxDz - dPzDx, dPyDx - dPxDy);
}

typedef struct {
    double centerX;
    double centerZ;
    double groundY;
    double cloudBaseY;
    double coreRadius;
    double peakWind;
    double translationX;
    double translationZ;
    double turbulence;
} vd_vortex;

inline double vd_height_fraction(const vd_vortex* p, double y) {
    double h = vd_div(y - p->groundY, p->cloudBaseY - p->groundY);
    return h < 0.0 ? 0.0 : (h > 1.0 ? 1.0 : h);
}

// The Java reads this ratio off two floats and divides them as floats before it widens the answer, so
// the same three casts are here. Doing it in double instead moves the last digits of every violent
// tornado's downdraft.
inline double vd_two_cell_fraction(const vd_vortex* p) {
    float t = ((float) p->peakWind - VD_EF2_WIND) / (VD_EF4_WIND - VD_EF2_WIND);
    if (t <= 0.0f) {
        return 0.0;
    }
    if (t >= 1.0f) {
        return 1.0;
    }
    double d = (double) t;
    return d * d * (3.0 - 2.0 * d);
}

inline double vd_tangential_speed(const vd_vortex* p, double r) {
    double rc = p->coreRadius;
    if (r <= rc) {
        return p->peakWind * vd_div(r, rc);
    }
    return p->peakWind * vd_exp(VD_OUTER_DECAY * vd_log(vd_div(rc, r)));
}

inline double vd_corner_profile(double aboveGround, double coreRadius) {
    if (aboveGround <= 0.0) {
        return 0.0;
    }
    double peakHeight = VD_CORNER_FLOW_HEIGHT * coreRadius;
    double k = vd_div(aboveGround, peakHeight);
    return k * vd_exp(1.0 - k);
}

inline double vd_vertical_profile(double h) {
    return (1.0 - vd_exp(-h * 8.0)) * (1.0 - 0.3 * h);
}

inline double vd_vertical_speed(const vd_vortex* p, double r, double aboveGround, double h) {
    double rc = p->coreRadius;
    double peak = VD_UPDRAFT_FRACTION * p->peakWind;

    double ringOffset = vd_div(r - rc, VD_UPDRAFT_RING_WIDTH * rc);
    double ring = vd_exp(-ringOffset * ringOffset);

    double axisOffset = vd_div(r, VD_DOWNDRAFT_WIDTH * rc);
    double axis = vd_exp(-axisOffset * axisOffset) * vd_two_cell_fraction(p) * VD_DOWNDRAFT_STRENGTH;

    double aloft = peak * (ring - axis) * vd_vertical_profile(h);
    double corner = peak * VD_CORNER_FLOW_STRENGTH * ring * vd_corner_profile(aboveGround, rc);
    return aloft + corner;
}

inline double3 vd_rankine(const vd_vortex* p, double x, double y, double z) {
    double dx = x - p->centerX;
    double dz = z - p->centerZ;
    double r = vd_sqrt(dx * dx + dz * dz);
    double influence = p->coreRadius * VD_INFLUENCE_FACTOR;
    if (r >= influence) {
        return (double3)(0.0, 0.0, 0.0);
    }

    double t = vd_div(r, influence);
    double fade = 1.0 - t * t;
    double h = vd_height_fraction(p, y);
    double tangential = vd_tangential_speed(p, r) * fade * (1.0 - VD_SURFACE_DRAG * h);
    double radial = -VD_INFLOW_RATIO * tangential * vd_exp(-VD_INFLOW_DECAY * h);
    double vertical = vd_vertical_speed(p, r, y - p->groundY, h) * fade;

    if (r < VD_AXIS_EPSILON) {
        return (double3)(0.0, vertical, 0.0);
    }

    double invR = vd_div(1.0, r);
    double tx = dz * invR;
    double tz = -dx * invR;
    double rx = dx * invR;
    double rz = dz * invR;

    return (double3)(
            tangential * tx + radial * rx + p->translationX * fade,
            vertical,
            tangential * tz + radial * rz + p->translationZ * fade);
}

inline double3 vd_wind(const vd_vortex* p, double phase, double x, double y, double z) {
    double3 w = vd_rankine(p, x, y, z);
    if (p->turbulence <= 0.0) {
        return w;
    }
    double speed = vd_sqrt(w.x * w.x + w.y * w.y + w.z * w.z);
    if (speed <= 0.0) {
        return w;
    }

    double dx = x - p->centerX;
    double dz = z - p->centerZ;
    double r = vd_sqrt(dx * dx + dz * dz);

    double sx = dx;
    double sz = dz;
    if (r > VD_TURBULENCE_EPSILON) {
        double angularRate = vd_div(vd_tangential_speed(p, r), r);
        double angle = fmod(-(angularRate * phase), VD_TAU);
        double2 turn = vd_sincos(angle);
        double c = turn.y;
        double s = turn.x;
        sx = dx * c - dz * s;
        sz = dx * s + dz * c;
    }
    double sy = y - VD_UPDRAFT_FRACTION * p->peakWind * phase * 0.15;

    double frequency = vd_div(1.0, p->coreRadius * VD_STRUCTURE_SCALE);
    double3 noise = vd_curl(sx * frequency, sy * frequency, sz * frequency);

    double h = vd_height_fraction(p, y);
    double roughness = 1.0 + (VD_GROUND_ROUGHNESS - 1.0) * (1.0 - h);
    double amplitude = p->turbulence * speed * roughness;
    return w + noise * amplitude;
}

/**
 * The four hand-written functions above, one input per work item, four answers out.
 *
 * Not used by the game either. A wind speed that disagrees says nothing about which of exp, log, sine
 * or a plain division is the one that drifted, and the driver's own are not above suspicion: this is
 * where that question gets an answer instead of an argument.
 */
__kernel void math_probe(const int count, __global const double* in, __global double* out) {
    int i = get_global_id(0);
    if (i >= count) {
        return;
    }
    double x = in[i];
    double2 sc = vd_sincos(x);
    int b = i * 7;
    out[b] = vd_exp(x);
    out[b + 1] = vd_log(fabs(x) + 0.25);
    out[b + 2] = sc.x;
    out[b + 3] = sc.y;
    out[b + 4] = vd_div(1.0, fabs(x) + 0.25);
    out[b + 5] = vd_sqrt(fabs(x) + 0.25);
    out[b + 6] = fmod(x * 1000.0, VD_TAU);
}

/**
 * The wind field alone, one sample per work item.
 *
 * Not used by the game. It exists so the parity test can hold the field itself against the Java without
 * the integrator in between: a disagreement in a wind speed and a disagreement in where a piece ends up
 * look the same from outside, and only one of them is the kernel's fault.
 */
__kernel void probe(
        const int count,
        __global const double* point,
        __global double* out,
        const double centerX,
        const double centerZ,
        const double groundY,
        const double cloudBaseY,
        const double coreRadius,
        const double peakWind,
        const double translationX,
        const double translationZ,
        const double turbulence,
        const double phase) {

    int i = get_global_id(0);
    if (i >= count) {
        return;
    }

    vd_vortex p;
    p.centerX = centerX;
    p.centerZ = centerZ;
    p.groundY = groundY;
    p.cloudBaseY = cloudBaseY;
    p.coreRadius = coreRadius;
    p.peakWind = peakWind;
    p.translationX = translationX;
    p.translationZ = translationZ;
    p.turbulence = turbulence;

    int b = i * 3;
    double3 w = vd_wind(&p, phase, point[b], point[b + 1], point[b + 2]);
    out[b] = w.x;
    out[b + 1] = w.y;
    out[b + 2] = w.z;
}

__kernel void advect(
        const int count,
        __global double* position,
        __global double* velocity,
        __global const double* terminal,
        const double centerX,
        const double centerZ,
        const double groundY,
        const double cloudBaseY,
        const double coreRadius,
        const double peakWind,
        const double translationX,
        const double translationZ,
        const double turbulence,
        const double phase,
        const double dt) {

    int i = get_global_id(0);
    if (i >= count) {
        return;
    }

    vd_vortex p;
    p.centerX = centerX;
    p.centerZ = centerZ;
    p.groundY = groundY;
    p.cloudBaseY = cloudBaseY;
    p.coreRadius = coreRadius;
    p.peakWind = peakWind;
    p.translationX = translationX;
    p.translationZ = translationZ;
    p.turbulence = turbulence;

    int b = i * 3;
    double3 pos = (double3)(position[b], position[b + 1], position[b + 2]);
    double3 vel = (double3)(velocity[b], velocity[b + 1], velocity[b + 2]);

    double term = fmax(VD_MIN_TERMINAL, terminal[i]);
    double drag = vd_div(VD_GRAVITY, term * term);

    double3 w = vd_wind(&p, phase, pos.x, pos.y, pos.z);
    double3 rel = w - vel;
    double relative = vd_sqrt(rel.x * rel.x + rel.y * rel.y + rel.z * rel.z);

    // Same substep count as the processor path, and taken from the same slope, so a piece that has just
    // been hit by the core wall is cut into the same number of pieces on both.
    double slope = 2.0 * drag * relative * dt;
    int substeps = (int) ceil(slope);
    if (substeps < 1) {
        substeps = 1;
    }
    if (substeps > VD_MAX_SUBSTEPS) {
        substeps = VD_MAX_SUBSTEPS;
    }
    double h = vd_div(dt, (double) substeps);

    for (int s = 0; s < substeps; s++) {
        if (s > 0) {
            w = vd_wind(&p, phase, pos.x, pos.y, pos.z);
            rel = w - vel;
            relative = sqrt(rel.x * rel.x + rel.y * rel.y + rel.z * rel.z);
        }
        // Drag taken at the end of the step and solved for, as on the processor. Explicit, a leaf
        // meeting a core wall overshoots the wind and diverges inside one tick.
        double k = drag * relative;
        double settle = vd_div(1.0, 1.0 + k * h);
        vel.x = (vel.x + k * w.x * h) * settle;
        vel.y = (vel.y + (k * w.y - VD_GRAVITY) * h) * settle;
        vel.z = (vel.z + k * w.z * h) * settle;

        pos.x += vel.x * h;
        pos.y += vel.y * h;
        pos.z += vel.z * h;
    }

    position[b] = pos.x;
    position[b + 1] = pos.y;
    position[b + 2] = pos.z;
    velocity[b] = vel.x;
    velocity[b + 1] = vel.y;
    velocity[b + 2] = vel.z;
}

// Detail under the size of a cell, made rather than stored.
// Reads CloudDetail, so cloud_field.glsl has to be included first.

// Octaves of the detail, and what each one does to the one before it. Two and not three: the third
// would land near forty metres, which is under the spacing of the march samples that read it, and a
// wave nobody samples twice comes back as sparkle.
const int DETAIL_OCTAVES = 2;
const float FINER = 2.17;
const float FAINTER = 0.5;

/**
 * One number per lattice point, with no transcendental in it.
 *
 * <p>A sine based hash is the usual one and it costs a quarter rate instruction eight times per
 * octave, which at twenty wet samples down a ray is most of what the detail costs. This is float
 * arithmetic only.
 *
 * <p>Everything here is kept small on purpose, and that is the whole design rather than a detail.
 * A hash ending on the fractional part of a large product has no fractional part left to take: at a
 * lattice coordinate of sixty, the obvious three way product runs past a quarter of a million, a
 * float holds eighteen bits of that as the whole number and the six bits left over give sixty four
 * distinct outputs. The noise then comes back in steps, and the steps draw as hatching across every
 * cloud far enough from the camera. Folding to the unit interval first and adding rather than
 * multiplying keeps the last product near ten thousand, which leaves the mantissa enough room.
 */
float scatterOf(vec3 lattice) {
    vec3 spun = fract(lattice * 0.1031);
    spun += dot(spun, spun.zyx + 31.32);
    return fract((spun.x + spun.y) * spun.z);
}

/** Value noise: the lattice smoothed along a Hermite curve, which is eight corners and seven mixes. */
float wobble(vec3 at) {
    vec3 whole = floor(at);
    vec3 part = at - whole;
    part = part * part * (3.0 - 2.0 * part);
    float x00 = mix(scatterOf(whole + vec3(0, 0, 0)), scatterOf(whole + vec3(1, 0, 0)), part.x);
    float x10 = mix(scatterOf(whole + vec3(0, 1, 0)), scatterOf(whole + vec3(1, 1, 0)), part.x);
    float x01 = mix(scatterOf(whole + vec3(0, 0, 1)), scatterOf(whole + vec3(1, 0, 1)), part.x);
    float x11 = mix(scatterOf(whole + vec3(0, 1, 1)), scatterOf(whole + vec3(1, 1, 1)), part.x);
    return mix(mix(x00, x10, part.y), mix(x01, x11, part.y), part.z);
}

/**
 * Three octaves of billow noise, nought to one.
 *
 * <p>Billow rather than plain noise, which is the fold at the middle: taking the distance from half
 * instead of the value itself turns smooth hills into creased ridges, and creased ridges at three
 * scales is what reads as the cauliflower surface of a cumulus. It is the cheap stand-in for Worley,
 * whose twenty-seven neighbour distances buy a better crease than this march can pay for.
 *
 * <p>The frequency step is not two. Doubling lines every octave's lattice up with the last one, and
 * the corners then stack into a visible grid, which is the artefact this whole function exists to
 * remove.
 */
float billow(vec3 at) {
    float sum = 0.0;
    float total = 0.0;
    float loud = 1.0;
    for (int octave = 0; octave < DETAIL_OCTAVES; octave++) {
        sum += loud * (1.0 - abs(2.0 * wobble(at) - 1.0));
        total += loud;
        at *= FINER;
        loud *= FAINTER;
    }
    return sum / total;
}

/**
 * One cell's worth of water, broken up by that detail.
 *
 * <p>Subtract and stretch back, which is the shape the erosion has to have: taking the noise away
 * would thin the whole cloud, and what is wanted is the core untouched and the edge eaten. So the
 * bite grows as the sample gets fainter, and the rest is stretched so a core that was full stays
 * full. A lone wet cell, which is most of what a sixty-four metre grid produces at the top of a
 * cumulus, stops drawing as a smooth ball and starts drawing as a wisp.
 */
float eroded(float water, float peak, vec3 metres) {
    if (water <= 0.0 || peak <= 0.0 || CloudDetail.y <= 0.0) {
        return water;
    }
    // On the root of the fraction and not on the fraction itself. This field is optically thin
    // nearly everywhere, so a cell holding a tenth of the peak is the ordinary case rather than the
    // faint edge, and a bite taken against the raw fraction removes every such cell whole: an
    // erosion that only ever answers nothing or everything is a threshold. The root is also the
    // scale the atlas stores, so a sample the noise leaves alone comes back exactly as it went in.
    float part = sqrt(clamp(water / peak, 0.0, 1.0));
    float bite = CloudDetail.y * billow(metres * CloudDetail.x) * (1.0 - part);
    float left = max(0.0, (part - bite) / max(1.0e-4, 1.0 - bite));
    return left * left * peak;
}

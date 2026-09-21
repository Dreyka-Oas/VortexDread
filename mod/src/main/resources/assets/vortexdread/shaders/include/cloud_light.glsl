// What reaches one point inside the cloud, and which way it leaves.
// Reads the uniform block and waterAt, so cloud_field.glsl has to be included first.

// Octaves of the multiple scattering approximation, and what each one does to the one before it:
// thins the extinction it sees, quietens what it contributes, and flattens its lobe. Three is
// where everyone stops, because the fourth changes nothing anyone can see. The count is fixed
// rather than tuned, since the three values below travel in a vec3 that has exactly three slots.
const int OCTAVES = 3;
const float THINNER = 0.5;
const float QUIETER = 0.5;
const float FLATTER = 0.5;

/**
 * One lobe of Henyey and Greenstein, scaled so a droplet scattering every way alike answers one.
 *
 * The usual form carries a quarter of pi in the denominator, which belongs to a radiance integral
 * this march never writes out. Dropping it leaves a number that multiplies the light directly and
 * is one when the lobe is off, so turning the scattering off cannot change the brightness of the
 * sky.
 */
float lobe(float pull, float facing) {
    float squared = pull * pull;
    return (1.0 - squared) / pow(max(1.0e-4, 1.0 + squared - 2.0 * pull * facing), 1.5);
}

/**
 * Which way a droplet sends the light it catches, one answer per octave of the scattering sum.
 *
 * Two lobes and not one, because a water droplet does two things at once that no single lobe
 * describes. Most of what it catches carries on roughly forward, which is why a cloud with the sun
 * behind it has a bright rim and why the sky next to the sun glares. A smaller part comes straight
 * back, which is what lights the cloud you look at with the sun over your own shoulder. One lobe
 * fitted between the two gets neither.
 *
 * <p>Three answers and not one, because the octaves stand for light that has already bounced.
 * A photon that scattered four times has forgotten which way it came in, so the lobe that applies
 * to it is flatter than the one that applies to light arriving straight from the sun. Using the
 * first octave's lobe for all three would put a sharp forward peak on the diffuse part of the
 * light and darken the side of the sky ninety degrees from the sun further than any cloud is.
 *
 * <p>Once per pixel, since the angle between the view and the sun is the same the whole way down a
 * straight ray, and six powers per sample is not a price this march can pay.
 */
vec3 lobesAt(vec3 ray) {
    float facing = dot(ray, SunToward.xyz);
    float forward = CloudScatter.x;
    float back = CloudScatter.y;
    vec3 lobes = vec3(0.0);
    for (int octave = 0; octave < OCTAVES; octave++) {
        lobes[octave] = mix(lobe(forward, facing), lobe(back, facing), 0.5);
        forward *= FLATTER;
        back *= FLATTER;
    }
    return lobes;
}

/**
 * How much of the sun reaches one point and comes back at the viewer, by marching at the sun and
 * counting the water in the way.
 *
 * Not one exponential but a sum of three, which is the cheap stand-in for multiple scattering. A
 * photon that bounced around inside the cloud before it left travelled through less extinction than
 * a straight line through the same water says it did, so each octave halves the extinction and
 * halves what it contributes. Without this every cloud comes out the colour of slate, because
 * single scattering under-counts by most of the light a real cloud sends back.
 *
 * The steps at the sun double their span. Six is enough because of it: the water in the first
 * hundred metres decides most of the answer and the water a kilometre away only has to be counted
 * roughly.
 */
float sunReach(vec3 metres, vec3 lobes) {
    float depth = 0.0;
    float span = SunToward.w;
    float along = 0.0;
    int steps = int(CloudScatter.w);
    for (int step = 0; step < steps; step++) {
        depth += waterAt(metres + SunToward.xyz * (along + 0.5 * span)) * span;
        along += span;
        span *= 2.0;
    }
    float thickness = depth * VolumeCells.w;
    float lit = 0.0;
    float total = 0.0;
    float thinner = 1.0;
    float quieter = 1.0;
    for (int octave = 0; octave < OCTAVES; octave++) {
        lit += quieter * lobes[octave] * exp(-thickness * thinner);
        total += quieter;
        thinner *= THINNER;
        quieter *= QUIETER;
    }
    // Divided by the weights alone, not by the lobes: a point the sun reaches unobstructed has to
    // come back brighter than the daylight falling on it when it faces the sun and dimmer when it
    // does not, which is the whole point of the lobe.
    return lit / total;
}

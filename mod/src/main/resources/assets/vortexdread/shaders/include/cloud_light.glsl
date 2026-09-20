// What reaches one point inside the cloud, and which way it leaves.
// Reads the uniform block and waterAt, so cloud_field.glsl has to be included first.

// Towards the sun. Six is enough because the steps double: the water in the first hundred metres
// decides most of the answer and the water a kilometre away only has to be counted roughly.
const int LIGHT_STEPS = 6;

// Octaves of the multiple scattering approximation, and how much thinner and how much quieter each
// one is than the last. Three is where everyone stops, because the fourth changes nothing anyone
// can see.
const int OCTAVES = 3;
const float THINNER = 0.5;
const float QUIETER = 0.5;

/**
 * How much of the sun reaches one point, by marching at it and counting the water in the way.
 *
 * Not one exponential but a sum of three, which is the cheap stand-in for multiple scattering. A
 * photon that bounced around inside the cloud before it left travelled through less extinction than
 * a straight line through the same water says it did, so each octave halves the extinction and
 * halves what it contributes. Without this every cloud comes out the colour of slate, because
 * single scattering under-counts by most of the light a real cloud sends back.
 */
float sunReach(vec3 metres) {
    float depth = 0.0;
    float span = SunToward.w;
    float along = 0.0;
    for (int step = 0; step < LIGHT_STEPS; step++) {
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
        lit += quieter * exp(-thickness * thinner);
        total += quieter;
        thinner *= THINNER;
        quieter *= QUIETER;
    }
    // Divided back out so a point the sun reaches unobstructed gets exactly the light that falls on
    // it.
    return lit / total;
}

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
 * Which way a droplet sends the light it catches, for a view and a sun this far apart.
 *
 * Two lobes and not one, because a water droplet does two things at once that no single lobe
 * describes. Most of what it catches carries on roughly forward, which is why a cloud with the sun
 * behind it has a bright rim and why the sky next to the sun glares. A smaller part comes straight
 * back, which is what lights the cloud you look at with the sun over your own shoulder. One lobe
 * fitted between the two gets neither.
 */
float phaseAt(vec3 ray) {
    float facing = dot(ray, SunToward.xyz);
    return mix(lobe(CloudScatter.x, facing), lobe(CloudScatter.y, facing), 0.5);
}

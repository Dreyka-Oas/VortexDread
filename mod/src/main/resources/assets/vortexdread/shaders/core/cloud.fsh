#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <vortexdread:cloud_field.glsl>
#moj_import <vortexdread:cloud_detail.glsl>

in vec2 texCoord;

out vec4 fragColor;

// Along the view. Deliberately coarse and deliberately fixed: the jitter, the reprojection and the
// quality levels are a later pass. What keeps this usable at sixty-four is that the march is clipped
// to the altitudes that actually hold water, so the steps land in the cloud instead of around it.
const int STEPS = 64;

// Towards the sun. Six is enough because the steps double: the water in the first hundred metres
// decides most of the answer and the water a kilometre away only has to be counted roughly.
const int LIGHT_STEPS = 6;

// Octaves of the multiple scattering approximation, and how much thinner and how much quieter each one
// is than the last. Three is where everyone stops, because the fourth changes nothing anyone can see.
const int OCTAVES = 3;
const float THINNER = 0.5;
const float QUIETER = 0.5;

// Below this the cloud in front has swallowed everything behind and the rest of the march is arithmetic
// on a number nobody will see.
const float OPAQUE = 0.004;

/**
 * How much of the sun reaches one point, by marching at it and counting the water in the way.
 *
 * Not one exponential but a sum of three, which is the cheap stand-in for multiple scattering. A
 * photon that bounced around inside the cloud before it left travelled through less extinction than a
 * straight line through the same water says it did, so each octave halves the extinction and halves
 * what it contributes. Without this every cloud comes out the colour of slate, because single
 * scattering under-counts by most of the light a real cloud sends back.
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
    // Divided back out so a point the sun reaches unobstructed gets exactly the light that falls on it.
    return lit / total;
}

/**
 * A different starting offset for every pixel, nought to one.
 *
 * Interleaved gradient noise, which is one dot product and needs neither a texture nor a uniform. The
 * march has to start somewhere inside its first step, and starting every ray at the same place turns
 * one step boundary into a line drawn across the whole sky. Scattered per pixel it becomes grain, and
 * the eye reads grain on a cloud as cloud.
 */
float dither(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

void main() {
    // The reciprocal of a perspective matrix's diagonal is the tangent of half the angle on that axis,
    // so the ray needs no field of view uniform of its own. Right is the other way from left.
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = normalize(CameraForward.xyz
            - ndc.x * CameraLeft.xyz / ProjMat[0][0]
            + ndc.y * CameraUp.xyz / ProjMat[1][1]);

    // Both horizontal axes tile, so the only faces the ray can cross are the floor and the ceiling of
    // the band, and the band is the wet altitudes rather than the whole volume.
    float camY = CameraInVolume.y;
    float below = CloudBand.x - camY;
    float above = CloudBand.y - camY;
    float enter = 0.0;
    float leave = AtlasShape.w;
    if (abs(ray.y) > 1.0e-4) {
        float toLow = below / ray.y;
        float toHigh = above / ray.y;
        enter = max(0.0, min(toLow, toHigh));
        leave = min(leave, max(toLow, toHigh));
    } else if (below > 0.0 || above < 0.0) {
        discard;
    }
    if (leave <= enter) {
        discard;
    }

    // Beer and Lambert down the view, and the same again at each step towards the sun.
    float span = (leave - enter) / float(STEPS);
    float start = enter + dither(gl_FragCoord.xy) * span;
    float transmittance = 1.0;
    vec3 scattered = vec3(0.0);
    for (int step = 0; step < STEPS; step++) {
        vec3 at = CameraInVolume.xyz + ray * (start + float(step) * span);
        // Eroded down the view but not towards the sun: the shadow a cloud casts on itself is a low
        // frequency thing, and paying for the detail seven times over would not change it.
        float water = eroded(waterAt(at), CameraLeft.w, at);
        if (water <= 0.0) {
            continue;
        }
        float stepThrough = exp(-water * VolumeCells.w * span);
        // Powder is measured over one cell and not over one step, since it says how dense the cloud is
        // here and not how finely this frame chose to sample it. Over a step it would change the look
        // of the sky every time the quality setting moved.
        float powder = 1.0 - exp(-2.0 * water * VolumeCells.w * CameraInVolume.w);
        vec3 lit = SunLight.rgb * sunReach(at) * mix(1.0, powder, SunLight.w) + SkyLight.rgb;
        // The exact integral over the step rather than a rectangle at its middle, which is what stops
        // a coarse march from drawing the cloud in bands of its own step size.
        scattered += transmittance * lit * (1.0 - stepThrough);
        transmittance *= stepThrough;
        if (transmittance < OPAQUE) {
            break;
        }
    }

    // Already multiplied by its own coverage, so the pipeline blends premultiplied and does not do it
    // a second time. Dividing the colour back out would only lose precision where the cloud is thin.
    fragColor = vec4(scattered, 1.0 - transmittance);
}

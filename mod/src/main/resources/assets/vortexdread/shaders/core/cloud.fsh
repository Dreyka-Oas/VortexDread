#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <vortexdread:cloud_field.glsl>
#moj_import <vortexdread:cloud_detail.glsl>
#moj_import <vortexdread:cloud_light.glsl>

in vec2 texCoord;

out vec4 fragColor;

// Below this the cloud in front has swallowed everything behind and the rest of the march is arithmetic
// on a number nobody will see.
const float OPAQUE = 0.004;

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

    vec3 lobes = lobesAt(ray);

    // Beer and Lambert down the view, and the same again at each step towards the sun. What keeps a
    // budget this coarse usable is that the march is clipped to the altitudes that actually hold
    // water, so the steps land in the cloud instead of around it.
    int steps = int(CloudScatter.z);
    float span = (leave - enter) / float(steps);
    float start = enter + dither(gl_FragCoord.xy) * span;
    float transmittance = 1.0;
    vec3 scattered = vec3(0.0);
    for (int step = 0; step < steps; step++) {
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
        // The lobes ride inside the sun term and nowhere else. Light from a direction comes back
        // differently depending on where you stand; the sky arrives from everywhere at once and
        // comes out the same whichever way you look.
        vec3 lit = SunLight.rgb * sunReach(at, lobes) * mix(1.0, powder, SunLight.w) + SkyLight.rgb;
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

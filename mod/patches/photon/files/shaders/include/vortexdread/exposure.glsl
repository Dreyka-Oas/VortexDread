#if !defined INCLUDE_VORTEXDREAD_EXPOSURE
#define INCLUDE_VORTEXDREAD_EXPOSURE

/*
  How much light a storm takes out of the whole picture.

  This is not applied to the picture. The pack measures the frame it has been handed and opens its
  aperture until the median lands where it wants it, so anything the mod subtracts downstream is given
  back a frame later and what survives is a swing between a near black capture and a washed white one.
  The aperture is the only place a storm can take light away and have it stay away.

  Read from the same texture the funnel march reads, but only the three fields that matter here, and
  in the vertex stage, where the pack works out its exposure once per frame rather than once per pixel.
*/

uniform sampler2D colortex15;

const int vortexdread_exposure_rows = 4;
const float vortexdread_exposure_reach = 2048.0;

// Where the storm's own shadow starts giving out and where it has gone, in blocks. Flat between the
// camera and the first figure, because the mass overhead is kilometres wide: someone a few hundred
// blocks from the funnel is not on the edge of anything, they are under the middle of it.
const float vortexdread_pall_full = 700.0;
const float vortexdread_pall_gone = 2000.0;

// The most of the light a storm directly overhead is allowed to take. Two and a half stops: a summer
// afternoon under a supercell reads somewhere near dusk, and the pack's own tone curve does the rest.
const float vortexdread_pall_depth = 0.72;

// What a storm that has not dropped anything yet already takes. A mesocyclone is overhead long before
// a funnel is, and the deck is what blocks the sun, not the column under it.
const float vortexdread_pall_floor = 0.45;

float vortexdread_exposure_unpack(float low, float high) {
    return (low * 255.0 + high * 255.0 * 256.0) * (1.0 / 65535.0);
}

/** A multiplier on the pack's own exposure, one where there is no storm within reach. */
float vortexdread_exposure_scale() {
    float worst = 0.0;

    for (int i = 0; i < vortexdread_exposure_rows; ++i) {
        if (texelFetch(colortex15, ivec2(5, i), 0).x < 0.5) {
            continue;
        }

        vec4 place = texelFetch(colortex15, ivec2(0, i), 0);
        vec4 reach = texelFetch(colortex15, ivec2(2, i), 0);
        vec4 colour = texelFetch(colortex15, ivec2(3, i), 0);

        vec2 axis = vec2(
            vortexdread_exposure_unpack(place.x, place.y) * 2.0 * vortexdread_exposure_reach
                - vortexdread_exposure_reach,
            vortexdread_exposure_unpack(place.z, place.w) * 2.0 * vortexdread_exposure_reach
                - vortexdread_exposure_reach
        );
        float descent = vortexdread_exposure_unpack(reach.z, reach.w);
        float ground_load = colour.a;

        float near = 1.0 - smoothstep(vortexdread_pall_full, vortexdread_pall_gone, length(axis));

        // Barely weighted by what the storm has picked up. Dust in the air is what colours a storm, not
        // what darkens it: one over a lake blocks the sun exactly as well as one over a wheat field.
        float weight = near
                     * mix(vortexdread_pall_floor, 1.0, descent)
                     * (0.88 + 0.12 * ground_load);
        worst = max(worst, weight);
    }

    return 1.0 - vortexdread_pall_depth * clamp(worst, 0.0, 1.0);
}

#endif

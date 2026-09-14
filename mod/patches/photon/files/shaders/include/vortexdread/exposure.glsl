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

// The pack's own flash factor, which is what a bolt does to the whole sky here.
#include "/include/misc/lightning_flash.glsl"

// How far the aperture closes while a bolt is lit. The pack brightens the entire sky by a fixed factor
// and its exposure is a running average that cannot follow a tenth of a second, so the frame clips to
// flat white and every silhouette in it goes with it. Nobody standing under a storm sees white: what
// they see is the mass lighting up from inside, and stopping down for the length of the flash is what
// puts that back. Only while a storm of the mod's own is near, so an ordinary night keeps the pack's
// behaviour.
const float vortexdread_flash_stop = 0.66;

const int vortexdread_exposure_rows = 4;
const float vortexdread_exposure_reach = 2048.0;

// Where the storm's own shadow starts giving out and where it has gone, in blocks. Flat between the
// camera and the first figure, because the mass overhead is kilometres wide: someone a few hundred
// blocks from the funnel is not on the edge of anything, they are under the middle of it.
const float vortexdread_pall_full = 700.0;
const float vortexdread_pall_gone = 2000.0;

// Where the aperture is put while a storm of the mod's own is overhead, against where the pack put it.
//
// Measured rather than chosen. At noon this pack renders a clear sky at a mean of 192 out of 255 and
// the same sky under vanilla thunder at 41, which is a drop of more than two stops before the mod has
// touched anything: the whole frame lands in the bottom sixth of the range and every colour in it is
// gone. Nobody standing under a storm at midday sees that. What they see is dim, flat and grey, with
// the colours still in it, which is about half of a clear noon. This number is what puts the frame
// back there, and the pall below is then measured from somewhere an eye would recognise.
const float vortexdread_storm_open = 2.3;

// The most of the light a storm directly overhead is allowed to take, once the aperture is back where
// the line above puts it. A summer afternoon under a supercell reads like an hour before dusk, and the
// pack's own tone curve does the rest.
const float vortexdread_pall_depth = 0.18;

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

    float near_storm = clamp(worst, 0.0, 1.0);
    float flash = clamp(LIGHTNING_FLASH_UNIFORM, 0.0, 1.0) * near_storm;

    // Opened first, then stopped down: the pall is a fraction of a daylight an eye would recognise, and
    // taking it off the pack's own crushed value instead would be measuring darkness from darkness.
    float open = mix(1.0, vortexdread_storm_open, near_storm);

    return open * (1.0 - vortexdread_pall_depth * near_storm)
               * (1.0 - vortexdread_flash_stop * flash);
}

#endif

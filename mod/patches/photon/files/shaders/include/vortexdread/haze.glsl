#if !defined INCLUDE_VORTEXDREAD_HAZE
#define INCLUDE_VORTEXDREAD_HAZE

/*
  The air between the camera and the storm.

  A tornado's lower half is almost never seen against sky. It stands in front of land, so every pixel
  of it arrives through a kilometre of air the pack has already filled with rain haze, and whatever
  that haze is made of is what the funnel fades into. The pack's rain haze is bright: plenty of mie
  scattering, an albedo of a half, lit by the whole dome. A dark funnel seen through it washes to white
  from about half its height down, which reads as a funnel that stops in mid air and is the single
  loudest thing wrong with a storm at any distance.

  What is actually under a supercell is the opposite. The same air, more of it, and almost no light
  reaching it, because the thing casting the shadow is fifteen kilometres across. High extinction, low
  albedo: near visibility drops and what is lost goes dark instead of pale. The funnel then stays
  readable all the way down, the rain curtain beside it separates from the sky, and the land under the
  base loses its colour the way it does in every photograph of one.
*/

#include "/include/fog/overworld/parameters.glsl"
#include "/include/vortexdread/sky.glsl"

// What the haze gives back of what it takes, against the half the pack's rain haze returns.
const float vortexdread_haze_albedo = 0.18;

// How much more of the view the haze takes out, and how much of the blue sheen is left in it.
const float vortexdread_haze_reach = 1.25;
const float vortexdread_haze_sheen = 0.55;

/** The air the storm leaves behind it, from the air the weather asked for. */
OverworldFogParameters vortexdread_storm_haze(OverworldFogParameters params) {
    float hold = vortexdread_sky_hold();
    if (hold <= 0.0) {
        return params;
    }

    params.mie_extinction_coeff *= mix(1.0, vortexdread_haze_reach, hold);
    params.mie_scattering_coeff = min(
        params.mie_scattering_coeff,
        params.mie_extinction_coeff * mix(1.0, vortexdread_haze_albedo, hold)
    );
    params.rayleigh_scattering_coeff *= mix(1.0, vortexdread_haze_sheen, hold);
    return params;
}

#endif // INCLUDE_VORTEXDREAD_HAZE

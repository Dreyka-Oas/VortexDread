#if !defined INCLUDE_VORTEXDREAD_SKY
#define INCLUDE_VORTEXDREAD_SKY

/*
  The kind of sky a storm stands in, decided before the pack works out what to draw in it.

  Everything else the mod does to the cloud layer adds coverage: the tower fills the gaps, the lowering
  lets the base down, the ring outside is thinned. All of it needs somewhere to add to, and a layer the
  pack has already taken to full coverage has none. That is not an edge case here, it is the only case,
  because a tornado arrives with its rain and the game's rain is what drives the pack's humidity to one.
  Left alone, the storm builds beautifully while the rain is still coming on and then dissolves into a
  flat white sheet the moment it arrives.

  So the storm says what its own weather is. Drier than the rain suggests, because the air a supercell
  draws on is dry at cloud level and that is the whole reason the base is so sharply defined; warmer,
  because what the pack calls temperature is what decides between a stratus sheet and something with
  towers in it; and windier, because there is no such thing as a still supercell. None of it touches the
  fog, the rain or anything the player walks through, only what the cloud layer is made of.
*/

#include "/include/vortexdread/state.glsl"
#include "/include/weather/core.glsl"

// What the cloud layer is told instead, while a storm holds the sky.
const float vortexdread_sky_humidity = 0.62;
const float vortexdread_sky_warmth = 0.72;
const float vortexdread_sky_wind = 0.70;

/** How much of the sky the storm has taken over, zero with none and one once the funnel is down. */
float vortexdread_sky_hold() {
    if (texelFetch(vortexdread_state, ivec2(5, 0), 0).x < 0.5) {
        return 0.0;
    }
    vec4 reach = texelFetch(vortexdread_state, ivec2(2, 0), 0);
    return clamp01((reach.z * 255.0 + reach.w * 255.0 * 256.0) * (1.0 / 65535.0));
}

/** The weather the cloud layer is built from, as the storm leaves it. */
Weather vortexdread_storm_sky(Weather weather) {
    float hold = vortexdread_sky_hold();
    if (hold <= 0.0) {
        return weather;
    }
    weather.humidity = mix(weather.humidity,
                           min(weather.humidity, vortexdread_sky_humidity), hold);
    weather.temperature = mix(weather.temperature,
                              max(weather.temperature, vortexdread_sky_warmth), hold);
    weather.wind = mix(weather.wind, max(weather.wind, vortexdread_sky_wind), hold);
    return weather;
}

#endif // INCLUDE_VORTEXDREAD_SKY

#if !defined INCLUDE_VORTEXDREAD_LAYERS
#define INCLUDE_VORTEXDREAD_LAYERS

/*
  The storm as a stack of decks rather than one deck stretched.

  A supercell is not a cloud. It is a tower that punches through every level of the atmosphere on its
  way up, and what makes a photograph of one read as enormous is that you can count the levels: the
  ragged scud a few hundred feet off the ground, the hard flat base of the updraft, the boiling mass
  above it, then the anvil spread flat across the whole sky at the tropopause, and the cirrus torn off
  the top of that and carried downwind for fifty kilometres. Five things at five heights, each lit
  differently, each moving at a different speed. One layer made thicker is a big cloud; several layers
  stacked is weather.

  Photon already carries the levels. Layer zero is the cumulus the funnel is cut out of, layer one is a
  second volumetric march four hundred units above it, and the planar cirrus sits above both. Away from
  a storm the pack's own weather leaves layer one almost empty, which is right for an ordinary day and
  wrong for this one. So the storm fills it: layer one goes to an overcast sheet, which is the anvil,
  and the cirrus goes up with it, which is the blow-off.

  The anvil then pays for itself twice. It is the mass a viewer reads the height of the storm against,
  and it is what stands between the sun and the deck under it. Photon spends that as l0_shadow, half
  the direct light off the cumulus and a little bounce back onto its top, so the deck the funnel is
  made of darkens because something is genuinely above it rather than because a number was lowered.

  Applied at the end of the parameter build, after the pack has worked its own values out. The two
  extinction terms are raised here rather than left to follow from l0_shadow, because the pack thins
  the layer it has decided is shaded and the funnel is the one thing that must not thin.
*/

#include "/include/sky/clouds/parameters.glsl"
#include "/include/vortexdread/sky.glsl"

// Where the anvil is taken to, as a floor under whatever the weather asked for. The two components are
// the low and high coverage the layer is shaped between, so the sheet is dense and still has holes.
const vec2 vortexdread_anvil_coverage = vec2(0.66, 0.94);

// How flat it is. An anvil has hit the tropopause and cannot rise any further, so it spreads instead,
// and what spreads is a sheet rather than a field of separate lumps.
const float vortexdread_anvil_flat = 0.82;

// The cirrus torn off the top and carried downwind, and what is left of the mackerel sky underneath it.
const float vortexdread_blowoff = 0.72;
const float vortexdread_mackerel = 0.30;

// How much of the sun the anvil takes off the deck below, on top of what the pack already worked out.
const float vortexdread_anvil_shade = 0.80;

// How much more solid the cumulus deck is under a storm. The pack tunes its cloud for a sky you look
// past; this one is the subject of the shot, and a funnel a viewer can see the horizon through is the
// single most common way a rendered tornado fails to be one.
const float vortexdread_deck_density = 1.5;

// How much of that extra extinction comes back out as light. Under one, so the deck gains body faster
// than it gains brightness, which is what keeps a storm dark while it gets thicker.
const float vortexdread_deck_albedo = 0.74;

/** The layers a storm stacks over itself, from the ones the weather alone would have built. */
CloudsParameters vortexdread_storm_layers(CloudsParameters params) {
    float hold = vortexdread_sky_hold();
    if (hold <= 0.0) {
        return params;
    }

    params.l1_coverage = mix(params.l1_coverage,
                             max(params.l1_coverage, vortexdread_anvil_coverage), hold);
    params.l1_cumulus_stratus_blend = mix(params.l1_cumulus_stratus_blend,
                                          max(params.l1_cumulus_stratus_blend,
                                              vortexdread_anvil_flat), hold);

    params.cirrus_amount = mix(params.cirrus_amount,
                               max(params.cirrus_amount, vortexdread_blowoff), hold);
    params.cirrocumulus_amount = mix(params.cirrocumulus_amount,
                                     params.cirrocumulus_amount * vortexdread_mackerel, hold);

    params.l0_shadow = mix(params.l0_shadow,
                           max(params.l0_shadow, vortexdread_anvil_shade), hold);

    float body = mix(1.0, vortexdread_deck_density, hold);
    params.l0_extinction_coeff *= body;
    params.l0_scattering_coeff *= mix(body, body * vortexdread_deck_albedo, hold);

    return params;
}

#endif // INCLUDE_VORTEXDREAD_LAYERS

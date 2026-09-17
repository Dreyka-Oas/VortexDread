#if !defined INCLUDE_VORTEXDREAD_LIGHTNING
#define INCLUDE_VORTEXDREAD_LIGHTNING

/*
  Lightning as a point source inside the cloud volume.

  Photon ships a single flash factor and adds it to every cloud pixel at the same strength, which is
  what a camera exposure change looks like rather than what a discharge looks like. A real stroke is
  two or three kilometres of channel buried inside the mass: the cloud around it goes white, the cloud
  a few kilometres away barely moves, and the shape of the lit region is the reason a night storm reads
  as having depth at all. Iris hands the position over in lightningBoltPosition, so the only thing
  missing was using it.

  The term is added inside the cumulus march, next to the sun and sky terms, so it passes through the
  same transmittance and the same aerial perspective they do.
*/

// Not every program that marches the cloud volume pulls the pack's own flash factor in: the prepare
// pass builds the coverage map without ever touching the sky code that declares it. Asking for it
// here rather than assuming it costs nothing, the header guards itself.
#include "/include/misc/lightning_flash.glsl"

uniform vec4 lightningBoltPosition; // xyz relative to the camera, w set while a bolt is rendering

// How far the channel throws light, in blocks, before the falloff has halved it.
const float vortexdread_lightning_reach = 260.0;

// A second, far wider lobe and how much of the channel's output goes into it. A stroke is buried in a
// cloud that scatters it, so what leaves the lit region is not one point source falling off as the
// inverse square: the mass around it turns into a lamp in its own right, and a storm several hundred
// blocks across lights up over its whole width with the part the stroke went through brightest. One
// narrow lobe alone puts a bright patch on one side of the deck and leaves the rest of it dark.
//
// Exponential rather than inverse square, because this lobe is light that has been through cloud to
// get where it is, and what cloud does to light over a distance is Beer's law.
const float vortexdread_lightning_spill = 340.0;
const float vortexdread_lightning_spill_gain = 0.5;

// Where the whole thing is finished off, in blocks. Both lobes have a tail, and an inverse square has
// a long one: without this a stroke inside the storm still puts a few percent onto the fair weather
// cumulus sitting on the horizon four kilometres away, and a sky where every cloud in sight flickers
// together is the one thing a real storm never looks like. Past this the stroke may as well not exist.
const float vortexdread_lightning_fade = 1200.0;

// How far up the channel is taken to run from the bolt's own position, in blocks. Lightning is a line,
// not a point, and the difference shows most on a stroke that comes down to the ground: its entity sits
// on the terrain, two hundred blocks under the cloud it came out of, so a point source there would
// light the field and leave the storm above it untouched.
const float vortexdread_lightning_channel = 420.0;

// Radiance of the channel. Large, because it is divided by the inverse square almost immediately and
// because what it has to compete with is an overcast sky at noon.
const vec3 vortexdread_lightning_color = vec3(0.78, 0.85, 1.0) * 130.0;

/**
 * How much light one step of the march receives from the stroke.
 *
 * cloud_pos and air_viewer_pos are both in the curved frame the cloud functions work in, where the
 * planet centre is the origin and a block is CLOUDS_SCALE units long.
 */
vec3 vortexdread_lightning_gain(
    vec3 cloud_pos,
    vec3 air_viewer_pos,
    float sky_optical_depth,
    float extinction_coeff,
    float step_transmittance
) {
    if (LIGHTNING_FLASH_UNIFORM < 0.004 || lightningBoltPosition.w < 0.5) {
        return vec3(0.0);
    }

    vec3 bolt_pos = air_viewer_pos + lightningBoltPosition.xyz * CLOUDS_SCALE;

    // The nearest point of the channel rather than the bolt's own position. Up in the curved frame the
    // cloud functions work in is the direction away from the planet centre, which is the origin.
    vec3 up = normalize(bolt_pos);
    float along = clamp(dot(cloud_pos - bolt_pos, up),
                        0.0, vortexdread_lightning_channel * CLOUDS_SCALE);
    float to_bolt = distance(cloud_pos, bolt_pos + up * along) / CLOUDS_SCALE;

    // Inverse square, with a core the width of the channel's own glow so the term stays finite for a
    // sample sitting on top of the stroke, plus the wide lobe that carries the rest of the storm, and
    // the cutoff that keeps the pair of them from reaching the other side of the sky.
    float squared = to_bolt * to_bolt;
    float falloff = 1.0 / (1.0 + squared / (vortexdread_lightning_reach * vortexdread_lightning_reach))
        + vortexdread_lightning_spill_gain * exp(-to_bolt / vortexdread_lightning_spill);
    falloff *= exp(-to_bolt / vortexdread_lightning_fade);

    // The light still has to leave the cloud to be seen, and it leaves upward and sideways through
    // whatever is above this sample. That is what turns a flash into a lit region with an edge.
    float escape = exp(-sky_optical_depth * extinction_coeff * 0.3);

    // The same scattering integral Photon uses for its own terms, so this one is in the same units.
    float scattered = 1.0 - step_transmittance;

    return vortexdread_lightning_color * (LIGHTNING_FLASH_UNIFORM * falloff * escape * scattered);
}

#endif // INCLUDE_VORTEXDREAD_LIGHTNING

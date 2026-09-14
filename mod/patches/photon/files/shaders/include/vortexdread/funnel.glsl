#if !defined INCLUDE_VORTEXDREAD_FUNNEL
#define INCLUDE_VORTEXDREAD_FUNNEL

/*
  The tornado, marched as cloud inside the pack.

  A mod cannot hand a shader pack a uniform, so the storms arrive as a texture the mod rewrites every
  frame: one row per funnel, one texel per field, sixteen bits for anything that has to be accurate.
  Everything below reads that and never talks to the mod again.

  It is drawn here, in the pass that has already blended the translucent layer and has not yet applied
  the fog, so the column picks up the pack's own aerial perspective, its own bloom and its own tone
  mapping, and it is occluded by terrain through the depth that pass already holds. The alternative, a
  program of the mod's own, writes into buffers the deferred pass overwrites a moment later and shows
  nothing at all.
*/

// The state rides in on a sampler this pass has no other use for, because a pack asks for a custom
// texture by sampler name and there is no name of its own to ask under. Which one is chosen is settled
// in shaders.properties, and the two have to agree.
uniform sampler2D colortex15;
#define vortexdread_state colortex15

// Where the bolt is. Iris hands it over camera relative, with the fourth component set only while one
// is actually being drawn, and the header guards itself against the passes that already have it.
#include "/include/vortexdread/lightning.glsl"

const int vortexdread_max_funnels = 4;

// Ranges the two settings texels are folded into, and where they sit. The mod writes them on every
// row, so no funnel has to be found first.
const int vortexdread_settings_field = 6;
const float vortexdread_steps_span = 256.0;
const float vortexdread_detail_span = 32.0;

// The wisp size these frequencies were tuned against, in blocks. Matches LookConfig's own default, so
// a player who never touches the setting gets exactly what was tuned.
const float vortexdread_detail_reference = 1.8;

// Extinction per unit of density. A mature funnel is opaque: what reaches the eye is the little that
// scattered off its near face, which is why photographed ones read as black against the rain.
const float vortexdread_extinction = 0.62;

// Below this a sample is not worth a light march, which is the most expensive thing in here.
const float vortexdread_lit_threshold = 0.04;

// Shape of the condensation, matched to the mod's own program so the two draw the same tornado.
// Narrow where it meets the ground and widest against the cloud, which is the way round a funnel
// actually stands: the flare belongs at the top, and the debris at the foot is a separate thing.
const float vortexdread_waist = 0.58;
const float vortexdread_flare = 0.78;
const float vortexdread_ring_reach = 1.25;
const float vortexdread_ring_swell = 0.9;
const float vortexdread_ring_rough = 1.3;
const float vortexdread_ring_drop = 0.9;
const float vortexdread_wall_reach = 1.9;
const float vortexdread_wall_hang = 0.24;
const float vortexdread_wall_rise = 1.4;

// The mass overhead, both measured in funnel heights, and how many samples it is worth. Few,
// because it is a slab: what a ray does inside one is the distance it crosses.
//
// Its width comes off the height rather than off the core, because that is what sets it: a storm
// deep enough to hang a funnel a kilometre long is kilometres across whether the funnel it made is
// a rope or a wedge.
const float vortexdread_meso_reach = 3.0;
const float vortexdread_meso_thick = 0.16;
const int vortexdread_meso_steps = 8;

// Furthest the torn rim can push past that reach, as a multiple of it. Follows from how the rim is
// drawn below and has to stay above it, since it is only there to stop the march early.
const float vortexdread_meso_rim_max = 1.6;

// How far the underside hangs below the cloud base, as a fraction of the funnel's height, and the most
// the lumps below can add to it. The slab the ray crosses is opened to the second one, or the deepest
// lumps fall outside the march and the base ends on a plane again.
const float vortexdread_meso_sag = 0.05;
const float vortexdread_meso_sag_max = 2.35;

// Ranges the mod folded each field into before it fit them into bytes.
const float vortexdread_reach = 2048.0;
const float vortexdread_ground_span = 1152.0;
const float vortexdread_ground_floor = -128.0;
const float vortexdread_radius_span = 128.0;
const float vortexdread_height_span = 512.0;

struct VortexdreadFunnel {
    vec2 axis;        // where the axis is, relative to the camera
    float ground_y;   // absolute height of the ground under it
    float core_radius;
    float height;     // ground to cloud base
    float descent;    // 0 hanging out of the cloud, 1 on the ground
    float ground_load;
    float wind;       // against its own peak
    float flash;
    vec3 tint;
};

/** The two bytes the mod wrote for one number, back into the number. */
float vortexdread_unpack(float low, float high) {
    return (low * 255.0 + high * 255.0 * 256.0) * (1.0 / 65535.0);
}

/** Samples along the view ray, as the player set it. */
int vortexdread_march_steps() {
    vec4 quality = texelFetch(vortexdread_state, ivec2(vortexdread_settings_field, 0), 0);
    return int(max(8.0, vortexdread_unpack(quality.x, quality.y) * vortexdread_steps_span));
}

/** Samples toward the light, which is what gives the column its own shadow. */
int vortexdread_light_march_steps() {
    vec4 quality = texelFetch(vortexdread_state, ivec2(vortexdread_settings_field, 0), 0);
    return int(vortexdread_unpack(quality.z, quality.w) * vortexdread_steps_span);
}

/** Size of the smallest wisp worth drawing, in blocks. */
float vortexdread_detail() {
    vec4 quality = texelFetch(vortexdread_state, ivec2(vortexdread_settings_field + 1, 0), 0);
    return max(0.2, vortexdread_unpack(quality.x, quality.y) * vortexdread_detail_span);
}

bool vortexdread_read(int index, out VortexdreadFunnel funnel) {
    if (texelFetch(vortexdread_state, ivec2(5, index), 0).x < 0.5) {
        return false;
    }

    vec4 place = texelFetch(vortexdread_state, ivec2(0, index), 0);
    vec4 size = texelFetch(vortexdread_state, ivec2(1, index), 0);
    vec4 reach = texelFetch(vortexdread_state, ivec2(2, index), 0);
    vec4 colour = texelFetch(vortexdread_state, ivec2(3, index), 0);
    vec4 light = texelFetch(vortexdread_state, ivec2(4, index), 0);

    funnel.axis = vec2(
        vortexdread_unpack(place.x, place.y) * 2.0 * vortexdread_reach - vortexdread_reach,
        vortexdread_unpack(place.z, place.w) * 2.0 * vortexdread_reach - vortexdread_reach
    );
    funnel.ground_y =
        vortexdread_unpack(size.x, size.y) * vortexdread_ground_span + vortexdread_ground_floor;
    funnel.core_radius = vortexdread_unpack(size.z, size.w) * vortexdread_radius_span;
    funnel.height = vortexdread_unpack(reach.x, reach.y) * vortexdread_height_span;
    funnel.descent = vortexdread_unpack(reach.z, reach.w);
    funnel.tint = colour.rgb;
    funnel.ground_load = colour.a;
    funnel.flash = vortexdread_unpack(light.x, light.y);
    funnel.wind = vortexdread_unpack(light.z, light.w);
    return funnel.core_radius > 0.5 && funnel.height > 1.0;
}

/**
 * How hard one point is lit by the bolt that is going off right now.
 *
 * <p>A flash that lifts the whole volume by the same amount is a screen effect wearing a storm's
 * clothes. What a bolt inside a funnel does is light the part of it the channel is in: one side goes
 * white, the far wall stays where it was, and half a second later it is dark again.
 */
float vortexdread_bolt_gain(vec3 p, float flash) {
    if (flash <= 0.0) {
        return 0.0;
    }
    if (lightningBoltPosition.w < 0.5) {
        // The glow a bolt leaves behind outlives the bolt itself, and by then there is no position to
        // read: what is left is the volume still giving the light back, evenly.
        return flash * 0.3;
    }
    vec3 bolt = lightningBoltPosition.xyz + vec3(0.0, cameraPosition.y, 0.0);
    return flash * (0.15 + 0.85 * exp(-distance(p, bolt) * 0.014));
}

float vortexdread_hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vortexdread_value_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = vortexdread_hash(i);
    float n100 = vortexdread_hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = vortexdread_hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = vortexdread_hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = vortexdread_hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = vortexdread_hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = vortexdread_hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = vortexdread_hash(i + vec3(1.0, 1.0, 1.0));
    return mix(
        mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
        mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y),
        f.z
    );
}

float vortexdread_fbm(vec3 p) {
    float total = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; ++i) {
        total += amplitude * vortexdread_value_noise(p);
        p *= 2.03;
        amplitude *= 0.5;
    }
    return total;
}

/** Radius of the condensation wall at a height fraction, before the turbulence roughens it. */
float vortexdread_wall_radius(VortexdreadFunnel f, float h) {
    // A shallow curve rather than a cone: the wall stands nearly vertical through the middle of its
    // height and opens out over the last third, where the inflow is still spread wide.
    float profile = (vortexdread_waist + 0.16 * f.wind) + (vortexdread_flare + 0.25 * f.wind) * h * h;

    // And it spreads at the very bottom, in the metre or two where the surface stops the inflow from
    // going any further down and it has to turn.
    profile += 0.22 * (1.0 - smoothstep(0.0, 0.09, h));
    return f.core_radius * profile;
}

float vortexdread_ring_radius(VortexdreadFunnel f) {
    return f.core_radius * (vortexdread_ring_reach + vortexdread_ring_swell * f.ground_load);
}

/**
 * Density of the ground the storm has in the air.
 *
 * <p>Air runs inward along the surface, arrives at the core with nowhere left to go, and what it
 * picked up goes up in a ring around the base. Tallest against the core wall, thinning outward to a
 * ragged edge, and opaque where the condensation above it is a sheath.
 */
float vortexdread_ring_density(VortexdreadFunnel f, vec3 p, float roughness, float clock) {
    if (f.ground_load < 0.004) {
        return 0.0;
    }
    float drop = f.core_radius * vortexdread_ring_drop;
    float above = p.y - f.ground_y;
    if (above < -drop) {
        return 0.0;
    }

    vec2 offset = p.xz - f.axis;
    float radius = length(offset);

    // The edge of a debris cloud is a ring of lobes rolling outward, never a circle. The coordinate
    // closes on itself so the ring has no seam at the back.
    float spin = atan(offset.y, offset.x) + clock * 0.22;
    float lobes = vortexdread_value_noise(vec3(cos(spin), sin(spin), above * 0.04) * 3.2) - 0.5;
    float outer = vortexdread_ring_radius(f) * (1.0 + 0.42 * lobes + 0.2 * roughness);
    if (radius > outer) {
        return 0.0;
    }

    float lid = f.core_radius * (0.35 + 0.6 * f.ground_load)
              * (1.0 - 0.72 * smoothstep(0.1, 1.0, radius / outer));
    // The column's own grain only leans on the stack here rather than setting it. Driving both ends of
    // the fade from one smooth field draws its level sets across the whole dust plane, which reads as
    // contour lines on a map and not as anything a storm does.
    float stack = 1.0 - smoothstep(lid * (0.35 + 0.18 * roughness), lid * (1.25 + 0.35 * roughness), above);
    float edge = smoothstep(outer, outer * 0.62, radius);
    float under = above < 0.0 ? 1.0 - smoothstep(0.0, drop, -above) : 1.0;

    return stack * edge * under * (0.5 + 1.9 * f.ground_load) * f.descent;
}

/**
 * Density of the wall cloud the funnel comes out of.
 *
 * <p>A tornado does not hang off a flat cloud base. The mesocyclone pulls a block of that base down
 * with it, several times wider than the funnel and lowest right against it, and the funnel is that
 * block continuing downward rather than a separate object underneath it.
 */
float vortexdread_wall_cloud_density(VortexdreadFunnel f, vec3 p, float clock) {
    float hang = f.height * vortexdread_wall_hang;
    float rise = hang * vortexdread_wall_rise;
    float below = (f.ground_y + f.height) - p.y;
    if (below < -rise || below > hang) {
        return 0.0;
    }

    vec2 offset = p.xz - f.axis;
    float radius = length(offset);

    // Its own grain, at the scale of the thing rather than at the scale of the column's striations.
    float turn = clock * 0.09;
    vec2 spun = mat2(cos(turn), -sin(turn), sin(turn), cos(turn)) * offset;
    float lumps = vortexdread_fbm(vec3(spun, p.y * 1.4) * (1.6 / max(f.core_radius, 1.0))) - 0.5;
    // A second grain, four times finer. One octave at the scale of the whole lowering only bends its
    // outline, and the underside stays a surface: what it has instead is a stack of ragged shelves,
    // and those live at a fraction of the size of the thing they hang off.
    float shred = vortexdread_fbm(vec3(spun, p.y * 2.8) * (6.4 / max(f.core_radius, 1.0))) - 0.5;

    // The edge is torn rather than drawn: a lowering that ends on a circle reads as a saucer parked
    // over the field, which is the one shape no photograph of one has.
    float reach = f.core_radius * vortexdread_wall_reach * (1.0 + 1.6 * lumps);
    if (radius > reach) {
        return 0.0;
    }

    // Nearly all of the thickness is gone by the rim, which is what makes the shape a bowl hanging off
    // the base rather than a plate resting under it. A lowering keeps a fraction of its depth out
    // there, and that fraction is the whole difference between a wall cloud and a saucer.
    float lip = hang * (1.0 - 0.88 * smoothstep(0.0, 1.0, radius / reach))
              * (1.0 + 1.5 * lumps + 0.9 * shred);
    float body = 1.0 - smoothstep(lip * 0.08, lip * 1.05, below);

    // Upward it keeps going into the deck the pack is already drawing, so there is no lid between the
    // two and no gap either. The fade starts almost at once, or the lowering keeps full weight right up
    // to the deck and the two read as a disc parked under a ceiling instead of one mass.
    float cap = 1.0 - smoothstep(rise * 0.05, rise * 1.2, -below);
    // Ramped the whole way in rather than plateauing: a lowering that holds one density out to a fixed
    // fraction of its reach has a brim, and a brim on a cloud is a hat.
    float rim = smoothstep(reach, reach * 0.12, radius);

    return body * cap * rim * (0.72 + 0.6 * lumps);
}

/**
 * Density of condensate at a point, in camera-relative space.
 *
 * <p>A funnel is a sheath rather than a cone of fog: water condenses where the pressure drop is
 * steepest, which is at the core wall, and the middle of a mature one is comparatively clear.
 */
float vortexdread_density(
    VortexdreadFunnel f,
    vec3 p,
    float clock,
    out float dust_share,
    out float lowering_share
) {
    dust_share = 0.0;
    lowering_share = 0.0;
    float h = clamp01((p.y - f.ground_y) / f.height);
    float radius = length(p.xz - f.axis);

    float wall = vortexdread_wall_radius(f, h);
    float lifted = (p.y - f.ground_y) < f.core_radius * 5.0
        ? vortexdread_ring_radius(f) * vortexdread_ring_rough
        : 0.0;
    float lowered = (p.y - f.ground_y) > f.height * (1.0 - vortexdread_wall_hang)
        ? f.core_radius * vortexdread_wall_reach * 1.8
        : 0.0;
    float outer = max(max(wall * 1.5, lifted), lowered);
    if (radius > outer) {
        return 0.0;
    }

    // The whole column turns and the noise turns with it, so the striations wind round the funnel
    // rather than crawling across a surface that happens to be rotating underneath them.
    float turn = clock * (0.6 + 0.4 * f.wind) / max(wall, 1.0);
    float angle = atan(p.z - f.axis.y, p.x - f.axis.x) + turn;
    // Air climbing the wall turns many times on the way up, so a mark on the surface traces a helix.
    // Winding the sampling ring with height is that helix, and it keeps the noise continuous where
    // shearing the height by the bearing would leave a seam down one side. Narrow columns turn faster
    // for the same wind, which is why the pitch tightens as the wall closes in.
    float wound = angle + (p.y - f.ground_y) * (0.16 + 0.5 / max(wall, 2.0));
    // Frequency against the wisp the player asked for, so the default leaves these numbers where they
    // were tuned and a coarser setting spends its steps on fewer, larger features.
    float fineness = vortexdread_detail_reference / vortexdread_detail();
    vec3 sample_pos = vec3(cos(wound), 0.0, sin(wound)) * (radius * 0.35 * fineness)
                    + vec3(0.0, (p.y * 0.16 - clock * 0.375) * fineness, 0.0);
    float roughness = vortexdread_fbm(sample_pos) - 0.5;

    // A violent tornado rarely turns as one column. Two to five smaller vortices orbit the axis inside
    // the parent circulation and turn faster than it does, and that braid is what tells a photograph of
    // an EF4 from a photograph of an EF1. They live low, where the inflow is fastest and the pressure
    // drop steepest, and they need a parent wide enough to hold more than one. The core radius is the
    // absolute measure of that width: the wind field arrives normalised against each storm's own peak,
    // so a mature EF1 reads the same there as a mature EF5 and would braid just as hard.
    float violence = smoothstep(14.0, 40.0, f.core_radius);
    float suction_count = floor(2.0 + 3.0 * violence);
    float suction_room = smoothstep(0.62, 0.16, h) * violence * smoothstep(0.42, 0.80, f.wind);
    // Leaning with height, because each one is a vortex in its own right riding the parent's updraught
    // and trailing behind where it started.
    float braid = angle * suction_count - clock * 2.3 + (p.y - f.ground_y) * 0.06;
    float suction = (0.5 + 0.5 * cos(braid)) * suction_room;

    float edge = wall * (1.0 + 0.62 * roughness * (1.4 - h) + 0.34 * suction);
    float sheath = smoothstep(edge * 1.05, edge * 0.6, radius) * (1.0 + 1.0 * suction);

    // Hollow only well up the column. Down where the inflow arrives it is full of what it has picked
    // up, and a funnel drawn as a tube all the way to the ground shows daylight through its middle.
    float hollow = mix(1.0, smoothstep(edge * 0.25, edge * 0.65, radius),
                       0.55 * smoothstep(0.32, 0.95, h));
    float reach = smoothstep(1.0 - f.descent - 0.08, 1.0 - f.descent + 0.06, h);

    // The very top thins out instead of ending on a flat lid: the funnel does not stop, it becomes the
    // wall cloud, and a hard edge up there gives the whole illusion away.
    float crown = 1.0 - 0.4 * smoothstep(0.82, 1.0, h);

    float column = sheath * hollow * reach * crown * smoothstep(-2.0, 0.5, p.y - f.ground_y);
    float ring = vortexdread_ring_density(f, p, roughness, clock);
    float lowering = vortexdread_wall_cloud_density(f, p, clock);

    float total = column + ring + lowering;
    dust_share = total > 0.0 ? ring / total : 0.0;
    lowering_share = total > 0.0 ? lowering / total : 0.0;
    return clamp(total, 0.0, 1.6);
}

/** How much light reaches a point from above, by marching toward the sky. */
float vortexdread_light_transmittance(VortexdreadFunnel f, vec3 p, float step_size, float clock) {
    float depth = 0.0;
    float ignored;
    float also_ignored;
    int light_steps = vortexdread_light_march_steps();
    for (int i = 1; i <= light_steps; ++i) {
        depth += vortexdread_density(
            f, p + vec3(0.0, step_size * float(i), 0.0), clock, ignored, also_ignored);
    }
    return exp(-depth * step_size * vortexdread_extinction * 1.6);
}

/** Entry and exit of the view ray through the cylinder one funnel lives in. */
bool vortexdread_bounds(VortexdreadFunnel f, vec3 dir, out float t0, out float t1) {
    float max_radius = max(
        max(vortexdread_wall_radius(f, 1.0) * 1.5, vortexdread_ring_radius(f) * vortexdread_ring_rough),
        f.core_radius * vortexdread_wall_reach
    );
    float floor_y = f.ground_y - f.core_radius * vortexdread_ring_drop;
    float top = f.ground_y + f.height * (1.0 + vortexdread_wall_hang * vortexdread_wall_rise);

    vec2 oc = -f.axis;
    float a = dot(dir.xz, dir.xz);
    float b = 2.0 * dot(oc, dir.xz);
    float c = dot(oc, oc) - max_radius * max_radius;
    if (a < 1.0e-6) {
        if (c > 0.0) {
            return false;
        }
        t0 = 0.0;
        t1 = 1.0e6;
    } else {
        float disc = b * b - 4.0 * a * c;
        if (disc < 0.0) {
            return false;
        }
        float root = sqrt(disc);
        t0 = (-b - root) / (2.0 * a);
        t1 = (-b + root) / (2.0 * a);
    }

    // The camera's own height, in the same absolute frame the funnel was sent in.
    float eye = cameraPosition.y;
    if (abs(dir.y) > 1.0e-6) {
        float ta = (floor_y - eye) / dir.y;
        float tb = (top - eye) / dir.y;
        t0 = max(t0, min(ta, tb));
        t1 = min(t1, max(ta, tb));
    } else if (eye < floor_y || eye > top) {
        return false;
    }

    t0 = max(t0, 0.0);
    return t1 > t0;
}

/**
 * One funnel over the picture.
 *
 * @param scene_color what has been drawn so far, which the column is blended over
 * @param dir         view direction, world aligned
 * @param max_dist    distance to the nearest surface along it, so terrain hides the column
 */
vec3 vortexdread_draw_one(
    VortexdreadFunnel f,
    vec3 scene_color,
    vec3 dir,
    float max_dist,
    vec3 light_color,
    vec3 ambient_color,
    float dither
) {
    float t0;
    float t1;
    if (!vortexdread_bounds(f, dir, t0, t1)) {
        return scene_color;
    }
    t1 = min(t1, max_dist);
    if (t1 <= t0) {
        return scene_color;
    }

    float clock = frameTimeCounter * 2.0;
    int steps = vortexdread_march_steps();
    float step_size = (t1 - t0) / float(steps);

    // Condensation is water, and water is white. What colours a funnel is the ground in it, and even a
    // storm eating a forest comes out grey: the ingested colour is pulled most of the way back to its
    // own brightness before anything is drawn with it.
    float grey = dot(f.tint, vec3(0.299, 0.587, 0.114));
    vec3 body = mix(vec3(grey), f.tint, 0.3);

    // Lit by the pack's own daylight, which is what puts the column in the same photograph as the
    // landscape under it rather than next to it. Darker than the sky it stands against, always.
    vec3 sky_lit = body * (ambient_color * 0.5 + light_color * 0.09);
    vec3 flash_lit = mix(sky_lit, vec3(1.0, 0.96, 0.82) * (ambient_color * 4.5 + 0.15), 0.85);

    // The ring is ground, not water, so it keeps the colour it was torn out of and keeps it darker.
    vec3 dust_lit = mix(vec3(grey), f.tint, 0.95) * (ambient_color * 0.42 + light_color * 0.06);
    vec3 dust_flash_lit =
        mix(dust_lit, vec3(0.95, 0.88, 0.72) * (ambient_color * 3.6 + 0.15), 0.7);

    vec3 scattered = vec3(0.0);
    float transmittance = 1.0;

    for (int i = 0; i < steps; ++i) {
        if (transmittance < 0.02) {
            break;
        }
        float t = t0 + (float(i) + dither) * step_size;
        vec3 p = vec3(dir.x * t, cameraPosition.y + dir.y * t, dir.z * t);
        float dust_share;
        float lowering_share;
        float density = vortexdread_density(f, p, clock, dust_share, lowering_share);
        if (density <= 0.001) {
            continue;
        }
        float sigma = density * vortexdread_extinction * step_size;
        // A second march per sample is the most expensive thing in this loop, and it is paid on every
        // pixel of a funnel that fills the screen. Past this depth the sample is scaled by so little
        // transmittance that whatever shading the march would return cannot be told from a constant.
        float lit = 1.0;
        if (density > vortexdread_lit_threshold) {
            lit = transmittance > 0.15
                ? vortexdread_light_transmittance(f, p, f.height / 48.0, clock)
                : 0.45;
        }

        // The column is shadowed by itself, which is what gives it its volume. The dust at the foot is
        // not: it sits in open light on every side.
        float h = clamp01((p.y - f.ground_y) / f.height);
        float gain = vortexdread_bolt_gain(p, f.flash);
        vec3 material = mix(mix(sky_lit, flash_lit, gain), mix(dust_lit, dust_flash_lit, gain),
                            dust_share);
        float shade = mix(0.12 + 0.88 * lit, 0.3 + 0.4 * lit, dust_share);

        // What a lowering shows the ground is its underside, and the underside of a storm base is
        // the darkest thing in the frame. Left on the column's own shadowing it comes out white,
        // because there is nothing above a thin lowering for the light march to find.
        shade *= mix(1.0, 0.3, lowering_share);

        // Dark at the foot and lighter against the cloud, which is the gradient every photograph of one
        // has: the bottom is buried in its own rain and its own dust, the top is still in the open.
        float height = mix(0.3 + 0.5 * h, 0.5, dust_share);

        float absorbed = 1.0 - exp(-sigma);
        scattered += material * shade * height * absorbed * transmittance;
        transmittance *= exp(-sigma);
    }

    return scene_color * transmittance + scattered;
}

/**
 * The mesocyclone, as a slab of cloud the funnel hangs from.
 *
 * <p>A funnel ending in clear air is the tell that gives a render away. What it comes out of is a
 * rotating mass several kilometres across, and from underneath that mass is a ceiling: dark, banded,
 * turning, with the lowering and then the funnel underneath it.
 *
 * <p>Marched as a slab rather than as a volume. The shape is flat and wide, so the distance a ray
 * spends inside it follows from where it crosses the two planes, and a handful of samples across that
 * distance is as good as a hundred through a bounding cylinder the size of the storm.
 */
/**
 * Which way the air is feeding this storm, as a bearing.
 *
 * <p>Off two fields that do not move with the camera, because anything derived from the axis turns the
 * shelf with the player: a hard edge that swings round the sky as you walk is worse than no edge.
 */
float vortexdread_meso_inflow(VortexdreadFunnel f) {
    return 6.2831853 * fract(f.height * 0.137 + f.ground_y * 0.0193);
}

/**
 * What the rest of the sky does while a storm this size is standing in it.
 *
 * <p>The deck below is only the part a ray can cross. Under a real supercell the whole dome goes that
 * colour for tens of kilometres, and a frame whose middle is black while its edges keep the white of an
 * ordinary rain sky reads as a prop hung in front of the weather.
 *
 * <p>No march, because there is nothing here to resolve: one angle and one distance.
 */
vec3 vortexdread_overcast(VortexdreadFunnel f, vec3 dir, vec3 scene_color, float reach) {
    float near = 1.0 - clamp01(length(f.axis) / max(f.height * 24.0, 1.0));
    float shade = near * reach * (0.35 + 0.65 * f.ground_load) * f.descent;
    return mix(scene_color, scene_color * vec3(0.34, 0.37, 0.42), clamp01(shade) * 0.72);
}

/**
 * What the same storm does to the ground under it.
 *
 * <p>Not darkness. The pack measures the frame and opens its exposure to match, so light taken out of
 * the whole picture is handed back a frame later and what survives is the swing between the two: the
 * same scene comes out near black on one capture and washed white on the next.
 *
 * <p>What a storm actually does to a field holds up under any exposure, because it is not a level. The
 * light arriving there has bounced through a hundred cubic kilometres of cloud and comes down grey from
 * every direction at once, so the greens go grey-blue, the shadows fill in, and nothing in the frame is
 * saturated any more. Drained at the luminance it arrived with, and the eye reads it as a storm.
 */
vec3 vortexdread_pall(VortexdreadFunnel f, vec3 scene_color) {
    float near = 1.0 - clamp01(length(f.axis) / max(f.height * 24.0, 1.0));
    float weight = near * (0.35 + 0.65 * f.ground_load) * f.descent;
    float luma = dot(scene_color, vec3(0.299, 0.587, 0.114));
    vec3 drained = mix(vec3(luma), scene_color, 0.32) * vec3(0.90, 0.96, 1.12);
    return mix(scene_color, drained, clamp01(weight) * 0.70);
}

vec3 vortexdread_draw_meso(
    VortexdreadFunnel f,
    vec3 scene_color,
    vec3 dir,
    float max_dist,
    vec3 ambient_color,
    float dither
) {
    if (abs(dir.y) < 1.0e-4) {
        return scene_color;
    }

    float base = f.ground_y + f.height;
    float floor_y = base - f.height * vortexdread_meso_sag * vortexdread_meso_sag_max;
    float top = base + f.height * vortexdread_meso_thick;

    float eye = cameraPosition.y;
    float ta = (floor_y - eye) / dir.y;
    float tb = (top - eye) / dir.y;
    float t0 = max(min(ta, tb), 0.0);
    float t1 = min(max(ta, tb), max_dist);
    if (t1 <= t0) {
        return scene_color;
    }

    float reach = f.height * vortexdread_meso_reach;
    float clock = frameTimeCounter * 2.0;
    float step_size = (t1 - t0) / float(vortexdread_meso_steps);

    // Hoisted: the mass turns as one piece, so its rotation has nothing to do with where a sample is.
    float turn = clock * 0.05;
    mat2 deck_spin = mat2(cos(turn), -sin(turn), sin(turn), cos(turn));

    // The underside of a storm base, which is what a ground observer sees of it, sits in its own
    // shadow. It is the ceiling the whole scene is lit under, never a bright thing in the frame.
    vec3 under = mix(vec3(dot(f.tint, vec3(0.299, 0.587, 0.114))), f.tint, 0.2)
               * (ambient_color * 0.34);
    vec3 lit_from_within = vec3(1.0, 0.95, 0.8) * (ambient_color * 4.0 + 0.15);

    vec3 scattered = vec3(0.0);
    float transmittance = 1.0;

    for (int i = 0; i < vortexdread_meso_steps; ++i) {
        float t = t0 + (float(i) + dither) * step_size;
        vec3 p = vec3(dir.x * t, eye + dir.y * t, dir.z * t);
        vec2 offset = p.xz - f.axis;
        float radius = length(offset);
        // The early-out has to sit outside the furthest the torn rim below can reach, or it becomes
        // the edge itself: a circle of the same radius on every bearing, which is the one shape a
        // storm base never has.
        if (radius > reach * vortexdread_meso_rim_max) {
            continue;
        }

        float bearing = atan(offset.y, offset.x);
        // The whole field is turned rigidly rather than each sample being placed on a ring whose radius
        // sets its phase. Both read as rotation; only the second one draws its own level sets as rings
        // centred on the storm, and once the deck is light enough for its shading to show at all, those
        // rings are the first thing anyone sees.
        vec3 sample_pos = vec3(deck_spin * offset * (2.2 / reach), (p.y - base) * (6.0 / f.height));
        float grain = vortexdread_fbm(sample_pos);

        // The rim is torn on a bearing rather than on the fine grain, because what a circle of the
        // right size still gets wrong is the outline: a storm reaches much further one way than
        // another, and the shape of that is decided kilometres out, not metre by metre. The bearing
        // enters as a direction rather than as an angle, which is the same circle without the trig.
        float wobble = vortexdread_fbm(vec3(offset / max(radius, 1.0e-4), 0.3) * 2.2);
        float rim = reach * (0.55 + 0.85 * wobble + 0.2 * grain);

        // A lens rather than a slab: thickest over the updraught and thinning to nothing outward. A
        // layer of even thickness ends on a hard line, because a ray near its edge still crosses the
        // whole of it, and that line is what makes a storm base read as a plate in the sky.
        //
        // The falloff is not the same on every bearing. Air rises into the base on the inflow side and
        // the edge there is a wall, the shelf with the hard line under it that every storm chaser
        // photographs; downwind the same base frays out into nothing over kilometres. One exponent for
        // the whole circle draws the saucer this file exists to avoid.
        float inflow = 0.5 + 0.5 * cos(bearing - vortexdread_meso_inflow(f));
        float taper = 1.0 - pow(clamp01(radius / rim), mix(1.5, 3.6, inflow));
        if (taper <= 0.0) {
            continue;
        }
        // Two scales of structure, because one leaves a mass of even shade that no photograph of a
        // storm base has: the grain for the texture, and this for the light and dark of the whole.
        float mass = vortexdread_value_noise(vec3(offset * (2.6 / reach), clock * 0.012));

        float local_top = base + f.height * vortexdread_meso_thick * taper;
        // The underside hangs at a different height everywhere, which is the whole difference between
        // a storm base and a ceiling. Following the same mass that shades it keeps the low places dark.
        //
        // Stepped rather than poured: air feeding a base condenses at a few levels rather than at every
        // height at once, so the underside comes down in shelves with edges on them. Blended back
        // toward the smooth field, since pure steps give the whole thing a staircase.
        float shelves = floor(mass * 4.0) / 4.0;
        float local_floor = base
            - f.height * vortexdread_meso_sag * taper * (0.25 + 2.1 * mix(mass, shelves, 0.55));

        float lid = 1.0 - smoothstep(local_top - f.height * 0.04, local_top, p.y);
        float floor_fade = smoothstep(local_floor, local_floor + f.height * 0.03, p.y);

        // The mass swings the density wide rather than nudging it. A deck whose optical depth saturates
        // everywhere comes out as one flat silhouette cut against the sky, and what a storm base has
        // instead is light and dark across its whole width.
        float density = lid * floor_fade * taper * (0.12 + 0.42 * grain) * (0.25 + 1.6 * mass)
                      * (0.55 + 0.45 * f.ground_load);
        if (density <= 0.001) {
            continue;
        }

        // Lit from the top, which is the only direction light reaches a layer this deep, so what an
        // observer under it sees is the far end of a long absorption and nothing else. Near the rim
        // there is barely any layer left above the sample, which is why the edge of a storm base is
        // the bright part of it.
        float lit = exp(-(local_top - p.y) * 0.075);

        // The same two fields that built the shape also shade it. A deck this deep saturates its own
        // optical depth within the first samples, so everything the geometry does below that is thrown
        // away and what comes back is one flat silhouette: the light and dark of a storm base has to be
        // put on the colour directly. Where the underside hangs lowest there is most cloud overhead,
        // which is why the low places are the black ones.
        float relief = (0.42 + 0.58 * (1.0 - mass)) * (0.75 + 0.5 * grain);

        float sigma = density * vortexdread_extinction * step_size;
        float absorbed = 1.0 - exp(-sigma);
        vec3 flashed = mix(under, lit_from_within, vortexdread_bolt_gain(p, f.flash) * 0.8);
        scattered += flashed * (0.25 + 0.75 * lit) * relief * absorbed * transmittance;
        transmittance *= exp(-sigma);
    }

    return scene_color * transmittance + scattered;
}

/** Every funnel the mod sent, nearest last so the blending holds up when two overlap. */
vec3 vortexdread_draw_funnels(
    vec3 scene_color,
    vec3 dir,
    float max_dist,
    vec3 light_color,
    vec3 ambient_color,
    float dither
) {
    bool touched = false;
    for (int i = 0; i < vortexdread_max_funnels; ++i) {
        VortexdreadFunnel f;
        if (!vortexdread_read(i, f)) {
            continue;
        }
        // The overcast goes first, since everything after it is standing in front of it. How much of a
        // pixel the storm owns is measured two ways, and the pack says which by handing a distance of
        // a million on sky and the render distance on anything solid.
        //
        // On sky it is an angle, starting below the horizon line because a storm this size has already
        // taken the sky behind the hills. Only the last few degrees keep their light, and that thin
        // bright band under the base is what gives the dark mass its size.
        //
        // Solid ground is the other function's business, because taking light out of it is the one
        // thing that does not survive the pack's own exposure.
        if (max_dist > 1.0e5) {
            scene_color =
                vortexdread_overcast(f, dir, scene_color, smoothstep(-0.06, 0.16, dir.y));
        } else {
            scene_color = vortexdread_pall(f, scene_color);
        }
        // The mass next: it is above and behind the funnel from any ground level camera, and the
        // column is what has to end up in front.
        scene_color = vortexdread_draw_meso(f, scene_color, dir, max_dist, ambient_color, dither);
        scene_color =
            vortexdread_draw_one(f, scene_color, dir, max_dist, light_color, ambient_color, dither);
        touched = true;
    }

    // A quantum of jitter on the way out, and only where something was drawn. Everything above
    // deliberately flattens the picture, and a flat picture is where the levels the frame is eventually
    // written to stop hiding: the steps between them land on the iso-luminance lines of the fog and
    // come out as contours drawn across the whole landscape. Half a level of noise puts them back under
    // the grain, which is what a dither is for.
    return touched ? scene_color * (1.0 + (dither - 0.5) * 0.016) : scene_color;
}

#endif // INCLUDE_VORTEXDREAD_FUNNEL

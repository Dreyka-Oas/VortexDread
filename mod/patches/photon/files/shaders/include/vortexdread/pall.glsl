#if !defined INCLUDE_VORTEXDREAD_PALL
#define INCLUDE_VORTEXDREAD_PALL

/*
  What the rest of the picture does while a storm is standing in it.

  The funnel itself is not here and is not drawn by the mod at all. It is the pack's own cumulus layer,
  pulled through the floor of its shell by deck.glsl, so it is lit, shadowed, fogged and tone mapped
  with every other cloud in the frame. What is left for this pass is the two things a cloud pass cannot
  reach: the dome of sky outside the storm, and the ground under it.

  Both matter more than they sound. Under a real supercell the whole sky goes that colour for tens of
  kilometres, and a frame whose middle is dark while its edges keep the white of an ordinary rain sky
  reads as a prop hung in front of the weather.
*/

#include "/include/vortexdread/state.glsl"

// Ranges the mod folded each field into before it fit them into bytes.
const float vortexdread_pall_reach = 2048.0;
const float vortexdread_pall_height_span = 512.0;
const float vortexdread_pall_ground_span = 1152.0;
const float vortexdread_pall_ground_floor = -128.0;
const float vortexdread_pall_radius_span = 128.0;

struct VortexdreadStormLight {
    vec2 axis;      // where the axis is, relative to the camera
    float ground;   // absolute height of the ground under it
    float core;     // core radius, in blocks
    float height;   // ground to cloud base
    float descent;  // 0 hanging out of the cloud, 1 on the ground
    float load;     // how much of the ground it currently has in the air
};

float vortexdread_pall_unpack(float low, float high) {
    return (low * 255.0 + high * 255.0 * 256.0) * (1.0 / 65535.0);
}

bool vortexdread_pall_read(out VortexdreadStormLight storm) {
    if (texelFetch(vortexdread_state, ivec2(5, 0), 0).x < 0.5) {
        return false;
    }

    vec4 place = texelFetch(vortexdread_state, ivec2(0, 0), 0);
    vec4 size = texelFetch(vortexdread_state, ivec2(1, 0), 0);
    vec4 reach = texelFetch(vortexdread_state, ivec2(2, 0), 0);
    vec4 colour = texelFetch(vortexdread_state, ivec2(3, 0), 0);

    storm.axis = vec2(
        vortexdread_pall_unpack(place.x, place.y) * 2.0 * vortexdread_pall_reach
            - vortexdread_pall_reach,
        vortexdread_pall_unpack(place.z, place.w) * 2.0 * vortexdread_pall_reach
            - vortexdread_pall_reach
    );
    storm.ground = vortexdread_pall_unpack(size.x, size.y) * vortexdread_pall_ground_span
        + vortexdread_pall_ground_floor;
    storm.core = vortexdread_pall_unpack(size.z, size.w) * vortexdread_pall_radius_span;
    storm.height = vortexdread_pall_unpack(reach.x, reach.y) * vortexdread_pall_height_span;
    storm.descent = vortexdread_pall_unpack(reach.z, reach.w);
    storm.load = colour.a;
    return storm.height > 1.0;
}

// How much wider than the core the refresh has to reach, and the least it is allowed to be in blocks.
// Wide enough to hold the lowering as well as the funnel, since the two turn together and neither can
// be rebuilt from an earlier frame.
const float vortexdread_turning_span = 4.0;
const float vortexdread_turning_floor = 60.0;

// Where the refresh starts giving way, as a share of that radius. The ramp is the whole point: a pixel
// taken from this frame alone is softer than one that has accumulated twenty, so a boundary crossed in
// one step is a rectangle drawn across the sky in exactly the place the storm was supposed to be. Over
// a span it is a gradient nobody can find.
const float vortexdread_turning_soft = 0.55;

/**
 * How much of a view ray passes through the turning part of the storm, zero to one.
 *
 * <p>The cloud upscaling rebuilds three pixels in four out of the frames before them, on the assumption
 * that a cloud only ever slides with the wind. A column that turns breaks that assumption outright: the
 * reprojection lands on the wrong part of it, and what a player watches is not a storm rotating but a
 * patch of sky updating late, in blocks. Those pixels lean on this frame instead, by this much.
 *
 * @param dir       view direction, world aligned
 * @param camera_y  the eye's own height, since the storm arrives in absolute ones
 */
float vortexdread_turning_pixel(vec3 dir, float camera_y) {
    VortexdreadStormLight storm;
    if (!vortexdread_pall_read(storm)) {
        return 0.0;
    }

    float radius = max(storm.core * vortexdread_turning_span, vortexdread_turning_floor);
    float floor_y = storm.ground - 16.0;
    float top_y = storm.ground + storm.height + 0.5 * storm.height;

    // The slab the storm lives in.
    float near = 0.0;
    float far = 1.0e9;
    if (abs(dir.y) > 1.0e-6) {
        float a = (floor_y - camera_y) / dir.y;
        float b = (top_y - camera_y) / dir.y;
        near = min(a, b);
        far = max(a, b);
    } else if (camera_y < floor_y || camera_y > top_y) {
        return 0.0;
    }
    if (far <= max(near, 0.0)) {
        return 0.0;
    }

    // How near the axis the ray passes, measured where it is nearest rather than where it entered.
    float across = dot(dir.xz, dir.xz);
    float closest;
    if (across < 1.0e-9) {
        closest = length(storm.axis);
    } else {
        float along = clamp(dot(storm.axis, dir.xz) / across, max(near, 0.0), far);
        closest = distance(storm.axis, dir.xz * along);
    }

    return 1.0 - smoothstep(vortexdread_turning_soft * radius, radius, closest);
}

/** How much of the picture this storm owns, from how far away it is standing. */
float vortexdread_pall_weight(VortexdreadStormLight storm) {
    float near = 1.0 - clamp01(length(storm.axis) / max(storm.height * 24.0, 1.0));
    return clamp01(near * (0.35 + 0.65 * storm.load) * storm.descent);
}

/**
 * The sky outside the storm.
 *
 * <p>No march, because there is nothing here to resolve: one angle and one distance. It starts below
 * the horizon line, since a storm this size has already taken the sky behind the hills, and only the
 * last few degrees keep their light. That thin bright band under the base is what gives the dark mass
 * its size.
 */
vec3 vortexdread_overcast(VortexdreadStormLight storm, vec3 scene_color, float reach) {
    float shade = vortexdread_pall_weight(storm) * reach;
    return mix(scene_color, scene_color * vec3(0.52, 0.55, 0.62), shade * 0.68);
}

/**
 * The ground under it.
 *
 * <p>Not darkness. The pack measures the frame and opens its exposure to match, so light taken out of
 * the whole picture is handed straight back and what survives is the swing between the two: the same
 * field comes out near black on one capture and washed white on the next.
 *
 * <p>What a storm actually does to a field holds up under any exposure, because it is not a level. The
 * light arriving there has bounced through a hundred cubic kilometres of cloud and comes down grey from
 * every direction at once, so the greens go grey-blue, the shadows fill in, and nothing in the frame is
 * saturated any more. Drained at the luminance it arrived with, and the eye reads it as a storm.
 */
vec3 vortexdread_pall(VortexdreadStormLight storm, vec3 scene_color) {
    float luma = dot(scene_color, vec3(0.299, 0.587, 0.114));
    vec3 drained = mix(vec3(luma), scene_color, 0.32) * vec3(0.90, 0.96, 1.12);
    return mix(scene_color, drained, vortexdread_pall_weight(storm) * 0.70);
}

/**
 * @param scene_color what has been drawn so far
 * @param dir         view direction, world aligned
 * @param is_sky      whether this pixel is sky rather than something solid
 * @param dither      a quantum of noise, spent on the flat gradients this pass leaves behind
 */
vec3 vortexdread_storm_light(vec3 scene_color, vec3 dir, bool is_sky, float dither) {
    VortexdreadStormLight storm;
    if (!vortexdread_pall_read(storm)) {
        return scene_color;
    }

    scene_color = is_sky
        ? vortexdread_overcast(storm, scene_color, smoothstep(-0.06, 0.16, dir.y))
        : vortexdread_pall(storm, scene_color);

    // Everything above deliberately flattens the picture, and a flat picture is where the levels the
    // frame is eventually written to stop hiding: the steps between them land on the iso-luminance lines
    // of the fog and come out as contours drawn across the whole landscape. Half a level of noise puts
    // them back under the grain, which is what a dither is for.
    return scene_color * (1.0 + (dither - 0.5) * 0.016);
}

#endif // INCLUDE_VORTEXDREAD_PALL

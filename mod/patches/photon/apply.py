#!/usr/bin/env python3
"""Build a copy of Photon that knows what a tornado is.

Photon itself is never modified and never redistributed: the pack you point --pack at is read, the
result is written somewhere else, and the original is left alone. See README.md for what changes and
why.

Every edit is anchored on a line of Photon's own source. If an anchor is missing the script names it
and stops before writing anything, because a pack that half applied is a pack that fails to compile
with an error nobody can trace back to here.
"""

import argparse
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
FILES = HERE / "files"

# Photon's own text on the left, what replaces it on the right. Each entry is (file, anchor, text,
# how_many). Anchors are matched literally, indentation included.
Edit = tuple


def cloud_layer_edits(path: str, extinction: str) -> list:
    """The four edits that turn a flat flash into a point source, for one cloud layer.

    Both volumetric layers march the same way under different names, so the anchors differ only in
    the extinction coefficient each one reaches for.
    """
    return [
        (
            path,
            '#include "common.glsl"',
            '#include "common.glsl"\n#include "/include/vortexdread/lightning.glsl"',
            1,
        ),
        (
            path,
            "    vec2 scattering = vec2(0.0); // x: direct light, y: skylight",
            "    vec2 scattering = vec2(0.0); // x: direct light, y: skylight\n"
            "    vec3 lightning_scattering = vec3(0.0);",
            1,
        ),
        (
            path,
            "        transmittance *= step_transmittance;",
            "        lightning_scattering += vortexdread_lightning_gain(\n"
            "            ray_pos,\n"
            "            air_viewer_pos,\n"
            "            sky_optical_depth,\n"
            f"            {extinction},\n"
            "            step_transmittance\n"
            "        ) * transmittance;\n\n"
            "        transmittance *= step_transmittance;",
            1,
        ),
        (
            path,
            "        scattering.x * light_color + scattering.y * sky_color;",
            "        scattering.x * light_color + scattering.y * sky_color +\n"
            "        lightning_scattering;",
            1,
        ),
    ]


def deck_edits(path: str) -> list:
    """The storm, written into the layer whose base a player actually stands under.

    Only the first layer. What a wall cloud is made of is the deck at cloud base height, and the higher
    layers are the anvil spreading out above it, which does not lower and does not turn on any timescale
    someone under it can see.
    """
    return [
        # Anchored on the pack's own line rather than on the include the patch above adds: every anchor
        # is checked against the untouched pack before the first byte is written.
        (
            path,
            '#include "coverage_map.glsl"',
            '#include "coverage_map.glsl"\n#include "/include/vortexdread/deck.glsl"',
            1,
        ),
        # The tornado. A sample under the floor of the layer is moved back up into the piece of deck it
        # came out of, squeezed toward the axis and wound on the way, so everything below answers it as
        # ordinary cloud and the column comes back with the pack's own grain and the pack's own light.
        # A sample under the floor with no column over it is left where it was, and the pack's own early
        # exit two lines down throws it away.
        (
            path,
            "float clouds_cumulus_density(vec3 pos) {\n    float r = length(pos);",
            "float clouds_cumulus_density(vec3 pos) {\n    float r = length(pos);\n\n"
            "    // Vortex Dread: under the base the deck is the funnel, read from where it was drawn in\n"
            "    vortexdread_warp(pos, r);",
            1,
        ),
        # The shaping is handed both the coverage as it stood and the pack's own shaped result, so it
        # can hold on to the layer's floor where the storm is without redoing the egg everywhere else.
        (
            path,
            "    density = clouds_cumulus_altitude_shaping(density, altitude_fraction);",
            "    // Vortex Dread: the storm, bent into the layer rather than drawn beside it\n"
            "    VortexdreadDeck vortexdread_deck = vortexdread_deck_at(pos.xz);\n"
            "    float vortexdread_coverage = density;\n\n"
            "    density = clouds_cumulus_altitude_shaping(density, altitude_fraction);\n"
            "    density = vortexdread_deck_shape(\n"
            "        vortexdread_coverage, density, altitude_fraction, vortexdread_deck);",
            1,
        ),
        # The last thing the pack does to a sample is thin it toward the floor of the layer, which
        # throws away any base built before it. A storm is the one cloud whose base is flat and low and
        # meant to be stood under, so it is let through.
        (
            path,
            "    density *= 0.1 + 0.9 * smoothstep(0.2, 0.7, altitude_fraction);",
            "    density *= vortexdread_deck_base_taper(\n"
            "        0.1 + 0.9 * smoothstep(0.2, 0.7, altitude_fraction),\n"
            "        vortexdread_deck, altitude_fraction);",
            1,
        ),
        # Turned before the wind carries it, so the rotation is of the cloud field itself and not of a
        # pattern sliding across one.
        (
            path,
            "    pos.xz += cameraPosition.xz * CLOUDS_SCALE + wind.xz;",
            "    pos.xz = vortexdread_deck_swirl(pos.xz, vortexdread_deck);\n"
            "    pos.xz += cameraPosition.xz * CLOUDS_SCALE + wind.xz;",
            1,
        ),
        (
            path,
            "    uint primary_steps =\n"
            "        uint(mix(primary_steps_horizon, primary_steps_zenith, abs(ray_dir.y)));",
            "    // Vortex Dread: read once, since it cannot change between two samples of one ray\n"
            "    vortexdread_gather(air_viewer_pos);\n\n"
            "    uint primary_steps =\n"
            "        uint(mix(primary_steps_horizon, primary_steps_zenith, abs(ray_dir.y)));",
            1,
        ),
        # The column, given a walk of its own under the floor of the shell the pack walks.
        #
        # The deck's own walk is left exactly as the pack wrote it, spacing included, and that is the
        # point rather than an economy: a ray that crosses the column must sample the cloud above it in
        # the same places as the ray beside it, or the cylinder bounding the column draws its own outline
        # across the sky, and an outline in a cloud is a rectangle.
        (
            path,
            "    if (\n"
            "        dists.y < 0.0 // volume not intersected\n"
            "        || planet_intersected &&\n"
            "            r < clouds_cumulus_radius // planet blocking clouds\n"
            "        || terrain_intersected // terrain blocking clouds\n"
            "    ) {\n"
            "        return clouds_not_hit;\n"
            "    }\n"
            "\n"
            "    float ray_length =\n"
            "        (distance_to_terrain >= 0.0) ? distance_to_terrain : dists.y;\n"
            "    ray_length = clamp(ray_length - dists.x, 0.0, max_ray_length);\n"
            "    float step_length = ray_length * rcp(float(primary_steps));\n"
            "\n"
            "    vec3 ray_step = ray_dir * step_length;\n"
            "    vec3 ray_origin =\n"
            "        air_viewer_pos + ray_dir * (dists.x + step_length * dither);",
            "    // Vortex Dread: where the column stands on this ray, if it crosses it at all\n"
            "    vec2 vortexdread_span = vortexdread_column_span(air_viewer_pos, ray_dir);\n"
            "    bool vortexdread_hit = vortexdread_span.y > vortexdread_span.x;\n"
            "    bool vortexdread_layer = dists.y >= 0.0\n"
            "        && !(planet_intersected && r < clouds_cumulus_radius)\n"
            "        && !terrain_intersected;\n"
            "\n"
            "    if (!vortexdread_hit && !vortexdread_layer) {\n"
            "        return clouds_not_hit;\n"
            "    }\n"
            "    if (!vortexdread_layer) {\n"
            "        // Nothing of the layer on this ray, only the column hanging under it.\n"
            "        primary_steps = 0u;\n"
            "        dists = vec2(0.0);\n"
            "    }\n"
            "\n"
            "    float ray_length =\n"
            "        (distance_to_terrain >= 0.0) ? distance_to_terrain : dists.y;\n"
            "    ray_length = clamp(ray_length - dists.x, 0.0, max_ray_length);\n"
            "    float step_length = primary_steps > 0u\n"
            "        ? ray_length * rcp(float(primary_steps))\n"
            "        : 0.0;\n"
            "\n"
            "    vec3 ray_step = ray_dir * step_length;\n"
            "    vec3 ray_origin =\n"
            "        air_viewer_pos + ray_dir * (dists.x + step_length * dither);\n"
            "\n"
            "    // And the column its own, at a spacing taken from the wall the march must not miss\n"
            "    vortexdread_plan_walk(\n"
            "        vortexdread_span, distance_to_terrain >= 0.0 ? distance_to_terrain : 1.0e9);",
            1,
        ),
        # The loop, running the column's samples in front of the deck's.
        (
            path,
            "    for (uint i = 0u; i < primary_steps; ++i) {\n"
            "        if (transmittance < min_transmittance) {\n"
            "            break;\n"
            "        }\n"
            "\n"
            "        vec3 ray_pos = ray_origin + ray_step * i;",
            "    for (uint i = 0u; i < primary_steps + vortexdread_walk_steps; ++i) {\n"
            "        if (transmittance < min_transmittance) {\n"
            "            break;\n"
            "        }\n"
            "\n"
            "        // Vortex Dread: the column's samples first, at their own spacing, then the deck's\n"
            "        bool vortexdread_walking = i < vortexdread_walk_steps;\n"
            "        float sample_length = vortexdread_walking ? vortexdread_walk_step : step_length;\n"
            "        vec3 ray_pos = vortexdread_walking\n"
            "            ? air_viewer_pos + ray_dir * (vortexdread_walk_start\n"
            "                + (float(i) + dither) * vortexdread_walk_step)\n"
            "            : ray_origin + ray_step * (max(i, vortexdread_walk_steps)\n"
            "                - vortexdread_walk_steps);",
            1,
        ),
        (
            path,
            "        float distance_to_sample = distance(ray_origin, ray_pos);",
            "        float distance_to_sample = vortexdread_walking\n"
            "            ? distance(air_viewer_pos, ray_pos) - distance(air_viewer_pos, ray_origin)\n"
            "            : distance(ray_origin, ray_pos);",
            1,
        ),
        (
            path,
            "    float distance_sum = 0.0;\n"
            "    float distance_weight_sum = 0.0;",
            "    float distance_sum = 0.0;\n"
            "    float distance_weight_sum = 0.0;\n"
            "\n"
            "    // Vortex Dread: how much of this ray the column stopped, and where it started doing it\n"
            "    float vortexdread_column_extinct = 0.0;\n"
            "    float vortexdread_deck_extinct = 0.0;\n"
            "    float vortexdread_column_near = -1.0;",
            1,
        ),
        (
            path,
            "        // Update distance to cloud\n"
            "        distance_sum += distance_to_sample * density;",
            "        if (vortexdread_in_column) {\n"
            "            vortexdread_column_extinct += step_optical_depth;\n"
            "            if (vortexdread_column_near < 0.0) {\n"
            "                vortexdread_column_near = distance(air_viewer_pos, ray_pos);\n"
            "            }\n"
            "        } else {\n"
            "            vortexdread_deck_extinct += step_optical_depth;\n"
            "        }\n"
            "\n"
            "        // Update distance to cloud\n"
            "        distance_sum += distance_to_sample * density;",
            1,
        ),
        # A funnel stands where it stands, not where the slab holding it starts.
        #
        # Both the air painted in front of a cloud and the depth the composite tests it against are
        # measured from the point the ray enters the cumulus shell. On a ray pointed near the horizon
        # that point is kilometres out, so a column sixty blocks away is covered with a horizon's worth
        # of haze and comes back the colour of the sky behind it. Nothing is missing from the march: the
        # shape is there and it is opaque, and then it is painted over. Anchored on the column's own
        # distance in proportion to how much of the stopping on this ray the column did, so a ray that
        # only grazes it keeps the layer's perspective.
        (
            path,
            "    clouds_scattering = clouds_aerial_perspective(\n"
            "        clouds_scattering,\n"
            "        clouds_transmittance,\n"
            "        air_viewer_pos,\n"
            "        ray_origin,\n"
            "        ray_dir,\n"
            "        clear_sky\n"
            "    );\n"
            "\n"
            "    float apparent_distance = (distance_weight_sum == 0.0)\n"
            "        ? 1e6\n"
            "        : (distance_sum / distance_weight_sum) +\n"
            "            distance(air_viewer_pos, ray_origin);",
            "    float vortexdread_stopped =\n"
            "        vortexdread_column_extinct + vortexdread_deck_extinct;\n"
            "    float vortexdread_share = vortexdread_stopped > 0.0\n"
            "        ? vortexdread_column_extinct / vortexdread_stopped\n"
            "        : 0.0;\n"
            "    vec3 vortexdread_anchor = vortexdread_column_near < 0.0\n"
            "        ? ray_origin\n"
            "        : mix(ray_origin,\n"
            "              air_viewer_pos + ray_dir * vortexdread_column_near,\n"
            "              vortexdread_share);\n"
            "\n"
            "    clouds_scattering = clouds_aerial_perspective(\n"
            "        clouds_scattering,\n"
            "        clouds_transmittance,\n"
            "        air_viewer_pos,\n"
            "        vortexdread_anchor,\n"
            "        ray_dir,\n"
            "        clear_sky\n"
            "    );\n"
            "\n"
            "    float apparent_distance = (distance_weight_sum == 0.0)\n"
            "        ? 1e6\n"
            "        : (distance_sum / distance_weight_sum) +\n"
            "            distance(air_viewer_pos, ray_origin);\n"
            "    if (vortexdread_column_near >= 0.0) {\n"
            "        apparent_distance = mix(\n"
            "            apparent_distance, vortexdread_column_near, vortexdread_share);\n"
            "    }",
            1,
        ),
        (
            path,
            "        float step_optical_depth =\n"
            "            density * clouds_params.l0_extinction_coeff * step_length;",
            "        float step_optical_depth =\n"
            "            density * clouds_params.l0_extinction_coeff * sample_length;",
            1,
        ),
        # Taken here and not where it is used, because the two light marches in between run their own
        # density calls and each of those leaves the flag describing whatever sample it looked at last.
        #
        # The height too: the march reads it off the sample's own radius, which for a column sample is
        # under the floor of the layer and so negative, and it feeds an optical depth that is spent as
        # exp(-k * x). Negative there is a gain rather than an absorption, large enough to bleach the
        # whole frame. A column sample is at the height it read from.
        (
            path,
            "        float density = clouds_cumulus_density(ray_pos);",
            "        float density = clouds_cumulus_density(ray_pos);\n"
            "        float vortexdread_shade = vortexdread_gloom();\n"
            "        bool vortexdread_in_column = vortexdread_column > 0.0;\n"
            "        float vortexdread_light_depth = vortexdread_column_light_depth();\n"
            "        float vortexdread_sky_depth = vortexdread_column_sky_depth();\n"
            "        if (vortexdread_in_column) {\n"
            "            altitude_fraction = vortexdread_source_level;\n"
            "        }",
            1,
        ),
        # A funnel is lit through the whole storm above it, and the pack's ambient term is shadowed by
        # almost nothing on purpose, so without this the column comes out the brightest thing on screen.
        # The two light marches, answered rather than run for a column sample. Read from the copies
        # taken beside the density call, since each of those marches calls the density function itself
        # and leaves the column flag behind set to whatever its last sample happened to be.
        (
            path,
            "        float light_optical_depth = clouds_cumulus_optical_depth(\n"
            "            ray_pos,\n"
            "            light_dir,\n"
            "            hash.x,\n"
            "            lighting_steps\n"
            "        );\n"
            "        float sky_optical_depth = clouds_cumulus_optical_depth(\n"
            "            ray_pos,\n"
            "            sky_dir,\n"
            "            hash.y,\n"
            "            ambient_steps\n"
            "        );",
            "        float light_optical_depth = vortexdread_in_column\n"
            "            ? vortexdread_light_depth\n"
            "            : clouds_cumulus_optical_depth(\n"
            "                ray_pos, light_dir, hash.x, lighting_steps);\n"
            "        float sky_optical_depth = vortexdread_in_column\n"
            "            ? vortexdread_sky_depth\n"
            "            : clouds_cumulus_optical_depth(\n"
            "                ray_pos, sky_dir, hash.y, ambient_steps);",
            1,
        ),
        (
            path,
            "            ) *\n            transmittance;",
            "            ) *\n            transmittance * vortexdread_shade;",
            1,
        ),
    ]


EDITS = (
    cloud_layer_edits(
        "shaders/include/sky/clouds/cumulus.glsl",
        "clouds_params.l0_extinction_coeff",
    )
    + cloud_layer_edits(
        "shaders/include/sky/clouds/cumulus_congestus.glsl",
        "clouds_cumulus_congestus_extinction_coeff",
    )
    + deck_edits("shaders/include/sky/clouds/cumulus.glsl")
    + [
        # The cumulus layer, lifted. At stock the pack hangs it twelve hundred metres up, which at the
        # ten to one it draws clouds at is a hundred and twenty blocks over a player's head: a storm
        # written into a layer that low has no room to be a shape and comes out as fog on the hills.
        # Two thousand puts the base at two hundred blocks, which is both what a real storm base does
        # and enough height for a funnel to read as a column. TornadoConfig.cloudBaseAltitude follows
        # this number, and the two have to move together or the funnel ends in clear air.
        (
            "shaders/settings.glsl",
            "  #define CLOUDS_CUMULUS_ALTITUDE 1200.0 //",
            "  #define CLOUDS_CUMULUS_ALTITUDE 2000.0 //",
            1,
        ),
        # More steps along a ray that runs near the horizon. The pack's own clouds are low contrast and
        # kilometres wide, so forty samples spread over two thousand blocks never show; a storm is a
        # hard edged object a few hundred blocks across, and at that sample spacing its silhouette comes
        # back quantised into a dithered slab, which is the rectangle a player sees in the sky.
        (
            "shaders/settings.glsl",
            "  #define CLOUDS_CUMULUS_PRIMARY_STEPS_H 40 //",
            "  #define CLOUDS_CUMULUS_PRIMARY_STEPS_H 72 //",
            1,
        ),
        # The layer, held to the depth it had before it was lifted. This setting is a fraction of the
        # altitude and not a depth, so raising the base from twelve hundred to two thousand thickens the
        # deck by the same ratio on its own, and the extinction coefficient does not know that: the path
        # through it lengthens, the multiple scattering that lights a thick cloud here runs away with it,
        # and the whole sky goes to a white that drags the ground under it along. Six tenths of two
        # thousand is twelve hundred, which is the depth the pack was tuned at.
        (
            "shaders/settings.glsl",
            "  #define CLOUDS_CUMULUS_THICKNESS 1.00 //",
            "  #define CLOUDS_CUMULUS_THICKNESS 0.60 //",
            1,
        ),
        # The flat term is added to every cloud pixel on screen at one strength, whatever it is made
        # of and wherever the stroke was, and the Iris uniform behind it rises for any bolt anywhere in
        # the dimension. So a discharge inside one storm brightens the fair weather cumulus sitting on
        # the horizon by the same amount, and a sky in which every cloud in sight flickers together is
        # the one thing a real storm never looks like. Almost all of it is gone: what remains is a trace
        # of the afterglow, which is real, the sky staying lit for a moment after the channel is gone.
        (
            "shaders/include/sky/clouds/sampling.glsl",
            "        result.xyz += LIGHTNING_FLASH_UNIFORM * lightning_flash_intensity *\n"
            "            ambient_scattering;",
            "        result.xyz += LIGHTNING_FLASH_UNIFORM * lightning_flash_intensity *\n"
            "            ambient_scattering * 0.03; // the rest is applied in the volume now",
            1,
        ),
        # Same reason as the layer above: the sky picks up the afterglow, the cloud the bolt is
        # actually inside picks up the bolt.
        (
            "shaders/include/sky/sky.glsl",
            "    result.scattering.rgb += LIGHTNING_FLASH_UNIFORM *\n"
            "        lightning_flash_intensity * result.scattering.a;",
            "    result.scattering.rgb += LIGHTNING_FLASH_UNIFORM *\n"
            "        lightning_flash_intensity * result.scattering.a * 0.02;",
            1,
        ),
        # The clouds, refreshed four times as often and at twice the resolution.
        #
        # The pack rebuilds fifteen pixels in sixteen out of the frames before them, which is free on a
        # cloud field because a cloud field only ever slides with the wind and the reprojection knows
        # exactly how far. A column that turns is the one thing in the sky that assumption does not
        # cover: the history lands on the wrong part of it, the checkerboard hands the pixel back a
        # sixteenth of the time, and what a player watches is not a storm rotating but a patch of sky
        # updating late, in blocks. Halving the stride is what takes that from unwatchable to smooth,
        # and it stays a slider in the pack's own menu for anyone who would rather have the frames.
        (
            "shaders/settings.glsl",
            "  #define CLOUDS_TEMPORAL_UPSCALING 4 // [1 2 3 4]",
            "  #define CLOUDS_TEMPORAL_UPSCALING 2 // [1 2 3 4]",
            1,
        ),
        # And the offset the march starts on moves every frame rather than once a cycle. The pack holds
        # it still so that the pixels of one upscaled block agree with each other; the column does not
        # use that block, and a sample pattern that only changes every fourth frame is a pulse on it.
        # A layer the game's rain has already taken to full coverage has nothing left for the storm to
        # add to, and everything the mod does to it adds. Said here rather than worked around below, so
        # the tower, the lowering and the thinned ring all get the headroom they were written for.
        (
            "shaders/include/weather/clouds.glsl",
            '#include "/include/weather/core.glsl"',
            '#include "/include/weather/core.glsl"\n#include "/include/vortexdread/sky.glsl"\n'
            '#include "/include/vortexdread/layers.glsl"',
            1,
        ),
        (
            "shaders/include/weather/clouds.glsl",
            "CloudsParameters get_clouds_parameters(Weather weather) {\n"
            "    CloudsParameters params;",
            "CloudsParameters get_clouds_parameters(Weather weather) {\n"
            "    weather = vortexdread_storm_sky(weather);\n"
            "    CloudsParameters params;",
            1,
        ),
        # A storm is several decks at several heights, not one deck made thicker, and the pack already
        # marches a second volumetric layer four hundred units over the first. Away from a storm the
        # weather leaves that one nearly empty, which is right for an ordinary day. Filled, it is the
        # anvil, and what stands between the sun and the deck the funnel is cut out of.
        (
            "shaders/include/weather/clouds.glsl",
            "        ));\n\n    return params;\n}",
            "        ));\n\n    return vortexdread_storm_layers(params);\n}",
            1,
        ),
        # The haze under the storm is the other half of the same lie, and it is the one the funnel
        # depends on: its lower half is seen against land, so whatever the air in front of it is made of
        # is what it fades into.
        (
            "shaders/include/weather/fog.glsl",
            '#include "/include/weather/core.glsl"',
            '#include "/include/weather/core.glsl"\n#include "/include/vortexdread/haze.glsl"',
            1,
        ),
        (
            "shaders/include/weather/fog.glsl",
            "    return params;\n}\n\n#endif // INCLUDE_WEATHER_FOG",
            "    return vortexdread_storm_haze(params);\n}\n\n#endif // INCLUDE_WEATHER_FOG",
            1,
        ),
        (
            "shaders/program/d1_clouds.fsh",
            "    dither = r1(frameCounter / checkerboard_area, dither);",
            "    dither = r1(frameCounter, dither);",
            1,
        ),
        (
            "shaders/program/d2_clouds_upscaling.fsh",
            '#include "/include/utility/bicubic.glsl"',
            '#include "/include/utility/bicubic.glsl"\n#include "/include/vortexdread/pall.glsl"',
            1,
        ),
        # Read here, spent on the history weight below. Only there: the obvious second move, mixing a
        # bilinear copy of this frame back into the pixel, reads a different tap of the low resolution
        # buffer than the checkerboard did and comes out systematically brighter, which draws the column's
        # bounding cylinder across the sky as a pale band.
        (
            "shaders/program/d2_clouds_upscaling.fsh",
            "    vec4 current = texelFetch(colortex9, src_texel, 0);",
            "    // Vortex Dread: nothing about a turning column can be rebuilt out of an earlier frame\n"
            "    vec3 vortexdread_dir = normalize(\n"
            "        view_to_scene_space(screen_to_view_space(vec3(uv, 1.0), false, false))\n"
            "        - gbufferModelViewInverse[3].xyz);\n"
            "    float vortexdread_turning =\n"
            "        vortexdread_turning_pixel(vortexdread_dir, eyeAltitude);\n\n"
            "    vec4 current = texelFetch(colortex9, src_texel, 0);",
            1,
        ),
        (
            "shaders/program/d2_clouds_upscaling.fsh",
            "    history_weight *= offcenter_rejection;",
            "    history_weight *= offcenter_rejection;\n"
            "    // Vortex Dread: twenty frames of a rotating column average into a smear\n"
            "    history_weight *= 1.0 - vortexdread_turning;",
            1,
        ),
        # What the storm does to the light in the rest of the frame. The funnel itself is not drawn here
        # and is not drawn by the mod at all: it is the pack's own cumulus layer, pulled through the
        # floor of its shell by deck.glsl. What is left is the dome of sky outside the storm and the
        # ground under it, neither of which a cloud pass can reach.
        # After the uniform block rather than next to the other includes: GLSL will not take a use before
        # its declaration, and this reads the camera.
        (
            "shaders/program/c1_blend_layers.fsh",
            "void main() {",
            '#include "/include/vortexdread/pall.glsl"\n\nvoid main() {',
            1,
        ),
        (
            "shaders/program/c1_blend_layers.fsh",
            "    // Blend fog\n",
            "    // Vortex Dread: the sky and the ground, as a storm this size leaves them\n"
            "    fragment_color = vortexdread_storm_light(\n"
            "        fragment_color,\n"
            "        direction_world,\n"
            "        is_sky,\n"
            "        texelFetch(noisetex, ivec2(gl_FragCoord.xy) & 511, 0).b\n"
            "    );\n\n"
            "    // Blend fog\n",
            1,
        ),
        # The first frame in a world, which the pack spends on a black screen.
        #
        # The running average starts from whatever sits in the alpha of colortex5, and on a fresh world
        # that buffer is cleared, so it starts from zero. Zero is not a NaN and not an infinity, so the
        # guard below lets it through, and the frame is the target exposure blended most of the way
        # toward nothing. What a player sees is the game going black on join and coming back over a
        # second or so, resolving unevenly because the temporal passes behind it are refilling at the
        # same time. Nothing to do with the storm, and it happens to anyone running the pack, but a mod
        # whose whole subject is a dark sky cannot ship a black screen and call it someone else's.
        (
            "shaders/program/c4_taa_exposure.vsh",
            "    if (isnan(previous_exposure) || isinf(previous_exposure)) {",
            "    if (isnan(previous_exposure) || isinf(previous_exposure) ||\n"
            "        previous_exposure <= 0.0) { // a cleared buffer is none of the first two",
            1,
        ),
        # The aperture. Photon reads the frame it was handed and opens up until the median lands where
        # it wants it, so a storm that only takes light out of the picture downstream has that light
        # handed straight back and comes out as an oscillation instead of a mood. Moving the aperture
        # itself is the one change the pack cannot undo a frame later.
        #
        # Applied here at the point the exposure is spent, and not in c4 where it is computed, because
        # c4 stores what it computes as the history the next frame's running average starts from. A
        # factor written in there is re-applied every frame against a target that already carries it,
        # which converges on the factor raised to a power rather than on the factor, and past a certain
        # value does not converge at all. Read from the same texel c14 already reads, so this costs one
        # multiply on a value the pass had in hand anyway.
        (
            "shaders/program/c14_color_grading.fsh",
            "void main() {",
            '#include "/include/vortexdread/exposure.glsl"\n\n'
            "void main() {",
            1,
        ),
        (
            "shaders/program/c14_color_grading.fsh",
            "    float exposure = texelFetch(colortex5, ivec2(0), 0).a;",
            "    float exposure = texelFetch(colortex5, ivec2(0), 0).a;\n"
            "    // Vortex Dread: where the storm overhead puts the aperture\n"
            "    exposure *= vortexdread_exposure_scale();",
            1,
        ),
        # The last step of the eight bit ramp, spent on a pattern that moves rather than one that stands
        # still. The ordered matrix is a fixed weave over the whole screen, invisible on a bright field
        # and a legible thread once a storm has closed the aperture two stops. Rolling the noise every
        # frame spends the same one step and leaves the eye nothing to lock onto.
        (
            "shaders/program/final.fsh",
            "    fragment_color = dither_8bit(fragment_color, bayer16(vec2(texel)));",
            "    fragment_color = dither_8bit(\n"
            "        fragment_color, interleaved_gradient_noise(vec2(texel), int(frameTimeCounter * 100.0)));",
            1,
        ),
        # The state the funnels travel in. Iris resolves a namespaced location through the game's own
        # texture manager, so what is named here is a texture the mod registers and rewrites every
        # frame rather than a file in the pack. Its own sampler rather than a borrowed colortex: the
        # pack keeps its combined depth buffer in the last one, so overriding that would cost the far
        # horizon to anyone running Distant Horizons or Voxy, and the pack declares that sampler itself
        # when either is loaded, which a second declaration of the same name refuses to compile beside.
        (
            "shaders/shaders.properties",
            "# minecraft clouds texture",
            "# Vortex Dread: one row per funnel, written by the mod\n"
            "customTexture.vortexdread_state = vortexdread:textures/effect/funnel_state.png\n\n"
            "# minecraft clouds texture",
            1,
        ),
    ]
)

COPIES = [
    ("shaders/include/vortexdread/state.glsl", "shaders/include/vortexdread/state.glsl"),
    ("shaders/include/vortexdread/lightning.glsl", "shaders/include/vortexdread/lightning.glsl"),
    ("shaders/include/vortexdread/deck.glsl", "shaders/include/vortexdread/deck.glsl"),
    ("shaders/include/vortexdread/pall.glsl", "shaders/include/vortexdread/pall.glsl"),
    ("shaders/include/vortexdread/sky.glsl", "shaders/include/vortexdread/sky.glsl"),
    ("shaders/include/vortexdread/haze.glsl", "shaders/include/vortexdread/haze.glsl"),
    ("shaders/include/vortexdread/layers.glsl", "shaders/include/vortexdread/layers.glsl"),
    ("shaders/include/vortexdread/exposure.glsl", "shaders/include/vortexdread/exposure.glsl"),
]


def unpack(source: Path, workspace: Path) -> Path:
    if source.is_dir():
        root = workspace / "pack"
        shutil.copytree(source, root)
    else:
        root = workspace / "pack"
        root.mkdir()
        with zipfile.ZipFile(source) as archive:
            archive.extractall(root)

    # Some packs ship with everything one level down, and some do not.
    if (root / "shaders").is_dir():
        return root
    inner = [child for child in root.iterdir() if (child / "shaders").is_dir()]
    if len(inner) == 1:
        return inner[0]
    raise SystemExit(f"no shaders folder in {source}")


def check(root: Path) -> list:
    """Every anchor, before a single byte is written."""
    missing = []
    for path, anchor, _, count in EDITS:
        target = root / path
        if not target.is_file():
            missing.append(f"{path}: file not in this pack")
            continue
        found = target.read_text(encoding="utf-8").count(anchor)
        if found != count:
            first = anchor.splitlines()[0]
            missing.append(f"{path}: expected {count} of {first!r}, found {found}")
    return missing


def apply(root: Path) -> None:
    for path, anchor, replacement, count in EDITS:
        target = root / path
        text = target.read_text(encoding="utf-8")
        target.write_text(text.replace(anchor, replacement, count), encoding="utf-8")

    for source, destination in COPIES:
        written = root / destination
        written.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(FILES / source, written)


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch a copy of Photon for Vortex Dread")
    parser.add_argument("--pack", required=True, type=Path, help="Photon zip or extracted folder")
    parser.add_argument("--out", required=True, type=Path, help="folder to write, replaced if present")
    args = parser.parse_args()

    if not args.pack.exists():
        print(f"no pack at {args.pack}", file=sys.stderr)
        return 1

    with tempfile.TemporaryDirectory(prefix="vortexdread-photon-") as scratch:
        root = unpack(args.pack, Path(scratch))
        missing = check(root)
        if missing:
            print("this pack is not the one the patch was written against:", file=sys.stderr)
            for line in missing:
                print(f"  {line}", file=sys.stderr)
            return 2
        apply(root)

        if args.out.exists():
            shutil.rmtree(args.out)
        shutil.copytree(root, args.out)

    print(f"written to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

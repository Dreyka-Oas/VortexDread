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


EDITS = (
    cloud_layer_edits(
        "shaders/include/sky/clouds/cumulus.glsl",
        "clouds_params.l0_extinction_coeff",
    )
    + cloud_layer_edits(
        "shaders/include/sky/clouds/cumulus_congestus.glsl",
        "clouds_cumulus_congestus_extinction_coeff",
    )
    + [
        # What is left of the flat term is the afterglow, which is a real thing: the sky stays lit for
        # a moment after the channel is gone. What it stops being is the whole effect.
        (
            "shaders/include/sky/clouds/sampling.glsl",
            "        result.xyz += LIGHTNING_FLASH_UNIFORM * lightning_flash_intensity *\n"
            "            ambient_scattering;",
            "        result.xyz += LIGHTNING_FLASH_UNIFORM * lightning_flash_intensity *\n"
            "            ambient_scattering * 0.35; // the rest is applied in the volume now",
            1,
        ),
        # Same reason as the layer above: the sky picks up the afterglow, the cloud the bolt is
        # actually inside picks up the bolt.
        (
            "shaders/include/sky/sky.glsl",
            "    result.scattering.rgb += LIGHTNING_FLASH_UNIFORM *\n"
            "        lightning_flash_intensity * result.scattering.a;",
            "    result.scattering.rgb += LIGHTNING_FLASH_UNIFORM *\n"
            "        lightning_flash_intensity * result.scattering.a * 0.3;",
            1,
        ),
        (
            "shaders/include/misc/material_masks.glsl",
            "// Special material for dragon death beams\n#define MATERIAL_DRAGON_BEAM 103",
            "// Special material for dragon death beams\n#define MATERIAL_DRAGON_BEAM 103\n\n"
            "// Vortex Dread: the funnel and the cloud base it hangs from\n"
            "#define MATERIAL_TORNADO 200",
            1,
        ),
        (
            "shaders/entity.properties",
            "#if MC_VERSION >= 11300",
            "#if MC_VERSION >= 11300\n\n"
            "# Vortex Dread funnel\nentity.10200 = vortexdread:tornado",
            1,
        ),
        # The funnel itself. It goes in the pass that has blended the translucent layer and has not yet
        # applied the fog, so the column is occluded by terrain, fogged with everything else and tone
        # mapped with the rest of the frame.
        # After the uniform block rather than next to the other includes: the funnel reads the camera
        # and the clock, and GLSL will not take a use before its declaration.
        (
            "shaders/program/c1_blend_layers.fsh",
            "void main() {",
            '#include "/include/vortexdread/funnel.glsl"\n\nvoid main() {',
            1,
        ),
        (
            "shaders/program/c1_blend_layers.fsh",
            "    // Blend fog\n",
            "    // Vortex Dread: the funnel, marched through the scene as cloud\n"
            "    fragment_color = vortexdread_draw_funnels(\n"
            "        fragment_color,\n"
            "        direction_world,\n"
            "        is_sky ? 1.0e6 : view_distance,\n"
            "        light_color,\n"
            "        ambient_color,\n"
            "        texelFetch(noisetex, ivec2(gl_FragCoord.xy) & 511, 0).b\n"
            "    );\n\n"
            "    // Blend fog\n",
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
        # frame rather than a file in the pack.
        (
            "shaders/shaders.properties",
            "# minecraft clouds texture",
            "# Vortex Dread: one row per funnel, written by the mod\n"
            "texture.composite.colortex15 = vortexdread:textures/effect/funnel_state.png\n\n"
            "# minecraft clouds texture",
            1,
        ),
        (
            "shaders/program/gbuffers_all_translucent.fsh",
            "#if defined PROGRAM_GBUFFERS_ENTITIES_TRANSLUCENT\n"
            "        // Lightning (old versions)",
            "#if defined PROGRAM_GBUFFERS_ENTITIES_TRANSLUCENT\n"
            "        // Vortex Dread: condensate, already lit by the mod's own march\n"
            "        if (material_mask == MATERIAL_TORNADO) {\n"
            "            fragment_color.rgb *= 1.0 - 0.35 * smoothstep(0.0, 600.0, length(position_scene));\n"
            "        }\n\n"
            "        // Lightning (old versions)",
            1,
        ),
    ]
)

COPIES = [
    ("shaders/include/vortexdread/lightning.glsl", "shaders/include/vortexdread/lightning.glsl"),
    ("shaders/include/vortexdread/funnel.glsl", "shaders/include/vortexdread/funnel.glsl"),
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

# Photon patch

Vortex Dread draws its own funnel and its own storm, and it looks the way it is meant to without any
shaderpack at all. This folder is for the other case: you run Photon, and you want the storm the mod
builds to be lit by the pack rather than beside it.

Photon is not included here. Its licence does not allow redistribution on a platform that pays its
uploaders, and this mod is on two of those. What is included is a script that edits a copy of a pack
you already downloaded.

## What it changes

Lightning stops being an exposure change. Photon has one flash factor and adds it to every cloud pixel
at the same strength, so a stroke on the horizon brightens the cloud above your head by exactly as
much as the cloud it is inside. Iris passes the position of the bolt in `lightningBoltPosition`, and
the patch feeds that into the cumulus march as a point source: inverse square from the channel, and
the light still has to climb out through whatever sits above the sample before anyone sees it. A
storm lights up from the inside, in the part of it the stroke went through.

That is what makes the mod's internal flashes work. Vortex Dread drops visual-only bolts inside the
funnel and inside the mesocyclone; with the patch on, each one lights the cloud it is standing in.

The funnel is tagged as its own material so the pack knows what it is looking at, and it is shaded
with the pack's cloud phase functions and its aerial perspective instead of the generic translucent
entity path. A tornado forty chunks away then sits behind the same haze as everything else at that
distance, which is most of what makes it read as being where it is.

## Applying it

```
python3 apply.py --pack ~/.minecraft/shaderpacks/photon_v1.3b.zip --out ~/.minecraft/shaderpacks/photon_vortexdread
```

`--pack` takes the zip or an already extracted folder. `--out` is written from scratch; it is removed
first if it exists, so point it somewhere that holds nothing else. Select the result in the shader
screen like any other pack.

The script checks every anchor before it writes anything. If Photon changes a line the patch leans on,
it says which one and stops rather than producing a pack that fails to compile. Written against
Photon v1.3b.

## Removing it

Delete the output folder and select the original pack again. Nothing is written back into the pack you
downloaded.

# Photon patch

The tornado is made of cloud. Not a model, not a particle system, not a cone with a scrolling texture
on it: the funnel is the cumulus layer Photon was already drawing over your head, pulled down through
the floor of its own shell and wound around an axis. That is what this folder is for. Run Photon with
this patch applied and the storm is built out of the sky you were standing under; run it without, and
the mod still spawns tornadoes, still tears the ground up and still throws you, but the funnel is not
there to look at.

Photon is not included here. Its licence does not allow redistribution on a platform that pays its
uploaders, and this mod is on two of those. What is included is a script that edits a copy of a pack
you already downloaded.

## What it changes

**The funnel is the cloud deck.** Every sample the pack takes under the cloud base inside the column is
moved to the piece of deck it came out of, opened out by how much the vortex is pinched and turned by
how far it has fallen, and handed back to the pack as ordinary cumulus. So the funnel has the lumps of
the cloud it hangs from, it is lit by the same sun with the same phase functions, it goes behind the
same haze at the same distance, and it rotates because the cloud it is reading is being read through a
rotation. Change the tornado's width and the column genuinely widens, because the width is what decides
which ring of deck each height reads.

**The sky above it is organised.** Coverage is added inside the mesocyclone so the gaps close into one
mass, the base is let down to the floor of the layer under the funnel and scalloped so its underside is
not a flat table, and the ring outside is thinned so the mass in the middle reads as a mass. The deck
turns, slowly, faster toward the middle.

**The storm is several decks, not one deck made thicker.** What makes a photograph of a supercell read
as enormous is that you can count the levels in it: the hard flat base of the updraft, the boiling mass
above it, the anvil spread across the whole sky where the tower hit the tropopause and could not rise
any further, and the cirrus torn off the top of that and carried downwind. Photon already marches a
second volumetric layer four hundred units over the first and already draws planar cirrus over both;
away from a storm its weather leaves them nearly empty, which is right for an ordinary day. The patch
fills them while a storm holds the sky. The anvil then pays for itself twice, once as the mass a viewer
reads the height of the storm against, and once as the thing standing between the sun and the deck the
funnel is cut out of, which is what darkens that deck without a single number being lowered anywhere.

**The storm says what its own weather is.** The game's rain drives Photon's humidity to one and its
cloud coverage with it, and a layer already at full coverage has nothing left for any of the above to
add to. Left alone the storm builds beautifully while the rain comes on and then dissolves into a flat
sheet the moment it arrives. The patch tells the cloud layer the air is drier, warmer and windier than
the rain suggests, which is what the air over a supercell actually is.

**The haze under it goes dark instead of pale.** A tornado's lower half is seen against land, through a
kilometre of rain haze, and Photon's rain haze is bright. A dark funnel through it washes out from
half its height down. Under the storm the same air takes more light out and gives less back, so the
funnel stays readable to the ground and the land under the base loses its colour.

**Lightning stops being an exposure change.** Photon has one flash factor and adds it to every cloud
pixel at the same strength, so a stroke on the horizon brightens the cloud above your head by exactly
as much as the cloud it is inside. Iris passes the position of the bolt in `lightningBoltPosition`, and
the patch feeds that into the cumulus march as a source with a shape: a channel running up from the
bolt rather than a point at it, a narrow lobe for the cloud the stroke is buried in and a far wider one
for the mass around it, since that mass is itself scattering the light and becomes a lamp in its own
right. So the storm lights up over its whole width with the part the stroke went through brightest, and
a strike that comes down to the ground lights the cloud it came out of rather than only the field it
landed in. The light still has to climb out through whatever sits above the sample before anyone sees
it, which is what gives the lit region an edge.

**The turning part of the sky is not reused between frames.** Photon rebuilds three cloud pixels in
four out of the frames before them, on the assumption that a cloud only ever slides with the wind. A
column that turns breaks that outright, and what a player sees is not a storm rotating but a patch of
sky updating late, in blocks. Those pixels lean on the current frame instead, over a soft edge so the
boundary is a gradient rather than a rectangle drawn across the sky.

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

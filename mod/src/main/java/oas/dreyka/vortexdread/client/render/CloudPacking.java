package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.atmosphere.SkySnapshot;

/**
 * Turning a field of floats into the bytes a sampler reads back.
 *
 * <p>Eight bits per cell, scaled to the field's own peak. The peak is the scale for the same reason the
 * network encoding picks it: an absolute one would spend most of its range on values a sky never
 * reaches, since a cumulus peaks four orders of magnitude below one. Sixteen bits would need two
 * channels and hand-written filtering, because interpolating a high byte and a low byte separately is
 * meaningless across a carry.
 *
 * <p>Square rooted on the way in and squared on the way back out, which is the whole difference between
 * a usable sky and a dotted one. Nearly all of a cloud field is faint: the wisp around a cumulus lives
 * in the bottom few hundredths of the peak, and a linear scale leaves that whole region three or four
 * levels to work with, so the filter reconstructs it as a lattice of noughts and ones. The root spends
 * a quarter of the range under a sixteenth of the peak, which is where the eye is looking.
 *
 * <p>Separate from {@link CloudAtlas} because the layout is read by three callers and this walk by one.
 * Where a cell lands in the image is arithmetic on the grid's shape; what number lands there is a
 * question about the field, and the two change for different reasons.
 */
public final class CloudPacking {

    private static final int LEVELS = 255;

    /** Red, green, blue, alpha, in the order the shader reads them off a sampler. */
    private static final int[] SHIFTS = {16, 8, 0, 24};

    /** Where one texel of the atlas goes, so the packing can be pinned without a graphics context. */
    public interface Texels {

        /** Alpha, red, green, blue from the top, which is the word the game's image class stores. */
        void put(int x, int y, int argb);
    }

    /**
     * What one written field turned out to hold: the scale its levels are a fraction of, and the
     * altitudes that have water in them, as cell indices with the high one past the last wet cell.
     *
     * <p>The band is what keeps the marcher honest. Cloud occupies a few hundred metres of a volume
     * three kilometres tall, so a ray told to spread a fixed number of steps over the whole thing spends
     * most of them in clear air and crosses the cloud in two, which is what draws a cloud in slabs.
     */
    public record Filled(float peak, int lowestCell, int pastHighestCell) {
    }

    private CloudPacking() {
    }

    /** Writes one snapshot into an image of that atlas's shape, and says what it found doing it. */
    public static Filled write(CloudAtlas atlas, SkySnapshot sky, Texels into) {
        if (!atlas.fits(sky)) {
            throw new IllegalArgumentException("this atlas is not shaped for " + sky.sizeX + " by "
                    + sky.sizeY + " by " + sky.sizeZ);
        }
        float peak = 0.0f;
        for (float value : sky.cloudWater) {
            peak = Math.max(peak, value);
        }
        // Under half a level a cell rounds to nothing on the card, so it is out of the band whatever
        // the float says. Half a level is the square of half a step, since the scale is a root.
        float faintest = peak * (0.5f / LEVELS) * (0.5f / LEVELS);
        int perLayer = sky.sizeX * sky.sizeZ;
        int lowest = sky.sizeY;
        int past = 0;
        for (int at = 0; at < sky.cloudWater.length; at++) {
            if (sky.cloudWater[at] > faintest) {
                int y = at / perLayer;
                lowest = Math.min(lowest, y);
                past = y + 1;
            }
        }
        float ofPeak = peak <= 0.0f ? 0.0f : 1.0f / peak;
        for (int tile = 0; tile < atlas.tiles(); tile++) {
            writeTile(atlas, sky, into, tile, ofPeak);
        }
        return new Filled(peak, Math.min(lowest, past), past);
    }

    private static void writeTile(CloudAtlas atlas, SkySnapshot sky, Texels into, int tile,
            float ofPeak) {
        int left = atlas.tileLeft(tile) + CloudAtlas.BORDER;
        int top = atlas.tileTop(tile) + CloudAtlas.BORDER;
        int lowest = tile * CloudAtlas.PER_TEXEL;
        for (int z = -CloudAtlas.BORDER; z < sky.sizeZ + CloudAtlas.BORDER; z++) {
            // The wrap is the simulation's own: both horizontal axes tile, so the border holds the far
            // edge of the same tile and a sample straddling the seam reads a continuous field.
            int wrappedZ = Math.floorMod(z, sky.sizeZ);
            for (int x = -CloudAtlas.BORDER; x < sky.sizeX + CloudAtlas.BORDER; x++) {
                int wrappedX = Math.floorMod(x, sky.sizeX);
                int argb = 0;
                for (int channel = 0; channel < CloudAtlas.PER_TEXEL; channel++) {
                    int y = lowest + channel;
                    // The topmost tile runs past the grid when the height is not a multiple of four.
                    // Those channels read as empty air, which is what is above the model anyway.
                    int level = y >= sky.sizeY ? 0
                            : level(sky.cloudWater[sky.index(wrappedX, y, wrappedZ)], ofPeak);
                    argb |= level << SHIFTS[channel];
                }
                into.put(left + x, top + z, argb);
            }
        }
    }

    /** One cell as a level, on the root scale the shader squares back. */
    private static int level(float value, float ofPeak) {
        float root = (float) Math.sqrt(Math.max(0.0f, value * ofPeak));
        return Math.min(LEVELS, Math.max(0, Math.round(root * LEVELS)));
    }
}

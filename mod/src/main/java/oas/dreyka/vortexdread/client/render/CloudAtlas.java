package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.atmosphere.SkySnapshot;

/**
 * The cloud volume laid out flat, because this version of the game has no three dimensional texture.
 *
 * <p>Blaze3D creates two dimensional textures and nothing else: there is no GL_TEXTURE_3D anywhere in it,
 * and the depth argument its texture call takes is for cube faces. So the volume goes into one image as a
 * grid of tiles, which is how every volumetric renderer worked before hardware had the third dimension.
 *
 * <p>Four altitudes per tile rather than one, one per channel, because the image is RGBA whether or not
 * the fourth dimension is wanted and a single channel throws three quarters of it away. It buys more than
 * the memory: a sample between two altitudes is one fetch instead of two whenever both sit in the same
 * texel, which is three times in four, and the marcher spends most of its frame on exactly that fetch.
 *
 * <p>Every tile carries a one texel border, and that border is not padding. Hardware filtering inside a
 * tile would otherwise reach into the tile packed beside it, which puts cloud from one altitude on the
 * edge of another, and it would also lose the horizontal wrap: the simulation's x and z axes tile, so the
 * border is the opposite edge of the same tile rather than a repeat of the nearest column.
 *
 * <p>Eight bits per cell. Sixteen would need two channels and hand-written filtering, since interpolating
 * a high byte and a low byte separately is meaningless across a carry. If a screenshot shows steps on a
 * cloud edge, that is the measurement that changes this, not an argument.
 */
public final class CloudAtlas {

    /** Altitudes in one texel, which is the channel count of the image and not a tuning number. */
    public static final int PER_TEXEL = 4;

    /** How wide the border around each tile is, in texels, on every side. */
    public static final int BORDER = 1;

    private static final int LEVELS = 255;

    /** Red, green, blue, alpha, in the order the shader reads them off a sampler. */
    private static final int[] SHIFTS = {16, 8, 0, 24};

    /** Where one texel of the atlas goes, so the layout can be pinned without a graphics context. */
    public interface Texels {

        /** Alpha, red, green, blue from the top, which is the word the game's image class stores. */
        void put(int x, int y, int argb);
    }

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private final int tiles;

    /** Tiles per row of the atlas, chosen so the image comes out roughly square. */
    private final int tilesAcross;

    private final int tileWidth;
    private final int tileHeight;
    private final int width;
    private final int height;

    public CloudAtlas(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.tiles = (sizeY + PER_TEXEL - 1) / PER_TEXEL;
        this.tilesAcross = Math.max(1, (int) Math.ceil(Math.sqrt(tiles)));
        this.tileWidth = sizeX + 2 * BORDER;
        this.tileHeight = sizeZ + 2 * BORDER;
        this.width = tilesAcross * tileWidth;
        this.height = ((tiles + tilesAcross - 1) / tilesAcross) * tileHeight;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** What the shader needs to turn an altitude into a tile position, along with the tile size. */
    public int tilesAcross() {
        return tilesAcross;
    }

    public int tileWidth() {
        return tileWidth;
    }

    public int tileHeight() {
        return tileHeight;
    }

    /** Whether a snapshot is the shape this atlas was laid out for. A server can change the grid. */
    public boolean fits(SkySnapshot sky) {
        return sky.sizeX == sizeX && sky.sizeY == sizeY && sky.sizeZ == sizeZ;
    }

    /**
     * Writes one snapshot into an image of this shape.
     *
     * <p>The peak is the scale, the same choice the network encoding makes and for the same reason: an
     * absolute scale would spend most of its range on values a sky never reaches, and the peak of a cloud
     * field is four orders of magnitude below one.
     *
     * @return the peak the levels are a fraction of, which the shader needs to read a density back out
     */
    public float write(SkySnapshot sky, Texels into) {
        if (!fits(sky)) {
            throw new IllegalArgumentException("this atlas is shaped for " + sizeX + " by " + sizeY
                    + " by " + sizeZ + ", not " + sky.sizeX + " by " + sky.sizeY + " by " + sky.sizeZ);
        }
        float peak = 0.0f;
        for (float value : sky.cloudWater) {
            peak = Math.max(peak, value);
        }
        float toLevels = peak <= 0.0f ? 0.0f : LEVELS / peak;
        for (int tile = 0; tile < tiles; tile++) {
            writeTile(sky, into, tile, toLevels);
        }
        return peak;
    }

    private void writeTile(SkySnapshot sky, Texels into, int tile, float toLevels) {
        int left = (tile % tilesAcross) * tileWidth + BORDER;
        int top = (tile / tilesAcross) * tileHeight + BORDER;
        int lowest = tile * PER_TEXEL;
        for (int z = -BORDER; z < sizeZ + BORDER; z++) {
            // The wrap is the simulation's own: both horizontal axes tile, so the border holds the far
            // edge of the same tile and a sample straddling the seam reads a continuous field.
            int wrappedZ = Math.floorMod(z, sizeZ);
            for (int x = -BORDER; x < sizeX + BORDER; x++) {
                int wrappedX = Math.floorMod(x, sizeX);
                int argb = 0;
                for (int channel = 0; channel < PER_TEXEL; channel++) {
                    int y = lowest + channel;
                    // The topmost tile runs past the grid when the height is not a multiple of four.
                    // Those channels read as empty air, which is what is above the model anyway.
                    int level = y >= sizeY ? 0
                            : level(sky.cloudWater[sky.index(wrappedX, y, wrappedZ)], toLevels);
                    argb |= level << SHIFTS[channel];
                }
                into.put(left + x, top + z, argb);
            }
        }
    }

    private static int level(float value, float toLevels) {
        return Math.min(LEVELS, Math.max(0, Math.round(value * toLevels)));
    }
}

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
 * the fourth dimension is wanted. It buys more than the memory: a sample between two altitudes is one
 * fetch instead of two whenever both sit in the same texel, which is three times in four, and the
 * marcher spends most of its frame on exactly that fetch.
 *
 * <p>Every tile carries a one texel border, and that border is not padding. Hardware filtering inside a
 * tile would otherwise reach into the tile packed beside it, which puts cloud from one altitude on the
 * edge of another, and it would also lose the horizontal wrap: the simulation's x and z axes tile, so the
 * border is the opposite edge of the same tile rather than a repeat of the nearest column.
 *
 * <p>This class is the layout alone, and {@link CloudPacking} is what fills it. The shader repeats the
 * same arithmetic off three numbers in a uniform, so any change here is a change there.
 */
public final class CloudAtlas {

    /** Altitudes in one texel, which is the channel count of the image and not a tuning number. */
    public static final int PER_TEXEL = 4;

    /** How wide the border around each tile is, in texels, on every side. */
    public static final int BORDER = 1;

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

    public int sizeY() {
        return sizeY;
    }

    public int tiles() {
        return tiles;
    }

    /** The left edge of one tile in the image, border included, which is where the shader starts. */
    public int tileLeft(int tile) {
        return (tile % tilesAcross) * tileWidth;
    }

    public int tileTop(int tile) {
        return (tile / tilesAcross) * tileHeight;
    }

    /** Whether a snapshot is the shape this atlas was laid out for. A server can change the grid. */
    public boolean fits(SkySnapshot sky) {
        return sky.sizeX == sizeX && sky.sizeY == sizeY && sky.sizeZ == sizeZ;
    }
}

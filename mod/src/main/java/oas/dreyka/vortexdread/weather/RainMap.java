package oas.dreyka.vortexdread.weather;

import oas.dreyka.vortexdread.atmosphere.SkySnapshot;

/**
 * Where it is raining, one answer per column of sky.
 *
 * <p>Minecraft holds a single switch for a world thirty million blocks wide, so a storm anywhere means rain
 * everywhere and a clear horizon under a downpour. The field this is built from already says how much water
 * sits over any given point, cell by cell, so the honest answer per position was there the whole time and
 * nothing was asking for it.
 *
 * <p>A column total rather than a cell reading, because depth of water overhead is what falls: one wet cell
 * high up is a wisp, six stacked is a shower. The total is a path integral through the cloud, water per kilo
 * of air times the metres it spans, which comes out as a length and reads as the depth of the puddle that
 * column would make if it all fell at once.
 *
 * <p>Built once per step on the worker, then read from the tick and the frame without a lock. Every field is
 * final and the arrays are never written after the constructor returns, so the reference publishing it is
 * the only thing that has to be safe.
 */
public final class RainMap {

    /**
     * Column depth a sky can hold and still be dry, metres of water.
     *
     * <p>Condensation starts as a trace across a great many cells well before any of them holds a drop worth
     * falling, so a map with no floor has the whole sky drizzling from the first cumulus. The number is the
     * depth a fair-weather cumulus reaches, measured on the shipped grid: below it the cloud is something to
     * look at, above it the cloud is something to stand under.
     */
    public static final float WETTEST_DRY_COLUMN = 0.05f;

    /** Column depth that reads as full rain, metres of water. Past it a heavier cloud changes nothing. */
    public static final float HEAVIEST_COLUMN = 0.5f;

    private static final RainMap DRY = new RainMap(new float[0], 1, 1, 1.0f, 0.0f);

    private final float[] strength;
    private final int sizeX;
    private final int sizeZ;
    private final float cellSize;
    private final float heaviest;

    private RainMap(float[] strength, int sizeX, int sizeZ, float cellSize, float heaviest) {
        this.strength = strength;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.cellSize = cellSize;
        this.heaviest = heaviest;
    }

    /** The map for a world with no sky yet: a server still opening one, or a client not sent a field. */
    public static RainMap dry() {
        return DRY;
    }

    /** Sums each column of the field into a rain strength. One pass, which is why it runs on the worker. */
    public static RainMap of(SkySnapshot sky) {
        float[] strength = new float[sky.sizeX * sky.sizeZ];
        float heaviest = 0.0f;
        for (int y = 0; y < sky.sizeY; y++) {
            for (int z = 0; z < sky.sizeZ; z++) {
                int row = z * sky.sizeX;
                int fromField = (y * sky.sizeZ + z) * sky.sizeX;
                for (int x = 0; x < sky.sizeX; x++) {
                    strength[row + x] += sky.cloudWater[fromField + x];
                }
            }
        }
        for (int column = 0; column < strength.length; column++) {
            float depth = strength[column] * sky.cellSize;
            float scaled = (depth - WETTEST_DRY_COLUMN) / (HEAVIEST_COLUMN - WETTEST_DRY_COLUMN);
            float held = Math.min(1.0f, Math.max(0.0f, scaled));
            strength[column] = held;
            if (held > heaviest) {
                heaviest = held;
            }
        }
        return new RainMap(strength, sky.sizeX, sky.sizeZ, sky.cellSize, heaviest);
    }

    /**
     * Whether a simulated sky is behind this map at all.
     *
     * <p>Distinct from {@link #raining()} on purpose, and the distinction is the difference between two
     * worlds that look identical from a boolean: one where the mod has decided it is not raining, and one
     * where the mod has decided nothing because no field has arrived. The first overrides the game, the
     * second must leave it alone or a player on a server without the mod watches their rain stop falling.
     */
    public boolean arrived() {
        return strength.length > 0;
    }

    /** Whether anywhere in the domain is under rain, which is the answer the game's own switch wants. */
    public boolean raining() {
        return heaviest > 0.0f;
    }

    /** The strongest rain anywhere, 0 to 1, for the one gradient a client has to set for the whole world. */
    public float heaviest() {
        return heaviest;
    }

    /** Whether it rains on a block, in world coordinates. */
    public boolean rainsAt(int blockX, int blockZ) {
        return strengthAt(blockX, blockZ) > 0.0f;
    }

    /** How hard it rains on a block, 0 to 1, in world coordinates. */
    public float strengthAt(int blockX, int blockZ) {
        if (strength.length == 0) {
            return 0.0f;
        }
        return strengthAtCell(cellOf(blockX, sizeX), cellOf(blockZ, sizeZ));
    }

    /** Whether it rains on a cell of the grid, for tests and for whoever already has cell coordinates. */
    public boolean rainsAtCell(int x, int z) {
        return strengthAtCell(x, z) > 0.0f;
    }

    public float strengthAtCell(int x, int z) {
        if (strength.length == 0 || x < 0 || z < 0 || x >= sizeX || z >= sizeZ) {
            return 0.0f;
        }
        return strength[z * sizeX + x];
    }

    // The domain covers a few kilometres and a world covers thirty million, so the sky tiles rather than
    // running out. Same wrap the renderer applies to the same field, or a player walking east would see rain
    // stop at a line in the ground the cloud above them knows nothing about.
    private int cellOf(int block, int cells) {
        int cell = (int) Math.floor(block / cellSize) % cells;
        return cell < 0 ? cell + cells : cell;
    }
}

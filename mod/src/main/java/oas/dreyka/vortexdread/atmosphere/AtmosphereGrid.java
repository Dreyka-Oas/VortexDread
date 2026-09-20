package oas.dreyka.vortexdread.atmosphere;

/**
 * The state of one patch of atmosphere.
 *
 * <p>Six fields: three components of velocity, the potential temperature, and the two water mixing
 * ratios, vapour and condensed droplets. Pressure is not here because it is diagnostic: the projection
 * stage solves for it, uses it and throws it away within one step.
 *
 * <p>Temperature and water sit at cell centres. Velocity does not: each component sits on the face it
 * crosses, so {@code velocityX} at an index is the flow through the low x face of that cell, and the
 * same for the other two. The arrays are the same length because every cell owns one face per axis, the
 * one on its low side, and the horizontal wrap supplies the rest. Only the vertical runs out: the top
 * face of the highest row has no cell above it, and there is nothing to store because the lid is a wall.
 *
 * <p>That offset is not a stylistic choice, it is what makes the projection work. Collocating velocity
 * with pressure forces the divergence and the gradient onto differences two cells wide, whose
 * composition is the Laplacian of a grid of twice the spacing. It splits into eight independent
 * lattices, the checkerboard pattern that alternates between them is invisible to the pressure solve,
 * and what the solver cannot see it cannot remove: the field fills with a chequered mode that grows
 * until the numbers stop being finite. Staggered, the divergence of the gradient is exactly the seven
 * point Laplacian the solver relaxes, so there is no mode it is blind to.
 *
 * <p>The vertical axis is y, and it is the short one. A patch of sky is wide and shallow.
 */
public final class AtmosphereGrid {

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;

    /** Edge length of one cell, in metres. The physics is in SI units and knows nothing about blocks. */
    public final float cellSize;

    public final float[] velocityX;
    public final float[] velocityY;
    public final float[] velocityZ;
    public final float[] potentialTemperature;
    public final float[] vapour;
    public final float[] cloudWater;

    private final int cellCount;

    public AtmosphereGrid(int sizeX, int sizeY, int sizeZ, float cellSize) {
        if (sizeX < 4 || sizeY < 4 || sizeZ < 4) {
            throw new IllegalArgumentException("a grid needs at least two interior cells per axis");
        }
        // The pressure solver sweeps every other cell, which only works if the two horizontal axes come
        // back to the parity they started on after wrapping around.
        if ((sizeX & 1) != 0 || (sizeZ & 1) != 0) {
            throw new IllegalArgumentException("the two horizontal sizes must be even, got " + sizeX
                    + " by " + sizeZ);
        }
        if (cellSize <= 0.0f) {
            throw new IllegalArgumentException("cell size must be positive, got " + cellSize);
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cellSize = cellSize;
        this.cellCount = sizeX * sizeY * sizeZ;
        this.velocityX = new float[cellCount];
        this.velocityY = new float[cellCount];
        this.velocityZ = new float[cellCount];
        this.potentialTemperature = new float[cellCount];
        this.vapour = new float[cellCount];
        this.cloudWater = new float[cellCount];
    }

    public int cellCount() {
        return cellCount;
    }

    /** x runs fastest, then z, then altitude, so one horizontal slab is contiguous. */
    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /** The same index with every axis clamped, which is what a stencil reaching past an edge wants. */
    public int clampedIndex(int x, int y, int z) {
        int cx = x < 0 ? 0 : (x >= sizeX ? sizeX - 1 : x);
        int cy = y < 0 ? 0 : (y >= sizeY ? sizeY - 1 : y);
        int cz = z < 0 ? 0 : (z >= sizeZ ? sizeZ - 1 : z);
        return (cy * sizeZ + cz) * sizeX + cx;
    }

    /**
     * The index with the two horizontal axes wrapped and the vertical one clamped.
     *
     * <p>This is the one the solver uses. The patch of sky tiles horizontally, so water and momentum
     * leaving one side arrive on the other instead of draining out of the domain, and the grid can stay
     * anchored to the world rather than following a player around. Altitude does not wrap: the floor is
     * the ground and the ceiling is the top of the troposphere.
     */
    public int wrappedIndex(int x, int y, int z) {
        int wx = x % sizeX;
        if (wx < 0) {
            wx += sizeX;
        }
        int wz = z % sizeZ;
        if (wz < 0) {
            wz += sizeZ;
        }
        int cy = y < 0 ? 0 : (y >= sizeY ? sizeY - 1 : y);
        return (cy * sizeZ + wz) * sizeX + wx;
    }

    /**
     * The vertical flow through a horizontal face, counting faces from the ground up.
     *
     * <p>Face 0 is the ground and face {@code sizeY} is the lid. Neither lets air through, and the second
     * of the two has no slot in the array at all, so both answer zero here rather than at every call site
     * that reaches for a face above the cell it is working on.
     */
    public float faceVelocityY(int x, int y, int z) {
        if (y <= 0 || y >= sizeY) {
            return 0.0f;
        }
        return velocityY[index(x, y, z)];
    }

    /** The x velocity at the centre of a cell, halfway between the two faces it sits between. */
    public float centreVelocityX(int x, int y, int z) {
        return 0.5f * (velocityX[index(x, y, z)] + velocityX[wrappedIndex(x + 1, y, z)]);
    }

    public float centreVelocityY(int x, int y, int z) {
        return 0.5f * (faceVelocityY(x, y, z) + faceVelocityY(x, y + 1, z));
    }

    public float centreVelocityZ(int x, int y, int z) {
        return 0.5f * (velocityZ[index(x, y, z)] + velocityZ[wrappedIndex(x, y, z + 1)]);
    }

    public boolean isInterior(int x, int y, int z) {
        return x > 0 && y > 0 && z > 0 && x < sizeX - 1 && y < sizeY - 1 && z < sizeZ - 1;
    }

    /** Altitude of the centre of a cell row above the domain floor, in metres. */
    public float altitudeOf(int y) {
        return (y + 0.5f) * cellSize;
    }

    /**
     * Trilinear sample of a field at a position given in cell units, where integer values land on cell
     * centres. Out of range positions clamp rather than wrap, so the semi-Lagrangian backtrace can walk
     * past a wall without reading another cell's memory.
     */
    public float sample(float[] field, float x, float y, float z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float fx = x - x0;
        float fy = y - y0;
        float fz = z - z0;

        float c00 = lerp(field[clampedIndex(x0, y0, z0)], field[clampedIndex(x0 + 1, y0, z0)], fx);
        float c10 = lerp(field[clampedIndex(x0, y0, z0 + 1)], field[clampedIndex(x0 + 1, y0, z0 + 1)], fx);
        float c01 = lerp(field[clampedIndex(x0, y0 + 1, z0)], field[clampedIndex(x0 + 1, y0 + 1, z0)], fx);
        float c11 = lerp(field[clampedIndex(x0, y0 + 1, z0 + 1)],
                field[clampedIndex(x0 + 1, y0 + 1, z0 + 1)], fx);

        return lerp(lerp(c00, c10, fz), lerp(c01, c11, fz), fy);
    }

    /** Trilinear sample wrapping horizontally, which is what the semi-Lagrangian backtrace wants. */
    public float samplePeriodic(float[] field, float x, float y, float z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float fx = x - x0;
        float fy = y - y0;
        float fz = z - z0;

        float c00 = lerp(field[wrappedIndex(x0, y0, z0)], field[wrappedIndex(x0 + 1, y0, z0)], fx);
        float c10 = lerp(field[wrappedIndex(x0, y0, z0 + 1)], field[wrappedIndex(x0 + 1, y0, z0 + 1)], fx);
        float c01 = lerp(field[wrappedIndex(x0, y0 + 1, z0)], field[wrappedIndex(x0 + 1, y0 + 1, z0)], fx);
        float c11 = lerp(field[wrappedIndex(x0, y0 + 1, z0 + 1)],
                field[wrappedIndex(x0 + 1, y0 + 1, z0 + 1)], fx);

        return lerp(lerp(c00, c10, fz), lerp(c01, c11, fz), fy);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

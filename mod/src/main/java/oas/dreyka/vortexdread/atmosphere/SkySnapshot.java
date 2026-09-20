package oas.dreyka.vortexdread.atmosphere;

/**
 * One step of the sky, frozen, for whoever is drawing it.
 *
 * <p>A copy rather than the grid itself, and the reason is that the two sides keep different time. The sky
 * advances once every two hundred ticks on a worker thread; a frame is drawn sixty times a second on another.
 * Handing the renderer the live arrays would have it sampling a field halfway through being overwritten, which
 * shows as a seam crossing the sky on whichever frame lands inside a step.
 *
 * <p>Only the condensed water travels. That is the field a cloud is, and the other five are what produced it:
 * a renderer given the velocity or the vapour would have nothing to do with either.
 *
 * <p>A class with final fields rather than a record, for the same reason {@link AtmosphereGrid} is one. A
 * record holding an array compares by array identity, so two snapshots of the same sky would come back
 * unequal and printing one would name a reference, and neither is worth the four lines it saves.
 */
public final class SkySnapshot {

    /** Which step this is, so a reader can tell a new one from the one it already drew. */
    public final long step;

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;

    /** Edge of one cell in metres. */
    public final float cellSize;

    /** Condensed water per cell, kg per kg of air. Never written after this is handed over. */
    public final float[] cloudWater;

    private SkySnapshot(long step, int sizeX, int sizeY, int sizeZ, float cellSize, float[] cloudWater) {
        this.step = step;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cellSize = cellSize;
        this.cloudWater = cloudWater;
    }

    /** Copies what a renderer needs out of a grid the worker thread is about to change again. */
    public static SkySnapshot of(AtmosphereGrid grid, long step) {
        return new SkySnapshot(step, grid.sizeX, grid.sizeY, grid.sizeZ, grid.cellSize,
                grid.cloudWater.clone());
    }

    /**
     * Takes over a field the caller has just built and will not touch again, which is what arrives off the
     * network: copying it a second time would double the allocation for nothing.
     */
    public static SkySnapshot adopting(long step, int sizeX, int sizeY, int sizeZ, float cellSize,
            float[] cloudWater) {
        int cells = sizeX * sizeY * sizeZ;
        if (cloudWater.length != cells) {
            throw new IllegalArgumentException("a " + sizeX + " by " + sizeY + " by " + sizeZ
                    + " sky holds " + cells + " cells, not " + cloudWater.length);
        }
        return new SkySnapshot(step, sizeX, sizeY, sizeZ, cellSize, cloudWater);
    }

    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /** How high the modelled column reaches, metres, which is where the renderer stops marching. */
    public float domainHeight() {
        return sizeY * cellSize;
    }

    /** How wide it is, metres. The horizontal axes tile, so this is also the repeat distance. */
    public float domainWidth() {
        return sizeX * cellSize;
    }
}

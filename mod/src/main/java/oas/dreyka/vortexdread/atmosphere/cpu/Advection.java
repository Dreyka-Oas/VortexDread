package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereScratch;

/**
 * Everything the flow carries, moved one step downstream.
 *
 * <p>Two schemes rather than one, because the two kinds of field want different things. Water and heat go
 * through {@link ScalarTransport}, which works off the flow through each face; velocity goes through
 * {@link MomentumTransport}, which traces backwards and loses a little energy doing it, which is the
 * harmless direction. Water is counted so that not a gram is created or lost, heat is moved so that a
 * uniform temperature stays uniform, and that class says why the same choice cannot serve both.
 *
 * <p>Counting faces buys the conservation at the price of a speed limit: a parcel crossing more than one
 * cell in a step outruns the face it was supposed to arrive through. Rather than forbid a long step, the
 * step is cut into slices short enough to stay inside the limit, so a calm sky costs one pass and a squall
 * costs several. The sky is allowed to be fast; what it is not allowed to do is skip cells.
 */
public final class Advection {

    /**
     * The largest share of a cell a parcel may cross in one slice, counting all three axes together.
     *
     * <p>Half rather than the whole because the face values are extrapolated: a parcel allowed to arrive
     * exactly on the far face has no margin left for the slope that told it what it was carrying.
     */
    private static final float COURANT_LIMIT = 0.5f;

    /**
     * How far the slicing is allowed to go before the step is simply taken as it is.
     *
     * <p>A field fast enough to need more than this has already gone wrong somewhere, and the useful
     * failure is a sky that looks wrong for a second rather than a server thread that stops answering
     * while it grinds through two hundred passes.
     */
    private static final int SLICE_CEILING = 8;

    private Advection() {
    }

    public static void advect(AtmosphereGrid grid, AtmosphereScratch scratch, float timeStep) {
        int slices = slicesFor(fastestFlow(grid), timeStep, grid.cellSize);
        float courant = timeStep / (slices * grid.cellSize);

        for (int slice = 0; slice < slices; slice++) {
            // Every field is carried by the flow as it stands at the start of the slice, so the traced
            // velocity waits in scratch until the three scalars have been moved by the old one.
            MomentumTransport.trace(grid, scratch, courant);
            ScalarTransport.move(grid, grid.potentialTemperature, scratch.advected, courant, false);
            ScalarTransport.move(grid, grid.vapour, scratch.advected, courant, true);
            ScalarTransport.move(grid, grid.cloudWater, scratch.advected, courant, true);

            int count = grid.cellCount();
            System.arraycopy(scratch.velocityX, 0, grid.velocityX, 0, count);
            System.arraycopy(scratch.velocityY, 0, grid.velocityY, 0, count);
            System.arraycopy(scratch.velocityZ, 0, grid.velocityZ, 0, count);
        }
    }

    /**
     * How fast the air is going in the cell that is emptying fastest.
     *
     * <p>The three components are added rather than taken separately. What limits the scheme is the total
     * share of a cell that empties in one slice, and air leaving diagonally empties it through two faces
     * at once.
     */
    public static float fastestFlow(AtmosphereGrid grid) {
        float fastest = 0.0f;
        for (int index = 0; index < grid.cellCount(); index++) {
            float together = Math.abs(grid.velocityX[index]) + Math.abs(grid.velocityY[index])
                    + Math.abs(grid.velocityZ[index]);
            fastest = Math.max(fastest, together);
        }
        return fastest;
    }

    /**
     * How many slices a step of this length needs, given that speed.
     *
     * <p>Taking the speed as an argument rather than the grid is what lets the card path use this rule
     * instead of a copy of it: reducing a field to its maximum is the one thing a card is bad at and the one
     * thing it has to do anyway, so it reduces and then asks here, and both paths slice the same.
     */
    public static int slicesFor(float fastest, float timeStep, float cellSize) {
        float crossed = fastest * timeStep / cellSize;
        int wanted = (int) Math.ceil(crossed / COURANT_LIMIT);
        return Math.min(SLICE_CEILING, Math.max(1, wanted));
    }
}

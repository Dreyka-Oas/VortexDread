package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;

/**
 * What happens at the floor and the lid of the domain.
 *
 * <p>Almost nothing, which is the point. The horizontal sides tile, which the index function already does,
 * so air and water leaving one edge arrive at the other and the domain loses neither. The flow through the
 * two walls is zero by construction, since the ground is the face below the lowest row and the lid is the
 * face above the highest and the grid answers zero for both. Heat and water need no condition either: a
 * stencil reaching past either wall reads the row it started from, which is the no flux condition written
 * as an index.
 *
 * <p>What the lid does need is to stop reflecting. A rigid ceiling sends every rising thermal back down as
 * a wave, the wave meets the next thermal, and the two add. So the top rows are slowed: whatever motion
 * arrives there is bled off, gently at the bottom of the band and hard at the top, and a disturbance fades
 * instead of bouncing.
 *
 * <p>Motion and nothing else. Pulling the temperature and the water up there back toward the still profile
 * as well is the tempting version and it quietly breaks the books: the air condenses, the heat that
 * releases is taken away as an anomaly, the water that condensed is taken away too, and then the vapour is
 * topped back up from the reference state, which hands the cell a fresh load of water to condense next
 * step. Each turn of that releases heat nothing paid for. It reads on a gauge as a domain warming three
 * degrees past anything in its own profile with no cloud anywhere to explain it.
 *
 * <p>This stage runs before the projection and not after it, which is the part that is easy to get
 * backwards and expensive to get wrong. The projection's whole job is to leave a flow that conserves mass;
 * anything written into the velocity afterwards undoes that, and the next step's projection has only the
 * vertical face to fix it with. The result is a vertical velocity at the lid growing by half again every
 * step, which reaches sixty metres a second inside a simulated minute and looks for all the world like a
 * physics problem.
 */
public final class Boundaries {

    /** How many rows below the lid take part in the absorbing band. */
    private static final int SPONGE_ROWS = 4;

    /** How long the topmost row of that band takes to bleed off what reached it, in seconds. */
    private static final float SPONGE_TIME = 20.0f;

    private Boundaries() {
    }

    /** The lowest row the band reaches, which the card path needs to know where to start its launch. */
    public static int firstSpongeRow(int sizeY) {
        return Math.max(1, sizeY - SPONGE_ROWS);
    }

    public static void apply(AtmosphereGrid grid, float timeStep) {
        int firstRow = firstSpongeRow(grid.sizeY);

        for (int y = firstRow; y < grid.sizeY; y++) {
            float depth = (y - firstRow + 1) / (float) SPONGE_ROWS;
            float share = Math.min(1.0f, depth * depth * timeStep / SPONGE_TIME);

            int rowStart = grid.index(0, y, 0);
            int rowEnd = rowStart + grid.sizeX * grid.sizeZ;
            for (int index = rowStart; index < rowEnd; index++) {
                grid.velocityX[index] -= grid.velocityX[index] * share;
                grid.velocityY[index] -= grid.velocityY[index] * share;
                grid.velocityZ[index] -= grid.velocityZ[index] * share;
            }
        }
    }
}

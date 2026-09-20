package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereScratch;

/**
 * The flow carrying itself, traced backwards, Stam's method.
 *
 * <p>Water and heat get the conservative treatment next door because a domain that invents water cooks
 * itself. Momentum does not need it. Tracing backwards loses a little energy to the interpolation every
 * step, and losing energy is the safe direction to be wrong in: a solver that gains it accelerates until
 * the numbers stop being finite, and there is nothing in a cloud that wants a wind the ground never
 * produced.
 *
 * <p>Each component sits on its own face, so each traces from a point offset half a cell from the
 * pressure lattice, and the other two components have to be averaged onto that point from the four faces
 * around it. The half cell then cancels: reading back into an array that is itself offset by the same
 * half cell lands on plain cell coordinates.
 */
final class MomentumTransport {

    private MomentumTransport() {
    }

    /** Traces all three components into the scratch buffers, leaving the grid untouched. */
    static void trace(AtmosphereGrid grid, AtmosphereScratch scratch, float courant) {
        for (int y = 0; y < grid.sizeY; y++) {
            for (int z = 0; z < grid.sizeZ; z++) {
                for (int x = 0; x < grid.sizeX; x++) {
                    int index = grid.index(x, y, z);

                    scratch.velocityX[index] = traceOne(grid, grid.velocityX, x, y, z, courant,
                            grid.velocityX[index], verticalAtSideFace(grid, x, y, z),
                            lateralAtSideFace(grid, x, y, z));

                    scratch.velocityY[index] = y == 0 ? 0.0f
                            : traceOne(grid, grid.velocityY, x, y, z, courant,
                                    alongAtFloorFace(grid, x, y, z), grid.velocityY[index],
                                    lateralAtFloorFace(grid, x, y, z));

                    scratch.velocityZ[index] = traceOne(grid, grid.velocityZ, x, y, z, courant,
                            alongAtBackFace(grid, x, y, z), verticalAtBackFace(grid, x, y, z),
                            grid.velocityZ[index]);
                }
            }
        }
    }

    private static float traceOne(AtmosphereGrid grid, float[] component, int x, int y, int z,
            float courant, float alongX, float alongY, float alongZ) {
        return grid.samplePeriodic(component, x - alongX * courant, y - alongY * courant,
                z - alongZ * courant);
    }

    /** The vertical flow at the low x face, averaged from the four horizontal faces around it. */
    private static float verticalAtSideFace(AtmosphereGrid grid, int x, int y, int z) {
        return 0.25f * (grid.faceVelocityY(x, y, z) + grid.faceVelocityY(x, y + 1, z)
                + grid.faceVelocityY(wrapBack(x, grid.sizeX), y, z)
                + grid.faceVelocityY(wrapBack(x, grid.sizeX), y + 1, z));
    }

    private static float lateralAtSideFace(AtmosphereGrid grid, int x, int y, int z) {
        return 0.25f * (grid.velocityZ[grid.index(x, y, z)]
                + grid.velocityZ[grid.wrappedIndex(x, y, z + 1)]
                + grid.velocityZ[grid.wrappedIndex(x - 1, y, z)]
                + grid.velocityZ[grid.wrappedIndex(x - 1, y, z + 1)]);
    }

    private static float alongAtFloorFace(AtmosphereGrid grid, int x, int y, int z) {
        return 0.25f * (grid.velocityX[grid.index(x, y, z)]
                + grid.velocityX[grid.wrappedIndex(x + 1, y, z)]
                + grid.velocityX[grid.wrappedIndex(x, y - 1, z)]
                + grid.velocityX[grid.wrappedIndex(x + 1, y - 1, z)]);
    }

    private static float lateralAtFloorFace(AtmosphereGrid grid, int x, int y, int z) {
        return 0.25f * (grid.velocityZ[grid.index(x, y, z)]
                + grid.velocityZ[grid.wrappedIndex(x, y, z + 1)]
                + grid.velocityZ[grid.wrappedIndex(x, y - 1, z)]
                + grid.velocityZ[grid.wrappedIndex(x, y - 1, z + 1)]);
    }

    private static float alongAtBackFace(AtmosphereGrid grid, int x, int y, int z) {
        return 0.25f * (grid.velocityX[grid.index(x, y, z)]
                + grid.velocityX[grid.wrappedIndex(x + 1, y, z)]
                + grid.velocityX[grid.wrappedIndex(x, y, z - 1)]
                + grid.velocityX[grid.wrappedIndex(x + 1, y, z - 1)]);
    }

    private static float verticalAtBackFace(AtmosphereGrid grid, int x, int y, int z) {
        return 0.25f * (grid.faceVelocityY(x, y, z) + grid.faceVelocityY(x, y + 1, z)
                + grid.faceVelocityY(x, y, wrapBack(z, grid.sizeZ))
                + grid.faceVelocityY(x, y + 1, wrapBack(z, grid.sizeZ)));
    }

    /** One step back along a periodic axis, since the vertical helper takes coordinates and not indices. */
    private static int wrapBack(int coordinate, int size) {
        return coordinate == 0 ? size - 1 : coordinate - 1;
    }
}

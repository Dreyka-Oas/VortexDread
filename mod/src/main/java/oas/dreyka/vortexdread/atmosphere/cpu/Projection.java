package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereScratch;

/**
 * Makes the flow conserve mass, Harris equations 11 and 12.
 *
 * <p>Buoyancy and confinement both push air around without asking where it goes, which leaves the field
 * with sources and sinks in it: cells that more air enters than leaves. Left alone that shows up as cloud
 * appearing out of nowhere and thinning out for no reason. The fix is the Helmholtz-Hodge decomposition,
 * which says any field splits into a part that conserves mass and the gradient of something. Solve for
 * that something, subtract its gradient, and what remains conserves mass.
 *
 * <p>Every difference here spans one cell, not two. The divergence of a cell is what crosses its high
 * face minus what crosses its low face, and the correction applied to a face is the pressure difference
 * across it. Those two compose into the seven point Laplacian this stage relaxes, which is the whole
 * point: a solver whose Laplacian does not match its own divergence operator leaves behind exactly the
 * modes it cannot represent, and on this grid that is a chequered pattern that grows without bound.
 *
 * <p>Not conjugate gradient, for Harris's reason: it converges in far fewer passes and each pass needs a
 * reduction across the whole grid, which is the one thing a card is bad at. Not plain Jacobi either,
 * which is a stencil but a slow one, and a projection stopped short of convergence is a projection that
 * did not happen. Red and black passes with over-relaxation are still nothing but a stencil, so still one
 * kernel launch each, and they get there in a small fraction of the passes.
 */
public final class Projection {

    /**
     * How far past the plain update each cell is pushed.
     *
     * <p>The plain Gauss-Seidel step moves a cell to the average its neighbours currently ask for, and
     * doing only that converges at a rate set by the size of the grid: information crosses one cell per
     * pass, so a forty row domain needs on the order of a thousand passes. Overshooting carries it much
     * further. The useful value climbs toward two as the grid grows, and above about 1.9 the overshoot
     * stops settling and starts ringing, so this sits below that for any grid worth simulating.
     */
    private static final float OVERSHOOT = 1.8f;

    private Projection() {
    }

    public static void apply(AtmosphereGrid grid, AtmosphereScratch scratch, int iterations) {
        measureDivergence(grid, scratch);
        java.util.Arrays.fill(scratch.pressure, 0.0f);
        relax(grid, scratch, iterations);
        subtractGradient(grid, scratch);
    }

    /**
     * How much more air leaves each cell than enters it, per second.
     *
     * <p>Summed over the whole domain this comes to zero whatever the field looks like, because the
     * horizontal terms wrap around and cancel and the two vertical walls pass nothing. That is not a
     * detail: a Poisson problem with no outlet has no solution at all unless its right hand side sums to
     * zero, and one that does not sends the mean pressure walking off on its own every pass.
     */
    private static void measureDivergence(AtmosphereGrid grid, AtmosphereScratch scratch) {
        float inverseSpan = 1.0f / grid.cellSize;
        for (int y = 0; y < grid.sizeY; y++) {
            for (int z = 0; z < grid.sizeZ; z++) {
                for (int x = 0; x < grid.sizeX; x++) {
                    int index = grid.index(x, y, z);
                    float flow = grid.velocityX[grid.wrappedIndex(x + 1, y, z)] - grid.velocityX[index]
                            + grid.faceVelocityY(x, y + 1, z) - grid.faceVelocityY(x, y, z)
                            + grid.velocityZ[grid.wrappedIndex(x, y, z + 1)] - grid.velocityZ[index];
                    scratch.divergence[index] = flow * inverseSpan;
                }
            }
        }
    }

    /**
     * Passes over the Poisson equation, every other cell at a time.
     *
     * <p>Two colours because a cell may only be updated from neighbours of the other colour: on this
     * stencil no cell touches another of its own parity, so each colour can be swept in any order at once
     * while still reading the newest values the previous colour left. That is what lets the answer
     * propagate across the grid within a pass rather than one cell per pass, and it is why there is no
     * second buffer here.
     *
     * <p>The boundary conditions come out of the index function for free. Horizontally it wraps, which is
     * periodic. Vertically it clamps, so the floor row reads itself as its own neighbour below, which
     * cancels one term of the stencil and leaves precisely the zero normal derivative a wall imposes.
     */
    private static void relax(AtmosphereGrid grid, AtmosphereScratch scratch, int iterations) {
        float squaredCell = grid.cellSize * grid.cellSize;
        float[] pressure = scratch.pressure;

        for (int pass = 0; pass < iterations; pass++) {
            for (int colour = 0; colour < 2; colour++) {
                for (int y = 0; y < grid.sizeY; y++) {
                    for (int z = 0; z < grid.sizeZ; z++) {
                        int first = ((x0(y, z) + colour) & 1);
                        for (int x = first; x < grid.sizeX; x += 2) {
                            float neighbours = pressure[grid.wrappedIndex(x + 1, y, z)]
                                    + pressure[grid.wrappedIndex(x - 1, y, z)]
                                    + pressure[grid.wrappedIndex(x, y + 1, z)]
                                    + pressure[grid.wrappedIndex(x, y - 1, z)]
                                    + pressure[grid.wrappedIndex(x, y, z + 1)]
                                    + pressure[grid.wrappedIndex(x, y, z - 1)];
                            int index = grid.index(x, y, z);
                            float wanted =
                                    (neighbours - squaredCell * scratch.divergence[index]) / 6.0f;
                            pressure[index] += OVERSHOOT * (wanted - pressure[index]);
                        }
                    }
                }
            }
        }
    }

    /** Which parity the row starts on, so that no two cells of one colour ever sit side by side. */
    private static int x0(int y, int z) {
        return (y + z) & 1;
    }

    /**
     * Takes the pressure difference across each face off the flow through it.
     *
     * <p>The two vertical walls are skipped rather than corrected. Nothing crosses them, so there is
     * nothing to take away, and leaving them alone is the same zero normal derivative the relaxation
     * already assumed.
     */
    private static void subtractGradient(AtmosphereGrid grid, AtmosphereScratch scratch) {
        float inverseSpan = 1.0f / grid.cellSize;
        float[] pressure = scratch.pressure;

        for (int y = 0; y < grid.sizeY; y++) {
            for (int z = 0; z < grid.sizeZ; z++) {
                for (int x = 0; x < grid.sizeX; x++) {
                    int index = grid.index(x, y, z);
                    float here = pressure[index];
                    grid.velocityX[index] -= (here - pressure[grid.wrappedIndex(x - 1, y, z)]) * inverseSpan;
                    grid.velocityZ[index] -= (here - pressure[grid.wrappedIndex(x, y, z - 1)]) * inverseSpan;
                    if (y > 0) {
                        grid.velocityY[index] -=
                                (here - pressure[grid.index(x, y - 1, z)]) * inverseSpan;
                    }
                }
            }
        }
    }
}

package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;

/**
 * Heat and water moved by counting what crosses each cell face, rather than by asking where the air came
 * from.
 *
 * <p>The difference decides whether the domain can hold its water. Tracing backwards lets every cell read
 * upstream on its own, so where the flow converges several cells read the same damp air and each keeps a
 * full copy of it. Nothing catches that: no value is out of range, no cell is negative, and the total
 * quietly climbs a fraction of a percent every step. Left running it manufactures enough water to warm
 * the domain past boiling, since each gram that condenses releases two and a half degrees and there is
 * nothing to give them back. Counting faces cannot do that: what leaves one cell is subtracted from it
 * and added to its neighbour, the same number in both places, so the sum over the domain is untouched by
 * construction.
 *
 * <p>All six faces are settled against the same starting field, and the cell is updated once. Doing one
 * axis at a time and feeding its answer to the next is cheaper and wrong: air converging along x while it
 * spreads along y is going nowhere overall, but the first sweep sees only the converging half and piles
 * the cell up before the second sweep has a chance to take it back out. Two hundred simulated seconds of
 * that reaches four hundred metres a second. Taken together the six faces cancel, which is what a flow
 * that conserves mass means.
 *
 * <p>The face value comes from the upwind cell extrapolated half a cell forward, which is second order
 * where the field is smooth. The slope is limited the way van Leer does it, the harmonic mean of the two
 * one sided differences and zero wherever they disagree in sign, so an edge stays an edge instead of
 * ringing into negative water on the dry side of a cloud.
 *
 * <p>Counting faces buys the total and charges for it elsewhere, and the bill lands on whichever field has
 * a large uniform part. Subtracting the six fluxes of a field that is the same everywhere leaves that value
 * times the divergence of the flow, which is zero only if the pressure solve converged perfectly, and it
 * never does. On water the leftover is harmless, a hundredth of a percent of a number already near zero.
 * On potential temperature it is multiplied by three hundred kelvin, and the result is a feedback loop
 * rather than an error: the false warming lifts the air, the lift leaves the next solve further from
 * converged, and the domain passes two hundred kelvin of anomaly inside two simulated minutes. Getting the
 * solve four times as accurate also stops it, and costs four times as much on a grid where it is already
 * the expensive stage.
 *
 * <p>So each field is moved the way its own physics asks. Water is counted across faces, which cannot
 * create or lose a gram. Heat is moved by the form that leaves a uniform field exactly uniform, which lets
 * the total drift by a fraction of a degree nothing measures, and heat has no total worth conserving.
 */
final class ScalarTransport {

    private ScalarTransport() {
    }

    /**
     * Advects one field in place.
     *
     * @param destination a scratch array the size of the grid, overwritten
     * @param courant the time step divided by the cell size, in seconds per metre
     * @param conserveTotal true to keep the sum over the domain exact, which every field made of water
     *     needs, false to keep a uniform field uniform instead
     */
    static void move(AtmosphereGrid grid, float[] field, float[] destination, float courant,
            boolean conserveTotal) {
        for (int y = 0; y < grid.sizeY; y++) {
            for (int z = 0; z < grid.sizeZ; z++) {
                for (int x = 0; x < grid.sizeX; x++) {
                    float leaving = 0.0f;
                    float spread = 0.0f;
                    for (int axis = 0; axis < 3; axis++) {
                        float lowFlow = flowAt(grid, axis, x, y, z, 0);
                        float highFlow = flowAt(grid, axis, x, y, z, 1);
                        leaving += highFlow * faceValue(grid, field, axis, x, y, z, 1, highFlow, courant)
                                - lowFlow * faceValue(grid, field, axis, x, y, z, 0, lowFlow, courant);
                        spread += highFlow - lowFlow;
                    }
                    int index = grid.index(x, y, z);
                    float carried = conserveTotal ? leaving : leaving - field[index] * spread;
                    destination[index] = field[index] - courant * carried;
                }
            }
        }
        System.arraycopy(destination, 0, field, 0, grid.cellCount());
    }

    /** The flow through one of the two faces a cell owns along an axis, low side at offset zero. */
    private static float flowAt(AtmosphereGrid grid, int axis, int x, int y, int z, int offset) {
        return switch (axis) {
            case 0 -> grid.velocityX[grid.wrappedIndex(x + offset, y, z)];
            case 1 -> grid.faceVelocityY(x, y + offset, z);
            default -> grid.velocityZ[grid.wrappedIndex(x, y, z + offset)];
        };
    }

    /** What the air crossing a face is carrying, taken from whichever side it is coming from. */
    private static float faceValue(AtmosphereGrid grid, float[] field, int axis, int x, int y, int z,
            int faceOffset, float flow, float courant) {
        boolean rising = flow >= 0.0f;
        int upwind = rising ? faceOffset - 1 : faceOffset;
        float side = rising ? 0.5f : -0.5f;
        float travelled = Math.abs(flow) * courant;
        float slope = limitedSlope(grid, field, axis, x, y, z, upwind);
        return at(grid, field, axis, x, y, z, upwind) + side * (1.0f - travelled) * slope;
    }

    private static float limitedSlope(AtmosphereGrid grid, float[] field, int axis, int x, int y, int z,
            int offset) {
        float here = at(grid, field, axis, x, y, z, offset);
        float behind = here - at(grid, field, axis, x, y, z, offset - 1);
        float ahead = at(grid, field, axis, x, y, z, offset + 1) - here;
        float product = behind * ahead;
        if (product <= 0.0f) {
            return 0.0f;
        }
        return 2.0f * product / (behind + ahead);
    }

    private static float at(AtmosphereGrid grid, float[] field, int axis, int x, int y, int z,
            int offset) {
        int ox = axis == 0 ? offset : 0;
        int oy = axis == 1 ? offset : 0;
        int oz = axis == 2 ? offset : 0;
        return field[grid.wrappedIndex(x + ox, y + oy, z + oz)];
    }
}

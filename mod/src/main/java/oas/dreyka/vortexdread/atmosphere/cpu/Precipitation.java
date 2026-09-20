package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;

/**
 * Droplets that grew heavy enough to fall, taken out of the air, after Kessler 1969.
 *
 * <p>A cloud droplet is about twenty microns across and falls at a centimetre a second, which against an
 * updraft is not falling at all. Where droplets are packed closely enough they collide and merge, and a
 * drop a hundred times heavier falls faster than anything can hold it up. That threshold is the only
 * thing standing between a cumulus and a permanent one.
 *
 * <p>Without this stage the domain has no way to lose water. Every gram the ground gives up stays in the
 * air, every gram that condenses warms it by two and a half degrees and never cools it back, and a run
 * that looked correct for twenty minutes reaches a hundred degrees and vertical winds no aircraft would
 * survive. The runaway is not a solver defect, it is a closed box with a fire under it.
 *
 * <p>Where the water goes is the next stage's problem rather than this one's. Rain that falls through
 * dry air below the cloud evaporates into it, cools it, and drops it as the gust front a storm arrives
 * on, which is machinery this mod will need and does not have yet. For now it leaves.
 */
public final class Precipitation {

    /**
     * Cloud water a cell carries before its droplets start merging into drops, kg per kg.
     *
     * <p>Measured cumulus sit between half a gram and three grams per kilogram, and rain out from about
     * one. Below this the droplets are too far apart to meet.
     */
    private static final float AUTOCONVERSION_THRESHOLD = 1.0e-3f;

    /** How fast the excess turns into something that falls, per second. */
    private static final float AUTOCONVERSION_RATE = 1.0e-3f;

    private Precipitation() {
    }

    /**
     * How much of the excess leaves in one step.
     *
     * <p>An explicit rate stepped past its own time constant removes more than there is, so the share is
     * capped whatever the step size.
     */
    public static float shareFor(float timeStep) {
        return Math.min(1.0f, AUTOCONVERSION_RATE * timeStep);
    }

    public static void apply(AtmosphereGrid grid, float timeStep) {
        float share = shareFor(timeStep);

        for (int index = 0; index < grid.cellCount(); index++) {
            float excess = grid.cloudWater[index] - AUTOCONVERSION_THRESHOLD;
            if (excess > 0.0f) {
                grid.cloudWater[index] -= excess * share;
            }
        }
    }
}

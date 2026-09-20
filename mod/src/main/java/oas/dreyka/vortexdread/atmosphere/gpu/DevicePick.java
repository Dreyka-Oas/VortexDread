package oas.dreyka.vortexdread.atmosphere.gpu;

import java.util.List;
import java.util.Locale;

/**
 * Which of the enumerated OpenCL devices the sky runs on.
 *
 * <p>Its own class, taking names and numbers rather than device handles, because the rule deserves a test and
 * no test can hold an OpenCL handle.
 *
 * <p>The rule it replaces is "the first device the driver listed", which on a machine carrying an integrated
 * part beside a discrete one is not a choice at all. Width alone does not replace it either, and the machine
 * this was written on is the counterexample: a current card and one from four generations back both report
 * thirty two compute units, because a compute unit is a unit of layout and not of speed. What separates them
 * is how fast those units tick, so the two are multiplied. It is a rough proxy for throughput and it is the
 * best one a driver will hand over without running anything.
 */
public final class DevicePick {

    private DevicePick() {
    }

    /**
     * @param names device names in enumeration order
     * @param computeUnits CL_DEVICE_MAX_COMPUTE_UNITS in the same order, 0 where the query failed, which only
     *     makes that device the least attractive rather than unusable
     * @param megahertz CL_DEVICE_MAX_CLOCK_FREQUENCY in the same order, same treatment
     * @param wanted the configured index, taken as given whenever it names a real device, so an operator can
     *     always overrule a rule that guesses their machine wrong
     */
    public static int choose(List<String> names, int[] computeUnits, int[] megahertz, int wanted) {
        if (wanted >= 0 && wanted < names.size()) {
            return wanted;
        }
        int best = 0;
        for (int i = 1; i < names.size(); i++) {
            if (better(names, computeUnits, megahertz, i, best)) {
                best = i;
            }
        }
        return best;
    }

    /** What the pick is ranking on, exposed so the boot line can print the number it decided with. */
    public static long throughput(int[] computeUnits, int[] megahertz, int index) {
        return (long) at(computeUnits, index) * at(megahertz, index);
    }

    // Vendor before throughput. Preferring AMD is this project's own hardware talking, and keeping it ahead of
    // the numbers stops a wide integrated part from taking the discrete card's work.
    private static boolean better(List<String> names, int[] units, int[] megahertz, int candidate,
            int incumbent) {
        boolean candidateAmd = isAmd(names.get(candidate));
        boolean incumbentAmd = isAmd(names.get(incumbent));
        if (candidateAmd != incumbentAmd) {
            return candidateAmd;
        }
        return throughput(units, megahertz, candidate) > throughput(units, megahertz, incumbent);
    }

    private static int at(int[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : 0;
    }

    private static boolean isAmd(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("AMD") || upper.contains("RADEON");
    }
}

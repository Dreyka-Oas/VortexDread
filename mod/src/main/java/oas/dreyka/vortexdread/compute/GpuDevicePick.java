package oas.dreyka.vortexdread.compute;

import java.util.List;
import java.util.Locale;

/**
 * Which of the enumerated OpenCL devices the advection runs on.
 *
 * <p>Its own class, taking names and widths instead of device handles, because the rule deserves a test
 * and no test can hold an OpenCL handle.
 *
 * <p>The rule it replaces is "the first device the driver listed", which on a machine carrying an
 * integrated part beside a discrete one is not a choice at all.
 */
final class GpuDevicePick {
    private GpuDevicePick() {
    }

    /**
     * @param names        device names in enumeration order
     * @param computeUnits CL_DEVICE_MAX_COMPUTE_UNITS in the same order, 0 where the query failed, which
     *                     only makes that device the least attractive rather than unusable
     * @param wanted       the configured index, taken as given whenever it names a real device, so an
     *                     operator can always overrule a rule that guesses their machine wrong
     */
    static int choose(List<String> names, int[] computeUnits, int wanted) {
        if (wanted >= 0 && wanted < names.size()) {
            return wanted;
        }
        int best = 0;
        for (int i = 1; i < names.size(); i++) {
            if (better(names, computeUnits, i, best)) {
                best = i;
            }
        }
        return best;
    }

    // Vendor before width. Preferring AMD is this project's own hardware talking, and keeping it ahead of
    // the width stops a wide integrated part from taking the discrete card's work.
    private static boolean better(List<String> names, int[] units, int candidate, int incumbent) {
        boolean candidateAmd = isAmd(names.get(candidate));
        boolean incumbentAmd = isAmd(names.get(incumbent));
        if (candidateAmd != incumbentAmd) {
            return candidateAmd;
        }
        return width(units, candidate) > width(units, incumbent);
    }

    private static int width(int[] units, int index) {
        return index < units.length ? units[index] : 0;
    }

    private static boolean isAmd(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("AMD") || upper.contains("RADEON");
    }
}

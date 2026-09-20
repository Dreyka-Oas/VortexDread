package oas.dreyka.vortexdread.atmosphere.gpu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;

/**
 * The kernel source as the driver receives it, and the definitions it is built with.
 *
 * <p>Six files rather than one, split the way the processor package is, so a stage can be read against the
 * class it came from. They are concatenated in the order listed below and not in the order the packaging task
 * happens to walk the folder: the force kernels call the noise helpers, so the file holding those has to come
 * first, and sorting the names alphabetically puts it second.
 *
 * <p>The grid arrives as build definitions rather than as kernel arguments. That lets the compiler fold the
 * addressing into constants, which is worth having on a stage that runs sixty times a step, and it costs a
 * rebuild whenever the grid changes, which happens when a server owner edits the config and never again.
 */
final class KernelSource {

    private static final String[] PARTS = {
            "sky_common", "sky_noise", "sky_transport", "sky_pressure", "sky_forces", "sky_water"
    };

    private KernelSource() {
    }

    static String read() {
        StringBuilder whole = new StringBuilder();
        for (String part : PARTS) {
            String path = "/kernels/" + part + ".clx";
            try (InputStream stream = KernelSource.class.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("missing kernel source " + path);
                }
                whole.append(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            } catch (IOException problem) {
                throw new IllegalStateException("cannot read kernel source " + path, problem);
            }
        }
        return whole.toString();
    }

    /**
     * What the program is built with, and what it is deliberately not built with.
     *
     * <p>No {@code -cl-fast-relaxed-math} and no {@code -cl-mad-enable}. Both reorder the arithmetic, the card
     * stops agreeing with the processor, and agreement between the two paths is not for sale for a percentage.
     * The language version is pinned low because nothing here needs anything newer and a driver that only
     * offers 1.2 is a driver that can still run the sky.
     *
     * <p>No {@code -cl-fp32-correctly-rounded-divide-sqrt} either, which is the one absence that is not obvious.
     * It asks for exactly what the sky needs, a division that rounds the way the processor's does, and it does
     * not deliver it: this driver accepts the option and ignores it, so a build that looked like it had the
     * guarantee had nothing. Asking is worse than not asking, since a stricter driver is entitled to refuse the
     * build outright over a capability its device genuinely lacks, and then a card that could have run the sky
     * runs nothing. {@code exact_divide} in sky_common.cl earns the rounding instead of requesting it.
     */
    static String buildOptions(AtmosphereGrid grid) {
        return "-cl-std=CL1.2"
                + " -DSIZE_X=" + grid.sizeX
                + " -DSIZE_Y=" + grid.sizeY
                + " -DSIZE_Z=" + grid.sizeZ
                + " -DCELL_COUNT=" + grid.cellCount()
                + " -DCELL_SIZE=" + literal(grid.cellSize);
    }

    /**
     * A float as a C literal that parses back to the same bits.
     *
     * <p>Java's shortest round-tripping decimal is also shortest round-tripping for the compiler, since both
     * round to nearest, so the constant the kernel folds is the one the processor path multiplies by. The
     * locale is pinned because a comma in place of the decimal point would not be a wrong number, it would be
     * two arguments.
     */
    private static String literal(float value) {
        return String.format(Locale.ROOT, "%sf", Float.toString(value));
    }
}

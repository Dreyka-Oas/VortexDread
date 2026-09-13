package oas.dreyka.vortexdread.compute;

import java.util.concurrent.atomic.AtomicLong;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.config.domain.ComputeConfig;
import oas.dreyka.vortexdread.debris.DebrisMotion;
import oas.dreyka.vortexdread.wind.VortexParameters;

/**
 * Where one tick of debris advection runs.
 *
 * <p>The card when there is one, the processor otherwise, and the processor is the reference: the same
 * equation, the same constants, the same answer to the tolerance the parity test holds them to. Nothing
 * about the game changes with the device, only how many pieces the budget affords.
 *
 * <p>The failure this guards against is the quiet one. A dispatcher that catches everything and falls
 * back makes a broken card path look exactly like a working one, so the counters below are kept and
 * reported: a run that cannot say which path it took has not proven anything.
 */
public final class AdvectionCompute {
    private AdvectionCompute() {
    }

    /** Dispatch failures in a row before the card is written off for the rest of the launch. */
    private static final int BREAKER_LIMIT = 3;

    /**
     * Pieces under which a dispatch costs more than it saves. Two buffer uploads and a read back are a
     * fixed price, and a handful of pieces never earns it.
     */
    private static final int WORTH_THE_TRIP = 64;

    private static volatile AdvectionKernel kernel;
    private static volatile boolean initialised;
    private static volatile boolean writtenOff;
    private static int failures;

    private static final AtomicLong ON_CARD = new AtomicLong();
    private static final AtomicLong ON_PROCESSOR = new AtomicLong();

    /**
     * Opens the device, once, and says so in the log.
     *
     * <p>Called from the server thread at start up rather than from the first dispatch: enumerating
     * devices and building a program costs a noticeable fraction of a second, and paying it inside a tick
     * is a stutter the player sees the moment the first tornado touches down.
     */
    public static synchronized void warmUp() {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!ComputeConfig.useGpu) {
            VortexDread.LOGGER.info("[VortexDread] advection on the processor: the graphics card is off in the config");
            return;
        }
        try {
            kernel = new AdvectionKernel();
            VortexDread.LOGGER.info("[VortexDread] advection on {}", kernel.deviceName());
        } catch (Throwable t) {
            kernel = null;
            VortexDread.LOGGER.info("[VortexDread] advection on the processor: no usable OpenCL device ({})",
                    t.getMessage());
        }
    }

    /** The device the advection is running on, or null when it is on the processor. */
    public static String deviceName() {
        AdvectionKernel current = kernel;
        return current == null || writtenOff ? null : current.deviceName();
    }

    public static long steppedOnCard() {
        return ON_CARD.get();
    }

    public static long steppedOnProcessor() {
        return ON_PROCESSOR.get();
    }

    /**
     * One tick for a batch of debris, wherever it runs.
     *
     * @param count how many pieces of the arrays are live, three doubles each in position and velocity
     */
    public static void stepAll(VortexParameters parameters, double phase, double dt,
                               double[] position, double[] velocity, double[] terminal, int count) {
        if (count <= 0) {
            return;
        }
        AdvectionKernel current = kernel;
        if (current != null && !writtenOff && ComputeConfig.useGpu && count >= WORTH_THE_TRIP) {
            try {
                current.advect(parameters, phase, dt, position, velocity, terminal, count);
                failures = 0;
                ON_CARD.addAndGet(count);
                return;
            } catch (Throwable t) {
                // A dispatch that failed wrote nothing back, so the processor below starts from the same
                // state the card was handed and the tick still produces the right answer.
                if (++failures >= BREAKER_LIMIT) {
                    writtenOff = true;
                    VortexDread.LOGGER.warn("[VortexDread] the graphics card failed {} dispatches in a row,"
                            + " advection is on the processor for the rest of this launch", failures);
                }
            }
        }
        DebrisMotion.stepAll(parameters, phase, dt, position, velocity, terminal, count);
        ON_PROCESSOR.addAndGet(count);
    }

    /** Forces the processor path for a run that has to compare the two. Test seam, not a config knob. */
    public static void forgetDevice() {
        kernel = null;
        initialised = true;
    }
}

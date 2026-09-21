package oas.dreyka.vortexdread.save;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;
import oas.dreyka.vortexdread.atmosphere.SkyRunner;
import oas.dreyka.vortexdread.atmosphere.SkySolver;

/**
 * The two moments a running sky meets the world file: taken out of the solver, and put back into one.
 *
 * <p>Both happen on the worker, which is the whole reason they are here rather than inline at the two call
 * sites. The solver belongs to one thread for its life, since the card's queue is opened and fed from that
 * thread alone, and reading a grid out or writing one back reaches into it. So the capture is passed to the
 * runner's worker and the restore runs inside the supplier the runner already builds the solver on.
 */
public final class SkyPersistence {

    private SkyPersistence() {
    }

    /**
     * The sky as it stands, ready for the world file, or null when there is none to write yet.
     *
     * <p>Null covers the first seconds of a world, where the solver is still being built, and a sky whose step
     * threw. Both mean the same thing to a caller: leave what is on disk alone. Writing an empty sky over a
     * good one would turn a transient failure into a lost afternoon.
     */
    public static SkyState capture(SkyRunner runner, AtmosphereSettings settings) {
        return runner.withSky(sky -> SkyState.of(sky.grid(), sky.stepsTaken(), settings));
    }

    /**
     * Puts a saved sky into a freshly built solver, or leaves it on its opening profile.
     *
     * <p>Runs on the worker as part of opening the sky, which is what keeps a ten megabyte restore off the
     * server thread: the world is already loading, and the first step is a cadence away.
     *
     * <p>The grid is reached through {@link SkySolver#grid()} before anything is written into it, which on the
     * card path is what settles the host copy before it is filled and sent back up. Writing into the array
     * first and fetching afterwards would download the device's own opening state over the restored one.
     *
     * @param saved what the world file held, or null on a world that has none
     */
    public static <T extends SkySolver> T resume(T sky, SkyState saved, AtmosphereSettings settings) {
        if (saved == null) {
            return sky;
        }
        if (saved.restoreInto(sky.grid(), settings)) {
            sky.resumeAt(saved.step());
            VortexDread.LOGGER.info("[VortexDread] the sky came back from the world file at step {}",
                    saved.step());
        }
        return sky;
    }
}

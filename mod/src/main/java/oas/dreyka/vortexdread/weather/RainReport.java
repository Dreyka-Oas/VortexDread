package oas.dreyka.vortexdread.weather;

import java.util.concurrent.atomic.AtomicReference;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;

/**
 * The rain map the rest of the mod reads, held across the two threads that need it.
 *
 * <p>The sky worker builds a map after each step and drops it here; the tick and the frame read it without
 * waiting. One reference swapped whole rather than a map mutated in place, so a reader either gets the step
 * before or the step after and never a column summed halfway.
 *
 * <p>It answers with a dry map rather than null when no sky has arrived yet, which is the same shape the
 * renderer already takes: a world still opening and a world with a clear sky give the same answer to every
 * question anybody asks, so neither needs a branch at the call site.
 */
public final class RainReport {

    private final AtomicReference<RainMap> latest = new AtomicReference<>(RainMap.dry());

    /** Called on the sky worker with each published step. */
    public void take(SkySnapshot sky) {
        latest.set(RainMap.of(sky));
    }

    /** The most recent map, never null. */
    public RainMap map() {
        return latest.get();
    }

    /** Dropped with the world, so the next one does not open under the last one's shower. */
    public void forget() {
        latest.set(RainMap.dry());
    }
}

package oas.dreyka.vortexdread.atmosphere;

/**
 * A patch of sky that can be advanced, whichever processor is doing the advancing.
 *
 * <p>Two implementations, and the whole point of the interface is that nothing above it can tell them apart.
 * {@link Atmosphere} is the reference and {@link oas.dreyka.vortexdread.atmosphere.gpu.GpuAtmosphere} is the
 * same arithmetic on the card, agreeing to the bit rather than to a tolerance, which is what lets a server on
 * one and a client on the other watch the same clouds.
 *
 * <p>Deliberately narrow. Everything the tests ask about a sky, the total condensed water, the row the base
 * sits on, the row the top reaches, stays on the class that has it, because those are questions asked of the
 * reference during development and not things the game calls every tick.
 */
public interface SkySolver extends AutoCloseable {

    void step();

    void step(int count);

    AtmosphereGrid grid();

    AtmosphereSettings settings();

    long stepsTaken();

    /** Simulated seconds since the first step. */
    float elapsedSeconds();

    /**
     * What took the work, in one line, for the log an operator reads after a launch.
     *
     * <p>Exists because the failure this mod can have is the quiet one: the choice falls back, the sky keeps
     * working, the frames are gone and nothing says so. A line that names the device, or names the processor
     * and why, is the difference between a card being used and being assumed.
     */
    String description();

    @Override
    void close();
}

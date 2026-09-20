package oas.dreyka.vortexdread.atmosphere;

/**
 * The two most recent steps and the clock between them, which is everything a frame needs.
 *
 * <p>The same pair on both sides. The server holds one because the thread that steps and the thread that
 * would draw keep different time; a client holds one because a step arrives every two hundred ticks and a
 * frame is drawn sixty times a second. The rule for blending them is identical, and two copies of it would
 * be two copies that could disagree about what the sky looks like.
 *
 * <p>Read from any thread. What makes that safe is that a snapshot is never written after it is offered and
 * every reference here is volatile, so a reader sees the old pair whole or the new pair whole.
 */
public final class SkyFeed {

    private volatile SkySnapshot current;
    private volatile SkySnapshot previous;
    private volatile long tickOfCurrent;
    private volatile int ticksPerStep = 1;

    /**
     * Takes a step in, and drops the pair when it is not newer than the one held.
     *
     * <p>That is not a reordered packet, since nothing here can deliver one: it is a sky that restarted,
     * a world reloaded or an operator reloading the options. Blending the first step of a new sky against
     * the last of the old one draws one frame of two skies at once.
     *
     * @param tick the tick the step became the current one, which the caller owns because the two sides
     *     measure it differently. A server counts from where the step was launched, a multiple of the
     *     cadence on every machine; a client counts from where the field arrived, since latency means the
     *     tick it lands on is not the tick it was computed on
     */
    public void offer(SkySnapshot sky, long tick, int cadence) {
        SkySnapshot held = current;
        if (held != null && sky.step <= held.step) {
            previous = null;
            current = null;
        }
        previous = current;
        current = sky;
        tickOfCurrent = tick;
        ticksPerStep = Math.max(1, cadence);
    }

    /**
     * What to draw, or null while there is only one step to draw it from.
     *
     * <p>Null rather than the single step on its own, because one step held still for ten seconds and then
     * jumping reads worse than a sky that arrives ten seconds late and then moves.
     *
     * <p>The fraction is worked out from the tick the caller is on rather than kept here, because a frame
     * is drawn between two ticks and only the caller knows how far through one it is.
     */
    public SkyView view(long tick, float partialTick) {
        SkySnapshot newest = current;
        SkySnapshot older = previous;
        if (newest == null || older == null) {
            return null;
        }
        float elapsed = tick - tickOfCurrent + partialTick;
        return new SkyView(older, newest, Math.min(1.0f, Math.max(0.0f, elapsed / ticksPerStep)));
    }

    /** The newest step, or null before the first one. */
    public SkySnapshot latest() {
        return current;
    }

    /** The one before it, so a client arriving mid-session can be handed a pair and draw straight away. */
    public SkySnapshot earlier() {
        return previous;
    }

    /** Which step is newest, or -1 before the first one. */
    public long step() {
        SkySnapshot newest = current;
        return newest == null ? -1L : newest.step;
    }

    /** Dropped with the world, so the next one does not open on the last one's clouds. */
    public void forget() {
        previous = null;
        current = null;
    }
}

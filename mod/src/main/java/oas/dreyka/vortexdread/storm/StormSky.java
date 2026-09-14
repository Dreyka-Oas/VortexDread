package oas.dreyka.vortexdread.storm;

/**
 * The weather a live funnel puts over the world, and the weather it gives back.
 *
 * <p>A tornado without a thunderstorm around it is the one thing no photograph of one has ever shown.
 * The cloud deck the mod lights from inside is the game's own thundering sky, so a funnel standing
 * under fair-weather cumulus gets no flashes, no darkening and no rain: the sky it needs is not there
 * to be lit. Natural funnels already arrive under one, since the director only seeds a mesocyclone
 * while the level thunders. One dropped by an operator arrives under whatever the sky happened to be.
 *
 * <p>So the funnel brings its own. The decision lives here rather than in the director because it is
 * a rule with corners, and the corner that matters is the one where the sky was already storming: the
 * mod has to know it never took anything, or clearing up after itself ends someone else's weather.
 */
public final class StormSky {

    /** Below this many ticks left the sky is renewed, so vanilla never clears it under a live funnel. */
    static final int RENEW_BELOW = 1200;

    /** How long the sky is told to keep going each time, in ticks. Longer than any renewal gap. */
    static final int HOLD_TICKS = 3600;

    /**
     * What a level's sky is doing.
     *
     * @param rainTime    ticks until the rain state flips, which is what vanilla counts down
     * @param thunderTime the same for the thunder state
     */
    public record Weather(boolean raining, boolean thundering, int rainTime, int thunderTime) {
    }

    /** A sky to write. Null anywhere below means the sky is already what it should be. */
    public record Order(boolean raining, boolean thundering, int rainTime, int thunderTime) {
    }

    private Weather found;

    /** Whether a funnel currently owns this level's weather. */
    public boolean holding() {
        return found != null;
    }

    /**
     * One survey of a level.
     *
     * @param alive how many funnels are standing in it
     * @param now   what its sky is doing this instant
     * @return the sky to write, or null to leave it alone
     */
    public Order next(int alive, Weather now) {
        if (alive > 0) {
            if (found == null) {
                found = now;
            }
            // Renewed rather than rewritten, or every survey would restart the same storm and the rain
            // would never be allowed to build or thin the way the rest of the sky reads it.
            if (now.thundering() && now.raining()
                    && now.thunderTime() > RENEW_BELOW && now.rainTime() > RENEW_BELOW) {
                return null;
            }
            return new Order(true, true, HOLD_TICKS, HOLD_TICKS);
        }

        if (found == null) {
            return null;
        }
        Weather back = found;
        found = null;
        return new Order(back.raining(), back.thundering(), back.rainTime(), back.thunderTime());
    }
}

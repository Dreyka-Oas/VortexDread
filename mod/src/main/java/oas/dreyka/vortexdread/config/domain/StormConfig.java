package oas.dreyka.vortexdread.config.domain;

/** How the sky behaves, and when it decides to drop a tornado out of itself. */
public final class StormConfig {
    private StormConfig() {
    }

    /** Whether a thunderstorm can produce a tornado on its own. Off leaves only the command. */
    public static boolean naturalTornadoes = true;

    /**
     * Chance, per minute of thunder, that a supercell organises and drops a funnel. A real outbreak
     * day is rarer than this; a Minecraft thunderstorm is short enough that the player would never
     * see one at true odds.
     */
    public static double tornadoChancePerMinute = 0.18;

    /** Ticks of quiet enforced after a tornado dissipates, whatever the odds say. */
    public static int cooldownTicks = 9600;

    /**
     * Shifts the random rating, in EF steps. Negative keeps the sky to weak tornadoes, positive makes
     * a violent one common. Zero follows the real distribution, where EF0 and EF1 are most of them.
     */
    public static double ratingBias = 0.0;

    /** Distance at which a player starts hearing the roar and seeing the sky turn. */
    public static int warningRadius = 640;

    /**
     * How far a storm is announced to a player who has no entity for it, in blocks.
     *
     * <p>Vanilla stops sending an entity at the server's view distance, which is a few hundred blocks,
     * while a mod that draws far terrain puts a horizon tens of kilometres out. A funnel that switches
     * off exactly where the view opens up is the worst place it could possibly do so. Zero turns this
     * off and leaves the funnel inside the tracker, where it was before.
     */
    public static double distantStormReach = 2048.0;

    /** Whether the mod takes over the look of a thunderstorm at all. Off leaves vanilla weather. */
    public static boolean rebuildStorms = true;

    /** Visual-only bolts fired into the storm cloud per minute, which is what lights it from inside. */
    public static int cloudFlashesPerMinute = 34;

    /** Bolts fired inside the funnel itself per minute once a tornado is on the ground. */
    public static int funnelFlashesPerMinute = 9;

    /**
     * Strokes that come down to the ground per minute, with the channel drawn.
     *
     * <p>Most lightning stays in the cloud and that is what the flashes above are, but a supercell puts
     * plenty of it on the ground too, and a storm where nothing is ever seen to come down does not read
     * as a storm at all. These carry no fire and no damage: what vanilla does to a player standing in
     * the open is left exactly as vanilla does it, at vanilla's own rate.
     */
    public static int groundStrokesPerMinute = 8;

    /** Whether those extra bolts are fired at all. Off leaves only the strikes vanilla would make. */
    public static boolean extraLightning = true;
}

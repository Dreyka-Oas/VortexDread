package oas.dreyka.vortexdread.config.domain;

/** What the funnel is allowed to take apart, and how hard it hits what it cannot lift. */
public final class DamageConfig {
    private DamageConfig() {
    }

    /** Whether blocks come out of the ground at all. Off leaves the wind and the debris already up. */
    public static boolean breakBlocks = true;

    /** Blocks examined per tick across every live tornado. The whole cost of destruction sits here. */
    public static int blockBudgetPerTick = 96;

    /**
     * Multiplies the wind pressure a block has to survive. Above one the storm strips bedrock-adjacent
     * builds; below one it takes leaves and glass and leaves the walls standing.
     */
    public static double resistanceScale = 1.0;

    /** Whether a block the storm takes drops its item. Off: what the wind takes is gone. */
    public static boolean dropItems = false;

    /** Heavy debris carried at once by one tornado. Each one is a real entity with real collision. */
    public static int maxDebris = 220;

    /**
     * Whether a piece of debris puts its block back down where it lands. Off by default: a tornado
     * destroys what it takes, and a track that rebuilds itself out of the wreckage as the storm passes
     * reads as a machine tidying up rather than as damage.
     */
    public static boolean debrisSettles = false;

    /**
     * Whether the wind outside the funnel and the debris it throws hurt anyone. What the column has
     * actually picked up is carried and thrown rather than hurt, whatever this says.
     */
    public static boolean damageEntities = true;

    /** Multiplies every point of damage the storm deals, wind and impact alike. */
    public static double damageScale = 1.0;

    /** Whether players are taken up the funnel. Off leaves them in the wind, which does hurt. */
    public static boolean liftPlayers = true;

    /** Wind speed, in blocks per second, under which nothing is lifted at all. */
    public static double liftThreshold = 18.0;
}

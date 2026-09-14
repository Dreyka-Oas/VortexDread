package oas.dreyka.vortexdread.tornado;

import java.util.random.RandomGenerator;

/**
 * How wide a tornado is for the wind it carries.
 *
 * <p>The rating says how hard the air moves and nothing at all about how big the thing is. Two
 * tornadoes of the same rating can be fifty metres and fifteen hundred metres across, and they are
 * different objects to stand near: the narrow one arrives and is past before the wide one has finished
 * crossing a field. Rolling width off the rating alone gives every violent storm the same silhouette,
 * which is the one thing footage of them never shows.
 *
 * <p>The width multiplies the core radius and nothing else. Wind, damage and lifespan stay where the
 * rating put them, so a rope EF5 tears the same roof off through a quarter of the frontage.
 */
public enum FunnelBuild {

    /** A thin fast column. Turns hardest of the three, because the same air is on a tighter circle. */
    ROPE("rope", 0.34, 1.18),

    /** The ordinary one: sides near vertical, roughly as wide as the core says. */
    STOVEPIPE("stovepipe", 1.0, 1.0),

    /** Wider than it is tall, which is what the word means. Slow to cross and impossible to outrun. */
    WEDGE("wedge", 2.35, 0.78);

    /** Share of tornadoes built each way, in the order above. */
    private static final double[] SHARE = {0.42, 0.43, 0.15};

    private final String key;
    private final double widthFactor;
    private final double heightFactor;

    FunnelBuild(String key, double widthFactor, double heightFactor) {
        this.key = key;
        this.widthFactor = widthFactor;
        this.heightFactor = heightFactor;
    }

    /** The name this build goes by in commands and in the language files. */
    public String key() {
        return key;
    }

    /** What a player reads instead of the key. */
    public String translationKey() {
        return "vortexdread.build." + key;
    }

    /** What the core radius is multiplied by. */
    public double widthFactor() {
        return widthFactor;
    }

    /**
     * What the funnel's height is multiplied by.
     *
     * <p>A rope hangs from the same cloud base as anything else and looks taller because it is narrow,
     * so the number here is small: it moves the look, it does not move the storm.
     */
    public double heightFactor() {
        return heightFactor;
    }

    /** Reads a build back off its key, for the command and for a saved tornado. */
    public static FunnelBuild byKey(String key) {
        for (FunnelBuild build : values()) {
            if (build.key.equals(key)) {
                return build;
            }
        }
        return STOVEPIPE;
    }

    /**
     * Draws a build.
     *
     * <p>Weighted toward the narrow end, because that is where the counts are. Wedges are what everyone
     * pictures and they are the rarest of the three.
     */
    public static FunnelBuild roll(RandomGenerator random) {
        double u = random.nextDouble();
        double running = 0.0;
        FunnelBuild[] all = values();
        for (int i = 0; i < SHARE.length; i++) {
            running += SHARE[i];
            if (u < running) {
                return all[i];
            }
        }
        return STOVEPIPE;
    }
}

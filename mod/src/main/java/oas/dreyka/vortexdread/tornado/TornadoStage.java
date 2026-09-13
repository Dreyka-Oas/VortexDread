package oas.dreyka.vortexdread.tornado;

/**
 * Where a tornado is in its life. The order is the order it happens in, and nothing walks it backwards.
 */
public enum TornadoStage {
    /** The funnel hangs out of the wall cloud and has not reached the ground. Nothing is damaged yet. */
    FORMING,

    /** Contact. The debris cloud lifts off the ground and the wind starts counting. */
    TOUCHDOWN,

    /** Full width and full strength, which is most of the life of anything worth a rating. */
    MATURE,

    /** Thinning into a rope, leaning over, still able to hurt and no longer able to widen. */
    ROPING,

    /** Over. The entity removes itself on this one. */
    GONE;

    public String translationKey() {
        return "vortexdread.stage." + name().toLowerCase(java.util.Locale.ROOT);
    }
}

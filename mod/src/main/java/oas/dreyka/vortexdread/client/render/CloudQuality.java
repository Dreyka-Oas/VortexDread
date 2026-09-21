package oas.dreyka.vortexdread.client.render;

/**
 * How many samples the march is allowed, as three named budgets.
 *
 * <p>Two numbers and not one, because they buy different things. The steps down the view decide
 * whether a cloud has a smooth body or is drawn in slices of its own step size, and they cost one
 * field read each. The steps towards the sun decide how deep a cloud shades itself, and they cost a
 * field read each as well but fire once per step that found water, so they multiply.
 *
 * <p>The steps towards the sun double their span, so their count sets a reach rather than a
 * resolution: four of them see the nearest seven hundred and fifty metres of water, eight see
 * nearly thirteen kilometres. A cloud is lit by what is above it within a few hundred metres, so
 * the low setting loses the shading of a tall tower and nothing else.
 */
public enum CloudQuality {
    LOW(32, 4),
    MEDIUM(64, 6),
    HIGH(128, 8);

    private static final CloudQuality[] BY_NUMBER = values();

    public final int viewSteps;

    public final int lightSteps;

    CloudQuality(int viewSteps, int lightSteps) {
        this.viewSteps = viewSteps;
        this.lightSteps = lightSteps;
    }

    /** The budget a config number asks for, clamped, since the file is edited by hand. */
    public static CloudQuality of(int wanted) {
        return BY_NUMBER[Math.min(BY_NUMBER.length - 1, Math.max(0, wanted))];
    }
}

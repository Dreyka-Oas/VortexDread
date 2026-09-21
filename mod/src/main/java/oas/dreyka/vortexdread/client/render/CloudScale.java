package oas.dreyka.vortexdread.client.render;

/**
 * How small the sky is drawn before it is stretched back over the screen.
 *
 * <p>The march is the whole frame budget: a hundred and twenty-eight steps down the view and eight at
 * the sun on each of them is over a thousand field reads for one pixel, and there are two million
 * pixels. Nothing inside the loop can be cut far enough to matter, so the pixel count is what moves,
 * and it moves as the square. Half the side is a quarter of the work.
 *
 * <p>What it costs is resolution on a thing that has almost none to lose. A cloud edge is a gradient
 * tens of metres wide seen from a kilometre away, so it already spans several pixels at full size, and
 * the sky behind it is a flat gradient. The parts of a frame that carry detail, terrain and entities,
 * are drawn after this pass at full size and are untouched.
 *
 * <p>One means no reduction and no second pass at all, which is the way out if the enlargement ever
 * shows a defect worth more than the frames it buys.
 */
public final class CloudScale {

    /** Past this the cloud reads as a coloured fog with visible blocks in it. */
    public static final int MOST = 4;

    private CloudScale() {
    }

    /** Whether a factor asks for a target of its own, false at one and at anything under it. */
    public static boolean shrinks(int wanted) {
        return clamped(wanted) > 1;
    }

    /**
     * One side of the reduced target, rounded up.
     *
     * <p>The rounding decides sample density and nothing else. Both passes work in the whole of their
     * own target, the march covering the viewport from corner to corner and the enlargement reading the
     * reduced image from corner to corner, so a side that does not divide the screen exactly changes how
     * many samples the sky got, never where they land. Up rather than down so the factor is a ceiling on
     * the loss rather than a floor.
     */
    public static int sideOf(int side, int wanted) {
        int factor = clamped(wanted);
        return Math.max(1, (side + factor - 1) / factor);
    }

    private static int clamped(int wanted) {
        return Math.min(MOST, Math.max(1, wanted));
    }
}

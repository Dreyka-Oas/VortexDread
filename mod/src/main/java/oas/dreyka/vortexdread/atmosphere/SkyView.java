package oas.dreyka.vortexdread.atmosphere;

/**
 * The sky as a frame should draw it: two steps and how far between them the frame sits.
 *
 * <p>Drawing from one snapshot alone would have the cloud field jump every ten seconds, which reads as the sky
 * stuttering rather than moving. Drawing between two costs a lag of one step and nothing else, and the lag is
 * not visible: what is on screen is a real state the sky passed through, ten seconds ago.
 *
 * @param from the older step, shown at a fraction of zero
 * @param to the newer step, shown at a fraction of one
 * @param fraction where between them this frame sits, 0 to 1
 */
public record SkyView(SkySnapshot from, SkySnapshot to, float fraction) {

    /** Condensed water at one cell, blended, which is the one number a renderer asks this for. */
    public float cloudWaterAt(int x, int y, int z) {
        int index = to.index(x, y, z);
        float older = from.cloudWater[index];
        return older + (to.cloudWater[index] - older) * fraction;
    }
}

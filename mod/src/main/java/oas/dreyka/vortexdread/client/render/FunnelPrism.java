package oas.dreyka.vortexdread.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * The box the funnel is marched inside, and the storm's numbers riding in its vertex attributes.
 *
 * <p>Shared because the same volume is drawn twice over from two different places: once per tornado
 * the client holds an entity for, and once per storm it only knows about from a packet. The box is
 * the same box either way, and a second copy of these numbers would be a second copy that drifts.
 */
final class FunnelPrism {
    private FunnelPrism() {
    }

    /** The widest the condensation gets, as a multiple of the core radius. Set in the program. */
    static final float WIDEST = 1.8f;

    /** How much wider than the widest condensation the box is, to leave room for the turbulence. */
    static final float BOX_MARGIN = 1.5f;

    /** How far the debris ring reaches with nothing in the air, as a multiple of the core radius. */
    static final float GROUND_RING_REACH = 1.45f;

    /** How much further it reaches with a full load of ground in it. Kept in step with the program. */
    static final float GROUND_RING_SWELL = 0.9f;

    /** Slack round that radius for the lobes on the ring's edge to push into. */
    static final float GROUND_RING_ROUGH = 1.3f;

    /** How far under the funnel's own ground the dust runs, as a multiple of the core radius. */
    static final float GROUND_RING_DROP = 0.9f;

    /**
     * How far the lowered cloud base spreads round the top of the column. Set in the program.
     *
     * <p>Its own reach with the slack the torn edge pushes into, plus the offset that puts the lowering
     * off the column's flank, plus the lean that carries the top of the column away from its foot. A
     * prism sized on the reach alone cuts the far side of the deck off in mid air.
     */
    static final float WALL_CLOUD_REACH = 5.2f * 1.5f + 1.7f + 1.8f;

    /** How far over the cloud base the prism reaches so that lowering has room to thin out. */
    static final float WALL_CLOUD_LOFT = 0.13f;

    /**
     * Writes the box with its faces turned inward, centred on {@code (0, 0, 0)}.
     *
     * <p>The march computes its own entry and exit from the volume, so exactly one fragment per pixel
     * is wanted. Inward faces give that whether the camera is outside the storm or standing in it: the
     * far wall is always in front of the eye, the near wall never is.
     */
    static void emit(VertexConsumer consumer, Matrix4f matrix, float coreRadius, float height,
                     float windFraction, float descent, float groundLoad,
                     int tint, float flash, int light) {
        int packedRadius = Math.round(coreRadius * 64.0f);
        int packedHeight = Math.round(height * 64.0f);
        int red = (tint >> 16) & 0xFF;
        int green = (tint >> 8) & 0xFF;
        int blue = tint & 0xFF;
        int alpha = Math.round(Math.min(1.0f, flash) * 255.0f);

        // A funnel that is lifting the ground reaches wider at its foot than anywhere else, wider again
        // where it pulls the cloud base down round its top, and lower than its own feet where the land
        // falls away beside it. A box that stops at the condensation cuts all three off in mid air.
        float halfWidth = coreRadius * WIDEST * BOX_MARGIN;
        float footWidth = coreRadius * (GROUND_RING_REACH + GROUND_RING_SWELL * groundLoad)
                * GROUND_RING_ROUGH;
        float halfBox = Math.max(Math.max(halfWidth, footWidth), coreRadius * WALL_CLOUD_REACH);
        float footDrop = coreRadius * GROUND_RING_DROP;

        float x0 = -halfBox;
        float y0 = -footDrop;
        float z0 = -halfBox;
        float x1 = halfBox;
        float y1 = height * (1.0f + WALL_CLOUD_LOFT);
        float z1 = halfBox;

        Writer writer = new Writer(consumer, matrix, packedRadius, packedHeight, windFraction,
                descent, groundLoad, red, green, blue, alpha, light);
        writer.face(x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
        writer.face(x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
        writer.face(x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
        writer.face(x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
        writer.face(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        writer.face(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
    }

    private record Writer(VertexConsumer consumer, Matrix4f matrix, int packedRadius, int packedHeight,
                          float windFraction, float descent, float groundLoad,
                          int red, int green, int blue, int alpha, int light) {

        void face(float ax, float ay, float az, float bx, float by, float bz,
                  float cx, float cy, float cz, float dx, float dy, float dz) {
            vertex(ax, ay, az);
            vertex(bx, by, bz);
            vertex(cx, cy, cz);
            vertex(dx, dy, dz);
        }

        private void vertex(float x, float y, float z) {
            // Three signed bytes, and the volume wants four numbers out of them. Which end of the box a
            // corner sits at is one bit, so it rides the sign of the wind rather than spending a whole
            // component of its own; the floor keeps that sign readable on a funnel that has barely
            // started turning.
            float signedWind = Math.max(0.02f, windFraction) * (y > 0.0f ? 1.0f : -1.0f);
            consumer.addVertex(matrix, x, y, z)
                    .setColor(red, green, blue, alpha)
                    .setUv(x, z)
                    .setUv1(packedRadius, packedHeight)
                    .setLight(light)
                    .setNormal(groundLoad, signedWind, descent);
        }
    }
}

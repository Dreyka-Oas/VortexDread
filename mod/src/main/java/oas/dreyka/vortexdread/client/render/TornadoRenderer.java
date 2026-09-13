package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.entity.TornadoEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The funnel, drawn as one prism and one raymarch.
 *
 * <p>No particles anywhere. A tornado is a body of condensed water with debris in it, and a swarm of
 * sprites reads as a swarm of sprites from every angle a player will ever look at it from. What is sent
 * to the card is the smallest box that contains the column, with the storm's own numbers written into
 * the vertex attributes; everything that looks like weather happens in the fragment program.
 */
public class TornadoRenderer extends EntityRenderer<TornadoEntity, TornadoRenderState> {

    /** The widest the condensation gets, as a multiple of the core radius. Set in the program. */
    private static final float WIDEST = 1.8f;

    /** How much wider than the widest condensation the box is, to leave room for the turbulence. */
    private static final float BOX_MARGIN = 1.5f;

    /** Wind that counts as full strength for the purpose of how violently the column boils. */
    private static final float REFERENCE_WIND = 90.0f;

    /** How far the debris ring reaches with nothing in the air, as a multiple of the core radius. */
    private static final float GROUND_RING_REACH = 1.45f;

    /** How much further it reaches with a full load of ground in it. Kept in step with the program. */
    private static final float GROUND_RING_SWELL = 0.9f;

    /** Slack round that radius for the lobes on the ring's edge to push into. */
    private static final float GROUND_RING_ROUGH = 1.3f;

    /** How far under the funnel's own ground the dust runs, as a multiple of the core radius. */
    private static final float GROUND_RING_DROP = 0.9f;

    /** How far the lowered cloud base spreads round the top of the column. Set in the program. */
    private static final float WALL_CLOUD_REACH = 2.3f * 1.8f;

    /** How far over the cloud base the prism reaches so that lowering has room to thin out. */
    private static final float WALL_CLOUD_LOFT = 0.19f;

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public TornadoRenderState createRenderState() {
        return new TornadoRenderState();
    }

    @Override
    public void extractRenderState(TornadoEntity tornado, TornadoRenderState state, float partialTick) {
        super.extractRenderState(tornado, state, partialTick);
        state.coreRadius = tornado.coreRadius();
        state.funnelHeight = tornado.funnelHeight();
        state.descent = tornado.descent();
        state.groundLoad = tornado.groundLoad();
        state.windFraction = Math.min(1.0f, tornado.wind() / REFERENCE_WIND);
        state.flash = tornado.flash();
        state.tint = tornado.tint();
    }

    @Override
    protected AABB getBoundingBoxForCulling(TornadoEntity tornado) {
        // Deeper than the wind reaches, because the dust runs down whatever the funnel is standing next
        // to. Culled on the wind's box alone, a column whose skirt is all that is on screen blinks out.
        return tornado.influenceBox().inflate(0.0, tornado.coreRadius() * GROUND_RING_DROP, 0.0);
    }

    @Override
    public void submit(TornadoRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        float halfWidth = state.coreRadius * WIDEST * BOX_MARGIN;
        float height = state.funnelHeight;
        int light = state.lightCoords;

        // Encoded once here and decoded once in the vertex program, because a core shader has no way of
        // being handed a value per entity. Two sixteenths-of-a-block shorts in the overlay channel, and
        // the normal, which is three bytes nobody else wants on a surface that has no lighting to do.
        int packedRadius = Math.round(state.coreRadius * 64.0f);
        int packedHeight = Math.round(height * 64.0f);

        int red = (state.tint >> 16) & 0xFF;
        int green = (state.tint >> 8) & 0xFF;
        int blue = state.tint & 0xFF;
        int alpha = Math.round(Math.min(1.0f, state.flash) * 255.0f);

        // A funnel that is lifting the ground reaches wider at its foot than anywhere else, wider again
        // where it pulls the cloud base down round its top, and lower than its own feet where the land
        // falls away beside it. A box that stops at the condensation cuts all three off in mid air.
        float footWidth = state.coreRadius * (GROUND_RING_REACH + GROUND_RING_SWELL * state.groundLoad)
                * GROUND_RING_ROUGH;
        float halfBox = Math.max(Math.max(halfWidth, footWidth), state.coreRadius * WALL_CLOUD_REACH);
        float footDrop = state.coreRadius * GROUND_RING_DROP;

        collector.submitCustomGeometry(poseStack, VortexRenderTypes.funnel(), (pose, consumer) -> {
            Matrix4f matrix = pose.pose();
            Box box = new Box(consumer, matrix, packedRadius, packedHeight,
                    state.windFraction, state.descent, state.groundLoad, red, green, blue, alpha, light);
            box.emit(-halfBox, -footDrop, -halfBox, halfBox, height * (1.0f + WALL_CLOUD_LOFT), halfBox);
        });
    }

    /**
     * Writes the prism with its faces turned inward.
     *
     * <p>The march computes its own entry and exit from the volume, so exactly one fragment per pixel
     * is wanted. Inward faces give that whether the camera is outside the storm or standing in it: the
     * far wall is always in front of the eye, the near wall never is.
     */
    private record Box(VertexConsumer consumer, Matrix4f matrix, int packedRadius, int packedHeight,
                       float windFraction, float descent, float groundLoad,
                       int red, int green, int blue, int alpha, int light) {

        void emit(float x0, float y0, float z0, float x1, float y1, float z1) {
            face(x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
            face(x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
            face(x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
            face(x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
            face(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
            face(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        }

        private void face(float ax, float ay, float az, float bx, float by, float bz,
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

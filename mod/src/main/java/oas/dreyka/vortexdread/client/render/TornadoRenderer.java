package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.entity.TornadoEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.AABB;

/**
 * The funnel, drawn as one prism and one raymarch.
 *
 * <p>No particles anywhere. A tornado is a body of condensed water with debris in it, and a swarm of
 * sprites reads as a swarm of sprites from every angle a player will ever look at it from. What is sent
 * to the card is the smallest box that contains the column, with the storm's own numbers written into
 * the vertex attributes; everything that looks like weather happens in the fragment program.
 */
public class TornadoRenderer extends EntityRenderer<TornadoEntity, TornadoRenderState> {

    /** Wind that counts as full strength for the purpose of how violently the column boils. */
    private static final float REFERENCE_WIND = 90.0f;

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
        return tornado.influenceBox()
                .inflate(0.0, tornado.coreRadius() * FunnelPrism.GROUND_RING_DROP, 0.0);
    }

    @Override
    public void submit(TornadoRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, VortexRenderTypes.funnel(), (pose, consumer) ->
                FunnelPrism.emit(consumer, pose.pose(), state.coreRadius, state.funnelHeight,
                        state.windFraction, state.descent, state.groundLoad, state.tint, state.flash,
                        state.lightCoords));
    }
}

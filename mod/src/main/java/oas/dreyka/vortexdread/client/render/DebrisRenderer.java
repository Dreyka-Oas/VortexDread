package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.entity.DebrisEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A piece of wreckage, drawn as the block it used to be.
 *
 * <p>The tumble is derived from the entity id and its age rather than stored on it: nothing in the
 * flight depends on which way a brick is facing, so it costs a tick of network traffic for nothing.
 * Three different rates keep two pieces of the same block from spinning in step.
 */
public class DebrisRenderer extends EntityRenderer<DebrisEntity, DebrisRenderState> {

    /** Turns per second on the fastest axis, for a piece moving at storm speed. */
    private static final float SPIN_RATE = 3.4f;

    public DebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
    }

    @Override
    public DebrisRenderState createRenderState() {
        return new DebrisRenderState();
    }

    @Override
    public void extractRenderState(DebrisEntity debris, DebrisRenderState state, float partialTick) {
        super.extractRenderState(debris, state, partialTick);
        BlockPos pos = debris.blockPosition();
        state.block.randomSeedPos = pos;
        state.block.blockPos = pos;
        state.block.blockState = debris.carried();
        state.block.biome = debris.level().getBiome(pos);
        state.block.level = debris.level();

        float age = debris.tickCount + partialTick;
        float seed = debris.getId() * 0.618f;
        state.spinYaw = (age * SPIN_RATE + seed * 360.0f) % 360.0f;
        state.spinPitch = (age * SPIN_RATE * 0.73f + seed * 211.0f) % 360.0f;
        state.spinRoll = (age * SPIN_RATE * 1.31f + seed * 97.0f) % 360.0f;
    }

    @Override
    public void submit(DebrisRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        BlockState carried = state.block.blockState;
        if (carried.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spinYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.spinPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.spinRoll));
        poseStack.translate(-0.5, -0.5, -0.5);
        collector.submitMovingBlock(poseStack, state.block);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

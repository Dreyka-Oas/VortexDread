package oas.dreyka.vortexdread.mixin.client;

import oas.dreyka.vortexdread.client.render.CloudFlash;
import oas.dreyka.vortexdread.config.domain.StormConfig;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the channel out of the sky when the discharge is inside the cloud.
 *
 * <p>Almost all lightning never reaches the ground, and from underneath what it looks like is the base
 * of the storm going white from within. The mod puts its flashes up in the deck for that reason, but
 * vanilla still draws the bolt: four flat quads per segment, seen from a few hundred blocks away as
 * bright rectangles hanging in mid air. The entity is kept, since Iris reads the flash position off it,
 * and only the geometry is dropped.
 */
@Mixin(LightningBoltRenderer.class)
public class LightningBoltRendererMixin {

    /**
     * How far above the terrain a bolt has to sit to count as intra-cloud, in blocks.
     *
     * <p>Measured against the ground under it rather than against an absolute height, because a strike
     * on a mountain top starts higher than a flash over a plain and neither reading should depend on
     * where the player happens to be standing. A bolt that reaches the ground is placed on the block it
     * hit, so it never clears this, whoever spawned it.
     */
    private static final double ABOVE_THE_GROUND = 40.0;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LightningBolt;Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;F)V",
            at = @At("TAIL"))
    private void vortexdread$markCloudFlash(LightningBolt bolt, LightningBoltRenderState state,
                                            float partialTick, CallbackInfo info) {
        double ground = bolt.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bolt.getBlockX(), bolt.getBlockZ());
        ((CloudFlash) state).vortexdread$insideTheDeck(bolt.getY() - ground > ABOVE_THE_GROUND);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void vortexdread$hideCloudFlash(LightningBoltRenderState state, PoseStack poses,
                                            SubmitNodeCollector collector, CameraRenderState camera,
                                            CallbackInfo info) {
        if (StormConfig.rebuildStorms && ((CloudFlash) state).vortexdread$insideTheDeck()) {
            info.cancel();
        }
    }
}

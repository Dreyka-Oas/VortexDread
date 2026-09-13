package oas.dreyka.vortexdread.mixin.client;

import oas.dreyka.vortexdread.client.render.StormPostEffect;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The view refusing to hold still near a funnel.
 *
 * <p>Applied to the camera rather than to the projection, because what a person under a storm actually
 * fights is their own head: the wind pushes it, they push back, and the result is a fast unsteady
 * wobble rather than a smooth sway. Two frequencies that do not divide into each other, so nothing about
 * it ever settles into a rhythm the eye can predict.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Inject(method = "setup", at = @At("TAIL"))
    private void vortexdread$shake(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity,
                                   boolean detached, boolean mirrored, float partialTick, CallbackInfo info) {
        float amount = StormPostEffect.shake();
        if (amount <= 0.0f) {
            return;
        }
        double t = level.getGameTime() + partialTick;
        float yaw = (float) (Math.sin(t * 1.31) + 0.6 * Math.sin(t * 3.77)) * amount;
        float pitch = (float) (Math.sin(t * 1.73) + 0.6 * Math.sin(t * 4.19)) * amount * 0.7f;
        setRotation(yRot + yaw, xRot + pitch);
    }
}

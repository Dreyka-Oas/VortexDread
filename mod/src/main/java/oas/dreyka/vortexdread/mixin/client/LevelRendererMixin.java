package oas.dreyka.vortexdread.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.world.phys.Vec3;
import oas.dreyka.vortexdread.client.render.CloudPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the game drawing its own clouds, since this mod draws the sky itself.
 *
 * <p>The pass is cancelled rather than the draw call, which is a frame graph pass never scheduled and a
 * render target never allocated, instead of one allocated and then written by nobody.
 *
 * <p>Two other ways in were looked at and both are worse here. The game already has a switch: it skips
 * the clouds entirely when the alpha of the cloud colour is zero, and that colour is a field of the
 * dimension type, so a data pack could turn them off with no code at all. What a data pack cannot do is
 * change one field, it replaces the whole file, so turning off a colour would mean shipping our own copy
 * of the overworld's minimum height, world height and logical height, and carrying that copy forward
 * through every game update. The other way, cancelling CloudRenderer.render, leaves the pass scheduled
 * and would also have to be right about what other mods do inside that class.
 *
 * <p>Sodium is fine with this. It does not take cloud rendering over, it overwrites the method that
 * builds the cloud mesh inside the game's own renderer, and the mesh is never asked for.
 *
 * <p>A shader pack is not fine with it, which is why both hooks ask first. A pack draws cloud of its
 * own and this mod steps aside for it, so cancelling the game's pass on top of that would leave a
 * player under a pack that reads the vanilla cloud geometry with no cloud at all.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void vortexdread$keepTheSkyClear(FrameGraphBuilder builder, CloudStatus status,
            Vec3 cameraPosition, long gameTime, float partialTick, int cloudColour, float cloudHeight,
            CallbackInfo info) {
        if (!CloudPass.standsDown()) {
            info.cancel();
        }
    }

    /**
     * The mod's own sky, added right behind the game's.
     *
     * <p>Here rather than where the clouds used to be because the two want opposite things from the depth
     * buffer. The game's cloud pass runs last, on a target of its own, and pays for a depth test to be
     * hidden by a hill. A volume marched from the camera has no geometry to test, so it goes in before the
     * terrain instead and is covered by it, which is free and is also the right answer: nothing the player
     * can build reaches the altitude this draws at.
     */
    @Inject(method = "addSkyPass", at = @At("TAIL"))
    private void vortexdread$drawOurOwnSky(FrameGraphBuilder builder, Camera camera,
            GpuBufferSlice fog, CallbackInfo info) {
        CloudPass.add(builder, targets, camera);
    }
}

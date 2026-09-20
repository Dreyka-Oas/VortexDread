package oas.dreyka.vortexdread.mixin.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
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
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void vortexdread$keepTheSkyClear(FrameGraphBuilder builder, CloudStatus status,
            Vec3 cameraPosition, long gameTime, float partialTick, int cloudColour, float cloudHeight,
            CallbackInfo info) {
        info.cancel();
    }
}

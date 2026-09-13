package oas.dreyka.vortexdread.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The one door into the game's own post processing.
 *
 * <p>Running a chain by hand means building a frame graph, holding a resource allocator and choosing a
 * moment in the frame that does not move between versions. The game already does all three, for the
 * creeper and the spider, and the only thing keeping a mod out is a private setter.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {

    @Invoker("setPostEffect")
    void vortexdread$setPostEffect(Identifier id);
}

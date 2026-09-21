package oas.dreyka.vortexdread.client.render;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import oas.dreyka.vortexdread.config.LookConfig;

/**
 * The target the sky is marched into when it is drawn small, and the pass that stretches it back.
 *
 * <p>Asked of the frame graph rather than kept in a field. A target the graph owns is allocated when the
 * pass that writes it is scheduled and handed back the moment the pass that reads it is done, so it
 * follows the window's size without being told, costs nothing on a frame the sky is not drawn, and can
 * share memory with another pass's target of the same shape. A target held in a static field would need
 * a resize hook, a close on world unload, and would sit in video memory whether or not it was used.
 */
public final class CloudEnlarge {

    /** Transparent black, which is the neutral of a premultiplied blend and so needs no clear pass. */
    private static final int EMPTY = 0;

    private CloudEnlarge() {
    }

    /**
     * The target the march writes, and the enlargement behind it when there is one.
     *
     * <p>At a factor of one this hands back the main target itself and adds nothing, which is the mod's
     * older behaviour exactly: one pass, marched straight onto the screen. Above one it makes a reduced
     * target, points the march at that, and schedules the pass that lays it back over the main one,
     * leaving {@code targets.main} on the version that pass wrote so the terrain drawn later covers it.
     */
    public static ResourceHandle<RenderTarget> marchInto(FrameGraphBuilder builder, FramePass march,
            LevelTargetBundle targets) {
        ResourceHandle<RenderTarget> small = small(builder, LookConfig.cloudScale);
        if (small == null) {
            targets.main = march.readsAndWrites(targets.main);
            return targets.main;
        }
        ResourceHandle<RenderTarget> reduced = march.readsAndWrites(small);
        // Its own pass rather than a second draw inside the march's: the two write different targets,
        // and the graph is what decides the small one is finished before the big one reads it.
        FramePass pass = builder.addPass("vortexdread_clouds_enlarge");
        pass.reads(reduced);
        ResourceHandle<RenderTarget> onto = pass.readsAndWrites(targets.main);
        pass.executes(() -> draw(reduced.get(), onto.get()));
        targets.main = onto;
        return reduced;
    }

    /** No depth attachment, since the march reads no depth and writes none. */
    private static ResourceHandle<RenderTarget> small(FrameGraphBuilder builder, int factor) {
        if (!CloudScale.shrinks(factor)) {
            return null;
        }
        RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();
        return builder.createInternal("vortexdread_clouds_small", new RenderTargetDescriptor(
                CloudScale.sideOf(screen.width, factor),
                CloudScale.sideOf(screen.height, factor), false, EMPTY));
    }

    private static void draw(RenderTarget small, RenderTarget onto) {
        // Clamped rather than repeating: the outermost half texel of the screen samples past the edge of
        // the reduced target, and a repeat there would fetch the opposite side of the sky.
        GpuSampler smooth = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vortexdread clouds enlarge", onto.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(CloudPipeline.ENLARGE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture(CloudPipeline.SMALL, small.getColorTextureView(), smooth);
            pass.draw(0, 3);
        }
    }
}

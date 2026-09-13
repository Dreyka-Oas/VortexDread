package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.VortexDread;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * The pass that draws the funnel.
 *
 * <p>It is one box and one fragment program. Everything visible happens inside that program: the box
 * is only there to say which pixels are worth marching through, and it carries the storm's numbers in
 * its vertex attributes because a core shader has nowhere else to get them from.
 *
 * <p>Depth writing is off and depth testing is on, which is what a volume wants: the funnel is hidden
 * by hills in front of it and hides nothing behind it.
 */
public final class VortexRenderTypes {
    private VortexRenderTypes() {
    }

    /**
     * Front faces are thrown away rather than back faces. The marched interval is computed from the
     * volume itself, so exactly one fragment per pixel is wanted, and the far side of the box is the
     * one that is still there when the camera is standing inside the storm.
     */
    public static final RenderPipeline FUNNEL_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(VortexDread.id("pipeline/funnel"))
            .withVertexShader(VortexDread.id("core/funnel"))
            .withFragmentShader(VortexDread.id("core/funnel"))
            .withSampler("Sampler2")
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .withDepthWrite(false)
            .build();

    // The same texture a patched shader pack reads the storms out of, bound here so the mod's own
    // program can take the render settings from it. A core shader has no uniform of its own to be
    // handed one through, and a value a player just changed has to arrive on the next frame.
    private static final RenderType FUNNEL = RenderType.create("vortexdread_funnel",
            RenderSetup.builder(FUNNEL_PIPELINE)
                    .useLightmap()
                    .withTexture("Sampler0", FunnelState.TEXTURE)
                    .bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)
                    .createRenderSetup());

    public static RenderType funnel() {
        return FUNNEL;
    }
}

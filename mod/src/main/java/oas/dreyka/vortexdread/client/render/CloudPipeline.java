package oas.dreyka.vortexdread.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import oas.dreyka.vortexdread.VortexDread;

/**
 * The program that draws the sky, and the names the draw binds things under.
 *
 * <p>No geometry at all. The vertex stage is the game's own screen quad, which builds one triangle
 * covering the viewport out of nothing but the vertex index, so there is no vertex buffer to fill and no
 * box whose far side the frustum might clip away. That last part is the reason: the volume is six
 * kilometres wide and three high, and a box that size lies almost entirely past the far plane. Marching
 * from a full-screen triangle instead puts the whole distance inside the fragment stage, where the far
 * plane has no say.
 *
 * <p>The pass runs between the sky and the terrain, which is why it asks for no depth at all. Cloud sits
 * a kilometre up and terrain within a few hundred metres, so terrain drawn afterwards covers it for
 * free, and a hill in front of a cloud needs no depth test to hide it.
 *
 * <p>A pipeline a mod holds is never in the game's own table, so it is not precompiled with the rest and
 * not reported on when a resource reload rejects it. It compiles on the first draw instead, and a
 * mistake in the program shows up there, in the log, with the driver's own message.
 */
public final class CloudPipeline {

    /** The block the numbers of one frame travel in. Matched in cloud.fsh, member for member. */
    public static final String UNIFORM = "CloudSky";

    public static final String OLDER = "CloudsFrom";
    public static final String NEWER = "CloudsTo";

    public static final RenderPipeline SKY = RenderPipeline.builder()
            .withLocation(VortexDread.id("pipeline/cloud"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(VortexDread.id("core/cloud"))
            .withSampler(OLDER)
            .withSampler(NEWER)
            .withUniform(UNIFORM, UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build();

    private CloudPipeline() {
    }
}

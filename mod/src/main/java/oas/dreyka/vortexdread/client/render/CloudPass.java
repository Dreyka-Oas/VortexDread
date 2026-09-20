package oas.dreyka.vortexdread.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalInt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;
import oas.dreyka.vortexdread.atmosphere.SkyView;
import oas.dreyka.vortexdread.client.VortexDreadClient;

/**
 * Where the sky is drawn, and when.
 *
 * <p>Straight after the game's own sky pass and before the terrain one, which is the whole occlusion
 * story. Cloud sits a kilometre up and terrain is drawn within a few hundred metres, so terrain written
 * afterwards covers the cloud behind it with no depth test, no depth buffer read and no ordering to get
 * right. A hill in front of a cloud hides it because the hill is drawn later.
 *
 * <p>The pass is added to the frame graph rather than drawn on the spot, so the game decides when it runs
 * and against which copy of the main target. Declaring the target as both read and written is what puts it
 * after the sky, since the graph orders passes by what they touch.
 */
public final class CloudPass {

    private static CloudVolume volume;
    private static CloudNumbers numbers;
    private static boolean announced;

    private CloudPass() {
    }

    /** Called from the mixin, once per frame, with the bundle the level renderer is building. */
    public static void add(FrameGraphBuilder builder, LevelTargetBundle targets, Camera camera) {
        SkyView view = VortexDreadClient.sky().view(partialTick());
        if (view == null || targets.main == null) {
            return;
        }
        FramePass pass = builder.addPass("vortexdread_clouds");
        targets.main = pass.readsAndWrites(targets.main);
        ResourceHandle<RenderTarget> main = targets.main;
        pass.executes(() -> draw(main.get(), camera, view));
    }

    /** Dropped with the world, since the next one may not have a sky of the same shape. */
    public static void forget() {
        if (volume != null) {
            volume.close();
            volume = null;
        }
        if (numbers != null) {
            numbers.close();
            numbers = null;
        }
        announced = false;
    }

    private static void draw(RenderTarget target, Camera camera, SkyView view) {
        SkySnapshot sky = view.to();
        if (volume == null || !volume.atlas().fits(sky)) {
            forget();
            volume = new CloudVolume(sky.sizeX, sky.sizeY, sky.sizeZ);
            numbers = new CloudNumbers();
        }
        // Both uploads first: the encoder refuses every other command while a render pass is open.
        volume.show(view.from(), view.to());
        GpuBuffer frame = numbers.write(camera, view, volume);
        announce(sky);

        GpuSampler edge = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vortexdread clouds", target.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(CloudPipeline.SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(CloudPipeline.UNIFORM, frame);
            pass.bindTexture(CloudPipeline.OLDER, volume.olderView(), edge);
            pass.bindTexture(CloudPipeline.NEWER, volume.newerView(), edge);
            // One triangle, built by the vertex stage out of its own index. No buffer to fill.
            pass.draw(0, 3);
        }
        numbers.rotate();
    }

    /**
     * The first step that has water in it, once per world.
     *
     * <p>On the first cloud rather than the first frame, which is what makes it worth reading: a sky that
     * never arrived, a sky still clear and a sky drawn wrong all look the same on screen, and this line
     * separates the third from the other two. Anything on screen after it and there is a renderer; nothing
     * on screen after it and the field reached the card and the program lost it.
     */
    private static void announce(SkySnapshot sky) {
        if (announced || volume.newerPeak() <= 0.0f) {
            return;
        }
        announced = true;
        CloudAtlas atlas = volume.atlas();
        VortexDread.LOGGER.info("[VortexDread] cloud on the card at step {}: {} by {} by {} cells in a"
                        + " {} by {} atlas, peak {}", sky.step, sky.sizeX, sky.sizeY, sky.sizeZ,
                atlas.width(), atlas.height(), volume.newerPeak());
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }
}

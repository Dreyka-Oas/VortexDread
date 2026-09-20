package oas.dreyka.vortexdread.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;

/**
 * The two steps of the sky that are on the graphics card, and what it costs to put one there.
 *
 * <p>Two because a frame sits between two steps and blends them, so both have to be readable at once.
 * They swap rather than being rewritten: the step that was the newer one last frame is the older one
 * now, and the field it already holds is the field it still needs, so a step costs exactly one upload
 * however long the session runs.
 *
 * <p>A texture of its own rather than the game's dynamic texture class, because that class carries an
 * image the game's own image format decides the shape of, and this one is written as bytes straight out
 * of the atlas. One less copy per step, and nothing to keep in step with a resource reload.
 */
public final class CloudVolume implements AutoCloseable {

    private final CloudAtlas atlas;
    private final GpuTexture[] fields = new GpuTexture[2];
    private final GpuTextureView[] views = new GpuTextureView[2];
    private final ByteBuffer scratch;

    private final long[] steps = {Long.MIN_VALUE, Long.MIN_VALUE};
    private final float[] peaks = new float[2];

    /** Which slot holds the older of the two. The other one holds the newer. */
    private int older;

    public CloudVolume(int sizeX, int sizeY, int sizeZ) {
        this.atlas = new CloudAtlas(sizeX, sizeY, sizeZ);
        this.scratch = ByteBuffer.allocateDirect(atlas.width() * atlas.height() * 4)
                .order(ByteOrder.nativeOrder());
        for (int slot = 0; slot < fields.length; slot++) {
            int at = slot;
            fields[slot] = RenderSystem.getDevice().createTexture(
                    () -> "vortexdread cloud field " + at,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, atlas.width(), atlas.height(), 1, 1);
            views[slot] = RenderSystem.getDevice().createTextureView(fields[slot]);
        }
    }

    public CloudAtlas atlas() {
        return atlas;
    }

    public GpuTextureView olderView() {
        return views[older];
    }

    public GpuTextureView newerView() {
        return views[1 - older];
    }

    public float olderPeak() {
        return peaks[older];
    }

    public float newerPeak() {
        return peaks[1 - older];
    }

    /**
     * Makes sure both steps of one frame are on the card.
     *
     * <p>Called before the render pass opens rather than inside it, since an upload is not a command a
     * pass accepts: the encoder refuses anything else while one is open.
     */
    public void show(SkySnapshot from, SkySnapshot to) {
        if (steps[1 - older] == from.step) {
            older = 1 - older;
        }
        if (steps[older] != from.step) {
            upload(older, from);
        }
        if (steps[1 - older] != to.step) {
            upload(1 - older, to);
        }
    }

    private void upload(int slot, SkySnapshot sky) {
        int width = atlas.width();
        peaks[slot] = atlas.write(sky, (x, y, argb) -> {
            int at = (y * width + x) * 4;
            scratch.put(at, (byte) (argb >> 16));
            scratch.put(at + 1, (byte) (argb >> 8));
            scratch.put(at + 2, (byte) argb);
            scratch.put(at + 3, (byte) (argb >>> 24));
        });
        // Absolute writes above, so the position never moved and the whole image is still remaining,
        // which is what the upload measures the buffer against.
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(fields[slot], scratch,
                NativeImage.Format.RGBA, 0, 0, 0, 0, width, atlas.height());
        steps[slot] = sky.step;
    }

    @Override
    public void close() {
        for (int slot = 0; slot < fields.length; slot++) {
            views[slot].close();
            fields[slot].close();
            steps[slot] = Long.MIN_VALUE;
        }
    }
}

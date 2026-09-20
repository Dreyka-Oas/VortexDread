package oas.dreyka.vortexdread.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.world.phys.Vec3;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;
import oas.dreyka.vortexdread.atmosphere.SkyView;
import oas.dreyka.vortexdread.config.LookConfig;
import org.joml.Vector3fc;

/**
 * The handful of numbers one frame hands the program, and the buffer they travel in.
 *
 * <p>Ten slots of four floats, in the order {@code cloud_field.glsl} declares them. Four floats each and
 * never three: the rule that lays a uniform block out in memory gives a three float member the room of four
 * but lets the next member start in the leftover quarter, and whether the code filling the buffer agrees
 * about that is a question with two answers on two drivers. A block of nothing but four float slots has one.
 *
 * <p>Three buffers deep rather than one, which is what the game's own passes do. Writing over a buffer the
 * card has not finished reading makes the write wait for it, and at sixty frames a second that wait is the
 * frame.
 */
public final class CloudNumbers implements AutoCloseable {

    private static final int SLOTS = 10;

    private static final int BYTES = size();

    private final MappableRingBuffer ring;

    public CloudNumbers() {
        this.ring = new MappableRingBuffer(() -> "vortexdread cloud numbers",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, BYTES);
    }

    /** Fills this frame's buffer and hands it back for the draw to bind. */
    public GpuBuffer write(Camera camera, SkyView view, CloudVolume volume, float partialTick) {
        SkySnapshot sky = view.to();
        CloudAtlas atlas = volume.atlas();
        CloudLight light = CloudLight.of(camera, partialTick);
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        Vec3 at = camera.position();
        double width = sky.domainWidth();

        GpuBuffer buffer = ring.currentBuffer();
        try (GpuBuffer.MappedView mapped = RenderSystem.getDevice().createCommandEncoder()
                .mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(mapped.data())
                    .putVec4(forward.x(), forward.y(), forward.z(), view.fraction())
                    .putVec4(up.x(), up.y(), up.z(), volume.older().peak())
                    .putVec4(left.x(), left.y(), left.z(), volume.newer().peak())
                    .putVec4(wrapped(at.x, width), (float) (at.y - LookConfig.groundLevelBlock),
                            wrapped(at.z, width), sky.cellSize)
                    .putVec4(sky.sizeX, sky.sizeY, sky.sizeZ, LookConfig.cloudExtinction)
                    .putVec4(atlas.tilesAcross(), atlas.tileWidth(), atlas.tileHeight(),
                            Math.max(1.0f, LookConfig.cloudReachMetres))
                    .putVec4(light.toward().x(), light.toward().y(), light.toward().z(),
                            Math.max(1.0f, LookConfig.cloudLightStepMetres))
                    .putVec4(light.sun().x(), light.sun().y(), light.sun().z(),
                            LookConfig.cloudPowder)
                    .putVec4(light.sky().x(), light.sky().y(), light.sky().z(), 0.0f)
                    .putVec4(floorOfBand(volume, sky), ceilingOfBand(volume, sky), 0.0f, 0.0f);
        }
        return buffer;
    }

    /**
     * The altitudes worth marching through, in metres, widened by a cell on each side.
     *
     * <p>Both steps count, since a cloud the older one still has and the newer one has lost would
     * otherwise be clipped away mid-fade. The extra cell is the vertical blend's reach: the lowest wet
     * cell mixes with the dry one under it, and cutting the march at the wet one cuts that gradient off
     * square.
     */
    private static float floorOfBand(CloudVolume volume, SkySnapshot sky) {
        int cell = Math.min(volume.older().lowestCell(), volume.newer().lowestCell());
        return Math.max(0, cell - 1) * sky.cellSize;
    }

    private static float ceilingOfBand(CloudVolume volume, SkySnapshot sky) {
        int cell = Math.max(volume.older().pastHighestCell(), volume.newer().pastHighestCell());
        return Math.min(sky.sizeY, cell + 1) * sky.cellSize;
    }

    private static int size() {
        Std140SizeCalculator room = new Std140SizeCalculator();
        for (int slot = 0; slot < SLOTS; slot++) {
            room.putVec4();
        }
        return room.get();
    }

    /** Moves on to the next buffer, after the draw that read this one has been issued. */
    public void rotate() {
        ring.rotate();
    }

    /**
     * The camera folded back into one tile of the field.
     *
     * <p>Folded here, in double, rather than in the program: a player thirty million blocks out has a
     * coordinate a float cannot tell from its neighbour, and the fold is the last place the full precision
     * still exists. What comes out is under six kilometres, which a float holds to the millimetre.
     */
    private static float wrapped(double along, double width) {
        double inside = along % width;
        return (float) (inside < 0.0 ? inside + width : inside);
    }

    @Override
    public void close() {
        ring.close();
    }
}

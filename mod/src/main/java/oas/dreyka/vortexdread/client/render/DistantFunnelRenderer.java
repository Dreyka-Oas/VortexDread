package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.config.domain.LookConfig;
import oas.dreyka.vortexdread.network.StormDigest;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the storms no entity exists for.
 *
 * <p>Vanilla stops sending an entity at the server's view distance, so a terrain mod that puts the
 * horizon tens of kilometres out gets a landscape with nothing standing on it. These come off a packet
 * instead, and the funnel they draw is the same one the entity path draws, through the same program.
 *
 * <p>Drawn after the entities so it sits in the same place in the pass order as the tornado it stands
 * in for, and so a storm that crosses from one path to the other does not jump a layer as it does.
 */
public final class DistantFunnelRenderer {
    private DistantFunnelRenderer() {
    }

    /**
     * Wind that counts as full strength for the purpose of how violently the column boils.
     *
     * <p>The same number the entity path uses, because the packet carries the storm's turn against its
     * own peak while the program wants it against a fixed one: a rope EF1 that boiled like an EF5
     * simply because it was running at its own maximum would be the tell.
     */
    private static final float REFERENCE_WIND = 90.0f;

    /** Peak gust used to turn the packet's share back into a speed, in metres per second. */
    private static final float ASSUMED_PEAK = 90.0f;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) {
                return;
            }
            if (ShaderPack.drawingTheWorld()) {
                return;
            }
            var storms = DistantStorms.withoutEntity(client.level);
            if (storms.isEmpty()) {
                return;
            }

            Vec3 camera = client.gameRenderer.getMainCamera().position();
            double reach = LookConfig.funnelRenderDistance;
            double reachSquared = reach * reach;
            PoseStack poseStack = context.matrices();
            var consumer = context.consumers().getBuffer(VortexRenderTypes.funnel());
            // Nothing here is lit by the world, and nothing here has a lightmap to be looked up in: the
            // chunk it stands on is not loaded. Full sky and no block light is what a funnel standing
            // out under an open sky would have read anyway.
            int light = LightTexture.pack(0, 15);

            for (StormDigest storm : storms) {
                double dx = storm.x() - camera.x;
                double dz = storm.z() - camera.z;
                if (dx * dx + dz * dz > reachSquared) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(dx, storm.groundY() - camera.y, dz);
                FunnelPrism.emit(consumer, poseStack.last().pose(), storm.coreRadius(), storm.height(),
                        Math.min(1.0f, storm.windShare() * ASSUMED_PEAK / REFERENCE_WIND),
                        storm.descent(), storm.groundLoad(), storm.tint(), storm.flash(), light);
                poseStack.popPose();
            }
        });
    }
}

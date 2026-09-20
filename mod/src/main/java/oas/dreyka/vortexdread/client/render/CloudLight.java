package oas.dreyka.vortexdread.client.render;

import net.minecraft.client.Camera;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import oas.dreyka.vortexdread.config.LookConfig;
import org.joml.Vector3f;

/**
 * What the world's light is doing this frame, in the terms the marcher needs.
 *
 * <p>All of it comes off the camera's environment probe, which is the game's own answer and already
 * follows the time, the weather, the biome and the dimension. Nothing here is a curve of our own, so a
 * cloud at dusk goes orange because the world went orange and not because this file decided to.
 *
 * @param toward unit vector at the sun, in world axes
 * @param sun the light falling on the lit face of a cloud
 * @param sky what lights the face the sun never reaches, which on a clear day is the blue overhead
 */
public record CloudLight(Vector3f toward, Vector3f sun, Vector3f sky) {

    public static CloudLight of(Camera camera, float partialTick) {
        EnvironmentAttributeProbe probe = camera.attributeProbe();
        // The game hangs the sun by turning a quarter circle about the vertical and then by this angle
        // about what has become the north-south line, which sends it from east to west and puts it
        // straight overhead at noon. The probe answers in degrees and the rotation wants radians.
        float angle = Mth.DEG_TO_RAD * probe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
        Vector3f toward = new Vector3f(-Mth.sin(angle), Mth.cos(angle), 0.0f);

        int lit = probe.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, partialTick);
        float strength = probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick);
        Vector3f sun = colour(lit).mul(strength * LookConfig.cloudSunLight);
        int overhead = probe.getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
        return new CloudLight(toward, sun, colour(overhead).mul(LookConfig.cloudSkyLight));
    }

    private static Vector3f colour(int packed) {
        return new Vector3f(ARGB.redFloat(packed), ARGB.greenFloat(packed), ARGB.blueFloat(packed));
    }
}

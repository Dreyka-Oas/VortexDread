package oas.dreyka.vortexdread.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import oas.dreyka.vortexdread.weather.Rain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rain drawn as hard as it is falling where the player stands.
 *
 * <p>Both methods open by reading one number for the whole world, the smoothed rain level, and that number
 * decides how many columns of falling water are built and how many drops and how much sound land around the
 * camera. Replaced by a reading at the camera's own position, a player who walks out from under a shower
 * watches it thin out around them and stop, rather than watching it switch off.
 *
 * <p>Redirect rather than head injection, because the number is wanted and the rest of the method is not:
 * the column layout, the biome test that picks snow over rain, and the heightmap lookups are all work worth
 * keeping. One call swapped and the game draws the same rain in the same way, at a different strength.
 *
 * <p>What makes this correct on a server with several players, which is the part the advice on this usually
 * gets wrong: no packet carries the strength. Every client holds the whole cloud field, so each one reads the
 * water over its own camera from the same numbers the server read, and two players a kilometre apart get
 * different rain without the server sending either of them anything.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getRainLevel(F)F"))
    private float vortexdread$drawTheRainThatIsHere(Level level, float partialTick, Level again,
            int radius, float tick, Vec3 camera, Object state) {
        return strength(level, level.getRainLevel(partialTick), BlockPos.containing(camera));
    }

    @Redirect(method = "tickRainParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float vortexdread$dropTheRainThatIsHere(ClientLevel level, float partialTick,
            ClientLevel again, Camera camera, int ticks, ParticleStatus particles, int radius) {
        return strength(level, level.getRainLevel(partialTick), camera.blockPosition());
    }

    // The game's own level is kept as the ceiling rather than ignored, so a rain the player has turned down
    // in their options, or one fading in after a join, still fades the simulated shower with it.
    private static float strength(Level level, float vanilla, BlockPos at) {
        if (!Rain.simulated(level)) {
            return vanilla;
        }
        return vanilla * Rain.strength(level, at);
    }
}

package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.config.domain.LookConfig;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.mixin.client.GameRendererAccess;
import oas.dreyka.vortexdread.wind.VortexParameters;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * What being near a tornado does to the picture.
 *
 * <p>Three things, and all three are things people report rather than things that look good in a
 * screenshot. The light goes first, well before the wind arrives. The air stops being uniform, so
 * everything past a certain distance shimmers the way a road does in summer, except sideways and far
 * faster. And when the funnel lights itself from inside, the whole scene lights with it.
 *
 * <p>The state travels to the shader through a single pixel rather than through a uniform, because a
 * post chain's uniforms are fixed when the resource pack loads and this has to follow a storm across a
 * field. Writing four bytes a frame costs nothing next to the pass that reads them.
 */
public final class StormPostEffect {
    private StormPostEffect() {
    }

    private static final Identifier CHAIN = VortexDread.id("storm");

    /**
     * Where the single pixel of state lives.
     *
     * <p>A post chain rewrites the location it was handed in JSON into {@code textures/effect/<it>.png}
     * before asking the texture manager for it, so what gets registered here is the name after that
     * rewrite rather than the one written in the file.
     */
    private static final Identifier STATE = VortexDread.id("storm_state")
            .withPath(path -> "textures/effect/" + path + ".png");

    /** Distance at which the storm starts to be felt, as a multiple of the funnel's own reach. */
    private static final double FELT_AT = 2.2;

    /** How fast the effect follows the storm, per tick. Slower leaving than arriving, as light does. */
    private static final float RISE = 0.14f;
    private static final float FALL = 0.05f;

    /** Worst wobble of the view, in degrees, standing in the middle of an EF5. */
    private static final float MAX_SHAKE = 0.85f;

    /** How much the level has to move before it is worth saying again, in hundredths. */
    private static final int WORTH_SAYING = 12;

    private static DynamicTexture state;
    private static float nearness;
    private static float flash;
    private static boolean applied;
    private static int reportedAt = -1;

    /** How far the view is being pushed around this frame, in degrees. Read by the camera. */
    public static float shake() {
        return LookConfig.screenShake ? nearness * nearness * MAX_SHAKE : 0.0f;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StormPostEffect::tick);
    }

    private static void tick(Minecraft client) {
        if (state == null) {
            state = new DynamicTexture("vortexdread storm state", 1, 1, false);
            client.getTextureManager().register(STATE, state);
        }
        if (client.level == null) {
            nearness = 0.0f;
            flash = 0.0f;
            clear(client);
            return;
        }

        follow(client);
        write();

        boolean wanted = LookConfig.postEffect && nearness > 0.004f;
        if (wanted && !applied) {
            ((GameRendererAccess) client.gameRenderer).vortexdread$setPostEffect(CHAIN);
            applied = true;
        } else if (!wanted && applied) {
            clear(client);
        }
        if (applied) {
            announce();
        }
    }

    /**
     * Says how far under the storm the picture currently is, whenever that has moved.
     *
     * <p>Once on the way in would be a line taken at the moment the effect is weakest, which says
     * nothing about whether it ever gets anywhere. What is worth reading is the climb.
     */
    private static void announce() {
        int level = Math.round(nearness * 100.0f);
        if (reportedAt >= 0 && Math.abs(level - reportedAt) < WORTH_SAYING) {
            return;
        }
        reportedAt = level;
        VortexDread.LOGGER.info("[VortexDread] storm in view: the picture is {}% under it", level);
    }

    /** Takes the strongest funnel in sight and eases the effect toward what it deserves. */
    private static void follow(Minecraft client) {
        var camera = client.gameRenderer.getMainCamera().position();
        float closest = 0.0f;
        float brightest = 0.0f;

        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof TornadoEntity tornado)) {
                continue;
            }
            double reach = tornado.coreRadius() * VortexParameters.INFLUENCE_FACTOR * FELT_AT;
            double dx = camera.x - tornado.getX();
            double dz = camera.z - tornado.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= reach) {
                continue;
            }
            // Squared rather than linear: the last fifty blocks are most of what a person feels, and a
            // straight ramp puts half the effect on a funnel still a field and a half away.
            float share = (float) (1.0 - distance / reach);
            closest = Math.max(closest, share * share * tornado.descent());
            brightest = Math.max(brightest, tornado.flash() * share);
        }

        nearness += (closest - nearness) * (closest > nearness ? RISE : FALL);
        // The flash is not eased upward. A bolt is instant, and a light that fades in is a light nobody
        // believes; only its decay is smoothed, and the funnel itself already does that.
        flash = Math.max(brightest, flash * 0.78f);
    }

    /** Nearness, flash and grit into the one pixel the chain samples. */
    private static void write() {
        NativeImage pixels = state.getPixels();
        if (pixels == null) {
            return;
        }
        int r = Math.round(Math.min(1.0f, nearness) * 255.0f);
        int g = Math.round(Math.min(1.0f, flash) * 255.0f);
        // Grit lags the rest: the dust and rain in the air stay there a moment after the funnel has gone
        // past, which is also what keeps the effect from snapping off at the edge of its own range.
        int b = Math.round(Math.min(1.0f, nearness * 1.3f) * 255.0f);
        // The fourth channel carries a setting rather than a measurement, since the chain has nowhere
        // else to read one from and a player who turns the green sky off expects it gone this frame.
        int a = Math.round((float) Math.clamp(LookConfig.skyGreen, 0.0, 1.0) * 255.0f);
        pixels.setPixel(0, 0, (a << 24) | (b << 16) | (g << 8) | r);
        state.upload();
    }

    private static void clear(Minecraft client) {
        if (!applied) {
            return;
        }
        client.gameRenderer.clearPostEffect();
        applied = false;
        reportedAt = -1;
        VortexDread.LOGGER.info("[VortexDread] storm out of view, the picture is its own again");
    }
}

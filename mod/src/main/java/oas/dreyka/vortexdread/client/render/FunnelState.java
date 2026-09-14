package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.config.domain.LookConfig;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.network.StormDigest;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Where a shader pack reads the storms from.
 *
 * <p>Iris hands a pack whatever uniforms it decides to hand it, and a mod cannot add one. What it can
 * do is register a texture under a name, because a pack is allowed to ask for a texture by resource
 * location and Iris resolves that through the game's own texture manager rather than through the pack
 * folder. So the funnels travel as pixels: a few bytes a frame, read back in the shader as numbers.
 *
 * <p>Written once per frame rather than once per tick, because the positions are relative to the
 * camera and the camera moves between ticks. A tick-old offset on a funnel two hundred blocks away is
 * a funnel that swims against the landscape whenever the player turns.
 *
 * <p>Without a pack installed nothing reads this and the cost is one 8 by 4 upload a frame, which is
 * a few hundred bytes and does not touch the frame budget.
 */
public final class FunnelState {
    private FunnelState() {
    }

    /**
     * The name the pack asks for.
     *
     * <p>Under {@code textures/} with a {@code .png} on the end, because that is the shape of a
     * resource location a pack is written with, even though nothing here is ever a file.
     */
    public static final Identifier TEXTURE = VortexDread.id("textures/effect/funnel_state.png");

    /** How many funnels a pack is told about. Past this the nearest ones win, which is all anyone sees. */
    public static final int MAX_FUNNELS = 4;

    /** Fields per funnel, one texel each. */
    private static final int FIELDS = 8;

    /**
     * First field of the two that carry render settings rather than a funnel.
     *
     * <p>Written on every row, empty ones included, so whoever reads them never has to find a row that
     * holds a storm first. Both readers, the mod's own program and a patched pack, take them from here
     * because neither has another way to be handed a number a player just changed.
     */
    private static final int SETTINGS_FIELD = 6;

    /** Ranges the two settings fields are folded into. */
    private static final double STEPS_SPAN = 256.0;
    private static final double DETAIL_SPAN = 32.0;

    /** Half the range a camera-relative offset can hold, in blocks. Past it the funnel is not sent. */
    private static final double REACH = 2048.0;

    /** Range the ground height is folded into, in blocks, and where it starts. */
    private static final double GROUND_SPAN = 1152.0;
    private static final double GROUND_FLOOR = -128.0;

    /** Ranges for the two sizes, in blocks. */
    private static final double RADIUS_SPAN = 128.0;
    private static final double HEIGHT_SPAN = 512.0;

    private static DynamicTexture state;

    public static void register() {
        // The texture has to exist before a pack asks for it, and a pack asks the moment the world
        // loads. Registering it on the way past the title screen is early enough for any of them.
        ClientTickEvents.END_CLIENT_TICK.register(client -> ensure(client));
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> write());
    }

    private static void ensure(Minecraft client) {
        if (state != null) {
            return;
        }
        state = new DynamicTexture("vortexdread funnel state", FIELDS, MAX_FUNNELS, false);
        client.getTextureManager().register(TEXTURE, state);
    }

    private static void write() {
        Minecraft client = Minecraft.getInstance();
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        ensure(client);
        NativeImage pixels = state.getPixels();
        if (pixels == null) {
            return;
        }

        // The nearest ones rather than the first ones the level happens to hand over. Sixteen storms can
        // be running at once and only four fit, so an arbitrary four means the one filling the screen
        // can be the one left out, which looks exactly like the funnel failing to draw.
        StormDigest[] nearest = new StormDigest[MAX_FUNNELS];
        double[] distances = new double[MAX_FUNNELS];
        int found = 0;
        if (client.level != null) {
            for (var entity : client.level.entitiesForRendering()) {
                if (entity instanceof TornadoEntity tornado) {
                    found = offer(nearest, distances, found, StormDigest.of(tornado), camera);
                }
            }
        }
        for (StormDigest distant : DistantStorms.withoutEntity(client.level)) {
            found = offer(nearest, distances, found, distant, camera);
        }

        int row = 0;
        while (row < found) {
            StormDigest storm = nearest[row];
            describe(pixels, row, storm, storm.x() - camera.x, storm.z() - camera.z);
            row++;
        }
        for (int empty = row; empty < MAX_FUNNELS; empty++) {
            for (int field = 0; field < SETTINGS_FIELD; field++) {
                pixels.setPixel(field, empty, 0);
            }
        }
        for (int every = 0; every < MAX_FUNNELS; every++) {
            settings(pixels, every);
        }
        state.upload();
    }

    /** Puts one storm in front of the sort, if it is close enough to be worth a row at all. */
    private static int offer(StormDigest[] nearest, double[] distances, int found,
            StormDigest storm, Vec3 camera) {
        double dx = storm.x() - camera.x;
        double dz = storm.z() - camera.z;
        if (Math.abs(dx) > REACH || Math.abs(dz) > REACH) {
            return found;
        }
        return insert(nearest, distances, found, storm, dx * dx + dz * dz);
    }

    private static void describe(NativeImage pixels, int row, StormDigest storm, double dx, double dz) {
        pair(pixels, 0, row, (dx + REACH) / (REACH * 2.0), (dz + REACH) / (REACH * 2.0));
        pair(pixels, 1, row, (storm.groundY() - GROUND_FLOOR) / GROUND_SPAN,
                storm.coreRadius() / RADIUS_SPAN);
        pair(pixels, 2, row, storm.height() / HEIGHT_SPAN, storm.descent());

        int tint = storm.tint();
        pixels.setPixel(3, row, rgba((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF,
                byteOf(storm.groundLoad())));

        pair(pixels, 4, row, storm.flash(), storm.windShare());
        // Says the row holds a funnel at all, so the pack can stop reading rather than marching four
        // volumes of nothing on every pixel of the screen.
        pixels.setPixel(5, row, rgba(255, 0, 0, 255));
    }

    /**
     * Keeps a short list sorted by distance, in place.
     *
     * <p>An insertion rather than a sort, because the list is four long and this runs every frame: a
     * comparator here costs a lambda and a boxed Double per storm per frame, for an ordering four
     * comparisons settle.
     *
     * @return how many entries the list holds afterwards
     */
    static int insert(StormDigest[] nearest, double[] distances, int found,
            StormDigest storm, double distance) {
        if (found == MAX_FUNNELS && distance >= distances[MAX_FUNNELS - 1]) {
            return found;
        }
        int at = Math.min(found, MAX_FUNNELS - 1);
        while (at > 0 && distances[at - 1] > distance) {
            nearest[at] = nearest[at - 1];
            distances[at] = distances[at - 1];
            at--;
        }
        nearest[at] = storm;
        distances[at] = distance;
        return Math.min(found + 1, MAX_FUNNELS);
    }

    /** What the player asked the funnel to cost, for whichever program ends up drawing it. */
    private static void settings(NativeImage pixels, int row) {
        pair(pixels, SETTINGS_FIELD, row, LookConfig.funnelSteps / STEPS_SPAN,
                LookConfig.funnelLightSteps / STEPS_SPAN);
        pair(pixels, SETTINGS_FIELD + 1, row, LookConfig.funnelDetail / DETAIL_SPAN, 0.0);
    }


    /**
     * Two numbers into one texel, each across two channels.
     *
     * <p>Eight bits puts a funnel's axis half a block away from where it is at two hundred blocks out,
     * which is visible as a wobble whenever the camera moves. Sixteen puts it under a centimetre.
     */
    private static void pair(NativeImage pixels, int x, int y, double first, double second) {
        int a = quantise(first);
        int b = quantise(second);
        pixels.setPixel(x, y, rgba(a & 0xFF, (a >> 8) & 0xFF, b & 0xFF, (b >> 8) & 0xFF));
    }

    private static int quantise(double value) {
        return Math.max(0, Math.min(65535, (int) Math.round(value * 65535.0)));
    }

    private static int byteOf(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
    }

    /** Four channels into the word the image stores, which is alpha, red, green, blue from the top. */
    private static int rgba(int red, int green, int blue, int alpha) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}

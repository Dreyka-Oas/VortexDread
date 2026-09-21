package oas.dreyka.vortexdread.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.client.render.CloudPass;
import oas.dreyka.vortexdread.net.SkyFieldPayload;
import oas.dreyka.vortexdread.weather.Rain;

/**
 * The client side, which is where the sky gets drawn.
 *
 * <p>It receives and it draws. The simulation stays on the server, so this holds no solver, no card and no
 * seed: what arrives is the cloud field itself, which is the only reason two players standing next to each
 * other cannot see different weather.
 */
public final class VortexDreadClient implements ClientModInitializer {

    private static final ClientSky sky = new ClientSky();

    /** The sky to draw, shared with whatever renders it. */
    public static ClientSky sky() {
        return sky;
    }

    @Override
    public void onInitializeClient() {
        Rain.onClient(sky::rain);

        // Off the network thread and onto the client one: unpacking allocates a field and the renderer reads
        // it, so both have to happen where the renderer looks.
        ClientPlayNetworking.registerGlobalReceiver(SkyFieldPayload.TYPE,
                (payload, context) -> context.client().execute(() -> accept(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> sky.onTick());

        // The field and the textures holding it go together: a world left behind may have had a grid the
        // next one is not shaped for, and a texture outlives a disconnect unless something closes it.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            sky.forget();
            CloudPass.forget();
        });
    }

    // The counterpart of the server's boot line, and for the same reason: without it a player on a server
    // that does not run the mod and a player whose clouds are simply not drawn yet look identical.
    private static void accept(SkyFieldPayload payload) {
        boolean first = sky.step() < 0L;
        sky.accept(payload.sky(), payload.ticksPerStep());
        if (first) {
            VortexDread.LOGGER.info("[VortexDread] sky arrived: step {}, {} by {} by {} at {} m,"
                    + " {} bytes", payload.step(), payload.sizeX(), payload.sizeY(), payload.sizeZ(),
                    payload.cellSize(), payload.packed().length);
        }
    }
}

package oas.dreyka.vortexdread.net;

import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.atmosphere.SkySnapshot;

/**
 * Who gets a step of the sky, and when.
 *
 * <p>Two threads meet here and only one cell is shared. The worker packs a field the moment it has stepped
 * and drops it in {@link #waiting}; the server thread takes it on the next tick and sends it. Packing off
 * the tick is the point: it is fifteen milliseconds on an overcast sky, and the worker has just gone idle
 * while the tick has forty-nine other things to do.
 *
 * <p>The last two are kept because a client needs a pair before it can draw anything. Sent oldest first on
 * join, a player who connects an hour in has a sky on screen from their first frame instead of after the
 * next step, which at the shipped cadence would be a ten second wait.
 */
public final class SkyBroadcast {

    private final AtomicReference<SkyFieldPayload> waiting = new AtomicReference<>();

    private SkyFieldPayload newer;
    private SkyFieldPayload older;
    private long sent;
    private int largest;

    /** Called on the worker, right after a step is published. */
    public void pack(SkySnapshot sky, int ticksPerStep) {
        waiting.set(SkyFieldPayload.of(sky, ticksPerStep));
    }

    /** Called on the server thread, every tick. Sends at most one field, since at most one is ever due. */
    public void onTick(MinecraftServer server) {
        SkyFieldPayload fresh = waiting.getAndSet(null);
        if (fresh == null) {
            return;
        }
        if (newer == null) {
            // Once, because it is the one number an operator needs to size the upload: everything after it
            // is the same field again, and the same size within a factor of two.
            VortexDread.LOGGER.info("[VortexDread] sky field is {} bytes for {} by {} by {}, one every"
                    + " {} ticks", fresh.packed().length, fresh.sizeX(), fresh.sizeY(), fresh.sizeZ(),
                    fresh.ticksPerStep());
        }
        older = newer;
        newer = fresh;
        sent++;
        largest = Math.max(largest, fresh.packed().length);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            SkyFieldPayload.sendTo(player, fresh);
        }
    }

    /**
     * What the session cost the upload, for the line logged when the world closes.
     *
     * <p>The largest rather than the average, because the largest is what a server owner has to have room
     * for: the field grows with the cloud in it, so a session that started clear and ended overcast spent
     * most of itself well under its own peak.
     */
    public String tally() {
        return sent + " fields, largest " + largest + " bytes";
    }

    /** Called when a player is ready to receive. Nothing to send before the sky's second step. */
    public void onJoin(ServerPlayer player) {
        if (older != null) {
            SkyFieldPayload.sendTo(player, older);
        }
        if (newer != null) {
            SkyFieldPayload.sendTo(player, newer);
        }
    }

    /** Dropped with the world, so the next one does not open by sending the last one's clouds. */
    public void forget() {
        waiting.set(null);
        newer = null;
        older = null;
        sent = 0L;
        largest = 0;
    }
}

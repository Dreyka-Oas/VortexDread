package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.network.StormDigest;
import oas.dreyka.vortexdread.network.StormPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The storms the client knows about without having an entity for them.
 *
 * <p>Read on the render thread and written on the network thread, which is why the map is concurrent
 * rather than guarded: a frame that reads a storm mid-update draws it a tenth of a second stale, and
 * a lock around a per-frame read costs more than that is worth.
 *
 * <p>Entries go stale rather than being deleted by the server. A funnel that dies stops being sent,
 * and a player who walks out of range stops hearing about it: both look the same from here, and both
 * want the same answer, which is that the storm fades off the list a moment later instead of hanging
 * on the horizon forever.
 */
public final class DistantStorms {
    private DistantStorms() {
    }

    private record Held(StormDigest digest, long at) {
    }

    private static final Map<Integer, Held> known = new ConcurrentHashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StormPayloads.Storms.TYPE, (payload, context) -> {
            long now = System.currentTimeMillis();
            for (StormDigest digest : payload.storms()) {
                known.put(digest.id(), new Held(digest, now));
            }
        });
        // A server change leaves entity ids meaning something else entirely, so nothing survives it.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> known.clear());
    }

    /**
     * Every storm still fresh enough to draw that the client has no entity for.
     *
     * <p>The entity wins wherever there is one: it is a tick fresher than any packet and it moves with
     * the interpolation the rest of the frame is using, so taking the packet instead would make a
     * funnel jump the moment a player rode close enough for the two to swap over.
     */
    public static List<StormDigest> withoutEntity(ClientLevel level) {
        if (known.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        known.entrySet().removeIf(entry -> now - entry.getValue().at() > StormPayloads.STALE_AFTER);

        List<StormDigest> live = new ArrayList<>(known.size());
        for (Held held : known.values()) {
            if (level != null && level.getEntity(held.digest().id()) instanceof TornadoEntity) {
                continue;
            }
            live.add(held.digest());
        }
        return live;
    }
}

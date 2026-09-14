package oas.dreyka.vortexdread.network;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.config.domain.StormConfig;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.entity.VortexEntities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * How a funnel reaches a player the entity tracker has given up on.
 *
 * <p>Minecraft stops sending an entity past the server's view distance, which on a normal server is
 * well under a kilometre, and a terrain mod like Voxy draws the land for tens of kilometres. A storm
 * that is the whole point of looking at the horizon therefore vanishes exactly where the horizon
 * starts being worth looking at. What travels here is the shape and nothing else: a few dozen bytes
 * per storm, a few times a second, for storms the client has no entity for.
 */
public final class StormPayloads {
    private StormPayloads() {
    }

    /**
     * Ticks between two sends.
     *
     * <p>A funnel kilometres away moves a fraction of a pixel a tick, and the client holds the last
     * shape it was given rather than blanking between packets, so this buys most of the bandwidth back
     * for nothing an eye can see.
     */
    private static final int EVERY = 10;

    /** How long a client keeps a storm it has stopped hearing about, in milliseconds. */
    public static final long STALE_AFTER = 2500L;

    /** How many storms one packet carries. The client draws four, so sending more buys nothing. */
    private static final int MAX_SENT = 8;

    public record Storms(List<StormDigest> storms) implements CustomPacketPayload {
        public static final Type<Storms> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(VortexDread.MOD_ID, "storms"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Storms> CODEC = StreamCodec.composite(
                StormDigest.CODEC.apply(ByteBufCodecs.list(MAX_SENT)), Storms::storms, Storms::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Registers the type on both sides and starts the server sending. Called from the common init. */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(Storms.TYPE, Storms.CODEC);
        ServerTickEvents.END_WORLD_TICK.register(StormPayloads::tick);
    }

    private static void tick(ServerLevel level) {
        if (level.getServer().getTickCount() % EVERY != 0) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }

        double reach = StormConfig.distantStormReach;
        if (reach <= 0.0) {
            return;
        }

        for (ServerPlayer player : players) {
            AABB box = player.getBoundingBox().inflate(reach);
            List<TornadoEntity> found = level.getEntities(VortexEntities.TORNADO, box, tornado -> true);
            if (found.isEmpty()) {
                continue;
            }
            List<StormDigest> digests = new ArrayList<>(Math.min(found.size(), MAX_SENT));
            for (TornadoEntity tornado : found) {
                if (digests.size() >= MAX_SENT) {
                    break;
                }
                digests.add(StormDigest.of(tornado));
            }
            ServerPlayNetworking.send(player, new Storms(digests));
        }
    }
}

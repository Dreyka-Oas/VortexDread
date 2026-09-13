package oas.dreyka.vortexdread.storm;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.config.domain.StormConfig;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.entity.VortexEntities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What turns weather into a tornado, one level at a time.
 *
 * <p>The chain is the real one and it is deliberately slow. A thunderstorm starts. Somewhere under it a
 * mesocyclone begins to turn, and it keeps turning for two minutes of game time whether or not anyone
 * is looking. The cloud deck lights up from inside while that happens. Only a mature column can drop
 * anything, and most of them never do. A player who learns to read that sequence gets a warning the
 * game never gives in words.
 */
public final class StormDirector {

    /** One director per dimension, since weather and tornadoes are per dimension. */
    private static final Map<ResourceKey<Level>, StormDirector> DIRECTORS = new HashMap<>();

    /** How often the director looks at the world rather than every tick. */
    private static final int SURVEY_INTERVAL = 20;

    /** How far from a player a mesocyclone is allowed to start, in blocks. */
    private static final double SEED_DISTANCE = 900.0;

    /** How far a mesocyclone may drift from every player before it is forgotten, in blocks. */
    private static final double ABANDON_DISTANCE = 4000.0;

    private Mesocyclone mesocyclone;
    private int cooldown;
    private int survey;

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(level -> forLevel(level).tick(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DIRECTORS.clear());
    }

    private static StormDirector forLevel(ServerLevel level) {
        return DIRECTORS.computeIfAbsent(level.dimension(), key -> new StormDirector());
    }

    private void tick(ServerLevel level) {
        CloudLightning.tick(level);
        if (cooldown > 0) {
            cooldown--;
        }

        if (mesocyclone != null) {
            mesocyclone.advance();
            lightTheColumn(level);
        }
        if (++survey < SURVEY_INTERVAL) {
            return;
        }
        survey = 0;

        if (!StormConfig.naturalTornadoes) {
            mesocyclone = null;
            return;
        }
        if (mesocyclone == null) {
            if (level.isThundering()) {
                seed(level);
            }
            return;
        }
        if (!level.isThundering() || abandoned(level)) {
            mesocyclone = null;
            return;
        }
        if (mesocyclone.mature() && cooldown <= 0) {
            attemptTouchdown(level);
        }
    }

    /** Starts a rotating column under the storm, out of sight of whoever is going to meet it. */
    private void seed(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer anchor = players.get(level.random.nextInt(players.size()));
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = SEED_DISTANCE * (0.5 + level.random.nextDouble() * 0.5);
        mesocyclone = new Mesocyclone(
                anchor.getX() + distance * Math.cos(angle),
                anchor.getZ() + distance * Math.sin(angle),
                // Heading back toward the player, give or take a quarter turn: a storm that always
                // wanders off is a storm nobody ever meets.
                angle + Math.PI + (level.random.nextDouble() - 0.5) * Math.PI * 0.5,
                level.random.nextLong());
        VortexDread.LOGGER.info("[VortexDread] mesocyclone turning at {} {} in {}",
                Math.round(mesocyclone.x()), Math.round(mesocyclone.z()), level.dimension().identifier());
    }

    /** Lights the rotating column from inside, harder as it matures. */
    private void lightTheColumn(ServerLevel level) {
        if (!CloudLightning.deckLit()) {
            return;
        }
        double chance = StormConfig.cloudFlashesPerMinute / (60.0 * 20.0) * mesocyclone.maturity();
        if (level.random.nextDouble() >= chance) {
            return;
        }
        double dx = mesocyclone.offsetAt(level.random.nextDouble(), level.random.nextDouble());
        double dz = mesocyclone.offsetAt(level.random.nextDouble(), level.random.nextDouble());
        CloudLightning.flashNear(level, mesocyclone.x() + dx, level.getSeaLevel(), mesocyclone.z() + dz);
    }

    /** Rolls for a funnel reaching the ground, and starts one if it does. */
    private void attemptTouchdown(ServerLevel level) {
        if (countAlive(level) >= StormConfig.maxConcurrent) {
            return;
        }
        double perSurvey = StormConfig.tornadoChancePerMinute / (60.0 * 20.0 / SURVEY_INTERVAL);
        if (level.random.nextDouble() >= perSurvey) {
            return;
        }
        TornadoEntity tornado = TornadoEntity.spawnRandom(level, mesocyclone.x(), mesocyclone.z(),
                StormConfig.ratingBias);
        if (tornado == null) {
            return;
        }
        mesocyclone.recordSpawn();
        cooldown = StormConfig.cooldownTicks;
        VortexDread.LOGGER.info("[VortexDread] funnel down from the mesocyclone: {} at {} {} in {}",
                tornado.rating().name(), Math.round(tornado.getX()), Math.round(tornado.getZ()),
                level.dimension().identifier());
        StormWarning.announce(level, tornado);
    }

    /** Whether every player has walked away from this column, which retires it. */
    private boolean abandoned(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - mesocyclone.x();
            double dz = player.getZ() - mesocyclone.z();
            if (dx * dx + dz * dz < ABANDON_DISTANCE * ABANDON_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    private static int countAlive(ServerLevel level) {
        AABB everywhere = new AABB(-3.0e7, level.getMinY(), -3.0e7, 3.0e7, level.getMaxY(), 3.0e7);
        return level.getEntities(VortexEntities.TORNADO, everywhere, tornado -> true).size();
    }
}

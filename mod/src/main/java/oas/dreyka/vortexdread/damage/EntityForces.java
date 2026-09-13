package oas.dreyka.vortexdread.damage;

import oas.dreyka.vortexdread.config.domain.DamageConfig;
import oas.dreyka.vortexdread.entity.DebrisEntity;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.wind.TurbulentVortex;
import oas.dreyka.vortexdread.wind.VortexParameters;
import oas.dreyka.vortexdread.wind.WindVector;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** What the flow does to anything standing in it. */
public final class EntityForces {
    private EntityForces() {
    }

    /** Below this the wind is weather rather than a force, in blocks per second. */
    private static final double FELT_FLOOR = 8.0;

    /**
     * How fast a body gives in to the air around it, per tick, at the core wall. A body is not a
     * feather: it keeps its own momentum for a moment, which is what makes being caught feel like being
     * dragged rather than being teleported onto a rail.
     */
    private static final double MAX_RESPONSE = 0.28;

    /** A creative or spectating player is passed over entirely. */
    private static final java.util.function.Predicate<Entity> AFFECTED =
            EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.NO_CREATIVE_OR_SPECTATOR);

    public static void apply(ServerLevel level, TornadoEntity tornado, VortexParameters parameters) {
        WindVector wind = new WindVector();
        double phase = tornado.phase();

        for (Entity victim : level.getEntities(tornado, tornado.influenceBox(), AFFECTED)) {
            if (victim instanceof DebrisEntity || victim instanceof TornadoEntity) {
                continue;
            }
            TurbulentVortex.sample(parameters, phase, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                    victim.getZ(), wind);
            double speed = wind.speed();
            if (speed < FELT_FLOOR) {
                continue;
            }

            boolean lift = speed >= DamageConfig.liftThreshold
                    && (DamageConfig.liftPlayers || !(victim instanceof Player));
            double vertical = lift ? wind.y : Math.min(wind.y, 0.0);

            double response = MAX_RESPONSE * Math.min(1.0, speed / Math.max(1.0f, parameters.peakWind()));
            Vec3 current = victim.getDeltaMovement();
            Vec3 target = new Vec3(wind.x / 20.0, vertical / 20.0, wind.z / 20.0);
            victim.setDeltaMovement(current.lerp(target, response));
            victim.hurtMarked = true;

            if (victim instanceof ServerPlayer player) {
                // The client owns a player's position, so a velocity the server never sends is a
                // velocity the player never feels.
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            if (victim instanceof LivingEntity living) {
                StormDamage.byWind(level, living, speed);
            }
        }
    }
}

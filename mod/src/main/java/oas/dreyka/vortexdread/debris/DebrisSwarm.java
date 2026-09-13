package oas.dreyka.vortexdread.debris;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oas.dreyka.vortexdread.compute.AdvectionCompute;
import oas.dreyka.vortexdread.entity.DebrisEntity;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.wind.VortexParameters;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Every piece one level has in the air, advected together.
 *
 * <p>Piece by piece inside each entity's own tick was the obvious way and the wrong one. The advection
 * is the same arithmetic on a few hundred independent bodies, which is exactly the shape a graphics card
 * answers, and a card cannot be handed one body at a time: the trip costs more than the sum.
 *
 * <p>The roll is kept by the loader's own load and unload events rather than by sweeping the level for
 * debris every tick. The game already knows when an entity arrives and when it goes, and a sweep pays
 * for that knowledge again twenty times a second.
 *
 * <p>Run at the start of the level tick, so each piece then moves on the velocity this pass just wrote.
 */
public final class DebrisSwarm {

    /** One roll per dimension, since a piece cannot be carried across one. */
    private static final Map<ResourceKey<Level>, DebrisSwarm> SWARMS = new HashMap<>();

    private final List<DebrisEntity> pieces = new ArrayList<>();

    /** Pieces grouped by the funnel carrying them. Cleared and refilled rather than rebuilt. */
    private final Map<TornadoEntity, List<DebrisEntity>> byOwner = new HashMap<>();

    private double[] position = new double[0];
    private double[] velocity = new double[0];
    private double[] terminal = new double[0];

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof DebrisEntity piece) {
                forLevel(level).pieces.add(piece);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof DebrisEntity piece) {
                forLevel(level).pieces.remove(piece);
            }
        });
        ServerTickEvents.START_WORLD_TICK.register(level -> forLevel(level).advance(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SWARMS.clear());
    }

    private static DebrisSwarm forLevel(ServerLevel level) {
        return SWARMS.computeIfAbsent(level.dimension(), key -> new DebrisSwarm());
    }

    /** How many pieces this level is carrying, for a report or a test. */
    public static int aloft(ServerLevel level) {
        return forLevel(level).pieces.size();
    }

    private void advance(ServerLevel level) {
        if (pieces.isEmpty()) {
            return;
        }
        byOwner.clear();
        for (DebrisEntity piece : pieces) {
            TornadoEntity owner = piece.owner(level);
            if (owner == null || piece.isRemoved()) {
                // A piece whose storm is gone still has weight, and its own tick takes care of that.
                continue;
            }
            byOwner.computeIfAbsent(owner, key -> new ArrayList<>()).add(piece);
        }

        for (Map.Entry<TornadoEntity, List<DebrisEntity>> group : byOwner.entrySet()) {
            advanceGroup(group.getKey(), group.getValue());
        }
    }

    private void advanceGroup(TornadoEntity owner, List<DebrisEntity> group) {
        int count = group.size();
        grow(count);
        for (int i = 0; i < count; i++) {
            DebrisEntity piece = group.get(i);
            Vec3 motion = piece.getDeltaMovement();
            int b = i * 3;
            position[b] = piece.getX();
            position[b + 1] = piece.getY();
            position[b + 2] = piece.getZ();
            velocity[b] = motion.x * 20.0;
            velocity[b + 1] = motion.y * 20.0;
            velocity[b + 2] = motion.z * 20.0;
            terminal[i] = piece.terminalVelocity();
        }

        VortexParameters parameters = owner.parameters();
        AdvectionCompute.stepAll(parameters, owner.phase(), 1.0 / 20.0, position, velocity, terminal, count);

        for (int i = 0; i < count; i++) {
            int b = i * 3;
            // The velocity is taken and the integrated position thrown away: the game owns where a body
            // ends up, because only it knows what the body ran into on the way.
            group.get(i).carriedBy(velocity[b] / 20.0, velocity[b + 1] / 20.0, velocity[b + 2] / 20.0);
        }
    }

    private void grow(int count) {
        if (terminal.length >= count) {
            return;
        }
        position = new double[count * 3];
        velocity = new double[count * 3];
        terminal = new double[count];
    }
}

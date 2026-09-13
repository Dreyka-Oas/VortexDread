package oas.dreyka.vortexdread.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.entity.VortexEntities;
import oas.dreyka.vortexdread.wind.EfScale;
import oas.dreyka.vortexdread.wind.TurbulentVortex;
import oas.dreyka.vortexdread.wind.WindVector;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everything another mod needs from Vortex Dread.
 *
 * <p>Reading works on either side and needs nothing set up. Making a tornado needs a server level, for
 * the same reason spawning anything else does.
 *
 * <p>Nothing here caches. A level knows which entities it is holding, and asking it is cheaper than
 * keeping a second list in step with it.
 */
public final class VortexDreadApi {
    private VortexDreadApi() {
    }

    /** Half the width of the largest world, in blocks. Nothing is ever loaded past it. */
    private static final double WORLD_EDGE = 3.0e7;

    /** Every tornado currently loaded in this level, in no particular order. */
    public static List<TornadoView> tornadoes(Level level) {
        List<TornadoView> found = new ArrayList<>();
        for (TornadoEntity tornado : live(level)) {
            found.add(tornado);
        }
        return found;
    }

    /**
     * The funnels this side knows about.
     *
     * <p>The two sides keep their entities in different places and neither exposes the other's, so the
     * one branch here is the price of an API that answers on both. A server asks its own index, which
     * covers every loaded chunk; a client asks what it is drawing, which is what it has.
     */
    private static List<? extends TornadoEntity> live(Level level) {
        if (level instanceof ServerLevel server) {
            return server.getEntities(VortexEntities.TORNADO, tornado -> !tornado.isRemoved());
        }
        // A client has no index by type, so the search goes through the box: the whole world, since a
        // storm is worth knowing about long before it is close, and a client only holds what it draws.
        return level.getEntities(VortexEntities.TORNADO,
                new AABB(-WORLD_EDGE, level.getMinY(), -WORLD_EDGE, WORLD_EDGE, level.getMaxY(), WORLD_EDGE),
                tornado -> !tornado.isRemoved());
    }

    /**
     * The tornado whose axis is nearest this point, if any is loaded.
     *
     * <p>Nearest by axis rather than by where the wind is strongest, because the axis is the thing a
     * caller can draw an arrow at. What the wind is doing at a point is {@link #windAt}.
     */
    public static Optional<TornadoView> nearest(Level level, Vec3 point) {
        TornadoView best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TornadoView view : tornadoes(level)) {
            double distance = view.position().distanceToSqr(point.x, view.position().y, point.z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = view;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The wind at a point, in blocks per second, from every storm that reaches it.
     *
     * <p>Two funnels close enough to overlap add rather than one winning, which is what the flow itself
     * does. Away from all of them this is the zero vector, not a breeze: ordinary weather is not this
     * mod's business.
     */
    public static Vec3 windAt(Level level, Vec3 point) {
        WindVector sample = new WindVector();
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (TornadoEntity tornado : live(level)) {
            if (!tornado.influenceBox().contains(point)) {
                continue;
            }
            TurbulentVortex.sample(tornado.parameters(), tornado.phase(), point.x, point.y, point.z, sample);
            x += sample.x;
            y += sample.y;
            z += sample.z;
        }
        return new Vec3(x, y, z);
    }

    /**
     * Drops a funnel at a place, at the strength a storm of that rating would reach.
     *
     * <p>The rating is a ceiling rather than a promise: what comes down grows toward it, holds for a
     * while and ropes out, so a caller asking for an EF5 gets a storm that spends part of its life
     * weaker than that. A caller wanting a given wind on the ground right now should watch
     * {@link TornadoView#wind()} rather than trusting the rating it asked for.
     */
    public static TornadoView spawn(ServerLevel level, double x, double z, EfScale rating) {
        return TornadoEntity.spawn(level, x, z, rating.minWind());
    }
}

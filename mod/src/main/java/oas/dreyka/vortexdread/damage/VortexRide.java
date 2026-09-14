package oas.dreyka.vortexdread.damage;

import oas.dreyka.vortexdread.wind.VortexParameters;
import oas.dreyka.vortexdread.wind.WindVector;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What happens to a body once the funnel has hold of it.
 *
 * <p>Sampling the flow and handing the result straight to the entity gives a shove, not a ride: the
 * field is strongest at the core wall and points mostly sideways, so a player thrown across it leaves
 * again on the far side within a second. What a tornado actually does to what it catches is keep it,
 * turn it around the axis and carry it up, and that is a spiral rather than a push. So the flow decides
 * which way round the body goes and how fast, and this decides the radius it holds and the rate it
 * climbs at, which are the two things the sampled field alone will not give.
 *
 * <p>Nothing here is stored between ticks. Where a body is against the axis is the whole state, so a
 * reload, a dimension change or a tornado dying mid-ride all resolve themselves.
 */
public final class VortexRide {
    private VortexRide() {
    }

    /** How far outside the visible funnel the column still takes hold, as a share of its radius. */
    private static final double CATCH_REACH = 1.3;

    /** Where in the funnel the ride settles, as a share of the radius at that height. */
    private static final double HOLD_SHARE = 0.78;

    /** How hard the body is pulled back onto that radius, in blocks per second per block of error. */
    private static final double HOLD_PULL = 1.6;

    /** Climb rate at the bottom of the funnel, in blocks per second. */
    private static final double CLIMB = 26.0;

    /** Share of the funnel height over which the climb eases off before the throw. */
    private static final double CREST = 0.12;

    /** Share of the tangential wind the body is actually turned at. A body is not air. */
    private static final double ORBIT_SHARE = 0.62;

    /** How fast the body gives in to the ride, per tick. Higher than the open flow: it is held, not blown. */
    private static final double RESPONSE = 0.5;

    /** Outward speed at the throw, in blocks per second. */
    private static final double THROW_OUT = 24.0;

    /** Extra lift at the throw, in blocks per second. */
    private static final double THROW_UP = 9.0;

    /** Whether the column has hold of a body there, which is what decides whether the wind may hurt it. */
    public static boolean holds(VortexParameters parameters, double x, double y, double z) {
        if (y < parameters.groundY() || y > parameters.cloudBaseY()) {
            return false;
        }
        double dx = x - parameters.centerX();
        double dz = z - parameters.centerZ();
        return dx * dx + dz * dz <= squared(parameters.funnelRadiusAt(y) * CATCH_REACH);
    }

    /**
     * One tick of the ride.
     *
     * @param wind the flow already sampled at the body, which is where the turn comes from
     */
    public static void carry(VortexParameters parameters, Entity victim, WindVector wind) {
        Vec3 perTick = target(parameters, victim.getX(), victim.getY(), victim.getZ(), wind).scale(1.0 / 20.0);
        // A throw is an event, not a negotiation: easing into it over several ticks would leave the body
        // hanging under the wall cloud while the funnel walks out from under it.
        boolean thrown = crested(parameters, victim.getY());
        victim.setDeltaMovement(thrown ? perTick : victim.getDeltaMovement().lerp(perTick, RESPONSE));
        victim.hurtMarked = true;

        // The climb is not a fall. Counting it would make every ride end the same way, whatever the
        // player does on the way down.
        victim.resetFallDistance();
    }

    /** Whether the body has reached the top of the funnel, where the ride turns into a throw. */
    public static boolean crested(VortexParameters parameters, double y) {
        return 1.0 - parameters.heightFraction(y) <= CREST;
    }

    /** Where the ride wants the body going, in blocks per second. */
    public static Vec3 target(VortexParameters parameters, double x, double y, double z, WindVector wind) {
        double dx = x - parameters.centerX();
        double dz = z - parameters.centerZ();
        double radius = Math.sqrt(dx * dx + dz * dz);

        // Anything that lands exactly on the axis has no direction to be pushed out along, and the
        // updraft alone will move it off within a tick or two.
        double outX = radius > 1.0e-3 ? dx / radius : 0.0;
        double outZ = radius > 1.0e-3 ? dz / radius : 0.0;

        double flow = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        double turnX = flow > 1.0e-3 ? wind.x / flow : -outZ;
        double turnZ = flow > 1.0e-3 ? wind.z / flow : outX;
        double orbit = flow * ORBIT_SHARE;

        if (crested(parameters, y)) {
            return new Vec3(outX * THROW_OUT + turnX * orbit, THROW_UP, outZ * THROW_OUT + turnZ * orbit);
        }

        double pull = (parameters.funnelRadiusAt(y) * HOLD_SHARE - radius) * HOLD_PULL;
        double left = 1.0 - parameters.heightFraction(y);
        double climb = CLIMB * Math.min(1.0, left / CREST);

        return new Vec3(turnX * orbit + outX * pull + parameters.translationX(),
                        climb,
                        turnZ * orbit + outZ * pull + parameters.translationZ());
    }

    private static double squared(double value) {
        return value * value;
    }
}

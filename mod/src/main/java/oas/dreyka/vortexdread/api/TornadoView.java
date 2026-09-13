package oas.dreyka.vortexdread.api;

import oas.dreyka.vortexdread.tornado.TornadoStage;
import oas.dreyka.vortexdread.wind.EfScale;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One tornado, read only.
 *
 * <p>Everything here is live rather than copied, so a view held across ticks keeps telling the truth
 * about a storm that is still moving. It stops telling the truth about a storm that has ended, which
 * is what {@link #alive()} is for.
 *
 * <p>The view exists on both sides. On the client it reads what the server sent, which is slightly
 * behind and never disagrees; anything deciding damage or placement belongs on the server anyway.
 */
public interface TornadoView {

    /** Where the axis meets the ground. */
    Vec3 position();

    /** Whether the storm still exists. A view of a finished one answers false and nothing else moves. */
    boolean alive();

    /** Rating from the wind it is turning at right now, which climbs and falls over its life. */
    EfScale rating();

    /** Strongest rating this one has reached, which is what a warning should be written against. */
    EfScale peakRating();

    /** Where it is in its life. */
    TornadoStage stage();

    /** Ticks since it formed. */
    int ageTicks();

    /** Tangential wind at the radius of maximum wind, in metres per second. */
    float wind();

    /** Radius of maximum wind, in blocks. The visible condensation is a little narrower. */
    float coreRadius();

    /** Ground to cloud base under the axis, in blocks. */
    float funnelHeight();

    /** How far down the column has reached, 0 hanging out of the cloud and 1 on the ground. */
    float descent();

    /** How much of the ground it is currently carrying, 0 clean and 1 wrapped in what it took. */
    float groundLoad();

    /** Colour the debris has given it, packed as 0xRRGGBB. Brown over a field, grey over water. */
    int tint();

    /** The box the wind reaches into. Outside it the storm does nothing at all. */
    AABB influenceBox();
}

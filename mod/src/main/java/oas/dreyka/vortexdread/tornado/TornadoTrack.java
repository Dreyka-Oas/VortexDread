package oas.dreyka.vortexdread.tornado;

import oas.dreyka.vortexdread.wind.CurlNoise;

/**
 * Where the tornado is and where it is going.
 *
 * <p>A real track is nearly straight and never exactly straight. It holds a heading for a while, bends
 * a few degrees, and occasionally takes a longer turn as the parent storm cycles. That is one noise
 * field read along the life of the storm, not a random walk: a random walk wanders visibly, and a
 * tornado that wanders visibly stops looking like weather.
 *
 * <p>No game types, so the track can be played forward in a test and checked against a map.
 */
public final class TornadoTrack {

    /** How far apart two samples of the wander noise are, in seconds of storm life. */
    private static final double WANDER_PERIOD = 26.0;

    /**
     * Width of the window the seed is folded into before it becomes a noise coordinate. The noise is
     * sampled on a unit lattice, so a coordinate the size of a real seed lands far outside the cell it
     * is interpolated in and the value comes back enormous instead of bounded.
     */
    private static final int CURVE_WINDOW = 4096;

    private final long seed;
    private final double curve;
    private final double speed;
    private final double wanderDegreesPerSecond;

    private double x;
    private double z;
    private double heading;
    private double elapsed;

    /**
     * @param x                      starting world x
     * @param z                      starting world z
     * @param heading                initial bearing in radians, measured from east toward south
     * @param speed                  ground speed in blocks per second
     * @param wanderDegreesPerSecond how far the bearing may bend per second
     * @param seed                   picks which wander curve this storm follows
     */
    public TornadoTrack(double x, double z, double heading, double speed,
                        double wanderDegreesPerSecond, long seed) {
        this.x = x;
        this.z = z;
        this.heading = heading;
        this.speed = speed;
        this.wanderDegreesPerSecond = wanderDegreesPerSecond;
        this.seed = seed;
        this.curve = Math.floorMod(seed, CURVE_WINDOW) * 0.618;
    }

    /** The seed this track was built from, so a saved storm resumes on the same curve. */
    public long seed() {
        return seed;
    }

    /** Moves the track on by an interval, in seconds. */
    public void advance(double dt) {
        elapsed += dt;
        double bend = CurlNoise.fbm(elapsed / WANDER_PERIOD, curve, 0.0, 2);
        heading += Math.toRadians(wanderDegreesPerSecond) * bend * dt;
        x += Math.cos(heading) * speed * dt;
        z += Math.sin(heading) * speed * dt;
    }

    /** Puts the track where a saved tornado left it. */
    public void restore(double x, double z, double heading, double elapsed) {
        this.x = x;
        this.z = z;
        this.heading = heading;
        this.elapsed = elapsed;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    public double heading() {
        return heading;
    }

    public double elapsed() {
        return elapsed;
    }

    /** East component of the ground speed, in blocks per second. */
    public double velocityX() {
        return Math.cos(heading) * speed;
    }

    /** South component of the ground speed, in blocks per second. */
    public double velocityZ() {
        return Math.sin(heading) * speed;
    }
}

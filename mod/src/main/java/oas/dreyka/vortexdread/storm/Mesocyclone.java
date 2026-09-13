package oas.dreyka.vortexdread.storm;

import oas.dreyka.vortexdread.tornado.TornadoTrack;

/**
 * The rotating updraft a tornado comes out of.
 *
 * <p>Tornadoes do not appear in open air. They hang from a mesocyclone: a column of the thunderstorm,
 * several kilometres across, that has already been turning for twenty minutes before anything reaches
 * the ground. Most mesocyclones never produce one at all. Modelling that column rather than rolling a
 * die against the weather is what makes the sky worth reading: the storm arrives, it darkens, it
 * starts flashing from the inside, and only then does something come down.
 *
 * <p>It travels like the storm it belongs to, which is the same track code a tornado uses, because on
 * radar the funnel simply follows its parent.
 */
public final class Mesocyclone {

    /** Radius of the rotating column, in blocks. Scaled down from the kilometres of the real thing. */
    public static final double RADIUS = 320.0;

    /** How long the column has to turn before it can drop anything, in ticks. */
    private static final int MATURITY_TICKS = 2400;

    /** Ground speed of the parent storm, in blocks per second. Storms outrun their own funnels. */
    private static final double DRIFT_SPEED = 9.0;

    /** How far the storm's bearing may bend per second, in degrees. */
    private static final double DRIFT_WANDER = 0.9;

    private final TornadoTrack path;
    private int age;
    private int spawned;

    public Mesocyclone(double x, double z, double heading, long seed) {
        this.path = new TornadoTrack(x, z, heading, DRIFT_SPEED, DRIFT_WANDER, seed);
    }

    public void advance() {
        age++;
        path.advance(1.0 / 20.0);
    }

    public double x() {
        return path.x();
    }

    public double z() {
        return path.z();
    }

    public int age() {
        return age;
    }

    /** How far along the column is to being able to drop a funnel, 0 to 1. */
    public double maturity() {
        return Math.min(1.0, (double) age / MATURITY_TICKS);
    }

    /** Whether the column has turned long enough to reach the ground. */
    public boolean mature() {
        return age >= MATURITY_TICKS;
    }

    public int spawned() {
        return spawned;
    }

    public void recordSpawn() {
        spawned++;
    }

    /**
     * A point somewhere in the rotating column, used to place the flashes that light it from inside.
     * Weighted toward the middle, which is where a real storm's charge separation is strongest.
     */
    public double offsetAt(double u, double v) {
        return (u * 2.0 - 1.0) * RADIUS * (0.35 + 0.65 * v);
    }
}

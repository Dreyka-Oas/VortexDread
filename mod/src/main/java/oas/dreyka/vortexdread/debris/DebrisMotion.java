package oas.dreyka.vortexdread.debris;

import oas.dreyka.vortexdread.wind.TurbulentVortex;
import oas.dreyka.vortexdread.wind.VortexParameters;
import oas.dreyka.vortexdread.wind.WindVector;

/**
 * How a piece of the world moves once the wind has it.
 *
 * <p>Drag against the air it is sitting in, and weight. Nothing else: no spring toward the axis, no
 * scripted orbit, no lifetime that ends with a fade. A plank ends up circling the core wall because
 * that is where the drag and the weight balance, not because it was told to.
 *
 * <p>Each piece carries its terminal velocity in still air instead of a mass and an area, which is the
 * same two numbers with the arithmetic already done, and is the one a reader can picture.
 *
 * <p>Written against flat arrays because this is the loop that goes to the graphics card. The kernel in
 * {@code kernels/} solves the same equation, and the two are compared against each other rather than
 * one being trusted.
 */
public final class DebrisMotion {
    private DebrisMotion() {
    }

    /**
     * Downward acceleration, in blocks per second squared. This is Minecraft's own, not Earth's: a
     * debris that fell at nine point eight next to a falling block that falls at thirty two would read
     * as a bug, and the drag model below is calibrated against it.
     */
    public static final double GRAVITY = 32.0;

    /** Most substeps one tick is ever split into, when the drag term gets stiff. */
    private static final int MAX_SUBSTEPS = 4;

    /** Slowest a piece is allowed to fall in still air, so the drag constant stays finite. */
    private static final double MIN_TERMINAL = 1.0;

    /**
     * Advances one batch of debris through one interval.
     *
     * @param p        the vortex carrying them
     * @param phase    seconds since the vortex formed, for the turbulence
     * @param dt       interval in seconds, normally one tick
     * @param position x, y, z per piece, read and written
     * @param velocity x, y, z per piece, read and written
     * @param terminal still air terminal velocity per piece, in blocks per second
     * @param count    how many pieces of the arrays are live
     */
    public static void stepAll(
            VortexParameters p,
            double phase,
            double dt,
            double[] position,
            double[] velocity,
            double[] terminal,
            int count) {
        WindVector wind = new WindVector();
        for (int i = 0; i < count; i++) {
            int b = i * 3;
            step(p, phase, dt, position, velocity, terminal[i], b, wind);
        }
    }

    /** One piece, one interval. The scratch vector belongs to the caller and is reused across pieces. */
    public static void step(
            VortexParameters p,
            double phase,
            double dt,
            double[] position,
            double[] velocity,
            double terminal,
            int base,
            WindVector wind) {

        double drag = dragConstant(terminal);
        TurbulentVortex.sample(p, phase, position[base], position[base + 1], position[base + 2], wind);

        double rx = wind.x - velocity[base];
        double ry = wind.y - velocity[base + 1];
        double rz = wind.z - velocity[base + 2];
        double relative = Math.sqrt(rx * rx + ry * ry + rz * rz);

        int substeps = substepsFor(drag, relative, dt);
        double h = dt / substeps;

        for (int s = 0; s < substeps; s++) {
            if (s > 0) {
                TurbulentVortex.sample(p, phase, position[base], position[base + 1], position[base + 2], wind);
                rx = wind.x - velocity[base];
                ry = wind.y - velocity[base + 1];
                rz = wind.z - velocity[base + 2];
                relative = Math.sqrt(rx * rx + ry * ry + rz * rz);
            }
            // The drag term is taken at the end of the step rather than the start. Solved for, since it
            // is linear in the new velocity once the relative speed in front of it is held at its old
            // value. A leaf has a terminal velocity of a few blocks a second and meets a core wall doing
            // eighty, which is a drag coefficient forty times larger than the step can carry explicitly:
            // taken at the start of the step it overshoots the wind, comes back harder, and a hundredth
            // of a second later the piece is a kilometre away. This form cannot overshoot at any step
            // size, and it settles on the same terminal velocity the explicit one aims at.
            double k = drag * relative;
            double settle = 1.0 / (1.0 + k * h);
            velocity[base] = (velocity[base] + k * wind.x * h) * settle;
            velocity[base + 1] = (velocity[base + 1] + (k * wind.y - GRAVITY) * h) * settle;
            velocity[base + 2] = (velocity[base + 2] + k * wind.z * h) * settle;

            position[base] += velocity[base] * h;
            position[base + 1] += velocity[base + 1] * h;
            position[base + 2] += velocity[base + 2] * h;
        }
    }

    /**
     * Drag per unit of relative speed squared, chosen so that a piece falling through still air settles
     * at exactly its terminal velocity.
     */
    public static double dragConstant(double terminal) {
        double t = Math.max(MIN_TERMINAL, terminal);
        return GRAVITY / (t * t);
    }

    /**
     * How finely the interval has to be cut. Not for stability, which the step above holds on its own,
     * but for accuracy: a piece crossing the core wall passes through a wind that changes by tens of
     * blocks per second over its own path in one tick, and one sample taken where it started puts it on
     * the wrong side of the wall.
     */
    private static int substepsFor(double drag, double relative, double dt) {
        double slope = 2.0 * drag * relative * dt;
        int n = (int) Math.ceil(slope);
        return n < 1 ? 1 : Math.min(n, MAX_SUBSTEPS);
    }
}

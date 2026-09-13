package oas.dreyka.vortexdread.wind;

/**
 * A wind velocity in blocks per second, mutable and caller-owned.
 *
 * <p>The advection loop runs over every debris, every entity and every sampled block position on each
 * tick, so the samplers write into a vector the caller already holds rather than returning a fresh one.
 */
public final class WindVector {
    public double x;
    public double y;
    public double z;

    public WindVector() {
    }

    public WindVector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void add(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
    }

    public void zero() {
        x = 0.0;
        y = 0.0;
        z = 0.0;
    }

    public double horizontalSpeed() {
        return Math.sqrt(x * x + z * z);
    }

    public double speed() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    @Override
    public String toString() {
        return "WindVector[" + x + ", " + y + ", " + z + "]";
    }
}

package oas.dreyka.vortexdread.atmosphere;

/**
 * The working arrays one simulation step needs, allocated once for the life of the simulation.
 *
 * <p>A step traces three velocity components into somewhere else before it can read the old values,
 * advects three scalars one at a time, and relaxes a pressure field it does not keep. Allocating that per
 * step would hand the collector tens of megabytes a second on the server thread, which is the kind of cost
 * that never shows up in a profile as the simulation and always shows up as a stutter somewhere else.
 */
public final class AtmosphereScratch {

    /**
     * Where the traced velocity waits.
     *
     * <p>It has to wait, because heat and water are carried by the flow as it stood at the start of the
     * step. Writing the new velocity straight into the grid would have the second half of the step carried
     * by air that has already moved.
     */
    public final float[] velocityX;
    public final float[] velocityY;
    public final float[] velocityZ;

    /** One advected scalar, reused by all three since each is copied back before the next is touched. */
    public final float[] advected;

    /** The pressure correction the projection solves for, and the imbalance it is solving against. */
    public final float[] pressure;
    public final float[] divergence;

    public AtmosphereScratch(int cellCount) {
        this.velocityX = new float[cellCount];
        this.velocityY = new float[cellCount];
        this.velocityZ = new float[cellCount];
        this.advected = new float[cellCount];
        this.pressure = new float[cellCount];
        this.divergence = new float[cellCount];
    }
}

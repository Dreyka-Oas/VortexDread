package oas.dreyka.vortexdread.atmosphere;

/**
 * The numbers the ground is driven with, derived once from the settings and the still profile.
 *
 * <p>Its own record because both paths need exactly these and computing them twice is how the two drift
 * apart. A cell size divided by a thermal width is one rounding, and a processor that did that division and a
 * card that was handed the two operands would not be running the same simulation by the end of the afternoon.
 * Derived here, uploaded as arguments, and the kernel does no arithmetic on them at all.
 *
 * @param seed what the thermal pattern is built from, and the one number a replay has to be given
 * @param latticePerCell how many thermal widths one cell spans, which is what turns a cell coordinate into a
 *     noise coordinate
 * @param secondsPerLattice the same for time: one unit along the noise's third axis per thermal period
 * @param temperatureAmplitude how much warmer than ambient the warm patches are, K
 * @param humidityAmplitude how much moister, as a fraction of the surface value
 * @param surfaceTemperature the potential temperature the pattern is measured against, K
 * @param surfaceVapour the vapour mixing ratio it is measured against, kg/kg
 */
public record SurfaceDrive(
        long seed,
        float latticePerCell,
        float secondsPerLattice,
        float temperatureAmplitude,
        float humidityAmplitude,
        float surfaceTemperature,
        float surfaceVapour) {

    /**
     * How long the lowest air takes to come most of the way to the ground's own state, in seconds.
     *
     * <p>Five minutes is the order of the real thing over a summer field, and it is slow enough that a thermal
     * leaving carries away more than the ground can immediately replace, which is what makes convection come
     * in bursts instead of one steady column.
     */
    private static final float RESPONSE_TIME = 300.0f;

    public static SurfaceDrive of(AtmosphereSettings settings, AtmosphereProfile profile, long seed) {
        return new SurfaceDrive(seed, settings.cellSize() / settings.thermalWidth(),
                1.0f / settings.thermalPeriod(), settings.temperatureAmplitude(),
                settings.humidityAmplitude(), profile.surfaceTemperature, profile.surfaceVapour);
    }

    /** Where along the noise's third axis the pattern has drifted to, which is what makes it move. */
    public float time(float elapsedSeconds) {
        return elapsedSeconds * secondsPerLattice;
    }

    /**
     * How much of the way to the ground's state one step of this length carries the lowest air.
     *
     * <p>A flux and not a value. Writing the target straight into the cells makes the surface an infinite
     * reservoir: a downdraft arriving with cold dry air is reset to warm and damp the same step it lands, so
     * the heat and the water keep coming however much the column has already taken.
     */
    public float relaxation(float timeStep) {
        return Math.min(1.0f, timeStep / RESPONSE_TIME);
    }
}

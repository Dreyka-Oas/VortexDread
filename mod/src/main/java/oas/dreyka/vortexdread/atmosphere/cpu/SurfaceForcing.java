package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereProfile;
import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;
import oas.dreyka.vortexdread.atmosphere.ThermalNoise;

/**
 * The warm damp air entering at the ground, and the only thing in the simulation that is not physics.
 *
 * <p>Ground does not heat evenly. A ploughed field, a stretch of rock and a patch of forest reach
 * different temperatures under the same sun, and the air above each rises at a different rate. That
 * unevenness is where every cloud starts, so it is set here rather than left to chance: a perfectly even
 * floor produces a perfectly even sheet with nothing in it.
 *
 * <p>The pattern drifts rather than flickering. A thermal that changed every step would seed nothing,
 * since a bubble needs to be fed for long enough to reach its condensation level.
 *
 * <p>What the ground hands over is a flux and not a value. Writing the target straight into the cells
 * makes the surface an infinite reservoir: a downdraft arriving with cold dry air is reset to warm and
 * damp the same step it lands, so the heat and the water keep coming however much the column has
 * already taken. Warm ground under air that is already that warm heats nothing, and the relaxation
 * below is what says so.
 */
public final class SurfaceForcing {

    private final ThermalNoise noise;
    private final float latticePerCell;
    private final float secondsPerLattice;
    private final float temperatureAmplitude;
    private final float humidityAmplitude;

    /** Offsets the humidity pattern off the temperature one so the two are related without being equal. */
    private static final float HUMIDITY_OFFSET = 37.0f;

    /**
     * How long the lowest air takes to come most of the way to the ground's own state, in seconds.
     *
     * <p>Five minutes is the order of the real thing over a summer field, and it is slow enough that a
     * thermal leaving carries away more than the ground can immediately replace, which is what makes
     * convection come in bursts instead of one steady column.
     */
    private static final float RESPONSE_TIME = 300.0f;

    public SurfaceForcing(AtmosphereSettings settings, long seed) {
        this.noise = new ThermalNoise(seed);
        this.latticePerCell = settings.cellSize() / settings.thermalWidth();
        this.secondsPerLattice = 1.0f / settings.thermalPeriod();
        this.temperatureAmplitude = settings.temperatureAmplitude();
        this.humidityAmplitude = settings.humidityAmplitude();
    }

    /**
     * Nudges the floor row, and the row above it, toward what the ground underneath them is doing.
     *
     * <p>Two rows rather than one because the floor itself is held at rest by the boundary stage, so a
     * bubble seeded there alone would be flattened before buoyancy ever saw it. Two rows of sixty odd
     * metres is also about the depth the surface actually stirs on a convective afternoon.
     *
     * @param elapsedSeconds simulated time since the start, which is what makes the pattern move
     */
    public void apply(AtmosphereGrid grid, AtmosphereProfile profile, float elapsedSeconds,
            float timeStep) {
        float time = elapsedSeconds * secondsPerLattice;
        float relaxation = Math.min(1.0f, timeStep / RESPONSE_TIME);

        for (int z = 0; z < grid.sizeZ; z++) {
            for (int x = 0; x < grid.sizeX; x++) {
                float latticeX = x * latticePerCell;
                float latticeZ = z * latticePerCell;

                float warmth = noise.layered(latticeX, latticeZ, time, 3);
                float damp = noise.layered(latticeX + HUMIDITY_OFFSET, latticeZ + HUMIDITY_OFFSET, time, 2);

                float temperature = profile.surfaceTemperature + temperatureAmplitude * warmth;
                float vapour = profile.surfaceVapour * (1.0f + humidityAmplitude * damp);
                if (vapour < 0.0f) {
                    vapour = 0.0f;
                }

                for (int y = 0; y < 2; y++) {
                    int index = grid.index(x, y, z);
                    grid.potentialTemperature[index] +=
                            (temperature - grid.potentialTemperature[index]) * relaxation;
                    grid.vapour[index] += (vapour - grid.vapour[index]) * relaxation;
                }
            }
        }
    }
}

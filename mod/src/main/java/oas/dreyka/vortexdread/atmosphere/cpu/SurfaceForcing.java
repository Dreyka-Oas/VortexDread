package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.SurfaceDrive;
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
 * <p>What the ground hands over is a flux and not a value, which {@link SurfaceDrive} says the reason for,
 * along with the rest of the numbers this stage is driven by.
 */
public final class SurfaceForcing {

    private final ThermalNoise noise;
    private final SurfaceDrive drive;

    /** Offsets the humidity pattern off the temperature one so the two are related without being equal. */
    private static final float HUMIDITY_OFFSET = 37.0f;

    public SurfaceForcing(SurfaceDrive drive) {
        this.noise = new ThermalNoise(drive.seed());
        this.drive = drive;
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
    public void apply(AtmosphereGrid grid, float elapsedSeconds, float timeStep) {
        float time = drive.time(elapsedSeconds);
        float relaxation = drive.relaxation(timeStep);

        for (int z = 0; z < grid.sizeZ; z++) {
            for (int x = 0; x < grid.sizeX; x++) {
                float latticeX = x * drive.latticePerCell();
                float latticeZ = z * drive.latticePerCell();

                float warmth = noise.layered(latticeX, latticeZ, time, 3);
                float damp = noise.layered(latticeX + HUMIDITY_OFFSET, latticeZ + HUMIDITY_OFFSET, time, 2);

                float temperature = drive.surfaceTemperature() + drive.temperatureAmplitude() * warmth;
                float vapour = drive.surfaceVapour() * (1.0f + drive.humidityAmplitude() * damp);
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

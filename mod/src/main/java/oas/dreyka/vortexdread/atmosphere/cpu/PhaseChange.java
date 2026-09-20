package oas.dreyka.vortexdread.atmosphere.cpu;

import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereProfile;
import oas.dreyka.vortexdread.atmosphere.Thermodynamics;

/**
 * Where the cloud actually comes from, Harris equations 7, 8 and 13.
 *
 * <p>Every cell asks how much vapour it could hold at its own temperature and pressure, and gives up the
 * excess as droplets. That is the only place in the whole simulation a cloud is created, and it is also
 * where the loop closes: condensing releases latent heat, the heat raises the potential temperature, the
 * warmer parcel is lighter, buoyancy lifts it further, and lifting it cools it into condensing more. Cut
 * the warming out and the same grid grows flat sheets instead of towers.
 */
public final class PhaseChange {

    private PhaseChange() {
    }

    public static void apply(AtmosphereGrid grid, AtmosphereProfile profile) {
        for (int y = 0; y < grid.sizeY; y++) {
            float exner = profile.exner[y];
            float pressure = profile.pressure[y];
            int rowStart = grid.index(0, y, 0);
            int rowEnd = rowStart + grid.sizeX * grid.sizeZ;

            for (int index = rowStart; index < rowEnd; index++) {
                float potentialTemperature = grid.potentialTemperature[index];
                float celsius = Thermodynamics.temperatureCelsius(potentialTemperature, exner);
                float saturation = Thermodynamics.saturationMixingRatio(celsius, pressure);
                float slope = Thermodynamics.saturationSlope(celsius, saturation);

                // A limiter can undershoot a cell that had almost no droplets in it by a rounding error, and
                // the line below would read a negative count as air owing water and drain the vapour to pay
                // for it. What it owes is far below anything a cloud is made of, so it is written off.
                float droplets = Math.max(0.0f, grid.cloudWater[index]);
                float change = Thermodynamics.phaseChange(saturation, slope, grid.vapour[index], droplets);

                grid.vapour[index] += change;
                grid.cloudWater[index] = droplets - change;
                grid.potentialTemperature[index] = potentialTemperature
                        + Thermodynamics.latentWarming(change, exner);
            }
        }
    }
}

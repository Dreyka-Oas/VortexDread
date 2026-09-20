package oas.dreyka.vortexdread.atmosphere;

/**
 * The still atmosphere the simulation runs inside: one value per altitude row, computed once.
 *
 * <p>Air at rest in this profile stays at rest, so anything that moves in the grid moves because the
 * simulation made it move. That is what makes the profile worth precomputing rather than folding into
 * the cell state: it is the reference every buoyancy term is measured against.
 *
 * <p>It has two layers, and that is the whole reason cumulus exist. Near the ground the air is stirred
 * all day by the sun and ends up at one uniform potential temperature and one uniform humidity, so a warm
 * bubble there keeps rising with nothing to stop it and nothing to dilute it. Above that the potential
 * temperature climbs with height, and a bubble arriving at that layer is colder than what it meets and
 * slows down.
 *
 * <p>Getting either layer wrong costs the cloud entirely. A profile stable from the floor stops the bubble
 * a couple of hundred metres up, below the altitude where it could have condensed, and the sky stays clear
 * whatever the ground does. A profile whose humidity falls off from the floor dilutes the bubble on the way
 * up, so it arrives at the condensation level with less water than it left with and never saturates. And a
 * profile neutral all the way lets every bubble run into the ceiling of the grid.
 */
public final class AtmosphereProfile {

    public final float[] exner;
    public final float[] pressure;
    public final float[] ambientTemperature;
    public final float[] ambientVapour;
    public final float[] ambientVirtual;

    /** Potential temperature at the floor, K. Near 300 over warm ground. */
    public final float surfaceTemperature;

    /**
     * Vapour mixing ratio through the mixed layer, kg/kg, derived rather than given.
     *
     * <p>It is set from the humidity at the top of the mixed layer, because that is the one place the
     * number is constrained: any higher and the still air up there would already be cloud, which is a sky
     * overcast from the first tick with no thermal having done anything.
     */
    public final float surfaceVapour;

    /**
     * @param sizeY number of altitude rows, matching the grid
     * @param cellSize cell edge in metres, matching the grid
     * @param surfaceTemperature potential temperature at the floor, K
     * @param mixedLayerHeight top of the neutral layer, metres
     * @param lapseRate how fast potential temperature rises above that layer, K per metre
     * @param mixedLayerHumidity how close to saturated the top of the mixed layer is, 0 to 1. Around 0.95
     *     on a day with cumulus; below roughly 0.8 no thermal carries enough water to condense
     * @param dryingHeight height over which the air above the mixed layer loses a factor of e of its
     *     relative humidity, metres
     */
    public AtmosphereProfile(int sizeY, float cellSize, float surfaceTemperature, float mixedLayerHeight,
            float lapseRate, float mixedLayerHumidity, float dryingHeight) {
        this.surfaceTemperature = surfaceTemperature;
        this.exner = new float[sizeY];
        this.pressure = new float[sizeY];
        this.ambientTemperature = new float[sizeY];
        this.ambientVapour = new float[sizeY];
        this.ambientVirtual = new float[sizeY];

        // Potential temperature is uniform below the mixed layer top, so the closed form is exact there and
        // the saturation it gives is the ceiling the whole layer's humidity is set against.
        float exnerAtTop = Thermodynamics.exner(mixedLayerHeight, surfaceTemperature);
        float celsiusAtTop = Thermodynamics.temperatureCelsius(surfaceTemperature, exnerAtTop);
        float saturationAtTop = Thermodynamics.saturationMixingRatio(celsiusAtTop,
                Thermodynamics.pressure(exnerAtTop));
        this.surfaceVapour = mixedLayerHumidity * saturationAtTop;

        float gravityOverHeat = Thermodynamics.GRAVITY / Thermodynamics.HEAT_CAPACITY;
        float previousTemperature = surfaceTemperature;
        float runningExner = 1.0f;
        float previousAltitude = 0.0f;

        for (int y = 0; y < sizeY; y++) {
            float altitude = (y + 0.5f) * cellSize;
            float aboveMixedLayer = Math.max(0.0f, altitude - mixedLayerHeight);
            float temperature = surfaceTemperature + lapseRate * aboveMixedLayer;

            // Hydrostatic balance integrated row by row. Doing it as a running sum rather than in closed
            // form keeps one expression for any lapse rate, the neutral case included, where the closed
            // form divides by zero.
            float meanTemperature = 0.5f * (previousTemperature + temperature);
            runningExner -= gravityOverHeat * (altitude - previousAltitude) / meanTemperature;
            this.exner[y] = runningExner;
            this.pressure[y] = Thermodynamics.pressure(runningExner);

            // Inside the mixed layer the air holds the same amount of water at every height, because that is
            // what stirred all day means, and the relative humidity therefore climbs toward the top: that
            // climb is the flat base every cumulus shares. Above it what falls off is the humidity rather
            // than the water, since specifying the water instead leaves the air up there sitting at ninety
            // odd percent whatever the number, and air that close to saturated condenses on the first
            // disturbance that reaches it, warms itself doing so, and rises to the ceiling of the grid.
            float vapourHere = surfaceVapour;
            if (aboveMixedLayer > 0.0f) {
                float humidity = mixedLayerHumidity
                        * (float) StrictMath.exp(-aboveMixedLayer / dryingHeight);
                vapourHere = humidity * saturationAt(y, temperature);
            }

            this.ambientTemperature[y] = temperature;
            this.ambientVapour[y] = vapourHere;
            this.ambientVirtual[y] = Thermodynamics.virtualPotentialTemperature(temperature, vapourHere);

            previousTemperature = temperature;
            previousAltitude = altitude;
        }
    }

    public int rows() {
        return exner.length;
    }

    /**
     * Saturation mixing ratio for a parcel of this potential temperature sitting at this altitude row.
     *
     * <p>The parcel's own temperature is used, not the ambient one, which is the whole point: a parcel
     * that was warmed by condensation saturates at a different level than the air around it.
     */
    public float saturationAt(int y, float potentialTemperature) {
        float celsius = Thermodynamics.temperatureCelsius(potentialTemperature, exner[y]);
        return Thermodynamics.saturationMixingRatio(celsius, pressure[y]);
    }

    /** Fills a grid with this profile, at rest, with no droplets anywhere. */
    public void reset(AtmosphereGrid grid) {
        for (int y = 0; y < grid.sizeY; y++) {
            float temperature = ambientTemperature[y];
            float vapourHere = ambientVapour[y];
            int rowStart = grid.index(0, y, 0);
            int rowEnd = rowStart + grid.sizeX * grid.sizeZ;
            for (int i = rowStart; i < rowEnd; i++) {
                grid.velocityX[i] = 0.0f;
                grid.velocityY[i] = 0.0f;
                grid.velocityZ[i] = 0.0f;
                grid.potentialTemperature[i] = temperature;
                grid.vapour[i] = vapourHere;
                grid.cloudWater[i] = 0.0f;
            }
        }
    }
}

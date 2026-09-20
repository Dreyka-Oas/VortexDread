package oas.dreyka.vortexdread.atmosphere;

/**
 * Everything the simulation needs to know that is not state, in SI units.
 *
 * <p>Metres, seconds and kelvin, with no mention of blocks or ticks anywhere. The simulation models a
 * real patch of troposphere and the mapping onto a Minecraft sky is somebody else's problem, which is
 * what lets the physics be checked against a meteorology table instead of against a screenshot.
 *
 * @param sizeX cells across, east to west. Even, which the pressure solver requires of both horizontal
 *     axes since it sweeps every other cell and they wrap
 * @param sizeY cells up. The short axis: a patch of sky is wide and shallow
 * @param sizeZ cells across, north to south. Even, for the same reason as sizeX
 * @param cellSize edge of one cell in metres. Below roughly 40 the grid costs more than the detail is
 *     worth, since anything finer than a cell is the renderer's job
 * @param surfaceTemperature potential temperature at the floor, K, near 300 over warm ground
 * @param mixedLayerHeight top of the neutral layer the sun stirs, metres. A bubble rises freely below it
 *     and meets resistance above it, so this has to sit above the condensation level or the sky stays
 *     clear whatever else is set
 * @param lapseRate rise of ambient potential temperature above that layer, K per metre. This is what caps
 *     a growing tower and flattens it into an anvil. Weak enough and nothing caps anything: condensing a
 *     gram of water per kilogram of air warms a parcel two and a half degrees, which at four degrees per
 *     kilometre buys it another six hundred metres of climb, and the next gram buys the same again
 * @param mixedLayerHumidity how close to saturated the top of the mixed layer is, 0 to 1. The single number
 *     that decides whether there are clouds at all: around 0.95 gives a sky with cumulus in it, below
 *     roughly 0.8 no thermal carries enough water to reach saturation and the sky stays clear
 * @param dryingHeight height over which the air above the mixed layer loses a factor of e of its relative
 *     humidity, metres. What matters is that the humidity falls and not just the water: air left sitting
 *     near saturation aloft condenses on the first disturbance that reaches it and warms itself into the
 *     ceiling of the grid
 * @param thermalWidth width of one warm patch of ground, metres
 * @param thermalPeriod how long a warm patch takes to move on, seconds
 * @param temperatureAmplitude how much warmer than ambient the warm patches are, K
 * @param humidityAmplitude how much moister than ambient they are, as a fraction of the surface value
 * @param pressureIterations relaxation passes over the pressure per step. Too few leaves the flow
 *     compressible, which looks like cloud appearing out of nothing
 * @param timeStep simulated seconds per step. The advection slices a long step up by itself so nothing
 *     here can outrun a cell, but a short step is not free either: the smaller the share of a cell a
 *     parcel crosses, the more the face values smooth it out, so a very fine step blurs a thermal on its
 *     way up and delays the first cloud. The altitude the cloud forms at does not move
 */
public record AtmosphereSettings(
        int sizeX,
        int sizeY,
        int sizeZ,
        float cellSize,
        float surfaceTemperature,
        float mixedLayerHeight,
        float lapseRate,
        float mixedLayerHumidity,
        float dryingHeight,
        float thermalWidth,
        float thermalPeriod,
        float temperatureAmplitude,
        float humidityAmplitude,
        int pressureIterations,
        float timeStep) {

    /**
     * A humid summer afternoon over warm ground: scattered cumulus with a base near a kilometre, some of
     * them growing into towers. The grid covers about six kilometres square and four high.
     */
    public static AtmosphereSettings fairWeather() {
        return new AtmosphereSettings(96, 48, 96, 64.0f, 300.0f, 1400.0f, 0.008f, 0.95f, 900.0f,
                900.0f, 240.0f, 1.5f, 0.3f, 30, 10.0f);
    }

    public AtmosphereGrid newGrid() {
        return new AtmosphereGrid(sizeX, sizeY, sizeZ, cellSize);
    }

    public AtmosphereProfile newProfile() {
        return new AtmosphereProfile(sizeY, cellSize, surfaceTemperature, mixedLayerHeight, lapseRate,
                mixedLayerHumidity, dryingHeight);
    }

    /** How high the modelled column reaches, metres. */
    public float domainHeight() {
        return sizeY * cellSize;
    }

    /** How wide it is, metres. The horizontal axes tile, so this is also the repeat distance. */
    public float domainWidth() {
        return sizeX * cellSize;
    }
}

package oas.dreyka.vortexdread.atmosphere;

/**
 * The thermodynamics of moist air, as a set of pure functions over one cell.
 *
 * <p>Every formula here comes from Harris, Baxter, Scheuermann and Lastra, Simulation of Cloud
 * Dynamics on Graphics Hardware, Graphics Hardware 2003, and the equation numbers of that paper are
 * quoted against each one. The pair that matters most is the saturation mixing ratio and the latent
 * heat: together they close the loop that separates a flat cloud from a tower. Condensing releases
 * heat, heat lightens the air, lighter air rises, rising air cools, and cooling condenses more.
 */
public final class Thermodynamics {

    /** Standard gravity, m/s2. */
    public static final float GRAVITY = 9.80665f;

    /** Specific heat of dry air at constant pressure, J/(kg K). */
    public static final float HEAT_CAPACITY = 1005.0f;

    /** Gas constant for dry air, J/(kg K). */
    public static final float GAS_CONSTANT = 287.0f;

    /** Latent heat of vaporisation of water near 0 C, J/kg. Within 10 % over plus or minus 40 C. */
    public static final float LATENT_HEAT = 2.501e6f;

    /** Reference pressure the Exner function is measured against, Pa. */
    public static final float BASE_PRESSURE = 100000.0f;

    /** Rd over cp, the exponent linking pressure to the Exner function. */
    private static final float KAPPA = GAS_CONSTANT / HEAT_CAPACITY;

    private static final float KELVIN_OFFSET = 273.15f;

    private Thermodynamics() {
    }

    /**
     * The Exner function at an altitude, for an atmosphere of uniform potential temperature.
     *
     * <p>Hydrostatic balance at constant potential temperature makes it linear in height, which is why
     * the environment needs no pressure array: one multiply gives the profile at any cell.
     *
     * @param altitudeMetres height above the domain floor
     * @param basePotentialTemperature the ambient potential temperature, K, near 300 in the low troposphere
     */
    public static float exner(float altitudeMetres, float basePotentialTemperature) {
        return 1.0f - GRAVITY * altitudeMetres / (HEAT_CAPACITY * basePotentialTemperature);
    }

    /** Environmental pressure at an altitude, Pa, from the Exner function there. Harris equation 5. */
    public static float pressure(float exner) {
        return BASE_PRESSURE * (float) StrictMath.pow(Math.max(exner, 1.0e-4f), 1.0f / KAPPA);
    }

    /** Absolute temperature, K, of a parcel of potential temperature theta. Harris equation 3. */
    public static float temperatureKelvin(float potentialTemperature, float exner) {
        return potentialTemperature * exner;
    }

    /** The same temperature in Celsius, which is the unit the saturation fit below expects. */
    public static float temperatureCelsius(float potentialTemperature, float exner) {
        return potentialTemperature * exner - KELVIN_OFFSET;
    }

    /**
     * Virtual potential temperature, which accounts for vapour making air lighter. Harris section 2.3.
     *
     * <p>The 0.61 looks negligible against a temperature near 300 and is not: it is what lets humid air
     * rise on a day when dry air of the same temperature would sit still.
     */
    public static float virtualPotentialTemperature(float potentialTemperature, float vapourRatio) {
        return potentialTemperature * (1.0f + 0.61f * vapourRatio);
    }

    /**
     * Buoyant acceleration per unit mass, m/s2. Harris equation 4.
     *
     * <p>Vapour lifts, condensed droplets weigh. The second term is what gives a mature cloud its flat
     * anvil instead of letting the column climb without end.
     */
    public static float buoyancy(float potentialTemperature, float vapourRatio, float cloudWaterRatio,
            float ambientVirtualTemperature) {
        float virtual = virtualPotentialTemperature(potentialTemperature, vapourRatio);
        return GRAVITY * (virtual / ambientVirtualTemperature - 1.0f - cloudWaterRatio);
    }

    /**
     * Saturation mixing ratio, kg of vapour per kg of dry air. Harris equation 6, a curve fit to the
     * standard tables accurate to 0.1 % between -30 and 30 C.
     *
     * <p>Single precision throughout and {@link PortableMath#exp} rather than the library, because this is
     * the one function in the simulation that runs per cell per step on both the processor and the card, and
     * the two have to return the same bits. That class says what goes wrong when they do not.
     *
     * @param temperatureCelsius parcel temperature, C
     * @param pressurePascals environmental pressure at the cell, Pa
     */
    public static float saturationMixingRatio(float temperatureCelsius, float pressurePascals) {
        float exponent = 17.67f * temperatureCelsius / (temperatureCelsius + 243.5f);
        return 380.16f * PortableMath.exp(exponent) / pressurePascals;
    }

    /**
     * The change in potential temperature caused by a change in vapour. Harris equation 8.
     *
     * @param vapourChange how much vapour the cell gained; negative when vapour condensed
     * @return the potential temperature change, positive when condensation released heat
     */
    public static float latentWarming(float vapourChange, float exner) {
        return -LATENT_HEAT * vapourChange / (HEAT_CAPACITY * exner);
    }

    /**
     * How fast the saturation ratio climbs with temperature, per kelvin.
     *
     * <p>The derivative of {@link #saturationMixingRatio}, which is cheap because the formula is an
     * exponential: the answer is the value itself times the derivative of the exponent.
     */
    public static float saturationSlope(float temperatureCelsius, float saturation) {
        float offset = temperatureCelsius + 243.5f;
        return saturation * 17.67f * 243.5f / (offset * offset);
    }

    /**
     * How much vapour changes phase in one step. Harris equation 13, with the feedback put back in.
     *
     * <p>Positive means droplets evaporated back into vapour, negative means vapour condensed. The minimum
     * is what stops a dry cell from evaporating water it does not hold.
     *
     * <p>Condensing does not leave the cell where it was. The heat released warms the air, warmer air holds
     * more vapour, and the amount it could hold has moved by the time the condensation finishes. Handing
     * over the whole excess as Harris writes it therefore overshoots, near the ground by a factor close to
     * three: too much heat comes out, the cell ends drier than saturated, the next step evaporates some
     * back and cools it, and the two take turns with a larger swing each time until the field stops being
     * finite. So the excess is divided by how far the target moves while it is being chased, which is one
     * step of Newton's method on the same equation and is what every cloud model does with it.
     *
     * @param saturation the saturation mixing ratio at this cell
     * @param saturationSlope how much that ratio moves per kelvin, from {@link #saturationSlope}
     * @param vapourRatio vapour present after advection
     * @param cloudWaterRatio droplets present after advection, never negative
     */
    public static float phaseChange(float saturation, float saturationSlope, float vapourRatio,
            float cloudWaterRatio) {
        float chase = 1.0f + LATENT_HEAT / HEAT_CAPACITY * saturationSlope;
        return Math.min((saturation - vapourRatio) / chase, cloudWaterRatio);
    }
}

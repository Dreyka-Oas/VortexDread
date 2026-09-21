package oas.dreyka.vortexdread.config;

import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;

/**
 * The sky a server decides on, and the only options that change what the simulation produces.
 *
 * <p>Read on the server and nowhere else. A client receives the cloud field itself rather than replaying
 * it, so none of these has to agree with anything on the far end: a player whose own file says a different
 * grid width still watches the server's weather, because the sizes travel with every field. What an
 * operator sets here is what every player on the server sees.
 *
 * <p>The two that pick the machinery rather than the weather change nothing anyone can see. Which card a
 * machine has is nobody else's business, and the card and the processor return the same numbers to the bit,
 * so an operator can switch between them mid-session and the sky carries on.
 *
 * <p>What is not here is deliberate. The thermodynamics are physics, not preference: the latent heat of
 * water and the gas constant of air are what they are, and an option to change them is an option to make
 * the sky wrong. What a server owner gets to decide is how much of the machine the sky is worth and what
 * kind of day it is.
 */
public final class SkyConfig {

    private SkyConfig() {
    }

    /**
     * Cells across, both horizontal axes. Even, because the pressure solver sweeps every other cell and
     * the axes wrap around.
     *
     * <p>This is the option that costs money. The work goes as the cube, so halving it is eight times
     * cheaper and covers a quarter of the ground at twice the cell size.
     */
    public static int cellsAcross = 96;

    /** Cells up. The short axis: a patch of sky is wide and shallow. */
    public static int cellsUp = 48;

    /**
     * Edge of one cell, metres. Below roughly forty the grid costs more than the detail is worth, since
     * anything finer than a cell is drawn by the renderer rather than simulated.
     */
    public static float cellSizeMetres = 64.0f;

    /** Simulated seconds the sky advances per step. */
    public static float secondsPerStep = 10.0f;

    /**
     * How fast the weather runs against the clock. One is real time.
     *
     * <p>Twenty by default, which is the ceiling, and the reason is what a player sees in their first
     * minute. Condensation needs about a hundred steps whatever this is set to, since that is how long the
     * warm air takes to reach its own saturation level. At one that first cloud is seventeen minutes of
     * staring at an empty sky, and a sky that has not condensed yet is indistinguishable from a mod that
     * is not working. At twenty it is under a minute, and the weather then goes on changing across a
     * session instead of holding one shape all evening. What the number buys is fewer ticks between steps
     * rather than a coarser step, so nothing about the physics changes with it.
     *
     * <p>Lowering it is the setting for someone who wants the real pace and knows to wait. Raising it past
     * twenty does not work: the steps come closer together than the card can finish them, the runner
     * starts skipping, and the weather ends up running slower than the number asks for with the clouds
     * jumping between the steps that did land.
     */
    public static float skyTimeScale = 20.0f;

    /**
     * Relaxation passes over the pressure per step.
     *
     * <p>Too few leaves the flow slightly compressible, which reads as cloud appearing out of nothing.
     * Thirty is enough because heat is advected by the form that tolerates a little leftover divergence;
     * a scheme that counted fluxes for heat as well would need four times as many.
     */
    public static int pressurePasses = 30;

    /**
     * Whether the step runs on the graphics card when one answers.
     *
     * <p>On by default, because shipping it off hands the win to nobody: almost no one goes looking for an
     * option they were never told about. The processor path stays the reference and takes over whole
     * wherever no device is found, so turning this off costs frames and changes no result.
     */
    public static boolean useGraphicsCard = true;

    /**
     * Which card, counting from zero in the order the driver lists them, or -1 to let the mod decide.
     *
     * <p>Here because the rule that decides cannot be right on every machine. It prefers the widest AMD part
     * it finds, which is this project's own hardware talking, and a machine laid out differently deserves a
     * way to overrule it rather than an explanation. The boot line names what was picked and what was passed
     * over, so the number to put here is readable from one run.
     */
    public static int graphicsCardIndex = -1;

    /** Potential temperature at the ground, K. Three hundred is a warm afternoon. */
    public static float surfaceTemperatureKelvin = 300.0f;

    /**
     * Top of the layer the sun stirs, metres, which is also the altitude the cloud base sits at.
     *
     * <p>It has to stay above the level where air off the ground saturates, or the sky is clear whatever
     * else is set: a bubble that meets resistance before it has cooled enough to condense stops there.
     */
    public static float mixedLayerMetres = 1400.0f;

    /**
     * How close to saturated the top of that layer is, 0 to 1.
     *
     * <p>The single number that decides whether there are clouds at all. Around 0.95 gives scattered
     * cumulus; below roughly 0.8 no thermal carries enough water to reach saturation.
     */
    public static float humidityAtCloudBase = 0.95f;

    /**
     * Rise of ambient potential temperature above the mixed layer, K per metre.
     *
     * <p>What caps a growing tower and flattens it into an anvil. Weak enough and nothing caps anything:
     * condensing a gram of water per kilogram of air warms a parcel two and a half degrees, which at four
     * degrees per kilometre buys another six hundred metres of climb, and the next gram buys the same.
     */
    public static float lapseRatePerMetre = 0.008f;

    /** How wide one warm patch of ground is, metres. */
    public static float thermalWidthMetres = 900.0f;

    /** How long a warm patch takes to move on, seconds. */
    public static float thermalPeriodSeconds = 240.0f;

    /** How much warmer than ambient the warm patches are, K. */
    public static float thermalWarmthKelvin = 1.5f;

    /** How much moister they are, as a fraction of the surface value. */
    public static float thermalDampness = 0.3f;

    /**
     * Server ticks between two steps, which is the only place Minecraft's clock meets the simulation's.
     *
     * <p>Counted in ticks rather than measured off a wall clock on purpose. A client blends across this
     * number to get from one field to the next, and a server that has dropped to fifteen ticks a second has
     * a sky that slows down with it rather than one that keeps jumping ahead of the frames.
     */
    public static int ticksPerStep() {
        float scale = Math.min(20.0f, Math.max(0.05f, skyTimeScale));
        return Math.max(1, Math.round(secondsPerStep * 20.0f / scale));
    }

    /** These options as the simulation wants them, in SI units and with no mention of Minecraft. */
    public static AtmosphereSettings settings() {
        return new AtmosphereSettings(evenAtLeast(cellsAcross), Math.max(4, cellsUp),
                evenAtLeast(cellsAcross), cellSizeMetres, surfaceTemperatureKelvin, mixedLayerMetres,
                lapseRatePerMetre, humidityAtCloudBase, dryingHeightMetres(), thermalWidthMetres,
                thermalPeriodSeconds, thermalWarmthKelvin, thermalDampness, pressurePasses,
                secondsPerStep);
    }

    /**
     * Height over which the air above the mixed layer loses a factor of e of its relative humidity.
     *
     * <p>Not an option, because it is not a choice anyone makes about the weather: what it controls is
     * whether the still air aloft is left sitting near saturation, and air that close to the line condenses
     * on the first disturbance that reaches it, warms itself doing so, and climbs into the ceiling of the
     * grid. Tying it to the depth of the mixed layer keeps that margin whatever the rest is set to.
     */
    private static float dryingHeightMetres() {
        return Math.max(300.0f, 0.65f * mixedLayerMetres);
    }

    private static int evenAtLeast(int cells) {
        int clamped = Math.max(8, cells);
        return (clamped & 1) == 0 ? clamped : clamped + 1;
    }
}

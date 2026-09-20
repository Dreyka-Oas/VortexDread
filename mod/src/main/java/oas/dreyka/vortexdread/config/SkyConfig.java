package oas.dreyka.vortexdread.config;

import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;

/**
 * The sky a server decides on, and the only options that change what the simulation produces.
 *
 * <p>Everything that shapes the sky has to hold the same value on the server and on every client connected
 * to it. Clients do not receive the cloud field, they replay the simulation from the same seed and the same
 * numbers, so a client whose grid is one cell narrower is a client watching different weather. Until the
 * network stage exists each side reads its own file and a mismatch shows as two skies; after it, the
 * server's values arrive on join and overwrite whatever the client had.
 *
 * <p>The two that pick the machinery rather than the weather are the exception, and they stay local. Which
 * card a machine has is nobody else's business, and the card and the processor return the same numbers to the
 * bit, so a server running on one and a client on the other still watch the same clouds.
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
     * <p>At one, a cumulus takes the twenty minutes it takes outside: the sky drifts, it does not boil. That
     * is the sky that was asked for and it is the default. A server that wants weather a player notices inside
     * one session raises this, and what it buys is fewer ticks between steps rather than a coarser step, so
     * nothing about the physics changes.
     *
     * <p>It has a ceiling because past it the sky stops being a simulation. Above roughly twenty, the steps
     * come closer together than the card can finish them and the runner starts skipping, which stops the pace
     * being reproducible and leaves two machines on different steps.
     */
    public static float skyTimeScale = 1.0f;

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
     * <p>Counted in ticks rather than measured off a wall clock on purpose. A client replays the sky from the
     * seed instead of receiving it, so the pace has to be something both sides can derive: at twenty ticks a
     * second the same tick number is the same step number on every machine, and a wall clock is not.
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

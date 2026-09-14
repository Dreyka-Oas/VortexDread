package oas.dreyka.vortexdread.config.domain;

/** What the funnel costs to draw, and what the storm does to the screen. Client side only. */
public final class LookConfig {
    private LookConfig() {
    }

    /**
     * Raymarch steps through the funnel volume. This is the one number that decides both how solid the
     * cloud looks and what it costs. Under sixteen the wall goes banded.
     */
    public static int funnelSteps = 40;

    /** Steps taken toward the light for the shadow inside the volume. Zero flattens the funnel. */
    public static int funnelLightSteps = 6;

    /** Size of the smallest visible wisp, in blocks. Smaller wants more steps to stay quiet. */
    public static double funnelDetail = 1.8;

    /**
     * How far the funnel is still drawn, in blocks. Beyond it the storm is sky and sound.
     *
     * <p>Well past what vanilla would ever send an entity for, because a mod drawing far terrain puts
     * the horizon tens of kilometres out and the storm on it arrives over the network instead.
     */
    public static double funnelRenderDistance = 2048.0;

    /** Whether the screen moves when the wind does. Off for anyone who cannot take it. */
    public static boolean screenShake = true;

    /** Whether the storm runs its own post pass: the darkening, the streaks, the flash. */
    public static boolean postEffect = true;

    /** How much the world colour turns toward the green a real tornado sky takes, from 0 to 1. */
    public static double skyGreen = 0.55;
}

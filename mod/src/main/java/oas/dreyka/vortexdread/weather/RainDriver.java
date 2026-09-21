package oas.dreyka.vortexdread.weather;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Pushes the game's own weather switch to agree with the simulation.
 *
 * <p>Needed because of the order the game asks its questions in. Everything that depends on position goes
 * through {@code isRainingAt}, which goes through {@code precipitationAt}, which opens by checking the global
 * switch and answers NONE when it is off. So a mod that only narrows the per-position answer narrows nothing:
 * with the switch off there is no rain to place anywhere.
 *
 * <p>The switch is therefore driven rather than replaced, and this is the one place it is written. On when any
 * column in the domain holds water, off when none does, and the per-position hook decides which of the wet
 * columns is over the player. The mod this idea came from pinned it to always on and filtered every caller by
 * hand; driven from the field, a genuinely clear sky turns the game's own rain off the same way the game does,
 * so anything that reads the switch and that nobody has thought of still gets a true answer.
 *
 * <p>The level's timers are pushed out with it. Left alone they would run the vanilla cycle underneath and
 * flip the switch back on a schedule of their own, which reads as rain arriving from nowhere between two
 * simulated showers.
 */
public final class RainDriver {

    /** Ticks of vanilla clear weather asked for at a time, so the game's own cycle never gets a turn. */
    private static final int HELD_CLEAR = 24_000;

    /**
     * Ticks the switch has to want the same thing before it is written.
     *
     * <p>Without it the switch tracks the field exactly, and a cloud sitting right on the threshold flips it
     * on and off every step. That reads as rain stuttering, and it is worse than either answer: the smoothed
     * level a client draws needs several seconds to fade, so a switch flipping faster than the fade leaves the
     * rain permanently halfway.
     *
     * <p>Five seconds, which is under one step at the shipped cadence, so a sky that genuinely turns wet is
     * not made to wait for the rain it earned.
     */
    static final int STEADY_FOR = 100;

    private final RainReport report;

    private Boolean lastSaid;
    private boolean wanting;
    private int steadyFor;

    public RainDriver(RainReport report) {
        this.report = report;
    }

    /** Called every server tick. Nothing is written unless the answer changed and held. */
    public void onTick(MinecraftServer server) {
        RainMap map = report.map();
        if (!map.arrived()) {
            return;
        }
        boolean wanted = map.raining();
        if (wanted != wanting) {
            wanting = wanted;
            steadyFor = 0;
            return;
        }
        if (steadyFor < STEADY_FOR) {
            steadyFor++;
            return;
        }
        if (lastSaid != null && lastSaid == wanted) {
            return;
        }
        lastSaid = wanted;
        for (ServerLevel level : server.getAllLevels()) {
            set(level, wanted);
        }
    }

    /** Dropped with the world, so the next one is not held to the last one's answer. */
    public void forget() {
        lastSaid = null;
        wanting = false;
        steadyFor = 0;
    }

    // Clear time and rain time both, since setWeatherParameters reads the pair: a clear time of zero with a
    // rain time set is the game being told to rain for that long and then decide for itself.
    private void set(ServerLevel level, boolean raining) {
        if (raining) {
            level.setWeatherParameters(0, HELD_CLEAR, true, level.isThundering());
        } else {
            level.setWeatherParameters(HELD_CLEAR, 0, false, false);
        }
    }
}

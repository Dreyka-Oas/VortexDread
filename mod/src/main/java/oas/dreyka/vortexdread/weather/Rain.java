package oas.dreyka.vortexdread.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Whether it is raining on a place, asked from wherever the question comes up.
 *
 * <p>Both sides answer it and neither caller has to say which it is. A server deciding that a cauldron fills
 * and a client deciding that a raindrop falls read the same field, since the field itself travels rather than
 * a summary of it, so the two cannot disagree about where the water is. That is the part the mod this idea
 * came from could not have: its field was a texture and its weather was an offset it had to broadcast every
 * second.
 *
 * <p>The two sides register themselves rather than being reached into, because this class is loaded on a
 * dedicated server where no client class exists at all. A mixin in the common set that named the client sky
 * directly would fail to load there, and the failure would arrive as a missing class during world load.
 *
 * <p>A world with no simulated sky answers nothing and leaves every caller alone. That is what a client on a
 * server without the mod needs: there the weather really is the game's, and an empty map would stop the rain
 * the player can see falling.
 */
public final class Rain {

    private static volatile Supply serverSide = () -> RainMap.dry();
    private static volatile Supply clientSide = () -> RainMap.dry();

    /** What a side hands over once, at startup, so this class never names either of them. */
    public interface Supply {
        RainMap map();
    }

    private Rain() {
    }

    public static void onServer(Supply supply) {
        serverSide = supply;
    }

    public static void onClient(Supply supply) {
        clientSide = supply;
    }

    /**
     * Whether a simulated sky is answering rather than the game's own switch.
     *
     * <p>A sky that has arrived but is clear answers true and reports rain nowhere, which is the point: that
     * is a world where the mod decides the weather and has decided it is not raining. A sky that has not
     * arrived answers false and the game keeps its own behaviour whole.
     */
    public static boolean simulated(Level level) {
        return map(level).arrived();
    }

    /** Whether water is falling over a block, ignoring what the game thinks of the block itself. */
    public static boolean over(Level level, BlockPos pos) {
        return strength(level, pos) > 0.0f;
    }

    /** How hard, 0 to 1, for whoever draws it or counts drops. */
    public static float strength(Level level, BlockPos pos) {
        return map(level).strengthAt(pos.getX(), pos.getZ());
    }

    /**
     * The whole map, for a caller about to ask about a great many positions.
     *
     * <p>The side is resolved per call rather than stored, because a single player session is both: the
     * integrated server owns the simulation and the client owns the drawing, in one process, and a field
     * decided once at startup would answer for whichever loaded first.
     */
    public static RainMap map(Level level) {
        return level != null && level.isClientSide() ? clientSide.map() : serverSide.map();
    }
}

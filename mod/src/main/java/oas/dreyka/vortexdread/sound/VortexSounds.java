package oas.dreyka.vortexdread.sound;

import oas.dreyka.vortexdread.VortexDread;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The loops a storm is made of.
 *
 * <p>Split by frequency rather than by volume, because that is how distance actually works on this
 * sound. Air absorption climbs steeply with frequency, so a kilometre of it removes the rush and leaves
 * the low end almost untouched: the same funnel is a waterfall from underneath and a freight train from
 * the next county. One file turned down gives neither.
 *
 * <p>All three are registered with a fixed range of zero, meaning the game is told not to attenuate
 * them. The client instances do it themselves, on curves that have the crossfade and the reach in
 * them, and the bed is not attenuated at all because it is played at the ear.
 */
public final class VortexSounds {
    private VortexSounds() {
    }

    /** Broadband, twenty hertz to two kilohertz. What is left of the storm at close range. */
    public static final SoundEvent TORNADO_ROAR = register("tornado_roar");

    /** Below a hundred and twenty hertz. What survives a long crossing of air. */
    public static final SoundEvent TORNADO_RUMBLE = register("tornado_rumble");

    /**
     * The air feeding the storm, played at the ear rather than at anything in the world.
     *
     * <p>A supercell is loud long before it has a funnel, and what it is loud with is wind rather than
     * roar. Leaving that out gives a storm that arrives in silence and then starts howling the moment a
     * column reaches the ground, which is the wrong way round: the build is most of the experience.
     */
    public static final SoundEvent STORM_INFLOW = register("storm_inflow");

    private static SoundEvent register(String path) {
        Identifier id = VortexDread.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Touching the class is enough to run its initialisers; this makes that intent readable. */
    public static void register() {
        VortexDread.LOGGER.debug("[VortexDread] sounds registered");
    }
}

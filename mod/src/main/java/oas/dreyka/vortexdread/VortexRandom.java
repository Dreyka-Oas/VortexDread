package oas.dreyka.vortexdread;

import net.minecraft.util.RandomSource;

import java.util.random.RandomGenerator;

/**
 * The game's generator seen through the standard interface.
 *
 * <p>The physics layer is written against {@link RandomGenerator} so it can be unit tested with a
 * fixed seed and no game running. {@link RandomSource} carries the same numbers and declares none of
 * the same types, so this is the one place the two meet.
 */
public final class VortexRandom {
    private VortexRandom() {
    }

    public static RandomGenerator of(RandomSource source) {
        return source::nextLong;
    }
}

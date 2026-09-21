package oas.dreyka.vortexdread.save;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;
import oas.dreyka.vortexdread.atmosphere.SkyRunner;

/**
 * Where the sky lives on disk: {@code <world>/data/vortexdread_sky.dat}, written by the game's own save.
 *
 * <p>Nothing here schedules anything. {@link #setDirty()} is the whole mechanism: the game collects dirty
 * data on autosave and on shutdown, encodes it on the calling thread and hands the write to a future of its
 * own. Measured on the shipped grid the encode is 2.5 milliseconds, a twentieth of a tick, and the gzip and
 * the disk are already off the thread, so there is nothing left to move and no second save path to keep
 * honest.
 *
 * <p>Overworld only, like the sky it stores. The simulation models one patch of troposphere with a seed and a
 * clock, and which dimension a player stands in does not change the weather over the overworld.
 *
 * <p>The state is optional because a fresh world has none and a world saved in the first seconds of a session
 * may still have none. An absent state reads as a sky that starts from its profile, which is what it is.
 */
public final class SkySavedData extends SavedData {

    private static final String NAME = "vortexdread_sky";

    public static final Codec<SkySavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            // Optional rather than required so a file written before a sky had ever stepped loads as an
            // empty sky instead of failing the parse and taking the world's data storage with it.
            SkyState.CODEC.optionalFieldOf("sky").forGetter(d -> Optional.ofNullable(d.state))
    ).apply(i, held -> new SkySavedData(held.orElse(null))));

    public static final SavedDataType<SkySavedData> TYPE =
            new SavedDataType<>(NAME, SkySavedData::new, CODEC, DataFixTypes.LEVEL);

    private SkyState state;

    public SkySavedData() {
        this(null);
    }

    public SkySavedData(SkyState state) {
        this.state = state;
    }

    /** What the last save held, or null on a world that has never stored a sky. */
    public SkyState state() {
        return state;
    }

    /** The sky read off the overworld's storage, or null when the world has none yet. */
    public static SkyState read(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE).state();
    }

    /**
     * Takes the running sky and marks the record for the game's next write.
     *
     * <p>Called from the server thread, on the event that fires at the head of the world save, so the state
     * collected here is the one the game encodes moments later. A runner with nothing to hand over leaves the
     * record untouched: writing an empty sky over a good one would turn a solver still opening, or one whose
     * step threw, into a lost afternoon.
     */
    public static void store(MinecraftServer server, SkyRunner runner, AtmosphereSettings settings) {
        SkyState captured = SkyPersistence.capture(runner, settings);
        if (captured == null) {
            return;
        }
        SkySavedData record = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        record.state = captured;
        record.setDirty();
    }
}

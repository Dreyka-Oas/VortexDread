package oas.dreyka.vortexdread.save;

import java.nio.ByteBuffer;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.atmosphere.AtmosphereGrid;
import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;

/**
 * A patch of sky frozen for the world file: the six fields, the step it was on, and the shape it has to be
 * read back into.
 *
 * <p>Whole floats, which is the one decision behind the size of this file. The alternative was measured and
 * refused: a quantised field is a tenth of the bytes and a different sky, because the solver amplifies a
 * one-in-ten-million perturbation into a visibly different cloud inside a hundred steps. Whole floats are
 * what keep the property the mod will not give up, that a sky saved on a machine with a card and reopened on
 * one without is the same sky, step for step.
 *
 * <p>The fields ride as one list rather than six named blocks so the walk over them is a loop in both
 * directions. Their order is the order of the constructor arguments and nothing reorders it; the count is
 * checked on the way in, which is the only thing a reader can check about a list of blobs.
 */
public record SkyState(int version, long step, SkyShape shape, List<ByteBuffer> fields) {

    /** Bumped when the meaning of what is written changes, which is what lets an old file be refused. */
    public static final int VERSION = 1;

    private static final int FIELD_COUNT = 6;

    public static final Codec<SkyState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("version").forGetter(SkyState::version),
            Codec.LONG.fieldOf("step").forGetter(SkyState::step),
            SkyShape.CODEC.fieldOf("shape").forGetter(SkyState::shape),
            Codec.BYTE_BUFFER.listOf().fieldOf("fields").forGetter(SkyState::fields)
    ).apply(i, SkyState::new));

    public static SkyState of(AtmosphereGrid grid, long step, AtmosphereSettings settings) {
        List<ByteBuffer> fields = List.of(
                ByteBuffer.wrap(FieldBytes.write(grid.velocityX)),
                ByteBuffer.wrap(FieldBytes.write(grid.velocityY)),
                ByteBuffer.wrap(FieldBytes.write(grid.velocityZ)),
                ByteBuffer.wrap(FieldBytes.write(grid.potentialTemperature)),
                ByteBuffer.wrap(FieldBytes.write(grid.vapour)),
                ByteBuffer.wrap(FieldBytes.write(grid.cloudWater)));
        return new SkyState(VERSION, step, SkyShape.of(settings), fields);
    }

    /**
     * Puts this state back into a grid, or refuses it and leaves the grid exactly as it was.
     *
     * <p>Answers rather than throws, because every way this can fail is a way an operator caused on purpose
     * and none of them should stop a world from opening. A grid resized between sessions, a warmer surface
     * set in the options, a file from a version that wrote something else: all of them mean the sky starts
     * from the profile again, and the caller says so in the log.
     *
     * @return whether the grid was written, so a refusal is visible to the caller rather than silent
     */
    public boolean restoreInto(AtmosphereGrid grid, AtmosphereSettings settings) {
        String refusal = whyNot(grid, settings);
        if (refusal != null) {
            VortexDread.LOGGER.warn("[VortexDread] the saved sky was not restored: {}", refusal);
            return false;
        }
        // Read after every check has passed, so a refused state cannot leave a grid half written.
        List<float[]> targets = List.of(grid.velocityX, grid.velocityY, grid.velocityZ,
                grid.potentialTemperature, grid.vapour, grid.cloudWater);
        for (int field = 0; field < FIELD_COUNT; field++) {
            FieldBytes.read(bytesOf(fields.get(field)), targets.get(field));
        }
        return true;
    }

    // One string rather than a boolean, because a sky that quietly started over is the failure this whole
    // file exists to avoid being invisible.
    private String whyNot(AtmosphereGrid grid, AtmosphereSettings settings) {
        if (version != VERSION) {
            return "it was written by version " + version + " and this reads version " + VERSION;
        }
        if (fields.size() != FIELD_COUNT) {
            return "it holds " + fields.size() + " fields rather than " + FIELD_COUNT;
        }
        SkyShape wanted = SkyShape.of(settings);
        if (!shape.equals(wanted)) {
            return "it was saved for " + shape + " and the options now ask for " + wanted;
        }
        if (grid.cellCount() != wanted.cellCount()) {
            return "the grid holds " + grid.cellCount() + " cells and the options describe "
                    + wanted.cellCount();
        }
        int expected = FieldBytes.PER_CELL * grid.cellCount();
        for (ByteBuffer field : fields) {
            if (field.remaining() != expected) {
                return "a field of " + field.remaining() + " bytes does not fill " + grid.cellCount()
                        + " cells";
            }
        }
        return null;
    }

    // The codec hands back a buffer whose backing array may be longer than the block or absent entirely,
    // depending on how the tag was built, so the bytes are taken through the buffer's own position.
    private static byte[] bytesOf(ByteBuffer field) {
        byte[] block = new byte[field.remaining()];
        field.duplicate().get(block);
        return block;
    }
}

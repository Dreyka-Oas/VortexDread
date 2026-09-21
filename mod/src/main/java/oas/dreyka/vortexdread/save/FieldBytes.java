package oas.dreyka.vortexdread.save;

import java.nio.ByteBuffer;

/**
 * One field of the grid as the bytes a world file holds, and back again.
 *
 * <p>Every bit of every float, with nothing scaled and nothing dropped. That is the one requirement, and it
 * came out of a measurement rather than a preference: this solver is chaotic, and a field restored a
 * rounding error away from the one written diverges from it at a rate that doubles about every ten steps. An
 * eight bit encoding of these same fields read back within 0.4 per cent of the peak and sat 111 per cent
 * away a hundred steps later, which is a different sky inside a minute of play. Sixteen bits bought almost
 * nothing, because what grows is the perturbation and not the encoding error.
 *
 * <p>Bytes rather than an int array, even though NBT has both. The saved file is gzipped by the game on the
 * way out, and a byte array is what deflate can model: the exponents of a smooth field repeat where the
 * mantissas do not, and splitting them across byte positions is what lets the stream find that. Measured on
 * the shipped grid, ten megabytes of fields leave as seven.
 */
public final class FieldBytes {

    /** Bytes one cell takes, which is what makes a block's length a check on the cell count. */
    public static final int PER_CELL = Float.BYTES;

    private FieldBytes() {
    }

    public static byte[] write(float[] field) {
        ByteBuffer bytes = ByteBuffer.allocate(PER_CELL * field.length);
        bytes.asFloatBuffer().put(field);
        return bytes.array();
    }

    /**
     * @throws IllegalArgumentException when the block does not hold exactly one float per cell, which is
     *     what a world file edited by hand or written by an older grid looks like
     */
    public static void read(byte[] block, float[] into) {
        if (block.length != PER_CELL * into.length) {
            throw new IllegalArgumentException("a block of " + block.length + " bytes does not fill "
                    + into.length + " cells");
        }
        ByteBuffer.wrap(block).asFloatBuffer().get(into);
    }
}

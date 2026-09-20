package oas.dreyka.vortexdread.net;

/**
 * The cloud field small enough to put in one packet.
 *
 * <p>Sending the cells was ruled out on a guess and the guess was wrong by three orders of magnitude. A
 * field of 442368 cells is 1.7 MB of floats, which sounds like the end of the idea, except that a sky is
 * almost entirely empty: a fair weather afternoon has cloud in one cell in six hundred and the rest hold
 * an exact zero, and runs of zeros are what deflate is best at. Measured on the shipped grid, a fair
 * weather field packs to three kilobytes and a forced overcast one, nine per cent of cells wet, to
 * eighty-five. Against one step every ten seconds that is a few hundred bytes a second per player, so the
 * server simulates and every client receives, which is both less code than a replay and a sky that is
 * identical by construction rather than by arithmetic.
 *
 * <p>Two steps get it there. Each value becomes a sixteen bit fraction of the field's own peak, which
 * halves the size because three of the four bytes of a float are mantissa noise that deflate cannot model.
 * Then the whole block is deflated. Sixteen bits rather than eight: one level of eight bits on a dense
 * cumulus is about a fifth of an optical depth across a cell, which draws as banding on the soft edge of a
 * cloud, and the two extra bytes per hundred cost nine per cent of the packet. Measured, the worst cell of
 * a real sky comes back within 0.0008 per cent of the peak.
 *
 * <p>The scale travels inside the block rather than beside it, so packing and unpacking are symmetric and
 * no caller can pair a field with the wrong one.
 */
public final class CloudField {

    /**
     * Ceiling on a packed field, bytes.
     *
     * <p>A custom payload is refused past 1048576 bytes, and this leaves a quarter of that for the header
     * and the framing underneath. The widest grid the options allow, forced overcast, measured at
     * eighty-five kilobytes, so a block that reaches this is a packet built to exhaust the reader rather
     * than a sky, which is why the decoder is the side that enforces it.
     */
    public static final int MAX_PACKED_BYTES = 768 * 1024;

    private static final int LEVELS = 65535;

    private static final int HEADER_BYTES = 4;

    private CloudField() {
    }

    public static byte[] pack(float[] field) {
        float peak = 0.0f;
        for (float value : field) {
            peak = Math.max(peak, value);
        }
        byte[] plain = new byte[HEADER_BYTES + 2 * field.length];
        writeInt(plain, Float.floatToRawIntBits(peak));
        // A clear sky has no peak to divide by, and every level in it is zero anyway.
        float toLevels = peak <= 0.0f ? 0.0f : LEVELS / peak;
        for (int cell = 0; cell < field.length; cell++) {
            int level = Math.min(LEVELS, Math.max(0, Math.round(field[cell] * toLevels)));
            int at = HEADER_BYTES + 2 * cell;
            plain[at] = (byte) (level >>> 8);
            plain[at + 1] = (byte) level;
        }
        return Zlib.deflate(plain);
    }

    /**
     * @param cells how many the caller expects, taken from the sizes in the payload header rather than from
     *     the block, so a block that does not match them is refused instead of resized
     */
    public static float[] unpack(byte[] packed, int cells) {
        if (packed.length > MAX_PACKED_BYTES) {
            throw new IllegalArgumentException("a packed cloud field of " + packed.length
                    + " bytes is past the ceiling of " + MAX_PACKED_BYTES);
        }
        byte[] plain = Zlib.inflate(packed, HEADER_BYTES + 2 * cells);
        float peak = Float.intBitsToFloat(readInt(plain));
        if (!Float.isFinite(peak) || peak < 0.0f) {
            throw new IllegalArgumentException("a cloud field cannot peak at " + peak);
        }
        float fromLevels = peak / LEVELS;
        float[] field = new float[cells];
        for (int cell = 0; cell < cells; cell++) {
            int at = HEADER_BYTES + 2 * cell;
            int level = ((plain[at] & 0xff) << 8) | (plain[at + 1] & 0xff);
            field[cell] = level * fromLevels;
        }
        return field;
    }

    private static void writeInt(byte[] into, int value) {
        into[0] = (byte) (value >>> 24);
        into[1] = (byte) (value >>> 16);
        into[2] = (byte) (value >>> 8);
        into[3] = (byte) value;
    }

    private static int readInt(byte[] from) {
        return ((from[0] & 0xff) << 24) | ((from[1] & 0xff) << 16)
                | ((from[2] & 0xff) << 8) | (from[3] & 0xff);
    }
}

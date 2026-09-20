package oas.dreyka.vortexdread.net;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Deflate and inflate a block whose plain length is known in advance.
 *
 * <p>Knowing the length is the whole reason this is not a stream. A block arrives over the network before
 * anything about it has been checked, and an inflate that grows its own buffer is an inflate a packet can
 * ask for gigabytes from. Here the caller states how many bytes it expects, and a block that does not
 * produce exactly that many is refused.
 */
public final class Zlib {

    private static final int CHUNK_BYTES = 64 * 1024;

    private Zlib() {
    }

    public static byte[] deflate(byte[] plain) {
        // Best compression rather than the default: this runs on a worker that has just spent milliseconds
        // on a step, and the block is a few hundred kilobytes at worst.
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(plain);
            deflater.finish();
            // Collected in chunks rather than into a buffer the size of the input, because a block can come
            // out larger than it went in: zlib pays a header and a stored block for anything it cannot
            // model, which is what a small block of noise is.
            ByteArrayOutputStream packed = new ByteArrayOutputStream(plain.length / 4 + CHUNK_BYTES);
            byte[] chunk = new byte[CHUNK_BYTES];
            while (!deflater.finished()) {
                packed.write(chunk, 0, deflater.deflate(chunk));
            }
            return packed.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflate(byte[] packed, int expected) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            // One byte of slack, which is what makes the length an equality rather than a floor: a block
            // that inflates to more than promised fills it and fails, where a buffer of exactly the expected
            // length would come back full and look correct.
            byte[] room = new byte[expected + 1];
            int written = 0;
            while (written < room.length) {
                int round = inflater.inflate(room, written, room.length - written);
                if (round == 0) {
                    break;
                }
                written += round;
            }
            if (written != expected || !inflater.finished()) {
                throw new IllegalArgumentException("a block of " + expected + " bytes inflated to "
                        + written);
            }
            byte[] plain = new byte[expected];
            System.arraycopy(room, 0, plain, 0, expected);
            return plain;
        } catch (DataFormatException malformed) {
            throw new IllegalArgumentException("a block did not inflate", malformed);
        } finally {
            inflater.end();
        }
    }
}

package oas.dreyka.vortexdread.atmosphere;

/**
 * The most water any one cell has held since the world opened, kilogram per kilogram.
 *
 * <p>A high-water mark and not a reading, because the question it answers is whether this sky ever condensed
 * at all, and the current field cannot answer it: a sky that built cumulus at noon and cleared by evening did
 * condense, and a number taken at shutdown would deny it. Zero means a hundred steps of nothing, which is a
 * world closed before its first drop or a simulation that is not running, and one of those has been read as
 * the other before.
 *
 * <p>Written by the worker after each step and read by the server thread at shutdown, which is what the
 * volatile is for. A pass over a field already hot in cache, so it costs less than the snapshot copy taken
 * beside it.
 */
final class WaterMark {

    private volatile float wettest;

    float wettest() {
        return wettest;
    }

    void note(float[] cloudWater) {
        float mark = wettest;
        for (float water : cloudWater) {
            if (water > mark) {
                mark = water;
            }
        }
        wettest = mark;
    }
}

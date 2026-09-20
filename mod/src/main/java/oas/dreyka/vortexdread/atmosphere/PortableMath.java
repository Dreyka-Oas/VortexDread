package oas.dreyka.vortexdread.atmosphere;

/**
 * Arithmetic that gives the same bits on the processor and on the graphics card.
 *
 * <p>One function, and it exists because of a gap in what the two platforms promise. Java specifies
 * {@code StrictMath.exp} to the last place, so two processors agree. OpenCL specifies {@code exp} only to
 * within four units in the last place, and each vendor spends that budget differently, so the same kernel
 * on an AMD part and on an NVIDIA one returns different bits for the same input. Neither promise is weak;
 * they are simply incompatible, and there is no compiler flag that reconciles them.
 *
 * <p>That matters here because the saturation mixing ratio calls an exponential for every cell of every
 * step, and it is the function that decides where a cloud forms. A card and a processor disagreeing in the
 * last place would be invisible for a few steps and then not: the fields feed back into each other, the
 * difference doubles every time round the loop, and within a couple of simulated minutes the two skies
 * have different clouds in different places. The same divergence separates two players replaying one seed.
 *
 * <p>So the exponential is written out rather than called, with a fixed number of terms evaluated in a
 * fixed order. Every operation below is a single IEEE 754 multiply or add on a float, which both platforms
 * round identically, and the only two library calls are floor and a power-of-two scaling, both of which are
 * exact. The card is built without {@code -cl-mad-enable} and with contraction switched off for the same
 * reason: a fused multiply-add rounds once where the source asks for twice, which is more accurate and is
 * the wrong answer.
 */
public final class PortableMath {

    /** 1 / ln 2, for finding which power of two the answer sits near. */
    private static final float INVERSE_LN2 = 1.4426950408889634f;

    /**
     * ln 2, split so that the reduction keeps its accuracy.
     *
     * <p>The high part has its low bits cleared, so multiplying it by a small integer is exact and the
     * subtraction that follows loses nothing. The low part carries the rest of ln 2 and is subtracted
     * afterwards, on a value already small enough that the product cannot cancel against it.
     */
    private static final float LN2_HIGH = 0.693359375f;
    private static final float LN2_LOW = -2.12194440e-4f;

    private PortableMath() {
    }

    /**
     * e to the power of x, to within half a unit in the last place over the range this simulation uses.
     *
     * <p>The argument is split into a whole number of powers of two and a remainder no larger than a third,
     * the remainder goes through the Taylor series of the exponential, and the powers of two are put back by
     * adjusting the exponent. Eight terms are enough because the remainder is small: the first term left out
     * contributes about five parts in a thousand million, well under the rounding of the result.
     *
     * <p>The saturation fit feeds this arguments between roughly minus six and plus four, and nothing here
     * guards against much larger ones. An argument near ninety overflows to infinity, which is what the
     * library does too.
     */
    public static float exp(float x) {
        int power = (int) Math.floor(x * INVERSE_LN2 + 0.5f);
        float remainder = x - power * LN2_HIGH - power * LN2_LOW;

        float series = 1.0f / 5040.0f;
        series = 1.0f / 720.0f + remainder * series;
        series = 1.0f / 120.0f + remainder * series;
        series = 1.0f / 24.0f + remainder * series;
        series = 1.0f / 6.0f + remainder * series;
        series = 0.5f + remainder * series;
        series = 1.0f + remainder * series;
        series = 1.0f + remainder * series;

        return Math.scalb(series, power);
    }
}

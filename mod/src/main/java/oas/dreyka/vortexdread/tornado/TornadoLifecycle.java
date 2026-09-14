package oas.dreyka.vortexdread.tornado;

import oas.dreyka.vortexdread.wind.EfScale;

/**
 * One tornado's life, from the funnel leaving the cloud base to the rope going out, with no game types
 * anywhere in it.
 *
 * <p>The shape of the curve is what sells the thing. A tornado that appears at full width, holds it and
 * vanishes reads as a spawned object. A real one hangs first, touches down narrow, widens as it takes
 * hold, runs at strength for most of its life, then draws itself out into a leaning rope that is still
 * dangerous while it is clearly dying. Every one of those is a number here.
 */
public final class TornadoLifecycle {

    /** Where in the life the wind peaks. Slightly before the middle, as measured tracks do. */
    private static final double PEAK_AT = 0.45;

    /** How much of the peak is lost between the peak and the start of the rope out. */
    private static final double LATE_DECAY = 0.45;

    /** Width at contact, as a fraction of the mature width. */
    private static final double TOUCHDOWN_WIDTH = 0.25;

    /** Width at the very end of the rope, as a fraction of the mature width. */
    private static final double ROPE_WIDTH = 0.10;

    /** How long after contact the tornado still counts as touching down. */
    private static final double TOUCHDOWN_WINDOW = 0.08;

    /** Nothing narrower than this, in blocks: a core the width of a fence post has nothing to draw. */
    private static final double MIN_CORE_RADIUS = 0.8;

    /** How far over the rope leans at the end, as a fraction of the funnel height. */
    private static final double MAX_TILT = 0.7;

    private final int lifespanTicks;
    private final float peakWind;
    private final FunnelShape shape;
    private final double matureRadius;

    private int age;

    public TornadoLifecycle(int lifespanTicks, float peakWind, FunnelShape shape) {
        if (lifespanTicks <= 0) {
            throw new IllegalArgumentException("a tornado needs a life to live");
        }
        this.lifespanTicks = lifespanTicks;
        this.peakWind = peakWind;
        this.shape = shape;
        this.matureRadius = shape.coreRadiusAtEf0()
                * Math.pow(peakWind / EfScale.EF0.minWind(), shape.radiusGrowthExponent())
                * shape.build().widthFactor();
    }

    /** One tick older. */
    public void advance() {
        if (age < lifespanTicks) {
            age++;
        }
    }

    /** Puts the clock back where a saved tornado left it. */
    public void restoreAge(int ticks) {
        age = Math.max(0, Math.min(lifespanTicks, ticks));
    }

    public int age() {
        return age;
    }

    public int lifespanTicks() {
        return lifespanTicks;
    }

    /** The strongest gust this tornado will ever reach, drawn once at birth. */
    public float peakWind() {
        return peakWind;
    }

    /** Mature core radius, in blocks, before the narrowing at either end of the life. */
    public double matureRadius() {
        return matureRadius;
    }

    /** How this one was built, which is what decides its width and how far up it reaches. */
    public FunnelBuild build() {
        return shape.build();
    }

    /** How far through its life it is, 0 at birth and 1 when it is over. */
    public double lifeFraction() {
        return (double) age / lifespanTicks;
    }

    public boolean finished() {
        return age >= lifespanTicks;
    }

    public TornadoStage stage() {
        double t = lifeFraction();
        if (t >= 1.0) {
            return TornadoStage.GONE;
        }
        if (t < shape.formingFraction()) {
            return TornadoStage.FORMING;
        }
        if (t < shape.formingFraction() + TOUCHDOWN_WINDOW) {
            return TornadoStage.TOUCHDOWN;
        }
        if (t < 1.0 - shape.ropeOutFraction()) {
            return TornadoStage.MATURE;
        }
        return TornadoStage.ROPING;
    }

    /** Whether the funnel has reached the ground, which is what lets it damage anything. */
    public boolean onGround() {
        TornadoStage stage = stage();
        return stage != TornadoStage.FORMING && stage != TornadoStage.GONE;
    }

    /**
     * How far the funnel reaches down from the cloud base, 0 while it is still a nub under the wall
     * cloud and 1 once it is standing on the ground.
     */
    public double descent() {
        double t = lifeFraction();
        if (t >= shape.formingFraction()) {
            return 1.0;
        }
        return smoothstep(t / shape.formingFraction());
    }

    /** Current gust at the core wall, in metres per second. */
    public float wind() {
        double t = lifeFraction();
        if (t >= 1.0) {
            return 0.0f;
        }
        double w;
        if (t < PEAK_AT) {
            w = smoothstep(t / PEAK_AT);
        } else {
            w = 1.0 - LATE_DECAY * smoothstep((t - PEAK_AT) / (1.0 - PEAK_AT));
        }
        double ropeStart = 1.0 - shape.ropeOutFraction();
        if (t > ropeStart) {
            // The square root is the point rather than a fudge. A roping tornado loses its width far
            // faster than it loses its wind, which is why a rope thin enough to see through still
            // takes a roof off. Fading both together would turn the rope into a dissolve.
            w *= Math.sqrt(smoothstep((1.0 - t) / shape.ropeOutFraction()));
        }
        return (float) (peakWind * w);
    }

    /** The rating a survey would give it right now, from the gust it is carrying. */
    public EfScale rating() {
        return EfScale.fromWind(wind());
    }

    /**
     * Current core radius, in blocks. Narrow at contact, full through maturity, and drawn out at the
     * end faster than the wind falls, which is what makes the rope a rope rather than a fading cone.
     */
    public double coreRadius() {
        double t = lifeFraction();
        double width;
        double contactEnd = shape.formingFraction() + TOUCHDOWN_WINDOW * 2.0;
        double ropeStart = 1.0 - shape.ropeOutFraction();
        if (t <= shape.formingFraction()) {
            width = TOUCHDOWN_WIDTH;
        } else if (t < contactEnd) {
            double k = (t - shape.formingFraction()) / (contactEnd - shape.formingFraction());
            width = TOUCHDOWN_WIDTH + (1.0 - TOUCHDOWN_WIDTH) * smoothstep(k);
        } else if (t < ropeStart) {
            width = 1.0;
        } else {
            double k = (t - ropeStart) / shape.ropeOutFraction();
            width = 1.0 - (1.0 - ROPE_WIDTH) * smoothstep(k);
        }
        return Math.max(MIN_CORE_RADIUS, matureRadius * width);
    }

    /**
     * How far the axis leans away from vertical, as a fraction of the funnel height. Zero until the
     * rope out, where a real funnel bends over and trails behind the storm that is leaving it.
     */
    public double tilt() {
        double t = lifeFraction();
        double ropeStart = 1.0 - shape.ropeOutFraction();
        if (t <= ropeStart) {
            return 0.0;
        }
        return MAX_TILT * smoothstep((t - ropeStart) / shape.ropeOutFraction());
    }

    private static double smoothstep(double k) {
        double c = k < 0.0 ? 0.0 : (k > 1.0 ? 1.0 : k);
        return c * c * (3.0 - 2.0 * c);
    }
}

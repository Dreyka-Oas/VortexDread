package oas.dreyka.vortexdread.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The range every numeric option is allowed to take.
 *
 * <p>A value out of range is clamped rather than refused. A server owner who writes a thousand into a
 * tick budget wants a bigger budget, and stopping the server over it helps nobody; what matters is
 * that nothing downstream ever sees a number it cannot survive.
 */
public final class ConfigBounds {
    private ConfigBounds() {
    }

    /** Inclusive range of one option. */
    public record Range(double min, double max) {
        public double clamp(double value) {
            return value < min ? min : (value > max ? max : value);
        }
    }

    private static final Map<String, Range> RANGES = build();

    private static Map<String, Range> build() {
        Map<String, Range> m = new HashMap<>();

        m.put("tornadoChancePerMinute", new Range(0.0, 1.0));
        m.put("cooldownTicks", new Range(0, 1_728_000));
        m.put("maxConcurrent", new Range(1, 16));
        m.put("ratingBias", new Range(-5.0, 5.0));
        m.put("warningRadius", new Range(32, 4096));
        m.put("cloudFlashesPerMinute", new Range(0, 600));
        m.put("funnelFlashesPerMinute", new Range(0, 600));

        m.put("coreRadiusAtEf0", new Range(1.0, 64.0));
        m.put("radiusGrowthExponent", new Range(0.5, 4.0));
        m.put("lifespanMinTicks", new Range(200, 1_728_000));
        m.put("lifespanMaxTicks", new Range(200, 1_728_000));
        m.put("travelSpeed", new Range(0.0, 40.0));
        m.put("trackWander", new Range(0.0, 30.0));
        m.put("turbulence", new Range(0.0, 2.0));
        m.put("cloudBaseAltitude", new Range(24.0, 512.0));
        m.put("formingFraction", new Range(0.0, 0.5));
        m.put("ropeOutFraction", new Range(0.0, 0.5));

        m.put("blockBudgetPerTick", new Range(0, 4096));
        m.put("resistanceScale", new Range(0.01, 100.0));
        m.put("maxDebris", new Range(0, 4000));
        m.put("damageScale", new Range(0.0, 100.0));
        m.put("liftThreshold", new Range(0.0, 200.0));

        m.put("gpuDeviceIndex", new Range(-1, 63));
        m.put("gpuWorkgroupSize", new Range(0, 1024));
        m.put("advectionThreads", new Range(0, 64));
        m.put("advectionBatch", new Range(64, 1_048_576));

        m.put("distantStormReach", new Range(0.0, 2048.0));

        m.put("funnelSteps", new Range(8, 256));
        m.put("funnelLightSteps", new Range(0, 32));
        m.put("funnelDetail", new Range(0.2, 32.0));
        m.put("funnelRenderDistance", new Range(64.0, 8192.0));
        m.put("skyGreen", new Range(0.0, 1.0));

        return Collections.unmodifiableMap(m);
    }

    /** The range of an option, or null when it carries none, which is every boolean. */
    public static Range of(String field) {
        return RANGES.get(field);
    }

    /** Every option name that carries a range. */
    public static java.util.Set<String> named() {
        return RANGES.keySet();
    }
}

package oas.dreyka.vortexdread.tornado;

import oas.dreyka.vortexdread.config.domain.TornadoConfig;

/**
 * The four numbers that decide what a funnel looks like over its life, pulled out of the config so the
 * lifecycle can be reasoned about, and tested, without a game around it.
 *
 * @param formingFraction      share of the life spent hanging out of the cloud before contact
 * @param ropeOutFraction      share of the life spent thinning into a rope at the end
 * @param coreRadiusAtEf0      core radius of the weakest tornado, in blocks
 * @param radiusGrowthExponent how fast the core widens as the wind rises
 */
public record FunnelShape(
        double formingFraction,
        double ropeOutFraction,
        double coreRadiusAtEf0,
        double radiusGrowthExponent) {

    public FunnelShape {
        if (formingFraction < 0.0 || ropeOutFraction < 0.0 || formingFraction + ropeOutFraction >= 1.0) {
            throw new IllegalArgumentException("forming and roping must leave a life in between");
        }
        if (coreRadiusAtEf0 <= 0.0) {
            throw new IllegalArgumentException("core radius must be positive");
        }
    }

    /** The shape the server is currently configured for. */
    public static FunnelShape fromConfig() {
        return new FunnelShape(
                TornadoConfig.formingFraction,
                TornadoConfig.ropeOutFraction,
                TornadoConfig.coreRadiusAtEf0,
                TornadoConfig.radiusGrowthExponent);
    }
}

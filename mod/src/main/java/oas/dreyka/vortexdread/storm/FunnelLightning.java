package oas.dreyka.vortexdread.storm;

import oas.dreyka.vortexdread.config.domain.StormConfig;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.wind.VortexParameters;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

/**
 * The lightning that goes off inside the funnel.
 *
 * <p>It is the thing chasers describe first and the thing renders leave out. A violent tornado at
 * night is not a black cone: it is lit from within, in bursts, and for a tenth of a second you can see
 * the whole inside of the wall, the debris in it and how far up the column goes. That is why the bolt
 * is spawned at a real position inside the volume rather than being a screen flash. The shader reads
 * the bolt's world position, so where it goes off decides which side of the funnel lights up.
 *
 * <p>Visual only. A funnel that also sets the wreckage on fire is a different mod.
 */
public final class FunnelLightning {
    private FunnelLightning() {
    }

    /** How fast the glow left behind by a bolt fades, per tick. */
    private static final float FLASH_DECAY = 0.12f;

    /** Below this share of its own peak a funnel has no charge separation worth the name. */
    private static final double ACTIVITY_FLOOR = 0.35;

    /** Where in the column bolts happen: near the wall cloud, not near the ground. */
    private static final double MIN_HEIGHT_FRACTION = 0.25;
    private static final double MAX_HEIGHT_FRACTION = 0.95;

    public static void tick(ServerLevel level, TornadoEntity tornado) {
        float flash = tornado.flash();
        if (flash > 0.0f) {
            tornado.setFlash(Math.max(0.0f, flash - FLASH_DECAY));
        }
        if (!StormConfig.extraLightning || StormConfig.funnelFlashesPerMinute <= 0.0) {
            return;
        }

        double intensity = tornado.wind() / Math.max(1.0f, tornado.peakWind());
        if (intensity < ACTIVITY_FLOOR) {
            return;
        }
        double chance = StormConfig.funnelFlashesPerMinute / (60.0 * 20.0) * intensity;
        if (level.random.nextDouble() >= chance) {
            return;
        }
        strike(level, tornado);
    }

    /** Puts one bolt somewhere on the funnel wall and lights the volume from there. */
    private static void strike(ServerLevel level, TornadoEntity tornado) {
        VortexParameters parameters = tornado.parameters();
        double h = MIN_HEIGHT_FRACTION
                + level.random.nextDouble() * (MAX_HEIGHT_FRACTION - MIN_HEIGHT_FRACTION);
        double y = parameters.groundY() + h * parameters.funnelHeight();
        double radius = parameters.funnelRadiusAt(y);
        double angle = level.random.nextDouble() * Math.PI * 2.0;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }
        bolt.setVisualOnly(true);
        bolt.snapTo(parameters.centerX() + radius * Math.cos(angle), y,
                parameters.centerZ() + radius * Math.sin(angle));
        level.addFreshEntity(bolt);
        tornado.setFlash(1.0f);
    }
}

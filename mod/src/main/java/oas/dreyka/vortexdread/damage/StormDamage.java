package oas.dreyka.vortexdread.damage;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.config.domain.DamageConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** The two ways a storm kills: the wind itself, and what the wind is carrying. */
public final class StormDamage {
    private StormDamage() {
    }

    /** Wind speed under which the flow alone hurts nobody, in blocks per second. */
    private static final double WIND_HARM_FLOOR = 40.0;

    /** Hearts per second at twice the harm floor, before the server's own scale. */
    private static final double WIND_HARM_RATE = 3.0;

    /** Impact speed under which a piece of debris is a bruise, in blocks per second. */
    private static final double DEBRIS_FLOOR = 12.0;

    public static final ResourceKey<DamageType> WIND =
            ResourceKey.create(Registries.DAMAGE_TYPE, VortexDread.id("wind"));

    public static final ResourceKey<DamageType> DEBRIS =
            ResourceKey.create(Registries.DAMAGE_TYPE, VortexDread.id("debris"));

    /** One tick of being inside the flow. */
    public static void byWind(ServerLevel level, LivingEntity victim, double windSpeed) {
        if (!DamageConfig.damageEntities || windSpeed <= WIND_HARM_FLOOR) {
            return;
        }
        double over = (windSpeed - WIND_HARM_FLOOR) / WIND_HARM_FLOOR;
        float amount = (float) (over * WIND_HARM_RATE * DamageConfig.damageScale / 20.0);
        if (amount > 0.0f) {
            victim.hurtServer(level, source(level, WIND, null), amount);
        }
    }

    /** One piece arriving at speed. */
    public static void byDebris(ServerLevel level, Entity piece, LivingEntity victim, double speed) {
        if (!DamageConfig.damageEntities || speed <= DEBRIS_FLOOR) {
            return;
        }
        float amount = (float) ((speed - DEBRIS_FLOOR) * 0.25 * DamageConfig.damageScale);
        if (amount > 0.0f) {
            victim.hurtServer(level, source(level, DEBRIS, piece), amount);
        }
    }

    private static DamageSource source(ServerLevel level, ResourceKey<DamageType> key, Entity cause) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), cause);
    }
}

package oas.dreyka.vortexdread.entity;

import oas.dreyka.vortexdread.VortexDread;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The two entity types this mod adds, and nothing else. */
public final class VortexEntities {
    private VortexEntities() {
    }

    public static final ResourceKey<EntityType<?>> TORNADO_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, VortexDread.id("tornado"));

    public static final ResourceKey<EntityType<?>> DEBRIS_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, VortexDread.id("debris"));

    /**
     * The funnel. Its own box is small on purpose: the wind reaches much further than the entity does,
     * and every sweep asks the tornado for its influence box rather than reading this one. Tracked at
     * the full render distance and updated every tick, because a funnel that stutters is worse than no
     * funnel at all.
     */
    public static final EntityType<TornadoEntity> TORNADO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            TORNADO_KEY,
            EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                    .sized(4.0f, 8.0f)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune()
                    .build(TORNADO_KEY));

    /** One piece of the world in flight. */
    public static final EntityType<DebrisEntity> DEBRIS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            DEBRIS_KEY,
            EntityType.Builder.<DebrisEntity>of(DebrisEntity::new, MobCategory.MISC)
                    .sized(0.98f, 0.98f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSummon()
                    .fireImmune()
                    .build(DEBRIS_KEY));

    /**
     * Keeps the ground under a funnel loaded and ticking.
     *
     * <p>A tornado works on the world rather than on a player's screen, so it has to keep going where
     * nobody is watching: a storm that stops eating the moment the last player walks away leaves a
     * track that ends in a straight line. The timeout is short and the entity renews it every tick, so
     * the moment the funnel dies the ground it was holding goes back to normal.
     */
    public static final TicketType TORNADO_TICKET = Registry.register(
            BuiltInRegistries.TICKET_TYPE,
            VortexDread.id("tornado"),
            new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));

    /** Touching the class is enough to run its initialisers; this makes that intent readable. */
    public static void register() {
        VortexDread.LOGGER.debug("[VortexDread] entity types registered");
    }
}

package oas.dreyka.vortexdread.entity;

import oas.dreyka.vortexdread.VortexRandom;
import oas.dreyka.vortexdread.api.TornadoView;
import oas.dreyka.vortexdread.api.event.TornadoLifeCallback;
import oas.dreyka.vortexdread.config.domain.DamageConfig;
import oas.dreyka.vortexdread.config.domain.LookConfig;
import oas.dreyka.vortexdread.config.domain.TornadoConfig;
import oas.dreyka.vortexdread.damage.DestructionSweep;
import oas.dreyka.vortexdread.damage.EntityForces;
import oas.dreyka.vortexdread.storm.FunnelLightning;
import oas.dreyka.vortexdread.tornado.FunnelBuild;
import oas.dreyka.vortexdread.tornado.FunnelShape;
import oas.dreyka.vortexdread.tornado.RatingRoll;
import oas.dreyka.vortexdread.tornado.TornadoLifecycle;
import oas.dreyka.vortexdread.tornado.TornadoStage;
import oas.dreyka.vortexdread.tornado.TornadoTrack;
import oas.dreyka.vortexdread.wind.EfScale;
import oas.dreyka.vortexdread.wind.VortexParameters;

import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One tornado.
 *
 * <p>The entity owns the state and nothing else: the life curve is in {@link TornadoLifecycle}, the
 * path is in {@link TornadoTrack}, the wind is in the sampler, and what the wind does to the world is
 * in the two sweeps. What is left here is the game's own business, the ticking, the syncing and the
 * saving.
 *
 * <p>The client is sent the state rather than the recipe, because a client whose config file disagrees
 * with the server's would otherwise draw a different tornado from the one that is killing it.
 */
public class TornadoEntity extends Entity implements TornadoView {

    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_WIND =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PEAK_WIND =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CORE_RADIUS =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DESCENT =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TILT =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GROUND_Y =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TINT =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FLASH =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_FUNNEL_HEIGHT =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GROUND_LOAD =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    /** Grey with a hint of the dirt it has not picked up yet, before anything is ingested. */
    private static final int DEFAULT_TINT = 0x9A9490;

    /** Widest area a funnel keeps loaded, in chunks. Past this the cost outgrows what is destroyed. */
    private static final int MAX_ANCHOR_RADIUS = 5;

    /**
     * Narrowest area a funnel keeps loaded, in chunks.
     *
     * <p>A radius of one leaves the chunk under the funnel loaded but one level short of ticking its
     * entities, so a rope narrow enough to ask for only one chunk stops ageing the moment the last
     * player walks away and is still hanging out of the cloud a quarter of an hour later. Two is the
     * first radius that reaches the entity ticking level, and it costs a handful of chunks.
     */
    private static final int MIN_ANCHOR_RADIUS = 2;

    /** Blocks torn out in one tick that count as a full load of ground in the air. */
    private static final float FULL_GROUND_LOAD = 24.0f;

    /**
     * How fast the debris ring follows what the ground is giving it, per tick.
     *
     * <p>Slow on purpose, and slower falling than rising. Dust that is already up stays up for seconds
     * after the funnel has left the field it tore apart, which is why a tornado crossing from ploughed
     * earth onto a road keeps its ring for a while and does not blink.
     */
    private static final float LOAD_RISE = 0.08f;
    private static final float LOAD_FALL = 0.02f;

    private TornadoLifecycle lifecycle;
    private TornadoTrack track;
    private final DestructionSweep destruction = new DestructionSweep();
    private final TintAccumulator tint = new TintAccumulator(DEFAULT_TINT);

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.lifecycle = new TornadoLifecycle(TornadoConfig.lifespanMinTicks, EfScale.EF0.minWind(),
                FunnelShape.fromConfig());
        this.track = new TornadoTrack(0.0, 0.0, 0.0, TornadoConfig.travelSpeed, TornadoConfig.trackWander, 0L);
    }

    /** Starts a tornado of a drawn strength at a place on the map, built whichever way the dice say. */
    public static TornadoEntity spawn(ServerLevel level, double x, double z, float peakWind) {
        return spawn(level, x, z, peakWind, FunnelBuild.roll(VortexRandom.of(level.random)));
    }

    /** The same, with the build named. */
    public static TornadoEntity spawn(ServerLevel level, double x, double z, float peakWind,
                                      FunnelBuild build) {
        return spawn(level, x, z, peakWind, build, -1.0, -1);
    }

    /**
     * The full order, which is what the command hands in.
     *
     * <p>A negative speed or lifespan means the config decides that one, so a caller can name the two
     * it cares about without having to look the others up.
     */
    public static TornadoEntity spawn(ServerLevel level, double x, double z, float peakWind,
                                      FunnelBuild build, double travelSpeed, int lifespanTicks) {
        TornadoEntity tornado = VortexEntities.TORNADO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (tornado == null) {
            return null;
        }
        int lifespan = lifespanTicks > 0
                ? lifespanTicks
                : TornadoConfig.lifespanMinTicks + level.random.nextInt(
                        Math.max(1, TornadoConfig.lifespanMaxTicks - TornadoConfig.lifespanMinTicks + 1));
        double speed = travelSpeed >= 0.0 ? travelSpeed : TornadoConfig.travelSpeed;
        tornado.lifecycle = new TornadoLifecycle(lifespan, peakWind, FunnelShape.fromConfig(build));
        tornado.track = new TornadoTrack(x, z, level.random.nextDouble() * Math.PI * 2.0,
                speed, TornadoConfig.trackWander, level.random.nextLong());
        // The ground has to be there before the funnel asks how high it is, or the tornado reads the
        // bottom of the world and hangs in the sky.
        tornado.anchor(level, x, z);
        level.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        tornado.snapToTrack(level);
        level.addFreshEntity(tornado);
        TornadoLifeCallback.EVENT.invoker().onFormed(level, tornado);
        return tornado;
    }

    /** Draws a strength from the real distribution and starts one. */
    public static TornadoEntity spawnRandom(ServerLevel level, double x, double z, double bias) {
        return spawn(level, x, z, RatingRoll.drawPeakWind(VortexRandom.of(level.random), bias));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AGE, 0);
        builder.define(DATA_WIND, 0.0f);
        builder.define(DATA_PEAK_WIND, EfScale.EF0.minWind());
        builder.define(DATA_CORE_RADIUS, (float) TornadoConfig.coreRadiusAtEf0);
        builder.define(DATA_DESCENT, 0.0f);
        builder.define(DATA_TILT, 0.0f);
        builder.define(DATA_GROUND_Y, 64.0f);
        builder.define(DATA_TINT, DEFAULT_TINT);
        builder.define(DATA_FLASH, 0.0f);
        builder.define(DATA_FUNNEL_HEIGHT, (float) TornadoConfig.funnelHeightAbove(0.0));
        builder.define(DATA_GROUND_LOAD, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        lifecycle.advance();
        if (lifecycle.finished()) {
            TornadoLifeCallback.EVENT.invoker().onGone(server, this);
            discard();
            return;
        }
        announceTouchdown(server);
        track.advance(1.0 / 20.0);
        anchor(server, track.x(), track.z());
        snapToTrack(server);
        publish();

        if (lifecycle.onGround()) {
            VortexParameters parameters = parameters();
            carryGround(destruction.run(server, this, parameters, tint));
            EntityForces.apply(server, this, parameters);
        } else {
            carryGround(0);
        }
        FunnelLightning.tick(server, this);
    }

    /**
     * Says once, on the tick contact happens.
     *
     * <p>Tracked with a flag rather than read off the stage, because the stage is a function of age and
     * a listener asking "has it touched down yet" on every tick of a fifteen minute storm is the sort of
     * thing an addon writes when the mod gave it nothing better.
     */
    private void announceTouchdown(ServerLevel server) {
        if (touchedDown || !lifecycle.onGround()) {
            return;
        }
        touchedDown = true;
        TornadoLifeCallback.EVENT.invoker().onTouchdown(server, this);
    }

    /** Follows how much of the ground is currently in the air, from what the sweep actually took. */
    private void carryGround(int brokenThisTick) {
        float wanted = Math.min(1.0f, brokenThisTick / FULL_GROUND_LOAD);
        float held = entityData.get(DATA_GROUND_LOAD);
        float rate = wanted > held ? LOAD_RISE : LOAD_FALL;
        entityData.set(DATA_GROUND_LOAD, held + (wanted - held) * rate);
    }

    /** Holds the ground the funnel is standing on, so the storm works with nobody watching. */
    private void anchor(ServerLevel level, double x, double z) {
        int radius = (int) Math.ceil(coreRadius() * VortexParameters.INFLUENCE_FACTOR / 16.0);
        level.getChunkSource().addTicketWithRadius(VortexEntities.TORNADO_TICKET,
                new net.minecraft.world.level.ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4),
                Math.max(MIN_ANCHOR_RADIUS, Math.min(radius, MAX_ANCHOR_RADIUS)));
    }

    /** Moves the entity onto its track and reads the ground under it. */
    private void snapToTrack(ServerLevel level) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(track.x()), (int) Math.floor(track.z()));
        setPos(track.x(), ground, track.z());
        entityData.set(DATA_GROUND_Y, (float) ground);
    }

    /** Copies the state the client draws from into the synced set. */
    private void publish() {
        entityData.set(DATA_AGE, lifecycle.age());
        entityData.set(DATA_WIND, lifecycle.wind());
        entityData.set(DATA_PEAK_WIND, lifecycle.peakWind());
        entityData.set(DATA_CORE_RADIUS, (float) lifecycle.coreRadius());
        entityData.set(DATA_DESCENT, (float) lifecycle.descent());
        entityData.set(DATA_TILT, (float) lifecycle.tilt());
        entityData.set(DATA_TINT, tint.packed());
        // Sent rather than read from the client's own file: a player whose config disagrees with the
        // server's would otherwise draw a funnel that stops short of the cloud it is hanging from.
        entityData.set(DATA_FUNNEL_HEIGHT,
                (float) (TornadoConfig.funnelHeightAbove(groundY()) * lifecycle.build().heightFactor()));
    }

    /** Ground to wall cloud, in blocks. */
    @Override
    public float funnelHeight() {
        return entityData.get(DATA_FUNNEL_HEIGHT);
    }

    /** How this one was built, for the command's listing and for anything that wants to say so. */
    public FunnelBuild build() {
        return lifecycle.build();
    }

    /** The wind field of this tornado right now, valid on either side. */
    public VortexParameters parameters() {
        return new VortexParameters(
                getX(),
                getZ(),
                groundY(),
                groundY() + funnelHeight(),
                Math.max(0.1f, coreRadius()),
                wind(),
                track.velocityX(),
                track.velocityZ(),
                (float) TornadoConfig.turbulence);
    }

    /** The box the wind reaches into, used for both the entity sweep and the renderer. */
    @Override
    public AABB influenceBox() {
        double reach = coreRadius() * VortexParameters.INFLUENCE_FACTOR;
        double top = groundY() + funnelHeight();
        return new AABB(getX() - reach, groundY() - 8.0, getZ() - reach,
                getX() + reach, top, getZ() + reach);
    }

    @Override
    public int ageTicks() {
        return entityData.get(DATA_AGE);
    }

    /** Seconds since the funnel formed, which is what winds the turbulence round. */
    public double phase() {
        return ageTicks() / 20.0;
    }

    @Override
    public float wind() {
        return entityData.get(DATA_WIND);
    }

    public float peakWind() {
        return entityData.get(DATA_PEAK_WIND);
    }

    @Override
    public float coreRadius() {
        return entityData.get(DATA_CORE_RADIUS);
    }

    @Override
    public float descent() {
        return entityData.get(DATA_DESCENT);
    }

    public float tilt() {
        return entityData.get(DATA_TILT);
    }

    public double groundY() {
        return entityData.get(DATA_GROUND_Y);
    }

    /** Packed colour of everything the funnel has swallowed, which is what gives it its own look. */
    @Override
    public int tint() {
        return entityData.get(DATA_TINT);
    }

    /** How much of the ground this funnel has in the air, nothing at 0 and a full debris ring at 1. */
    @Override
    public float groundLoad() {
        return entityData.get(DATA_GROUND_LOAD);
    }

    /** How hard the inside of the funnel is lit by its own lightning this tick, 0 to 1. */
    public float flash() {
        return entityData.get(DATA_FLASH);
    }

    public void setFlash(float value) {
        entityData.set(DATA_FLASH, value);
    }

    @Override
    public EfScale rating() {
        return EfScale.fromWind(wind());
    }

    @Override
    public EfScale peakRating() {
        return EfScale.fromWind(peakWind());
    }

    @Override
    public Vec3 position() {
        return new Vec3(getX(), groundY(), getZ());
    }

    @Override
    public boolean alive() {
        return !isRemoved();
    }

    @Override
    public TornadoStage stage() {
        return lifecycle.stage();
    }

    public TornadoLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * How many pieces this funnel currently has in the air.
     *
     * <p>Counted rather than surveyed: a periodic sweep of the influence box misses everything the wind
     * has already thrown past its own edge, and between two sweeps a mature funnel can put a thousand
     * blocks into the sky.
     */
    private int debrisAloft;

    /** Whether contact has already been announced, so it is announced exactly once. */
    private boolean touchedDown;

    public boolean canCarryMore() {
        return debrisAloft < DamageConfig.maxDebris;
    }

    public void debrisLaunched() {
        debrisAloft++;
    }

    public void debrisGone() {
        if (debrisAloft > 0) {
            debrisAloft--;
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        float peak = input.getFloatOr("PeakWind", EfScale.EF0.minWind());
        int lifespan = Math.max(1, input.getIntOr("Lifespan", TornadoConfig.lifespanMinTicks));
        FunnelBuild build = FunnelBuild.byKey(input.getStringOr("Build", FunnelBuild.STOVEPIPE.key()));
        lifecycle = new TornadoLifecycle(lifespan, peak, FunnelShape.fromConfig(build));
        lifecycle.restoreAge(input.getIntOr("Age", 0));

        double heading = input.read("Heading", Codec.DOUBLE).orElse(0.0);
        double elapsed = input.read("Elapsed", Codec.DOUBLE).orElse(0.0);
        // Its own speed rather than the config's, since a tornado spawned with one named keeps it
        // across a reload; an unsaved one falls back on the config, which is what it was anyway.
        double speed = input.read("Speed", Codec.DOUBLE).orElse(TornadoConfig.travelSpeed);
        track = new TornadoTrack(getX(), getZ(), heading, speed,
                TornadoConfig.trackWander, input.read("TrackSeed", Codec.LONG).orElse(0L));
        track.restore(getX(), getZ(), heading, elapsed);
        tint.restore(input.getIntOr("Tint", DEFAULT_TINT), input.getIntOr("TintWeight", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("PeakWind", lifecycle.peakWind());
        output.putInt("Lifespan", lifecycle.lifespanTicks());
        output.putInt("Age", lifecycle.age());
        output.putString("Build", lifecycle.build().key());
        output.store("Heading", Codec.DOUBLE, track.heading());
        output.store("Elapsed", Codec.DOUBLE, track.elapsed());
        output.store("TrackSeed", Codec.LONG, track.seed());
        output.store("Speed", Codec.DOUBLE, track.speed());
        output.putInt("Tint", tint.packed());
        output.putInt("TintWeight", tint.weight());
    }

    /** Nothing hurts a tornado, which is the whole problem with them. */
    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSquared) {
        double reach = LookConfig.funnelRenderDistance;
        return distanceSquared < reach * reach;
    }
}

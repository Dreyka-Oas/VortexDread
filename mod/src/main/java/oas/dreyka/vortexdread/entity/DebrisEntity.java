package oas.dreyka.vortexdread.entity;

import oas.dreyka.vortexdread.config.domain.DamageConfig;
import oas.dreyka.vortexdread.damage.StormDamage;
import oas.dreyka.vortexdread.damage.WindPressure;
import oas.dreyka.vortexdread.debris.DebrisMotion;
import oas.dreyka.vortexdread.wind.VortexParameters;
import oas.dreyka.vortexdread.wind.WindVector;

import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of the world the wind has taken.
 *
 * <p>It carries the block it used to be, because the eye reads a flying oak plank differently from a
 * flying grey square, and it carries its terminal velocity, because that is what decides whether the
 * storm lifts it to the cloud base or rolls it along the ground. Nothing about its path is scripted:
 * drag against the wind at its own position and its own weight, and it goes where those put it.
 *
 * <p>What it does not do is rebuild anything. What the storm takes is gone; a piece that lands stops
 * existing.
 */
public class DebrisEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_STATE_ID =
            SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.INT);

    /** Longest a piece stays in the air before it is dropped, in ticks. */
    private static final int MAX_LIFE = 600;

    /** Below this speed an impact is a bump rather than an injury, in blocks per second. */
    private static final double HARMLESS_SPEED = 12.0;

    private BlockState state = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
    private double terminalVelocity = 24.0;
    private int ownerId = -1;
    private int life;

    /** Set only on a piece its tornado counted, so a reloaded world does not double count. */
    private boolean counted;

    /** Set by the swarm pass earlier in the same tick, and cleared once this tick has used it. */
    private boolean carried;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
    }

    /** Puts a block that has just been torn out into the air, carried by the tornado that took it. */
    public static DebrisEntity launch(ServerLevel level, TornadoEntity owner, double x, double y, double z,
                                      BlockState taken, double resistance) {
        DebrisEntity debris = VortexEntities.DEBRIS.create(level,
                net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (debris == null) {
            return null;
        }
        debris.state = taken;
        debris.terminalVelocity = WindPressure.terminalVelocity(resistance);
        debris.ownerId = owner.getId();
        debris.setPos(x, y, z);
        debris.entityData.set(DATA_STATE_ID, Block.getId(taken));
        level.addFreshEntity(debris);
        owner.debrisLaunched();
        debris.counted = true;
        return debris;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE_ID, Block.getId(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (++life > MAX_LIFE) {
            discard();
            return;
        }

        if (carried) {
            // The swarm advected this piece at the start of the tick, along with every other piece its
            // storm is carrying, which is what lets that work go to a graphics card.
            carried = false;
        } else {
            // Nothing is holding it up any more. It still has weight, so it comes down.
            // Twice by twenty: once to turn seconds into ticks, once to turn blocks per second into
            // blocks per tick.
            Vec3 before = getDeltaMovement();
            setDeltaMovement(before.x, before.y - DebrisMotion.GRAVITY / 20.0 / 20.0, before.z);
        }

        move(MoverType.SELF, getDeltaMovement());
        strike(server);

        if (horizontalCollision || verticalCollision || onGround()) {
            settle(server);
        }
    }

    /** Hurts anything it passes through, hard enough to matter and only when it is moving. */
    private void strike(ServerLevel server) {
        if (!DamageConfig.damageEntities) {
            return;
        }
        double speed = getDeltaMovement().length() * 20.0;
        if (speed < HARMLESS_SPEED) {
            return;
        }
        for (Entity hit : server.getEntities(this, getBoundingBox().inflate(0.2), e -> e instanceof LivingEntity)) {
            StormDamage.byDebris(server, this, (LivingEntity) hit, speed);
        }
    }

    /**
     * The end of the piece. It is destroyed rather than placed: a storm that puts the wreckage back
     * down as it passes has undone its own damage.
     */
    private void settle(ServerLevel server) {
        if (DamageConfig.debrisSettles && !state.isAir()) {
            net.minecraft.core.BlockPos pos = blockPosition();
            if (server.getBlockState(pos).canBeReplaced()) {
                server.setBlockAndUpdate(pos, state);
            }
        }
        discard();
    }

    /** The funnel carrying this piece, or null once that storm is over. */
    public TornadoEntity owner(ServerLevel server) {
        if (ownerId < 0) {
            return null;
        }
        return server.getEntity(ownerId) instanceof TornadoEntity tornado ? tornado : null;
    }

    /** Speed this piece falls at in still air, in blocks per second. What sorts a debris field by weight. */
    public double terminalVelocity() {
        return terminalVelocity;
    }

    /** The swarm pass handing back the velocity it worked out for this piece, in blocks per tick. */
    public void carriedBy(double x, double y, double z) {
        setDeltaMovement(x, y, z);
        carried = true;
    }

    /** The block this piece used to be, which is what the client draws. */
    public BlockState carried() {
        return level().isClientSide() ? Block.stateById(entityData.get(DATA_STATE_ID)) : state;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        state = input.read("BlockState", BlockState.CODEC)
                .orElse(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        terminalVelocity = input.read("Terminal", Codec.DOUBLE).orElse(24.0);
        ownerId = input.getIntOr("Owner", -1);
        life = input.getIntOr("Life", 0);
        entityData.set(DATA_STATE_ID, Block.getId(state));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("BlockState", BlockState.CODEC, state);
        output.store("Terminal", Codec.DOUBLE, terminalVelocity);
        output.putInt("Owner", ownerId);
        output.putInt("Life", life);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (counted && level() instanceof ServerLevel server) {
            counted = false;
            TornadoEntity owner = owner(server);
            if (owner != null) {
                owner.debrisGone();
            }
        }
        super.remove(reason);
    }

    /** A flying block is not something you can fight back against. */
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
}

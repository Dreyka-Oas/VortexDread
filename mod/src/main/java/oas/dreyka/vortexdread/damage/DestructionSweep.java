package oas.dreyka.vortexdread.damage;

import oas.dreyka.vortexdread.api.event.BlockTakenCallback;
import oas.dreyka.vortexdread.config.domain.DamageConfig;
import oas.dreyka.vortexdread.entity.DebrisEntity;
import oas.dreyka.vortexdread.entity.TintAccumulator;
import oas.dreyka.vortexdread.entity.TornadoEntity;
import oas.dreyka.vortexdread.wind.TurbulentVortex;
import oas.dreyka.vortexdread.wind.VortexParameters;
import oas.dreyka.vortexdread.wind.WindVector;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What the wind takes out of the ground, one tick at a time.
 *
 * <p>A tornado does not level a disc all at once. It peels: the roof first, then what the roof was
 * holding down, then the walls now that the wind gets inside. That falls out of sampling the top of a
 * column rather than a fixed layer, so every pass over the same place goes one step deeper, and a
 * house comes apart in the order a house comes apart.
 *
 * <p>The sweep is budgeted rather than exhaustive. A mature funnel reaches over a disc of several
 * thousand columns, and visiting all of them every tick would cost more than the rest of the server
 * together. Instead a golden-angle spiral walks the disc, so the samples spread evenly instead of
 * clustering, and the tornado's own travel keeps the pattern off the blocks it visited last tick.
 *
 * <p>Nothing is put back. What the storm takes is destroyed.
 */
public final class DestructionSweep {

    /** How far past the core wall the damage still reaches, as a multiple of the core radius. */
    private static final double DAMAGE_REACH = 2.6;

    /** Golden angle, the one that makes successive samples never line up into spokes. */
    private static final double GOLDEN_ANGLE = 2.399963229728653;

    /** How many samples the spiral spends before it starts over near the axis. */
    private static final int SPIRAL_PERIOD = 733;

    /** Bias of the sample radius: under one it crowds the core, where the wind actually works. */
    private static final double RADIAL_BIAS = 0.62;

    /** Blocks tested per column before the spiral moves on. A roof and what it was covering. */
    private static final int DEPTH_PER_COLUMN = 3;

    /** How many samples the sweep is allowed per broken block, so a calm pass still ends. */
    private static final int SAMPLES_PER_BREAK = 5;

    /** Upward kick given to a piece the moment it comes out, in blocks per second. */
    private static final double RELEASE_LIFT = 6.0;

    private final WindVector wind = new WindVector();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private int spiral;

    /**
     * One tick of tearing.
     *
     * @return how many blocks came out, which is what the debris ring at the foot is drawn from
     */
    public int run(ServerLevel level, TornadoEntity tornado, VortexParameters parameters, TintAccumulator tint) {
        if (!DamageConfig.breakBlocks || DamageConfig.blockBudgetPerTick <= 0) {
            return 0;
        }

        double reach = parameters.coreRadius() * DAMAGE_REACH;
        double phase = tornado.phase();
        int budget = DamageConfig.blockBudgetPerTick;
        int broken = 0;
        int samplesLeft = budget * SAMPLES_PER_BREAK;

        while (budget > 0 && samplesLeft-- > 0) {
            spiral++;
            double angle = spiral * GOLDEN_ANGLE;
            double u = (double) (spiral % SPIRAL_PERIOD) / SPIRAL_PERIOD;
            double radius = reach * Math.pow(u, RADIAL_BIAS);
            int x = (int) Math.floor(parameters.centerX() + radius * Math.cos(angle));
            int z = (int) Math.floor(parameters.centerZ() + radius * Math.sin(angle));
            if (!level.isLoaded(cursor.set(x, level.getMinY(), z))) {
                continue;
            }

            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            for (int depth = 0; depth < DEPTH_PER_COLUMN && budget > 0; depth++) {
                int y = top - depth;
                if (y <= level.getMinY()) {
                    break;
                }
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                if (tear(level, tornado, parameters, tint, state, x, y, z, phase)) {
                    budget--;
                    broken++;
                }
            }
        }
        return broken;
    }

    /** Tests one block against the wind at its own position and takes it if the wind wins. */
    private boolean tear(ServerLevel level, TornadoEntity tornado, VortexParameters parameters,
                         TintAccumulator tint, BlockState state, int x, int y, int z, double phase) {
        double resistance = state.getBlock().getExplosionResistance();
        if (resistance >= WindPressure.NEVER_BREAKS) {
            return false;
        }

        TurbulentVortex.sample(parameters, phase, x + 0.5, y + 0.5, z + 0.5, wind);
        double speed = wind.speed();
        if (!WindPressure.breaks(resistance, speed, DamageConfig.resistanceScale)) {
            return false;
        }

        // A copy, because the cursor is reused for the next block and an addon that files this one away
        // would find it had quietly moved.
        if (!BlockTakenCallback.EVENT.invoker().allowTake(level, tornado, cursor.immutable(), state)) {
            return false;
        }

        tint.ingest(state.getMapColor(level, cursor).col);

        boolean carried = tornado.canCarryMore()
                && WindPressure.carries(resistance, speed, DamageConfig.resistanceScale);
        level.destroyBlock(cursor, DamageConfig.dropItems, tornado, 0);

        if (carried) {
            DebrisEntity piece = DebrisEntity.launch(level, tornado, x + 0.5, y + 0.5, z + 0.5, state, resistance);
            if (piece != null) {
                // The wind at the block plus a kick off the ground, so the piece clears whatever it was
                // sitting on instead of scraping along it for the first few ticks.
                piece.setDeltaMovement(wind.x / 20.0, (wind.y + RELEASE_LIFT) / 20.0, wind.z / 20.0);
            }
        }
        return true;
    }
}

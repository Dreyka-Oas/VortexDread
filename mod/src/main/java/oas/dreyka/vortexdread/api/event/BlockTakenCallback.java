package oas.dreyka.vortexdread.api.event;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import oas.dreyka.vortexdread.api.TornadoView;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether the storm is allowed to take this block.
 *
 * <p>This is the hook a claim mod needs, and the reason it is a veto rather than a notification: a
 * protected region has to be able to say no before the block goes, since what a tornado takes is
 * destroyed rather than dropped and there is nothing to put back afterwards.
 *
 * <p>Fired on the server thread, once per block the sweep has already decided the wind is strong
 * enough to lift. It is not asked about blocks the storm was never going to reach, so a listener that
 * refuses everything costs about what the destruction itself costs and no more.
 *
 * <p>A refused block also stops feeding the funnel its colour, which is right: what was never taken
 * was never in the air.
 */
@FunctionalInterface
public interface BlockTakenCallback {

    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    Event<BlockTakenCallback> EVENT = EventFactory.createArrayBacked(BlockTakenCallback.class,
            listeners -> (level, tornado, pos, state) -> {
                for (BlockTakenCallback listener : listeners) {
                    try {
                        if (!listener.allowTake(level, tornado, pos, state)) {
                            return false;
                        }
                    } catch (Throwable t) {
                        CallbackBlame.report(BLAMED, listener, t, "block taken",
                                "the block is taken as though nothing had objected");
                    }
                }
                return true;
            });

    /**
     * @return false to leave the block where it is, which no other listener can undo
     */
    boolean allowTake(ServerLevel level, TornadoView tornado, BlockPos pos, BlockState state);
}

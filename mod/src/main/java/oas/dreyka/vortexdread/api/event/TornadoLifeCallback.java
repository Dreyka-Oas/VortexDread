package oas.dreyka.vortexdread.api.event;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import oas.dreyka.vortexdread.api.TornadoView;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;

/**
 * The three moments in a tornado's life another mod is likely to care about.
 *
 * <p>One interface rather than three, because an addon that wants any of them almost always wants the
 * others too, and because a listener registered once cannot end up hearing about a birth without ever
 * hearing about the death that follows it.
 *
 * <p>All three fire on the server thread, and the view handed over is live: reading it later in the
 * same tick is fine, holding it past the death is not, since it answers {@code false} to
 * {@link TornadoView#alive()} from then on.
 */
public interface TornadoLifeCallback {

    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    Event<TornadoLifeCallback> EVENT = EventFactory.createArrayBacked(TornadoLifeCallback.class,
            listeners -> new TornadoLifeCallback() {
                @Override
                public void onFormed(ServerLevel level, TornadoView tornado) {
                    for (TornadoLifeCallback listener : listeners) {
                        try {
                            listener.onFormed(level, tornado);
                        } catch (Throwable t) {
                            blame(listener, t, "the storm goes on");
                        }
                    }
                }

                @Override
                public void onTouchdown(ServerLevel level, TornadoView tornado) {
                    for (TornadoLifeCallback listener : listeners) {
                        try {
                            listener.onTouchdown(level, tornado);
                        } catch (Throwable t) {
                            blame(listener, t, "the storm goes on");
                        }
                    }
                }

                @Override
                public void onGone(ServerLevel level, TornadoView tornado) {
                    for (TornadoLifeCallback listener : listeners) {
                        try {
                            listener.onGone(level, tornado);
                        } catch (Throwable t) {
                            blame(listener, t, "the entity is removed anyway");
                        }
                    }
                }
            });

    /**
     * The funnel has come out of the wall cloud and is on its way down. Nothing is damaged yet.
     *
     * <p>This is where a warning belongs. Everything the storm will be worth is already decided, so
     * {@link TornadoView#peakRating()} answers here and answers the same thing later.
     */
    void onFormed(ServerLevel level, TornadoView tornado);

    /** Contact with the ground. From this tick on, blocks go and anything standing in it is in it. */
    default void onTouchdown(ServerLevel level, TornadoView tornado) {
    }

    /** Over, and the entity is about to be removed. The last chance to read anything off it. */
    default void onGone(ServerLevel level, TornadoView tornado) {
    }

    private static void blame(Object listener, Throwable t, String outcome) {
        CallbackBlame.report(BLAMED, listener, t, "tornado life", outcome);
    }
}

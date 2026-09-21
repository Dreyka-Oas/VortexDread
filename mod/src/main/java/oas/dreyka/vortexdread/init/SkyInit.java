package oas.dreyka.vortexdread.init;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.atmosphere.Atmosphere;
import oas.dreyka.vortexdread.atmosphere.AtmosphereSettings;
import oas.dreyka.vortexdread.atmosphere.SkyRunner;
import oas.dreyka.vortexdread.atmosphere.SkySolver;
import oas.dreyka.vortexdread.atmosphere.gpu.GpuAtmosphere;
import oas.dreyka.vortexdread.config.SkyConfig;
import oas.dreyka.vortexdread.net.SkyBroadcast;
import oas.dreyka.vortexdread.net.SkyFieldPayload;

/**
 * The sky the server owns: opened with the world, stepped with the tick, released with the shutdown.
 *
 * <p>One per server rather than one per dimension. The simulation models a patch of troposphere with a seed
 * and a clock, and which dimension a player is standing in does not change the weather over the overworld.
 *
 * <p>The seed is the world's own, so a world reopened tomorrow gets the same weather it would have had.
 * What a client gets is the field itself, sent as each step lands, which is the only arrangement where two
 * players standing side by side cannot see different clouds.
 */
public final class SkyInit {

    private static final SkyBroadcast broadcast = new SkyBroadcast();

    private static SkyRunner runner;

    private SkyInit() {
    }

    public static void register() {
        SkyFieldPayload.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            long seed = server.overworld().getSeed();
            AtmosphereSettings settings = SkyConfig.settings();
            int cadence = SkyConfig.ticksPerStep();
            runner = new SkyRunner(() -> open(settings, seed), cadence,
                    sky -> broadcast.pack(sky, cadence));
        });

        // Ready rather than connected: a payload sent before the client has left the loading screen is a
        // payload sent to a listener that is not the play one yet.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> broadcast.onJoin(
                handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (runner == null) {
                return;
            }
            runner.onTick();
            broadcast.onTick(server);
            // Logged from here rather than from the worker, which is what makes this the line the rule asks
            // for: it comes off the server thread, so it appears on a dedicated server with no window at all.
            String notice = runner.takeNotice();
            if (notice != null) {
                VortexDread.LOGGER.info("[VortexDread] sky on {}, one step every {} ticks", notice,
                        SkyConfig.ticksPerStep());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (runner == null) {
                return;
            }
            // Two numbers rather than a farewell. The count says the sky kept up: divide it by how long
            // the world was open and compare against the cadence, and a sky that spent the session
            // skipping shows as a count well under what the ticks bought. The water says the sky ran at
            // all, which the count cannot: condensation starts around the hundredth step, so a world
            // closed before that and a simulation doing nothing both print a plausible count and an
            // empty screen, and one of those was read as the other.
            VortexDread.LOGGER.info("[VortexDread] sky stepped {} times, sent {}, wettest cell {}",
                    runner.stepsTaken(), broadcast.tally(), runner.wettestSeen());
            runner.close();
            runner = null;
            broadcast.forget();
        });
    }

    /**
     * The card when one answers and the operator wants it, the processor otherwise.
     *
     * <p>Catching everything and falling back is the only sane behaviour here, since a missing driver, a
     * refused build and a card already busy all arrive as different exceptions and all mean the same thing.
     * What makes the fallback honest is that it says so: the reason travels in the line the server logs, so a
     * machine quietly running three hundred times slower is a machine that said why on the way past.
     */
    private static SkySolver open(AtmosphereSettings settings, long seed) {
        if (!SkyConfig.useGraphicsCard) {
            VortexDread.LOGGER.info("[VortexDread] useGraphicsCard is off, so the processor takes the sky");
            return new Atmosphere(settings, seed);
        }
        try {
            return new GpuAtmosphere(settings, seed, SkyConfig.graphicsCardIndex);
        } catch (RuntimeException | UnsatisfiedLinkError noCard) {
            VortexDread.LOGGER.warn("[VortexDread] no graphics card took the sky: {}", noCard.getMessage());
            return new Atmosphere(settings, seed);
        }
    }
}

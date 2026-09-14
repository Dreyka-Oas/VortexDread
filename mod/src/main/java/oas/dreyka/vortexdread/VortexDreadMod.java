package oas.dreyka.vortexdread;

import oas.dreyka.vortexdread.command.VortexCommand;
import oas.dreyka.vortexdread.compute.AdvectionCompute;
import oas.dreyka.vortexdread.config.ConfigIo;
import oas.dreyka.vortexdread.debris.DebrisSwarm;
import oas.dreyka.vortexdread.entity.VortexEntities;
import oas.dreyka.vortexdread.network.StormPayloads;
import oas.dreyka.vortexdread.sound.VortexSounds;
import oas.dreyka.vortexdread.storm.StormDirector;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** The common entrypoint: settings, registries, commands. */
public class VortexDreadMod implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigIo.load();
        VortexEntities.register();
        VortexSounds.register();
        StormDirector.register();
        DebrisSwarm.register();
        StormPayloads.registerCommon();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> VortexCommand.register(dispatcher));
        // Opened here rather than at the first dispatch: enumerating devices and building a program takes
        // a good fraction of a second, and paying that inside a tick is a stutter at the worst moment.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> AdvectionCompute.warmUp());
        VortexDread.LOGGER.info("[VortexDread] ready");
    }
}

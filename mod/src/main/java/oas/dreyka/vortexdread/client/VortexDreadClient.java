package oas.dreyka.vortexdread.client;

import oas.dreyka.vortexdread.VortexDread;
import oas.dreyka.vortexdread.client.render.DebrisRenderer;
import oas.dreyka.vortexdread.client.render.DistantFunnelRenderer;
import oas.dreyka.vortexdread.client.render.DistantStorms;
import oas.dreyka.vortexdread.client.render.FunnelState;
import oas.dreyka.vortexdread.client.render.StormPostEffect;
import oas.dreyka.vortexdread.client.render.TornadoRenderer;
import oas.dreyka.vortexdread.client.sound.StormAudio;
import oas.dreyka.vortexdread.entity.VortexEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** The client entrypoint: everything that draws or is heard. */
public class VortexDreadClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(VortexEntities.TORNADO, TornadoRenderer::new);
        EntityRendererRegistry.register(VortexEntities.DEBRIS, DebrisRenderer::new);
        DistantStorms.register();
        DistantFunnelRenderer.register();
        FunnelState.register();
        StormAudio.register();
        StormPostEffect.register();
        VortexDread.LOGGER.info("[VortexDread] client ready");
    }
}

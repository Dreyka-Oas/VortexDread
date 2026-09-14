package oas.dreyka.vortexdread.mixin.client;

import oas.dreyka.vortexdread.client.render.CloudFlash;

import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on the render state for the one thing the renderer needs and vanilla does not record. */
@Mixin(LightningBoltRenderState.class)
public class LightningBoltRenderStateMixin implements CloudFlash {

    @Unique
    private boolean vortexdread$inTheDeck;

    @Override
    public boolean vortexdread$insideTheDeck() {
        return vortexdread$inTheDeck;
    }

    @Override
    public void vortexdread$insideTheDeck(boolean inside) {
        vortexdread$inTheDeck = inside;
    }
}

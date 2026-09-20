package oas.dreyka.vortexdread.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * The client side, which is where the sky gets drawn.
 *
 * <p>Empty on purpose for now. The clouds exist as a simulation before they exist as pixels, and the
 * stages that put them on screen register from here once there is something to register.
 */
public final class VortexDreadClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
    }
}

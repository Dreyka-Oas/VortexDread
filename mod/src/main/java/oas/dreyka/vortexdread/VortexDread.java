package oas.dreyka.vortexdread;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import oas.dreyka.vortexdread.init.BootstrapInit;
import oas.dreyka.vortexdread.init.SkyInit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod, on both sides.
 *
 * <p>A list of calls and nothing else. Anything that looks like logic in here is logic nobody can find
 * again, so each line names a stage and the stage owns what it does.
 */
public final class VortexDread implements ModInitializer {

    public static final String ID = "vortexdread";

    public static final Logger LOGGER = LoggerFactory.getLogger("VortexDread");

    /** This mod's namespace on anything the game addresses by name: a shader, a pipeline, a payload. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override
    public void onInitialize() {
        BootstrapInit.run();
        SkyInit.register();
    }
}

package oas.dreyka.vortexdread.init;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import oas.dreyka.vortexdread.config.ConfigFile;

/**
 * The options, read before anything asks for one.
 *
 * <p>This is where the file lives and the only place that knows it, which is what keeps
 * {@link ConfigFile} runnable under a test with no game around it.
 */
public final class BootstrapInit {

    private BootstrapInit() {
    }

    public static void run() {
        ConfigFile.load(file());
    }

    /** The oas folder is the author's namespace, so every mod by the same hand groups together. */
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("oas").resolve("vortexdread.json");
    }
}

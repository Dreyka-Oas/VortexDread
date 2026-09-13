package oas.dreyka.vortexdread;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's identity, and the logger every other class writes through. */
public final class VortexDread {
    private VortexDread() {
    }

    public static final String MOD_ID = "vortexdread";

    public static final Logger LOGGER = LoggerFactory.getLogger("VortexDread");

    /** Builds an identifier in this mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

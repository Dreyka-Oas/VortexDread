package oas.dreyka.vortexdread.client.render;

import java.lang.reflect.Method;
import oas.dreyka.vortexdread.VortexDread;

/**
 * Whether something else has taken the sky over.
 *
 * <p>A shader pack replaces the whole render pipeline and draws volumetric cloud of its own, inside
 * its own programs. Nothing this mod can hook cancels that, so a player running a pack would get two
 * skies stacked on each other: the pack's, decorative and lit by its own rules, and ours, in front of
 * it and blended over it. The answer is to stand down while a pack is on, since the pack is what the
 * player chose to look at.
 *
 * <p>Read through reflection rather than against the Iris jar. The question is only ever asked once
 * per frame on the render thread and the answer is one boolean, so a compile-time dependency on a mod
 * almost nobody has installed buys nothing and costs a build that breaks whenever Iris moves a
 * package, which it has done once already.
 */
public final class ShaderPacks {

    // Iris moved its API package when it was renamed, and both names are still in the wild.
    private static final String[] CANDIDATES = {
        "net.irisshaders.iris.api.v0.IrisApi",
        "net.coderbot.iris.api.v0.IrisApi",
    };

    private static boolean looked;
    private static Object api;
    private static Method asking;

    private ShaderPacks() {
    }

    /** True when a pack is loaded and drawing, false when none is installed and when one is off. */
    public static boolean inUse() {
        if (!looked) {
            look();
        }
        if (asking == null) {
            return false;
        }
        try {
            return (Boolean) asking.invoke(api);
        } catch (ReflectiveOperationException | ClassCastException failed) {
            // A pack whose state cannot be read is treated as absent: refusing to draw on a doubt
            // would hand the sky to nobody on every machine where the lookup breaks.
            asking = null;
            VortexDread.LOGGER.warn("[VortexDread] could not ask the shader pack loader, drawing anyway",
                    failed);
            return false;
        }
    }

    /** Dropped with the world, so a pack turned on between two worlds is noticed. */
    public static void forget() {
        looked = false;
        api = null;
        asking = null;
    }

    private static void look() {
        looked = true;
        for (String name : CANDIDATES) {
            try {
                Class<?> found = Class.forName(name);
                api = found.getMethod("getInstance").invoke(null);
                asking = found.getMethod("isShaderPackInUse");
                return;
            } catch (ReflectiveOperationException | RuntimeException absent) {
                // The ordinary case, since most installs have no shader loader at all.
            }
        }
    }
}

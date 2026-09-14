package oas.dreyka.vortexdread.client.render;

import oas.dreyka.vortexdread.VortexDread;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Whether a shader pack is drawing the world, and therefore whether the mod should draw the funnel.
 *
 * <p>The funnel is a volume marched inside a box, and the program that marches it is one of the mod's
 * own core shaders. A shader pack replaces the whole pipeline, and Iris has no way to guess which of
 * the pack's programs a mod's core shader belongs to: it says so once in the log and falls back, and
 * what the fallback draws is the marching box itself, a rectangle standing in the sky where the storm
 * should be.
 *
 * <p>So when a pack is loaded the box is not submitted at all. The patched copy of Photon that ships
 * with the mod marches the same column from inside its own cloud pass, where it takes the pack's
 * lighting instead of fighting it. A pack that has not been patched leaves no funnel, which is the
 * honest outcome: a rectangle is worse than nothing, and the patch is one command away.
 *
 * <p>Reflection rather than a dependency, because a player without Iris has to be able to run the mod
 * and the answer there is a constant no.
 */
public final class ShaderPack {
    private ShaderPack() {
    }

    private static final Method IN_USE = findIrisQuery();

    private static Method findIrisQuery() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return api.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException e) {
            VortexDread.LOGGER.warn("[VortexDread] Iris is loaded but its API did not answer: {}",
                    e.toString());
            return null;
        }
    }

    /** True while a pack is rendering, so the mod's own passes stand down. */
    public static boolean drawingTheWorld() {
        if (IN_USE == null) {
            return false;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (boolean) IN_USE.invoke(instance);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}

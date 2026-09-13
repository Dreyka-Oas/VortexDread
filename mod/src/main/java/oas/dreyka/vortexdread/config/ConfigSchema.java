package oas.dreyka.vortexdread.config;

import oas.dreyka.vortexdread.config.domain.ComputeConfig;
import oas.dreyka.vortexdread.config.domain.DamageConfig;
import oas.dreyka.vortexdread.config.domain.LookConfig;
import oas.dreyka.vortexdread.config.domain.StormConfig;
import oas.dreyka.vortexdread.config.domain.TornadoConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every option the mod has, found by walking the domain classes rather than listed a second time here.
 *
 * <p>A new option is one public static field on one of those classes plus one range in
 * {@link ConfigBounds}, and the file on disk, the command and the network snapshot all pick it up on
 * their own. The duplicate list that would otherwise drift is what this class exists to avoid.
 */
public final class ConfigSchema {
    private ConfigSchema() {
    }

    /** The domain classes, in the order their options are written to disk. */
    private static final Class<?>[] GROUPS = {
            StormConfig.class,
            TornadoConfig.class,
            DamageConfig.class,
            ComputeConfig.class,
            LookConfig.class,
    };

    private static final Map<String, Field> BY_NAME = index();

    private static Map<String, Field> index() {
        Map<String, Field> m = new LinkedHashMap<>();
        for (Class<?> group : GROUPS) {
            for (Field f : group.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                Field clash = m.put(f.getName(), f);
                if (clash != null) {
                    throw new IllegalStateException("two options share the name " + f.getName());
                }
            }
        }
        return Collections.unmodifiableMap(m);
    }

    /** Every option, in file order. */
    public static List<Field> all() {
        return new ArrayList<>(BY_NAME.values());
    }

    /** Name of the group an option belongs to, lowercase, as used for the file's top level keys. */
    public static String groupOf(Field field) {
        String simple = field.getDeclaringClass().getSimpleName();
        return simple.substring(0, simple.length() - "Config".length()).toLowerCase(Locale.ROOT);
    }

    /** The option of that name, or null. */
    public static Field find(String name) {
        return BY_NAME.get(name);
    }

    /** Reads an option's current value. */
    public static Object read(String name) {
        Field f = BY_NAME.get(name);
        if (f == null) {
            return null;
        }
        try {
            return f.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("option " + name + " is not readable", e);
        }
    }

    /**
     * Parses a written value, clamps it to its range and stores it.
     *
     * @return false when the name is unknown or the text does not parse, leaving the option untouched
     */
    public static boolean apply(String name, String raw) {
        Field f = BY_NAME.get(name);
        if (f == null || raw == null) {
            return false;
        }
        String text = raw.trim();
        try {
            Class<?> type = f.getType();
            if (type == boolean.class) {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    return false;
                }
                f.setBoolean(null, Boolean.parseBoolean(text));
                return true;
            }
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) {
                return false;
            }
            ConfigBounds.Range range = ConfigBounds.of(name);
            if (range != null) {
                value = range.clamp(value);
            }
            if (type == int.class) {
                f.setInt(null, (int) Math.round(value));
            } else if (type == long.class) {
                f.setLong(null, Math.round(value));
            } else if (type == float.class) {
                f.setFloat(null, (float) value);
            } else if (type == double.class) {
                f.setDouble(null, value);
            } else {
                return false;
            }
            return true;
        } catch (NumberFormatException | IllegalAccessException e) {
            return false;
        }
    }

    /** Every option and its current value, in file order. */
    public static Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Field> e : BY_NAME.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().get(null));
            } catch (IllegalAccessException ignored) {
                // A public static field of a public class is always readable; nothing to report.
            }
        }
        return out;
    }
}

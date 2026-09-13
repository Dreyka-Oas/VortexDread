package oas.dreyka.vortexdread.config;

import oas.dreyka.vortexdread.VortexDread;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The options file at {@code config/oas/vortexdread.json}.
 *
 * <p>Grouped by domain, written whole on first run and rewritten on every save so a version that adds
 * an option leaves the file complete rather than half old. A file that will not parse is moved aside
 * instead of being overwritten: someone spent time in there, and silently replacing it with the
 * defaults is the one outcome they cannot recover from.
 */
public final class ConfigIo {
    private ConfigIo() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("oas").resolve("vortexdread.json");
    }

    /** Reads the file into the domain classes, writing a fresh one when none exists. */
    public static synchronized void load() {
        Path path = file();
        if (!Files.exists(path)) {
            save();
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (Exception e) {
            quarantine(path, e);
            save();
            return;
        }

        int applied = 0;
        int refused = 0;
        for (Map.Entry<String, JsonElement> group : root.entrySet()) {
            if (!group.getValue().isJsonObject()) {
                refused++;
                continue;
            }
            for (Map.Entry<String, JsonElement> option : group.getValue().getAsJsonObject().entrySet()) {
                JsonElement value = option.getValue();
                if (!(value instanceof JsonPrimitive primitive)) {
                    refused++;
                    continue;
                }
                if (ConfigSchema.apply(option.getKey(), primitive.getAsString())) {
                    applied++;
                } else {
                    refused++;
                    VortexDread.LOGGER.warn("[VortexDread] config: '{}' is not an option of this version",
                            option.getKey());
                }
            }
        }
        VortexDread.LOGGER.info("[VortexDread] config: {} options read, {} ignored", applied, refused);

        // Rewrite so an option added by this version lands in the file the owner is editing.
        save();
    }

    /** Writes every current value back, atomically. */
    public static synchronized void save() {
        Map<String, JsonObject> groups = new LinkedHashMap<>();
        for (Field f : ConfigSchema.all()) {
            JsonObject group = groups.computeIfAbsent(ConfigSchema.groupOf(f), unused -> new JsonObject());
            Object value = ConfigSchema.read(f.getName());
            if (value instanceof Boolean b) {
                group.addProperty(f.getName(), b);
            } else if (value instanceof Number n) {
                group.addProperty(f.getName(), n);
            }
        }
        JsonObject root = new JsonObject();
        groups.forEach(root::add);

        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            VortexDread.LOGGER.error("[VortexDread] config: could not write {}", path, e);
        }
    }

    private static void quarantine(Path path, Exception cause) {
        Path aside = path.resolveSibling(path.getFileName() + ".broken");
        try {
            Files.move(path, aside, StandardCopyOption.REPLACE_EXISTING);
            VortexDread.LOGGER.error("[VortexDread] config: {} would not parse, kept as {} ({})",
                    path.getFileName(), aside.getFileName(), cause.toString());
        } catch (IOException e) {
            VortexDread.LOGGER.error("[VortexDread] config: {} would not parse and could not be set aside",
                    path, e);
        }
    }
}

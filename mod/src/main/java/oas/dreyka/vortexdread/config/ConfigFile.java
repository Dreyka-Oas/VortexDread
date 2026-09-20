package oas.dreyka.vortexdread.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@link SkyConfig} and {@link LookConfig} as JSON, by reflection over their fields.
 *
 * <p>Reflection rather than a serializer per option, so adding an option is adding a field and nothing
 * else. The file is rewritten after every read, which is what makes an option added by an update appear
 * with its default rather than silently staying invisible to whoever is editing the file.
 *
 * <p>It takes the path rather than finding it, so the whole class runs under a unit test against a
 * temporary directory with no game around it.
 */
public final class ConfigFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigFile() {
    }

    /**
     * Applies whatever the file names, leaves the rest at its default, then writes the full set back.
     *
     * <p>An unreadable file is moved aside rather than deleted and rather than stopping the load, because a
     * server that refuses to start over a stray comma is worse than a server running on defaults, and an
     * operator who lost the file they spent an afternoon on has lost more than the defaults are worth.
     */
    public static void load(Path file) {
        if (Files.isReadable(file)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                apply(parsed.getAsJsonObject());
            } catch (RuntimeException | IOException unreadable) {
                setAside(file);
            }
        }
        save(file);
    }

    public static synchronized void save(Path file) {
        JsonObject out = new JsonObject();
        for (Field field : options()) {
            try {
                Object value = field.get(null);
                if (value instanceof Boolean flag) {
                    out.addProperty(field.getName(), flag);
                } else {
                    out.addProperty(field.getName(), (Number) value);
                }
            } catch (IllegalAccessException unreachable) {
                throw new IllegalStateException("option " + field.getName() + " is not readable",
                        unreachable);
            }
        }
        write(file, GSON.toJson(out));
    }

    private static void apply(JsonObject read) {
        for (Field field : options()) {
            JsonElement value = read.get(field.getName());
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                Class<?> type = field.getType();
                if (type == boolean.class) {
                    field.setBoolean(null, value.getAsBoolean());
                } else if (type == int.class) {
                    field.setInt(null, value.getAsInt());
                } else if (type == float.class) {
                    field.setFloat(null, value.getAsFloat());
                }
            } catch (IllegalAccessException | NumberFormatException wrongShape) {
                // One option written as a word where a number belongs leaves that option at its default
                // and every other one still applied, which is the behaviour an operator editing by hand
                // wants: the mistake costs the line it is on.
            }
        }
    }

    /** The options, in declaration order so the file reads the way the classes do, weather first. */
    private static List<Field> options() {
        List<Field> found = new ArrayList<>();
        for (Class<?> holder : List.of(SkyConfig.class, LookConfig.class)) {
            for (Field field : holder.getDeclaredFields()) {
                int how = field.getModifiers();
                if (Modifier.isStatic(how) && Modifier.isPublic(how) && !Modifier.isFinal(how)) {
                    found.add(field);
                }
            }
        }
        return found;
    }

    /** Written beside the target and moved onto it, so a crash mid-write cannot leave half a file. */
    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Path partial = file.resolveSibling(file.getFileName() + ".part");
            Files.writeString(partial, text, StandardCharsets.UTF_8);
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException cannotWrite) {
            throw new IllegalStateException("the sky options could not be written to " + file,
                    cannotWrite);
        }
    }

    private static void setAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException cannotMove) {
            // Nothing useful is left to do: the file is unreadable and cannot be moved out of the way, so
            // the defaults stand and the rewrite below replaces it.
        }
    }
}

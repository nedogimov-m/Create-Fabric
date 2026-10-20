package com.simibubi.create.infrastructure.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads real sibling NeoForge scope files without modifying the source or replacing a native owner. */
final class NeoForgeTomlImport {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    /** Pinned NeoForge21.1.219 loads global config first, with defaultconfigs only as its seed. */
    static Path defaultServerSource(Path configDirectory, Path defaultDirectory) {
        Path global = configDirectory.resolve("create-server.toml");
        return exists(global) ? global : defaultDirectory.resolve("create-server.toml");
    }

    static Plan prepare(Path target, NativeConfigStore store, Path sourceOverride) {
        if (exists(target)) return null;
        String name = target.getFileName().toString();
        if (!name.endsWith(".json")) return null;
        Path source = sourceOverride != null ? sourceOverride
            : target.resolveSibling(name.substring(0, name.length() - 5) + ".toml");
        if (!exists(source)) return null;
        try {
            byte[] bytes = Files.readAllBytes(source);
            var values = new JsonObject();
            var types = new JsonArray();
            try {
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                var parsed = new TomlParser().parse(new StringReader(text));
                values = object(parsed, List.of(), types);
            } catch (RuntimeException invalid) {
                if (exists(target)) return null;
                throw new IllegalArgumentException("Cannot import genuine NeoForge TOML " + source, invalid);
            }
            var known = new LinkedHashSet<List<String>>();
            store.definitions().forEach(definition -> known.add(definition.path()));
            var unknown = new JsonArray();
            collectUnknown(values, List.of(), known, unknown);
            return new Plan(source, target, digest(bytes), values, types, unknown, store.scope());
        } catch (IOException failure) {
            if (exists(target)) return null;
            throw new UncheckedIOException("Cannot read genuine NeoForge scope " + source, failure);
        }
    }

    private static JsonObject object(UnmodifiableConfig input, List<String> path, JsonArray types) {
        var result = new JsonObject();
        input.valueMap().forEach((key, value) -> result.add(key, value(value, append(path, key), types)));
        return result;
    }

    private static JsonElement value(Object input, List<String> path, JsonArray types) {
        if (input instanceof UnmodifiableConfig config) return object(config, path, types);
        if (input instanceof List<?> list) {
            var result = new JsonArray();
            for (int i = 0; i < list.size(); i++) result.add(value(list.get(i), append(path, Integer.toString(i)), types));
            return result;
        }
        if (input == null) throw new IllegalArgumentException("Null is not a source TOML value at " + path);
        var type = new JsonObject();
        var segments = new JsonArray(); path.forEach(segments::add);
        type.add("path", segments); type.addProperty("type", input.getClass().getName()); types.add(type);
        if (input instanceof String text) return new JsonPrimitive(text);
        if (input instanceof Boolean flag) return new JsonPrimitive(flag);
        if (input instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d)) return new JsonPrimitive(d.toString());
            if (number instanceof Float f && !Float.isFinite(f)) return new JsonPrimitive(f.toString());
            return new JsonPrimitive(number);
        }
        // TOML date/time values cannot occur in source typed settings. Unknown user fields retain
        // the canonical value, original type and original file checksum in the migration report.
        if (input instanceof TemporalAccessor time) return new JsonPrimitive(time.toString());
        throw new IllegalArgumentException("Unsupported genuine TOML value at " + path + ": " + input.getClass().getName());
    }

    private static void collectUnknown(JsonObject object, List<String> path, Set<List<String>> known, JsonArray unknown) {
        object.entrySet().forEach(entry -> {
            var at = append(path, entry.getKey());
            if (known.contains(at)) return;
            boolean section = known.stream().anyMatch(key -> key.size() > at.size() && key.subList(0, at.size()).equals(at));
            if (section && entry.getValue().isJsonObject()) collectUnknown(entry.getValue().getAsJsonObject(), at, known, unknown);
            else {
                var field = new JsonObject();
                var segments = new JsonArray(); at.forEach(segments::add);
                field.add("path", segments);
                field.add("value", entry.getValue().deepCopy());
                unknown.add(field);
            }
        });
    }

    private static List<String> append(List<String> path, String key) {
        var result = new ArrayList<>(path); result.add(key); return List.copyOf(result);
    }
    private static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }
    private static String digest(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    /** The store has decoded all known values and prepared real consumers before publishing this plan. */
    static final class Plan {
        private final Path source, target;
        private final String sourceHash;
        private final JsonObject values;
        private final JsonArray types, unknown;
        private final ConfigScope scope;
        private Plan(Path source, Path target, String sourceHash, JsonObject values, JsonArray types,
                     JsonArray unknown, ConfigScope scope) {
            this.source = source; this.target = target; this.sourceHash = sourceHash;
            this.values = values.deepCopy(); this.types = types.deepCopy(); this.unknown = unknown.deepCopy(); this.scope = scope;
        }
        JsonObject document() { return values.deepCopy(); }

        boolean publish(JsonObject rendered) {
            if (exists(target)) return false;
            try {
                if (!sourceHash.equals(digest(Files.readAllBytes(source))))
                    throw new IllegalStateException("NeoForge TOML changed while its migration was prepared: " + source);
                byte[] nativeBytes = (JSON.toJson(rendered) + "\n").getBytes(StandardCharsets.UTF_8);
                String nativeHash = digest(nativeBytes);
                var report = new JsonObject();
                report.addProperty("formatVersion", 1);
                report.addProperty("phase", "validated_conversion");
                report.addProperty("scope", scope.name());
                report.addProperty("source", source.toAbsolutePath().normalize().toString());
                report.addProperty("sourceSha256", sourceHash);
                report.addProperty("target", target.toAbsolutePath().normalize().toString());
                report.addProperty("targetSha256", nativeHash);
                report.addProperty("parser", "com.electronwill.night-config:toml:3.8.3");
                report.add("unknownFields", unknown.deepCopy());
                report.add("sourceValueTypes", types.deepCopy());
                Path reportPath = target.resolveSibling(target.getFileName() + ".neoforge-migration-"
                    + sourceHash.substring(0, 16) + "-" + nativeHash.substring(0, 16) + ".json");
                byte[] reportBytes = (JSON.toJson(report) + "\n").getBytes(StandardCharsets.UTF_8);
                if (!createAbsent(reportPath, reportBytes) && !MessageDigest.isEqual(Files.readAllBytes(reportPath), reportBytes))
                    throw new IllegalStateException("Existing migration report has a different owner/content: " + reportPath);
                return createAbsent(target, nativeBytes);
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot publish validated NeoForge configuration migration to " + target, failure);
            }
        }
    }

    /** A complete same-filesystem temporary file is linked atomically with create-if-absent semantics.
     * ATOMIC_MOVE may replace an existing target on some providers, so it cannot establish this owner. */
    private static boolean createAbsent(Path target, byte[] bytes) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "create-neoforge-migration-", ".tmp");
        try {
            try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) output.write(data);
                output.force(true);
            }
            try { Files.createLink(target, temporary); return true; }
            catch (FileAlreadyExistsException owned) { return false; }
            catch (UnsupportedOperationException unsupported) {
                throw new IOException("This filesystem cannot atomically create an absent migration file: " + target, unsupported);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    private NeoForgeTomlImport() {}
}

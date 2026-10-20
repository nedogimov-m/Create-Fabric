package com.simibubi.create.foundation.utility;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
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
import java.util.HexFormat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Strict original1.21.1 scope discovery and create-if-absent publication into real native SavedData. */
public final class NeoForgeSavedDataImport {
    public static final int SOURCE_DATA_VERSION = 3955;
    private static final long NBT_LIMIT = 256L * 1024 * 1024;
    @FunctionalInterface public interface Converter {
        CompoundTag convert(CompoundTag source, int sourceVersion, HolderLookup.Provider registries);
    }

    public static <T extends SavedData> T load(MinecraftServer server, SavedDataType<T> type,
            String originalFile, String converterVersion, Converter converter) {
        if (!server.isSameThread()) throw new IllegalStateException("Original SavedData migration requires its actual server thread");
        var storage = server.overworld().getDataStorage();
        return load(storage, server.getWorldPath(LevelResource.ROOT).resolve("data"), nativeDirectory(storage),
            server.registryAccess(), type, originalFile, converterVersion, converter);
    }

    /** Also accepts an actual independent SavedDataStorage for native regression cases. */
    public static <T extends SavedData> T load(SavedDataStorage storage, Path dataDirectory, HolderLookup.Provider registries,
            SavedDataType<T> type, String originalFile, String converterVersion, Converter converter) {
        return load(storage, dataDirectory, dataDirectory, registries, type, originalFile, converterVersion, converter);
    }

    /** The original global source and the genuine native owner's folder may be different in 26.3. */
    public static <T extends SavedData> T load(SavedDataStorage storage, Path sourceDataDirectory,
            Path targetDataDirectory, HolderLookup.Provider registries, SavedDataType<T> type,
            String originalFile, String converterVersion, Converter converter) {
        Path sourceDirectory = sourceDataDirectory.toAbsolutePath().normalize();
        Path targetDirectory = targetDataDirectory.toAbsolutePath().normalize();
        if (!targetDirectory.equals(nativeDirectory(storage)))
            throw new IllegalArgumentException("SavedData target directory must be its actual native owner's folder");
        Path source = sourceDirectory.resolve(originalFile).normalize();
        Path target = type.id().withSuffix(".dat").resolveAgainst(targetDirectory).normalize();
        if (!source.getParent().equals(sourceDirectory) || !target.startsWith(targetDirectory) || target.equals(source))
            throw new IllegalArgumentException("SavedData migration must retain its actual original/native file scopes");
        synchronized (storage) {
            T installed = cached(storage, type);
            if (installed != null) return installed;
            try {
                if (exists(target)) return installNative(storage, target, registries, type);
                if (!exists(source)) return storage.computeIfAbsent(type);
                byte[] original = Files.readAllBytes(source);
                CompoundTag envelope = read(original);
                if (!(envelope.get("DataVersion") instanceof IntTag)
                        || envelope.getIntOr("DataVersion", -1) != SOURCE_DATA_VERSION)
                    throw invalid(source, "Unsupported original Minecraft data version");
                for (String key : envelope.keySet()) if (!key.equals("DataVersion") && !key.equals("data"))
                    throw invalid(source, "Unknown original SavedData envelope field " + key);
                if (!(envelope.get("data") instanceof CompoundTag oldData))
                    throw invalid(source, "Missing actual original data compound");
                var ops = registries.createSerializationContext(NbtOps.INSTANCE);
                CompoundTag converted = converter.convert(oldData.copy(), SOURCE_DATA_VERSION, registries);
                T candidate = type.codec().parse(ops, converted).getOrThrow();
                // Encode the real validated native value, not an unchecked renamed legacy payload.
                var nativeEnvelope = new CompoundTag();
                nativeEnvelope.put("data", type.codec().encodeStart(ops, candidate).getOrThrow());
                NbtUtils.addCurrentDataVersion(nativeEnvelope);
                var bytes = new ByteArrayOutputStream(); NbtIo.writeCompressed(nativeEnvelope, bytes);
                byte[] nativeBytes = bytes.toByteArray();
                installed = cached(storage, type);
                if (installed != null) return installed;
                if (exists(target)) return installNative(storage, target, registries, type);
                String originalHash = digest(original);
                if (!originalHash.equals(digest(Files.readAllBytes(source))))
                    throw invalid(source, "Original changed during migration preparation");
                var report = new JsonObject(); report.addProperty("formatVersion", 1);
                report.addProperty("phase", "validated_conversion"); report.addProperty("savedData", type.id().toString());
                report.addProperty("converter", converterVersion); report.addProperty("sourceDataVersion", SOURCE_DATA_VERSION);
                report.addProperty("source", source.toString()); report.addProperty("sourceSha256", originalHash);
                report.addProperty("target", target.toString()); report.addProperty("targetSha256", digest(nativeBytes));
                byte[] reportBytes = (new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n").getBytes(StandardCharsets.UTF_8);
                Path reportPath = target.resolveSibling(target.getFileName() + ".neoforge-migration-"
                    + originalHash.substring(0, 16) + "-" + digest(nativeBytes).substring(0, 16) + ".json");
                if (!createAbsent(reportPath, reportBytes) && !MessageDigest.isEqual(Files.readAllBytes(reportPath), reportBytes))
                    throw invalid(reportPath, "Existing report has different content/ownership");
                if (!createAbsent(target, nativeBytes)) return installNative(storage, target, registries, type);
                // No game callbacks occur between final cache validation, owner creation and this install.
                storage.set(type, candidate);
                return candidate;
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot migrate original SavedData " + source + " into " + target, failure);
            }
        }
    }

    /** Native SavedDataStorage writes the same folder after this importer publishes its initial file. */
    public static Path nativeDirectory(SavedDataStorage storage) {
        return ((SavedDataStorageAccessor) storage).create$getDataFolder().toAbsolutePath().normalize();
    }

    @SuppressWarnings("unchecked") private static <T extends SavedData> T cached(SavedDataStorage storage, SavedDataType<T> type) {
        var owner = ((SavedDataStorageAccessor) storage).create$getCache().get(type);
        return owner == null || owner.isEmpty() ? null : (T) owner.get();
    }
    private static <T extends SavedData> T installNative(SavedDataStorage storage, Path target,
            HolderLookup.Provider registries, SavedDataType<T> type) throws IOException {
        T actual = cached(storage, type);
        if (actual != null) return actual;
        var envelope = read(Files.readAllBytes(target));
        if (!(envelope.get("data") instanceof CompoundTag data)) throw invalid(target, "Invalid present native SavedData owner");
        var decoded = type.codec().parse(registries.createSerializationContext(NbtOps.INSTANCE), data).getOrThrow();
        actual = cached(storage, type);
        if (actual != null) return actual;
        storage.set(type, decoded);
        decoded.setDirty(false); // a preexisting native owner was read, not modified by migration
        return decoded;
    }
    private static CompoundTag read(byte[] bytes) throws IOException {
        var input = new ByteArrayInputStream(bytes);
        var quota = NbtAccounter.create(NBT_LIMIT);
        return bytes.length >= 2 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139
            ? NbtIo.readCompressed(input, quota) : NbtIo.read(new DataInputStream(input), quota);
    }
    private static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }
    private static boolean createAbsent(Path target, byte[] bytes) throws IOException {
        Path parent = target.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "create-neoforge-saveddata-", ".tmp");
        try {
            try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) output.write(buffer); output.force(true);
            }
            try { Files.createLink(target, temporary); return true; }
            catch (FileAlreadyExistsException owned) { return false; }
            catch (UnsupportedOperationException unsupported) { throw new IOException("Filesystem cannot atomically establish an absent SavedData owner " + target, unsupported); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static IllegalArgumentException invalid(Path source, String detail) {
        return new IllegalArgumentException(detail + ": " + source);
    }
    private NeoForgeSavedDataImport() {}
}

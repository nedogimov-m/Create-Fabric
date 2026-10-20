package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * One original SavedData scope may have two real native owners. Prepare and validate both before
 * publication, then retain a durable, content-addressed intent until both files are installed. No
 * owner is exposed through the native cache while only one target file has been established.
 */
public final class NeoForgeSavedDataPairImport {
    public record Pair<A extends SavedData, B extends SavedData>(A first, B second) {}
    public record Converted(CompoundTag first, CompoundTag second) {
        public Converted {
            first = first.copy();
            second = second.copy();
        }
    }
    @FunctionalInterface public interface Converter {
        /** Must validate complete cross-scope geometry, contents and identity custody. */
        Converted convert(CompoundTag source, int sourceVersion, HolderLookup.Provider registries);
    }
    private record Prepared(CompoundTag descriptor, byte[] descriptorBytes, byte[] first, byte[] second) {}
    private static final long NBT_LIMIT = 256L * 1024 * 1024;
    private static final String PREPARED = "prepared.dat", COMMITTED = "committed.dat";

    public static <A extends SavedData, B extends SavedData> Pair<A, B> load(
            MinecraftServer server, SavedDataType<A> first, SavedDataType<B> second,
            String originalFile, String transaction, String converterVersion, Converter converter) {
        if (!server.isSameThread())
            throw new IllegalStateException("Original paired migration requires its actual server thread");
        var storage = server.overworld().getDataStorage();
        return load(storage, server.getWorldPath(LevelResource.ROOT).resolve("data"),
            NeoForgeSavedDataImport.nativeDirectory(storage), server.registryAccess(), first, second,
            originalFile, transaction, converterVersion, converter);
    }

    /** Accepts an actual independent native storage for genuine on-disk regression cases. */
    public static <A extends SavedData, B extends SavedData> Pair<A, B> load(
            SavedDataStorage storage, Path dataDirectory, HolderLookup.Provider registries,
            SavedDataType<A> first, SavedDataType<B> second, String originalFile,
            String transaction, String converterVersion, Converter converter) {
        return load(storage, dataDirectory, dataDirectory, registries, first, second, originalFile,
            transaction, converterVersion, converter);
    }

    /** Keeps the original global source separate from the actual native owner's targets and journal. */
    public static <A extends SavedData, B extends SavedData> Pair<A, B> load(
            SavedDataStorage storage, Path sourceDataDirectory, Path targetDataDirectory,
            HolderLookup.Provider registries, SavedDataType<A> first, SavedDataType<B> second,
            String originalFile, String transaction, String converterVersion, Converter converter) {
        if (first.equals(second) || !transaction.matches("[a-z0-9][a-z0-9_.-]*"))
            throw new IllegalArgumentException("Paired migration requires distinct native types and a stable journal name");
        Path sourceDirectory = sourceDataDirectory.toAbsolutePath().normalize();
        Path directory = targetDataDirectory.toAbsolutePath().normalize();
        if (!directory.equals(NeoForgeSavedDataImport.nativeDirectory(storage)))
            throw new IllegalArgumentException("Paired SavedData targets must use their actual native owner's folder");
        Path source = sourceDirectory.resolve(originalFile).normalize();
        Path targetA = first.id().withSuffix(".dat").resolveAgainst(directory).normalize();
        Path targetB = second.id().withSuffix(".dat").resolveAgainst(directory).normalize();
        Path journal = directory.resolve("create").resolve(".neoforge-pair-" + transaction);
        if (!source.getParent().equals(sourceDirectory) || !targetA.startsWith(directory) || !targetB.startsWith(directory)
                || targetA.equals(source) || targetB.equals(source) || targetA.equals(targetB))
            throw new IllegalArgumentException("Paired migration must retain its actual original/native file scopes");
        synchronized (storage) {
            var currentA = cached(storage, first);
            var currentB = cached(storage, second);
            if (currentA != null && currentB != null) return new Pair<>(currentA, currentB);
            try {
                if (exists(journal.resolve(COMMITTED))) {
                    // Native owners can legitimately have changed after the committed conversion.
                    var prepared = descriptor(journal, first, second, originalFile, converterVersion);
                    if (!MessageDigest.isEqual(prepared, Files.readAllBytes(journal.resolve(COMMITTED))))
                        throw invalid("Paired migration commit has different source/target ownership");
                    requireBothOwners(storage, first, second, targetA, targetB);
                    return nativePair(storage, registries, first, second, targetA, targetB);
                }
                if (exists(journal.resolve(PREPARED))) {
                    var prepared = resume(journal, source, first, second, originalFile, converterVersion);
                    return publish(storage, registries, first, second, source, targetA, targetB, journal, prepared);
                }
                boolean ownerA = currentA != null || exists(targetA);
                boolean ownerB = currentB != null || exists(targetB);
                if (ownerA && ownerB)
                    return nativePair(storage, registries, first, second, targetA, targetB);
                if (!exists(source))
                    return new Pair<>(storage.computeIfAbsent(first), storage.computeIfAbsent(second));
                if (ownerA || ownerB)
                    throw invalid("Existing incomplete native scope cannot be combined with unrelated original owners");

                byte[] original = Files.readAllBytes(source);
                var envelope = read(original);
                keys(envelope, "SavedData envelope", "DataVersion", "data");
                if (integer(envelope, "DataVersion") != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
                    throw invalid("Unsupported original paired SavedData version");
                var converted = converter.convert(compound(envelope, "data").copy(),
                    NeoForgeSavedDataImport.SOURCE_DATA_VERSION, registries);
                var ops = registries.createSerializationContext(NbtOps.INSTANCE);
                // Both genuine native values must fully decode and re-encode before any journal/file owner exists.
                A candidateA = first.codec().parse(ops, converted.first).getOrThrow();
                B candidateB = second.codec().parse(ops, converted.second).getOrThrow();
                byte[] bytesA = envelope(first, candidateA, registries);
                byte[] bytesB = envelope(second, candidateB, registries);
                currentA = cached(storage, first);
                currentB = cached(storage, second);
                if (currentA != null && currentB != null) return new Pair<>(currentA, currentB);
                if (currentA != null || currentB != null || exists(targetA) || exists(targetB))
                    throw invalid("Native ownership changed while preparing the paired conversion");
                String sourceHash = digest(original);
                if (!sourceHash.equals(digest(Files.readAllBytes(source))))
                    throw invalid("Original paired SavedData changed during conversion preparation");
                var description = new CompoundTag();
                description.putInt("Format", 1);
                description.putInt("SourceDataVersion", NeoForgeSavedDataImport.SOURCE_DATA_VERSION);
                description.putString("Converter", converterVersion);
                description.putString("Source", originalFile);
                description.putString("SourceSha256", sourceHash);
                description.putString("FirstType", first.id().toString());
                description.putString("SecondType", second.id().toString());
                description.putString("FirstSha256", digest(bytesA));
                description.putString("SecondSha256", digest(bytesB));
                byte[] descriptionBytes = compressed(description);
                Files.createDirectories(journal);
                requireAbsentOrExact(journal.resolve(digest(bytesA) + ".dat"), bytesA);
                requireAbsentOrExact(journal.resolve(digest(bytesB) + ".dat"), bytesB);
                // Durable intent is last. A crash before this point has established neither target owner.
                requireAbsentOrExact(journal.resolve(PREPARED), descriptionBytes);
                forceDirectory(journal);
                forceDirectory(journal.getParent());
                forceDirectory(directory);
                return publish(storage, registries, first, second, source, targetA, targetB, journal,
                    new Prepared(description, descriptionBytes, bytesA, bytesB));
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot coordinate original SavedData scopes " + first.id() + " and " + second.id(), failure);
            }
        }
    }

    private static <A extends SavedData, B extends SavedData> Pair<A, B> publish(
            SavedDataStorage storage, HolderLookup.Provider registries, SavedDataType<A> first,
            SavedDataType<B> second, Path source, Path targetA, Path targetB, Path journal,
            Prepared prepared) throws IOException {
        // Repeat actual decoding on resume; a stored blob is not a fabricated ownership receipt.
        A candidateA = decode(prepared.first, registries, first);
        B candidateB = decode(prepared.second, registries, second);
        var currentA = cached(storage, first);
        var currentB = cached(storage, second);
        if (currentA != null && currentB != null) return new Pair<>(currentA, currentB);
        if (currentA != null || currentB != null)
            throw invalid("A native owner appeared before paired SavedData publication");
        if (!string(prepared.descriptor, "SourceSha256").equals(digest(Files.readAllBytes(source))))
            throw invalid("Prepared paired intent no longer describes the actual original file");
        requireAbsentOrExact(targetA, prepared.first);
        requireAbsentOrExact(targetB, prepared.second);
        // Both target links and their directory entries precede the commit marker and cache visibility.
        forceDirectory(targetA.getParent());
        if (!targetA.getParent().equals(targetB.getParent())) forceDirectory(targetB.getParent());
        requireAbsentOrExact(journal.resolve(COMMITTED), prepared.descriptorBytes);
        forceDirectory(journal);
        // No codecs, world callbacks, get/compute or I/O occur between these raw identity checks and set.
        currentA = cached(storage, first);
        currentB = cached(storage, second);
        if (currentA != null || currentB != null)
            throw invalid("Native paired cache ownership changed before its callback-free commit");
        storage.set(first, candidateA);
        storage.set(second, candidateB);
        candidateA.setDirty(false);
        candidateB.setDirty(false);
        return new Pair<>(candidateA, candidateB);
    }

    private static <A extends SavedData, B extends SavedData> byte[] descriptor(
            Path journal, SavedDataType<A> first, SavedDataType<B> second,
            String originalFile, String converterVersion) throws IOException {
        byte[] bytes = Files.readAllBytes(journal.resolve(PREPARED));
        var data = read(bytes);
        keys(data, "paired intent", "Format", "SourceDataVersion", "Converter", "Source", "SourceSha256",
            "FirstType", "SecondType", "FirstSha256", "SecondSha256");
        if (integer(data, "Format") != 1
                || integer(data, "SourceDataVersion") != NeoForgeSavedDataImport.SOURCE_DATA_VERSION
                || !string(data, "Converter").equals(converterVersion)
                || !string(data, "Source").equals(originalFile)
                || !string(data, "FirstType").equals(first.id().toString())
                || !string(data, "SecondType").equals(second.id().toString()))
            throw invalid("Unknown/different paired SavedData migration intent");
        for (var field : new String[] {"SourceSha256", "FirstSha256", "SecondSha256"})
            if (!string(data, field).matches("[a-f0-9]{64}")) throw invalid("Malformed paired intent content hash");
        return bytes;
    }

    private static <A extends SavedData, B extends SavedData> Prepared resume(
            Path journal, Path source, SavedDataType<A> first, SavedDataType<B> second,
            String originalFile, String converterVersion) throws IOException {
        byte[] descriptorBytes = descriptor(journal, first, second, originalFile, converterVersion);
        var description = read(descriptorBytes);
        if (!string(description, "SourceSha256").equals(digest(Files.readAllBytes(source))))
            throw invalid("Original paired file changed or disappeared after durable preparation");
        byte[] bytesA = Files.readAllBytes(journal.resolve(string(description, "FirstSha256") + ".dat"));
        byte[] bytesB = Files.readAllBytes(journal.resolve(string(description, "SecondSha256") + ".dat"));
        if (!string(description, "FirstSha256").equals(digest(bytesA))
                || !string(description, "SecondSha256").equals(digest(bytesB)))
            throw invalid("Prepared paired native content has changed");
        return new Prepared(description, descriptorBytes, bytesA, bytesB);
    }

    private static <A extends SavedData, B extends SavedData> void requireBothOwners(
            SavedDataStorage storage, SavedDataType<A> first, SavedDataType<B> second,
            Path targetA, Path targetB) {
        if (cached(storage, first) == null && !exists(targetA)
                || cached(storage, second) == null && !exists(targetB))
            throw invalid("A committed native scope is missing; stale prepared contents cannot replace it");
    }

    private static <A extends SavedData, B extends SavedData> Pair<A, B> nativePair(
            SavedDataStorage storage, HolderLookup.Provider registries, SavedDataType<A> first,
            SavedDataType<B> second, Path targetA, Path targetB) throws IOException {
        A actualA = cached(storage, first);
        B actualB = cached(storage, second);
        A decodedA = actualA == null ? decode(Files.readAllBytes(targetA), registries, first) : null;
        B decodedB = actualB == null ? decode(Files.readAllBytes(targetB), registries, second) : null;
        // A decoder may legitimately publish the winning actual native owner. Never replace it.
        actualA = cached(storage, first);
        actualB = cached(storage, second);
        if (actualA == null && decodedA == null || actualB == null && decodedB == null)
            throw invalid("A previously current native owner disappeared during paired native decoding");
        if (actualA == null) { storage.set(first, decodedA); decodedA.setDirty(false); actualA = decodedA; }
        if (actualB == null) { storage.set(second, decodedB); decodedB.setDirty(false); actualB = decodedB; }
        return new Pair<>(actualA, actualB);
    }

    @SuppressWarnings("unchecked") private static <T extends SavedData> T cached(
            SavedDataStorage storage, SavedDataType<T> type) {
        var slot = ((SavedDataStorageAccessor) storage).create$getCache().get(type);
        return slot == null || slot.isEmpty() ? null : (T) slot.get();
    }

    private static <T extends SavedData> T decode(byte[] bytes, HolderLookup.Provider registries,
            SavedDataType<T> type) throws IOException {
        var data = read(bytes);
        return type.codec().parse(registries.createSerializationContext(NbtOps.INSTANCE), compound(data, "data")).getOrThrow();
    }

    private static <T extends SavedData> byte[] envelope(SavedDataType<T> type, T actual,
            HolderLookup.Provider registries) throws IOException {
        var data = new CompoundTag();
        data.put("data", type.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), actual).getOrThrow());
        NbtUtils.addCurrentDataVersion(data);
        return compressed(data);
    }
    private static byte[] compressed(CompoundTag data) throws IOException {
        var output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(data, output);
        return output.toByteArray();
    }
    private static CompoundTag read(byte[] bytes) throws IOException {
        var input = new ByteArrayInputStream(bytes);
        var quota = NbtAccounter.create(NBT_LIMIT);
        return bytes.length >= 2 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139
            ? NbtIo.readCompressed(input, quota) : NbtIo.read(new DataInputStream(input), quota);
    }
    private static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }
    private static void requireAbsentOrExact(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "create-neoforge-pair-", ".tmp");
        try {
            try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            try { Files.createLink(target, temporary); }
            catch (FileAlreadyExistsException owned) {
                if (!MessageDigest.isEqual(Files.readAllBytes(target), bytes))
                    throw invalid("Present native file/intent has different original ownership: " + target);
            } catch (UnsupportedOperationException unsupported) {
                throw new IOException("Filesystem cannot establish an absent native owner: " + target, unsupported);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        catch (UnsupportedOperationException unsupported) {
            throw new IOException("Filesystem cannot durably order paired SavedData publication: " + directory, unsupported);
        }
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private NeoForgeSavedDataPairImport() {}
}

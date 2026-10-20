package com.simibubi.create.foundation.utility;

import com.simibubi.create.Create;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.storage.LevelResource;

/** A failed original import stays exceptional; its native source never becomes an empty load. */
public final class NeoForgeOriginalImportFailure extends RuntimeException {
    /** Read-only bridge to EntityStorage's actual native delegate. */
    public interface EntityStorageAccess {
        SimpleRegionStorage create$originalImportRegionStorage();
    }

    private final SimpleRegionStorage storage;
    private final ServerLevel level;
    private final ChunkPos requested;
    private final CompoundTag original;
    private final UUID reportId;
    private final AtomicBoolean saveAbortReported = new AtomicBoolean();

    private NeoForgeOriginalImportFailure(SimpleRegionStorage storage, ServerLevel level,
            ChunkPos requested, CompoundTag original, RuntimeException cause, UUID reportId) {
        super(original == null
            ? "Native storage access aborted after an original Create import refusal at " + requested
            : "Original Create import refused before native admission at " + requested, cause);
        this.storage = Objects.requireNonNull(storage);
        this.level = Objects.requireNonNull(level);
        this.requested = Objects.requireNonNull(requested);
        this.original = original == null ? null : original.copy();
        this.reportId = Objects.requireNonNull(reportId);
    }

    /** The caller must supply the actual untouched 3955 envelope, actual requested chunk and owner. */
    public static NeoForgeOriginalImportFailure refuse(SimpleRegionStorage actualStorage, ServerLevel actualLevel,
            ChunkPos actualRequested, CompoundTag actualOriginal, RuntimeException cause) {
        Objects.requireNonNull(actualOriginal);
        if (!(actualOriginal.get("DataVersion") instanceof IntTag version)
                || version.intValue() != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw new IllegalArgumentException("An original import refusal needs its genuine 3955 source envelope", cause);
        var failure = new NeoForgeOriginalImportFailure(actualStorage, actualLevel, actualRequested,
            actualOriginal, cause, UUID.randomUUID());
        var owner = (NeoForgeOriginalImportFailureOwner) (Object) actualStorage;
        boolean first = owner.create$recordOriginalImportFailure(failure);
        // Real native running=false; halt(true) would join the server/deserializer caller.
        actualLevel.getServer().halt(false);
        if (first) failure.reportOriginal();
        return failure;
    }

    public static NeoForgeOriginalImportFailure first(SimpleRegionStorage actualStorage) {
        return ((NeoForgeOriginalImportFailureOwner) (Object) actualStorage).create$firstOriginalImportFailure();
    }

    /** No new source is invented: this cancellation retains the first actual original as its cause. */
    public NeoForgeOriginalImportFailure forNativeLoad(ChunkPos actualRequested) {
        if (requested.equals(actualRequested)) return this;
        return new NeoForgeOriginalImportFailure(storage, level, actualRequested, null, this, reportId);
    }

    public static boolean abortEntitySave(EntityPersistentStorage<?> actualStorage) {
        if (!(actualStorage instanceof EntityStorage nativeStorage)) return false;
        var delegate = ((EntityStorageAccess) (Object) nativeStorage).create$originalImportRegionStorage();
        var failure = first(delegate);
        // isShutdown() means the server thread is dead in 26.3; isRunning() reads the real volatile flag.
        if (failure == null || failure.storage != delegate || failure.level.getServer().isRunning()) return false;
        failure.reportAbortedSave();
        return true;
    }

    public static boolean abortChunkSave(SimpleRegionStorage actualStorage, ServerLevel actualLevel) {
        var failure = first(actualStorage);
        if (failure == null || failure.storage != actualStorage || failure.level != actualLevel
                || actualLevel.getServer().isRunning()) return false;
        failure.reportAbortedSave();
        return true;
    }

    public static NeoForgeOriginalImportFailure owned(Throwable thrown, SimpleRegionStorage actualStorage,
            ServerLevel actualLevel) {
        return owned(thrown, actualStorage, actualLevel, null);
    }

    /** Recognize only our exact actual storage/world/request; unrelated native errors keep native behavior. */
    public static NeoForgeOriginalImportFailure owned(Throwable thrown, SimpleRegionStorage actualStorage,
            ServerLevel actualLevel, ChunkPos actualRequested) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = thrown; current != null && visited.size() < 32 && visited.add(current);
                current = current.getCause()) {
            if (current instanceof NeoForgeOriginalImportFailure failure && failure.storage == actualStorage
                    && failure.level == actualLevel
                    && (actualRequested == null || failure.requested.equals(actualRequested)))
                return failure;
        }
        return null;
    }

    public SimpleRegionStorage storage() { return storage; }
    public ServerLevel level() { return level; }
    public ChunkPos requested() { return requested; }
    public UUID reportId() { return reportId; }
    public Optional<CompoundTag> originalSnapshot() {
        return original == null ? Optional.empty() : Optional.of(original.copy());
    }

    /** The fence refuses writes before a supplier/serializer or delete can reach the native I/O worker. */
    public void reportAbortedSave() {
        if (saveAbortReported.compareAndSet(false, true))
            Create.LOGGER.error("Aborted native saves for {} after original import refusal {}. "
                + "The failed source remains on disk; native load status and entity material were not replaced.",
                storage.storageInfo(), reportId);
    }

    private void reportOriginal() {
        Create.LOGGER.error("Original Create import {} refused for {} at {}; stopping the native server. "
            + "The original record remains authoritative and no empty replacement is admitted.",
            reportId, storage.storageInfo(), requested, this);
        try {
            Path directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("create-import-refusals");
            Files.createDirectories(directory);
            Path originalPath = directory.resolve(reportId + ".original.nbt");
            NbtIo.writeCompressed(original.copy(), originalPath);
            var report = CrashReport.forThrowable(this, "Create original import refused before native admission");
            var details = report.addCategory("Original native source");
            details.setDetail("Storage", storage.storageInfo());
            details.setDetail("Actual level", level.dimension());
            details.setDetail("Actual requested chunk", requested);
            details.setDetail("Source DataVersion", NeoForgeSavedDataImport.SOURCE_DATA_VERSION);
            details.setDetail("Original payload copy", originalPath);
            details.setDetail("Save policy", "Aborted; failed source record stays on disk; no empty/replaceable chunk");
            if (!report.saveToFile(directory.resolve(reportId + ".txt"), ReportType.CHUNK_IO_ERROR))
                Create.LOGGER.error("Could not write original import refusal report {}", reportId);
        } catch (Exception reportingFailure) {
            addSuppressed(reportingFailure);
            Create.LOGGER.error("Could not archive original import refusal {}; the native source remains on disk",
                reportId, reportingFailure);
        }
    }
}

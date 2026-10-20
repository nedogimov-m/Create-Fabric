package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailure;
import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailureOwner;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SimpleRegionStorage.class)
public abstract class NeoForgeImportRegionStorageFailureMixin implements NeoForgeOriginalImportFailureOwner {
    @Unique private final AtomicReference<NeoForgeOriginalImportFailure> create$originalImportFailure = new AtomicReference<>();

    @Override public NeoForgeOriginalImportFailure create$firstOriginalImportFailure() {
        return create$originalImportFailure.get();
    }
    @Override public boolean create$recordOriginalImportFailure(NeoForgeOriginalImportFailure failure) {
        if (failure.storage() != (Object) this) throw new IllegalArgumentException("Foreign native storage failure");
        return create$originalImportFailure.compareAndSet(null, failure);
    }

    @WrapMethod(method="read(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Optional<CompoundTag>> create$refuseFurtherReads(ChunkPos requested,
            Operation<CompletableFuture<Optional<CompoundTag>>> original) {
        var failure = create$originalImportFailure.get();
        return failure == null ? original.call(requested)
            : CompletableFuture.failedFuture(failure.forNativeLoad(requested));
    }

    @WrapMethod(method="write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> create$refuseTagWrite(ChunkPos requested, CompoundTag tag,
            Operation<CompletableFuture<Void>> original) {
        var failure = create$originalImportFailure.get();
        if (failure == null) return original.call(requested, tag);
        failure.reportAbortedSave();
        return CompletableFuture.failedFuture(failure.forNativeLoad(requested));
    }

    @WrapMethod(method="write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> create$refuseSupplierOrDeleteWrite(ChunkPos requested, Supplier<CompoundTag> tag,
            Operation<CompletableFuture<Void>> original) {
        var failure = create$originalImportFailure.get();
        if (failure == null) return original.call(requested, tag);
        failure.reportAbortedSave();
        return CompletableFuture.failedFuture(failure.forNativeLoad(requested));
    }
}

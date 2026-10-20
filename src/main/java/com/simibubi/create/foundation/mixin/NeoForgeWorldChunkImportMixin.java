package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailure;
import com.simibubi.create.foundation.utility.NeoForgeWorldChunkImport;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Genuine raw source header and requested storage owner precede every native chunk parser/factory. */
@Mixin(ChunkMap.class)
public abstract class NeoForgeWorldChunkImportMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow private CompoundTag upgradeChunkTag(CompoundTag source) { throw new AssertionError(); }
    @WrapOperation(method="readChunk", at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap;read(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;"), require=1)
    private CompletableFuture<Optional<CompoundTag>> create$importOriginalTerrain(ChunkMap actualStorage, ChunkPos requested,
            Operation<CompletableFuture<Optional<CompoundTag>>> original) {
        return original.call(actualStorage, requested).thenApply(raw -> {
            var storage = (SimpleRegionStorage) actualStorage;
            var previous = NeoForgeOriginalImportFailure.first(storage);
            if (previous != null) throw previous.forNativeLoad(requested);
            if (raw.isEmpty() || !(raw.get().get("DataVersion") instanceof IntTag version) || version.intValue() != 3955) return raw;
            try {
                if (!level.dimension().equals(storage.storageInfo().dimension())) throw new IllegalArgumentException("Actual terrain level/storage dimensions disagree");
                return Optional.of(NeoForgeWorldChunkImport.convert(raw.get(), requested, level, this::upgradeChunkTag));
            } catch (RuntimeException originalRefusal) {
                throw NeoForgeOriginalImportFailure.refuse(storage, level, requested, raw.get(), originalRefusal);
            }
        });
    }
}

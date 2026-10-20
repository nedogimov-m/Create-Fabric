package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailure;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class NeoForgeImportChunkLoadFailureMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method="handleChunkLoadFailure",at=@At("HEAD"),require=1)
    private void create$keepTaggedOriginalFailureExceptional(Throwable thrown, ChunkPos requested,
            CallbackInfoReturnable<ChunkAccess> callback) {
        var failure = NeoForgeOriginalImportFailure.owned(thrown, (SimpleRegionStorage) (Object) this, level, requested);
        if (failure != null) throw failure;
    }

    @Inject(method="save",at=@At("HEAD"),cancellable=true,require=1)
    private void create$keepAbortedChunksUnsaved(ChunkAccess chunk, CallbackInfoReturnable<Boolean> callback) {
        var storage = (SimpleRegionStorage) (Object) this;
        if (NeoForgeOriginalImportFailure.first(storage) == null) return;
        NeoForgeOriginalImportFailure.first(storage).reportAbortedSave();
        callback.setReturnValue(false);
    }

    /** Leave native close reachable even when an already-running save observes its own failed load future. */
    @WrapMethod(method="saveAllChunks")
    private void create$abortOnlyOwnedChunkSave(boolean flush, Operation<Void> original) {
        var storage = (SimpleRegionStorage) (Object) this;
        if (NeoForgeOriginalImportFailure.abortChunkSave(storage, level)) return;
        try {
            original.call(flush);
        } catch (RuntimeException failure) {
            if (NeoForgeOriginalImportFailure.owned(failure, storage, level) != null
                    && NeoForgeOriginalImportFailure.abortChunkSave(storage, level)) return;
            throw failure;
        }
    }
}

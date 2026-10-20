package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailure;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The native save-probe catch must not mark a refused original chunk replaceable. */
@Mixin(ChunkMap.class)
public abstract class NeoForgeWorldChunkReplaceableGuardMixin {
    @WrapOperation(method="isExistingChunkFull", at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap;markPositionReplaceable(Lnet/minecraft/world/level/ChunkPos;)V"), require=2)
    private void create$keepOriginalFailureUnreplaceable(ChunkMap actual, ChunkPos requested, Operation<Void> original) {
        var failure = NeoForgeOriginalImportFailure.first((SimpleRegionStorage) actual);
        if (failure != null) throw failure.forNativeLoad(requested);
        original.call(actual, requested);
    }
}

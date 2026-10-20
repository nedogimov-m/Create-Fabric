package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The manager can inspect its actual permanent storage without constructing or mutating an owner map. */
@Mixin(EntityStorage.class)
public abstract class NeoForgeImportEntityStorageFailureMixin implements NeoForgeOriginalImportFailure.EntityStorageAccess {
    @Shadow @Final private SimpleRegionStorage simpleRegionStorage;
    @Override public SimpleRegionStorage create$originalImportRegionStorage() { return simpleRegionStorage; }

    // EntityStorage otherwise changes its native empty cache and serializes before calling the region writer.
    @Inject(method="storeEntities",at=@At("HEAD"),cancellable=true,require=1)
    private void create$refuseOriginalSourceReplacement(ChunkEntities<Entity> entities, CallbackInfo callback) {
        var failure = NeoForgeOriginalImportFailure.first(simpleRegionStorage);
        if (failure == null) return;
        failure.reportAbortedSave();
        callback.cancel();
    }
}

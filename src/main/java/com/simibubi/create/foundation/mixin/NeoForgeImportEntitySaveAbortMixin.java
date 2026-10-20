package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailure;
import com.simibubi.create.foundation.utility.NeoForgePlayerRegionImport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.stream.Stream;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentEntitySectionManager.class)
public abstract class NeoForgeImportEntitySaveAbortMixin<T extends EntityAccess> {
    @Shadow @Final private EntityPersistentStorage<T> permanentStorage;

    @WrapOperation(method="processPendingLoads",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/ChunkEntities;getEntities()Ljava/util/stream/Stream;"),require=1)
    private Stream<T> create$recheckActualPlayerRegionBatch(ChunkEntities<T> actualBatch,Operation<Stream<T>> original) {
        NeoForgePlayerRegionImport.beforeAdmission(permanentStorage,actualBatch);
        return original.call(actualBatch);
    }

    @Inject(method="saveAll",at=@At("HEAD"),cancellable=true,require=1)
    private void create$abortFailedOriginalSaveAtEntry(CallbackInfo callback) {
        if (NeoForgeOriginalImportFailure.abortEntitySave(permanentStorage)) callback.cancel();
    }

    // The preceding native flush runs actual deserializers; the first failure can occur inside this saveAll.
    @Inject(method="saveAll",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;processPendingLoads()V",shift=At.Shift.AFTER),cancellable=true,require=1)
    private void create$abortAfterActualPendingLoadBoundary(CallbackInfo callback) {
        if (NeoForgeOriginalImportFailure.abortEntitySave(permanentStorage)) callback.cancel();
    }
}

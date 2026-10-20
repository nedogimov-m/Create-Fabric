package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.trains.entity.CarriageNativeSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Actual store completes before native unload. A partial refusal must keep the native chunk queued. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class CarriageNativeSourceMixin {
    @WrapMethod(method="storeChunkSections")
    private boolean create$retainRefusedNativeSource(long chunk,java.util.function.Consumer<EntityAccess> unload,Operation<Boolean> original){try(var batch=CarriageNativeSources.batch(this,chunk)){return original.call(chunk,unload)&&!batch.refused();}}
    @WrapOperation(method="lambda$storeChunkSections$0",at=@At(value="INVOKE",target="Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private static java.util.stream.Stream<EntityAccess> create$visitActualManagedSourcesForNativeUnload(java.util.stream.Stream<EntityAccess> entries,java.util.function.Predicate<EntityAccess> eligible,Operation<java.util.stream.Stream<EntityAccess>> original){return original.call(entries,(java.util.function.Predicate<EntityAccess>)(entry->eligible.test(entry)||CarriageNativeSources.includeManagedSource(entry)));}
    @WrapOperation(method="storeChunkSections",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/EntityPersistentStorage;storeEntities(Lnet/minecraft/world/level/entity/ChunkEntities;)V"))
    private void create$witnessActualStorageSources(EntityPersistentStorage<EntityAccess> storage,ChunkEntities<EntityAccess> chunk,Operation<Void> original){try(var source=CarriageNativeSources.storage(this,chunk.getEntities().toList())){original.call(storage,chunk);}}
    @WrapMethod(method="unloadEntity")
    private void create$payActualSourcesBeforeNativeUnload(EntityAccess actual,Operation<Void> original){try(var source=CarriageNativeSources.unloading(this,actual)){if(!CarriageNativeSources.prepareUnload(actual)){CarriageNativeSources.refuse();return;}original.call(actual);}}
    @WrapOperation(method="unloadEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/EntityAccess;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void create$skipOnlyAlreadyPaidActualRemoval(EntityAccess access,Entity.RemovalReason reason,Operation<Void> original){if(access instanceof Entity actual&&CarriageNativeSources.alreadyRetired(actual))return;original.call(access,reason);}
}

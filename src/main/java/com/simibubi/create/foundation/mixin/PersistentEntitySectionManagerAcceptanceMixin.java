package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native 26.3 order: UUID, actual section insertion, level callback, onCreated, tracking. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerAcceptanceMixin {
    @Inject(method="addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",at=@At("HEAD"),cancellable=true)
    private void create$validateActualSource(EntityAccess entity,boolean loaded,CallbackInfoReturnable<Boolean> ci){
        if(!NativeEntityAcceptance.beforeNativeAdd(this,entity,loaded))ci.setReturnValue(false);
    }
    @Inject(method="addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/LevelCallback;onCreated(Ljava/lang/Object;)V",shift=At.Shift.AFTER),cancellable=true)
    private void create$keepRemovedAcceptedEndpointAbsent(EntityAccess entity,boolean loaded,CallbackInfoReturnable<Boolean> ci){
        if(NativeEntityAcceptance.removedAfterNativeCreation(this,entity))ci.setReturnValue(true);
    }
    @Redirect(method="addEntityUuid(Lnet/minecraft/world/level/entity/EntityAccess;)Z",at=@At(value="INVOKE",target="Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean create$recordActualUuidReservation(Set<UUID> owner,Object value,EntityAccess entity){
        var uuid=(UUID)value;boolean accepted=owner.add(uuid);
        if(accepted)NativeEntityAcceptance.afterNativeUuidAdd(this,entity,owner,uuid);
        return accepted;
    }
    @Inject(method="addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/EntitySection;add(Lnet/minecraft/world/level/entity/EntityAccess;)V"),cancellable=true)
    private void create$validateAtActualSection(EntityAccess entity,boolean loaded,CallbackInfoReturnable<Boolean> ci){
        if(!NativeEntityAcceptance.beforeNativeSectionAdd(this,entity))ci.setReturnValue(false);
    }
    @Redirect(method="addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/EntitySection;add(Lnet/minecraft/world/level/entity/EntityAccess;)V"))
    private void create$commitAtActualSection(EntitySection<EntityAccess> section,EntityAccess entity){
        try{section.add(entity);}finally{NativeEntityAcceptance.afterNativeSectionAdd(this,section,entity);}
    }
}

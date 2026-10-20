package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual native parent-list adoption precedes mount gameEvent and subclass post-adoption callbacks. */
@Mixin(Entity.class)
public abstract class CarriagePlayerReferenceEdgeMixin {
    @WrapMethod(method="startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z")
    private boolean create$prepareExactPassengerPublication(Entity parent,boolean force,boolean event,Operation<Boolean> original){try(var scope=CarriagePlayerReferenceTransitions.prepareMount((Entity)(Object)this,parent)){return original.call(parent,force,event)&&!scope.refused();}}
    @Inject(method="addPassenger",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;passengers:Lcom/google/common/collect/ImmutableList;",opcode=Opcodes.PUTFIELD,shift=At.Shift.BEFORE),cancellable=true)
    private void create$validateBeforeActualParentList(Entity passenger,CallbackInfo callback){if(!CarriagePlayerReferenceTransitions.beforeNativeParentList((Entity)(Object)this,passenger))callback.cancel();}
    @Inject(method="addPassenger",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;passengers:Lcom/google/common/collect/ImmutableList;",opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER))
    private void create$publishBeforeNativeMountCallbacks(Entity passenger,CallbackInfo callback){CarriagePlayerReferenceTransitions.afterNativeParentList((Entity)(Object)this,passenger);}
    @Inject(method="removeVehicle",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;vehicle:Lnet/minecraft/world/entity/Entity;",opcode=Opcodes.PUTFIELD,shift=At.Shift.BEFORE))
    private void create$supersedeBeforeNativeOldParentCallbacks(CallbackInfo callback){CarriagePlayerReferenceTransitions.beforeNativeUnlink((Entity)(Object)this);}
    @Inject(method="removeVehicle",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;vehicle:Lnet/minecraft/world/entity/Entity;",opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER))
    private void create$releaseActualVoluntaryNpcSourceView(CallbackInfo callback){com.simibubi.create.content.trains.entity.CarriageNpcMaterialOwners.afterActualUnlink((Entity)(Object)this);}
}

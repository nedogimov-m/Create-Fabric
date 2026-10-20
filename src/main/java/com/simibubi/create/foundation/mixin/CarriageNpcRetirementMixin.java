package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.trains.entity.CarriageNpcRetirement;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class CarriageNpcRetirementMixin {
    @Inject(method="setRemoved",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;removalReason:Lnet/minecraft/world/entity/Entity$RemovalReason;",opcode=Opcodes.PUTFIELD,shift=At.Shift.BEFORE),cancellable=true)
    private void create$prepareActualNpcPayment(Entity.RemovalReason reason,CallbackInfo callback){if(!CarriageNpcRetirement.beforeNativeReason((Entity)(Object)this,reason))callback.cancel();}
    @Inject(method="setRemoved",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;removalReason:Lnet/minecraft/world/entity/Entity$RemovalReason;",opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER))
    private void create$payBeforeNativeNpcRemovalCallbacks(Entity.RemovalReason reason,CallbackInfo callback){CarriageNpcRetirement.afterNativeReason((Entity)(Object)this,reason);com.simibubi.create.content.trains.entity.CarriageNpcMaterialOwners.afterActualReason((Entity)(Object)this,reason);}
}

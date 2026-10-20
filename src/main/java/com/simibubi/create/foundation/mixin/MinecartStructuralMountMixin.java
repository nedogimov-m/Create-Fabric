package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A genuinely occupied structural coupling's second cart cannot gain an unrelated rider. */
@Mixin(Entity.class)
public abstract class MinecartStructuralMountMixin {
    @Inject(method="startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",at=@At("HEAD"),cancellable=true)
    private void create$protectActualStructuralCart(Entity vehicle,boolean force,boolean sendEvent,CallbackInfoReturnable<Boolean> ci){
        if(vehicle instanceof AbstractMinecart cart){
            var attached=((AttachmentTarget)cart).getAttached(MinecartController.ATTACHMENT);
            if(attached!=null&&(attached.nativeBaseIsInactive()||!((Object)this instanceof AbstractContraptionEntity)&&attached.isCoupledThroughContraption()))ci.setReturnValue(false);
        }
    }
}

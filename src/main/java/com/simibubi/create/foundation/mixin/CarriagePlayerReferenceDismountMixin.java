package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native safe dismount has one computed destination; explicit player teleports still supersede. */
@Mixin(LivingEntity.class)
public abstract class CarriagePlayerReferenceDismountMixin {
    @WrapOperation(method="dismountVehicle(Lnet/minecraft/world/entity/Entity;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;dismountTo(DDD)V"))
    private void create$preserveExactNativeDismountTeleport(LivingEntity actual,double x,double y,double z,Operation<Void> original,@Local(argsOnly=true) Entity source){
        if(!(actual instanceof ServerPlayer player)){original.call(actual,x,y,z);return;}
        try(var dismount=CarriagePlayerReferenceTransitions.nativeDismount(player,source,x,y,z)){original.call(actual,x,y,z);}
    }
}

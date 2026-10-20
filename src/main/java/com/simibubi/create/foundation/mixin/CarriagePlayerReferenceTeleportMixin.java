package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import java.util.Set;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** A later voluntary teleport wins; only the exact prepared internal destination preserves intent. */
@Mixin(ServerPlayer.class)
public abstract class CarriagePlayerReferenceTeleportMixin {
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",at=@At("HEAD"))
    private void create$supersedeOldIntentBeforeExplicitTeleport(ServerLevel target,double x,double y,double z,Set<Relative> relative,float yaw,float pitch,boolean camera,CallbackInfoReturnable<Boolean> callback){if(relative.isEmpty())CarriagePlayerReferenceTransitions.beforeNativeTeleport((ServerPlayer)(Object)this,target,x,y,z);else CarriagePlayerReferenceTransitions.beforeExplicitRelativeTeleport((ServerPlayer)(Object)this);}
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at=@At("HEAD"))
    private void create$supersedeBeforeDirectNativeTransition(TeleportTransition transition,CallbackInfoReturnable<ServerPlayer> callback){var position=transition.position();if(transition.relatives().isEmpty())CarriagePlayerReferenceTransitions.beforeNativeTeleport((ServerPlayer)(Object)this,transition.newLevel(),position.x,position.y,position.z);else CarriagePlayerReferenceTransitions.beforeExplicitRelativeTeleport((ServerPlayer)(Object)this);}
    @Inject(method="teleportTo(DDD)V",at=@At("HEAD"))
    private void create$supersedeBeforeSameWorldTeleport(double x,double y,double z,CallbackInfo callback){var player=(ServerPlayer)(Object)this;CarriagePlayerReferenceTransitions.beforeNativeTeleport(player,player.level(),x,y,z);}
    @Inject(method="teleportRelative(DDD)V",at=@At("HEAD"))
    private void create$supersedeBeforeRelativeTeleport(double x,double y,double z,CallbackInfo callback){CarriagePlayerReferenceTransitions.beforeExplicitRelativeTeleport((ServerPlayer)(Object)this);}
    @WrapOperation(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;removePlayerImmediately(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void create$preserveOnlyExactNativeDimensionRemoval(ServerLevel source,ServerPlayer actual,Entity.RemovalReason reason,Operation<Void> original,@Local(argsOnly=true) TeleportTransition transition){try(var removal=CarriagePlayerReferenceTransitions.nativeDimensionRemoval(actual,source,transition)){original.call(source,actual,reason);}}
}



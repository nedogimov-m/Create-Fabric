package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.mounted.NativeCartLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VehicleEntity.class)
public abstract class MinecartReceiptVehicleDestroyMixin {
    @Inject(method="destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/Item;)V",at=@At("HEAD"),cancellable=true)
    private void create$noDuplicateBaseDrop(ServerLevel world,Item drop,CallbackInfo ci){if((Object)this instanceof AbstractMinecart cart&&NativeCartLifecycle.beforeDestroy(cart))ci.cancel();}
    @Inject(method="destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",at=@At("HEAD"),cancellable=true)
    private void create$actualPackedDestruction(ServerLevel world,DamageSource damage,CallbackInfo ci){if((Object)this instanceof AbstractMinecart cart&&NativeCartLifecycle.beforeDestroy(cart))ci.cancel();}
}

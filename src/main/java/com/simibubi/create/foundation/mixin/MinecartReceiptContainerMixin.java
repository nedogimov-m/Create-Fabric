package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.mounted.NativeCartLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Native raw slots really are empty; adapters refuse insertion, rather than accepting into a fake empty view. */
@Mixin(AbstractMinecartContainer.class)
public abstract class MinecartReceiptContainerMixin implements Container {
    private AbstractMinecartContainer create$cart(){return (AbstractMinecartContainer)(Object)this;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return !NativeCartLifecycle.inactive(create$cart());}
    @Override public boolean canTakeItem(Container destination,int slot,ItemStack stack){return !NativeCartLifecycle.inactive(create$cart());}
    @Inject(method="remove",at=@At("HEAD"),cancellable=true)
    private void create$beforeNativeContainerDrop(Entity.RemovalReason reason,CallbackInfo ci){if(NativeCartLifecycle.beforeRemove(create$cart(),reason))ci.cancel();}
    @Inject(method="destroy",at=@At("HEAD"),cancellable=true)
    private void create$beforeContainerDestruction(ServerLevel world,DamageSource damage,CallbackInfo ci){if(NativeCartLifecycle.beforeDestroy(create$cart()))ci.cancel();}
    @Inject(method="interact",at=@At("HEAD"),cancellable=true)
    private void create$noInactiveNativeMenu(Player player,InteractionHand hand,Vec3 point,CallbackInfoReturnable<InteractionResult> ci){if(NativeCartLifecycle.inactive(create$cart()))ci.setReturnValue(InteractionResult.FAIL);}
    @Inject(method="createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",at=@At("HEAD"),cancellable=true)
    private void create$noInactiveProvider(int id,Inventory inventory,Player player,CallbackInfoReturnable<AbstractContainerMenu> ci){if(NativeCartLifecycle.inactive(create$cart()))ci.setReturnValue(null);}
    @Inject(method="stillValid",at=@At("HEAD"),cancellable=true)
    private void create$closeOldNativeMenu(Player player,CallbackInfoReturnable<Boolean> ci){if(NativeCartLifecycle.inactive(create$cart()))ci.setReturnValue(false);}
}

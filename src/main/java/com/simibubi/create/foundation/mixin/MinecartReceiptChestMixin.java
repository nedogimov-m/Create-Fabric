package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.mounted.NativeCartLifecycle;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartChest.class)
public abstract class MinecartReceiptChestMixin {
    @Inject(method="interact",at=@At("HEAD"),cancellable=true)
    private void create$noInactiveChestMenu(Player player,InteractionHand hand,Vec3 point,CallbackInfoReturnable<InteractionResult> ci){if(NativeCartLifecycle.inactive((MinecartChest)(Object)this))ci.setReturnValue(InteractionResult.FAIL);}
    @Inject(method="createMenu",at=@At("HEAD"),cancellable=true)
    private void create$noInactiveDirectMenu(int id,Inventory inventory,CallbackInfoReturnable<AbstractContainerMenu> ci){if(NativeCartLifecycle.inactive((MinecartChest)(Object)this))ci.setReturnValue(null);}
}

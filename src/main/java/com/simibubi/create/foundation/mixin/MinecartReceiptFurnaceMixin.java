package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.mounted.NativeCartLifecycle;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MinecartFurnace.class)
public abstract class MinecartReceiptFurnaceMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void create$suspendActualInactiveFurnace(CallbackInfo ci){if(NativeCartLifecycle.suspendNativeTick((MinecartFurnace)(Object)this))ci.cancel();}
    @Inject(method="interact",at=@At("HEAD"),cancellable=true)
    private void create$noInactiveNativeFuel(Player player,InteractionHand hand,Vec3 point,CallbackInfoReturnable<InteractionResult> ci){if(NativeCartLifecycle.inactive((MinecartFurnace)(Object)this))ci.setReturnValue(InteractionResult.FAIL);}
    @Inject(method="addFuel",at=@At("HEAD"),cancellable=true)
    private void create$noInactiveFuelInsertion(Vec3 push,ItemStack fuel,CallbackInfoReturnable<Boolean> ci){if(NativeCartLifecycle.inactive((MinecartFurnace)(Object)this))ci.setReturnValue(false);}
}

package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.mounted.NativeCartLifecycle;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecart.class)
public abstract class MinecartReceiptRideableMixin {
    @Inject(method="interact",at=@At("HEAD"),cancellable=true)
    private void create$noInactiveNativeRider(Player player,InteractionHand hand,Vec3 point,CallbackInfoReturnable<InteractionResult> ci){if(NativeCartLifecycle.inactive((Minecart)(Object)this))ci.setReturnValue(InteractionResult.FAIL);}
}

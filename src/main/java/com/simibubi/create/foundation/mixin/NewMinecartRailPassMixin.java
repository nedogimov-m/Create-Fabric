package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.minecart.NativeMinecartHooks;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One call per actual native traversed rail iteration, including fast current minecart movement. */
@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartRailPassMixin extends MinecartBehavior {
    protected NewMinecartRailPassMixin(AbstractMinecart cart){super(cart);}
    @ModifyReturnValue(method="calculateTrackSpeed",at=@At("RETURN"))
    private Vec3 create$sourceRailPass(Vec3 actualComputedSpeed,@Local(argsOnly=true) BlockPos actualTraversedRail){
        return NativeMinecartHooks.railSpeed(minecart,actualTraversedRail,actualComputedSpeed);
    }
}

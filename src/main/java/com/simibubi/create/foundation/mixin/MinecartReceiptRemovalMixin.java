package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.mounted.NativeCartLifecycle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MinecartReceiptRemovalMixin {
    @Inject(method="remove",at=@At("HEAD"),cancellable=true)
    private void create$retainActualPackedOwner(Entity.RemovalReason reason,CallbackInfo ci){if((Object)this instanceof AbstractMinecart cart&&NativeCartLifecycle.beforeRemove(cart,reason))ci.cancel();}
}

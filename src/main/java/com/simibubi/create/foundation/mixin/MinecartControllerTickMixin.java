package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.minecart.NativeMinecartHooks;
import com.simibubi.create.content.contraptions.minecart.capability.NeoForgeMinecartImport;
import net.minecraft.world.level.storage.ValueInput;
import com.simibubi.create.content.contraptions.mounted.NativeCartLifecycle;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class MinecartControllerTickMixin {
    // Fabric reads native attachments before invoking this actual native method from Entity.load.
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void create$importNeoForgeController(ValueInput input,CallbackInfo ci){NeoForgeMinecartImport.migrate((AbstractMinecart)(Object)this,input);}
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void create$nativeControllerPreTick(CallbackInfo ci){var actual=(AbstractMinecart)(Object)this;if(NativeCartLifecycle.suspendNativeTick(actual)){ci.cancel();return;}NativeMinecartHooks.preTick(actual);if(NativeCartLifecycle.suspendMissingMountedOwner(actual))ci.cancel();}
}

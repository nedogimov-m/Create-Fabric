package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.fluids.FluidFog;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FluidFogMixin {
    @Inject(method="setupFog",at=@At("RETURN"))
    private void create$fluidFog(Camera camera,int distance,DeltaTracker delta,float darkness,ClientLevel level,CallbackInfoReturnable<FogData> callback){FluidFog.apply(camera,level,callback.getReturnValue());com.simibubi.create.content.equipment.armor.ArmorClient.applyFog(camera,level,callback.getReturnValue());}
}

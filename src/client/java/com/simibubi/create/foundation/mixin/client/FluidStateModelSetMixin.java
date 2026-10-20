package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.AllFluidModels;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

@Mixin(FluidStateModelSet.class)
abstract class FluidStateModelSetMixin {
    @Inject(method="bake",at=@At("RETURN"),cancellable=true)
    private static void create$fluids(MaterialBaker baker,CallbackInfoReturnable<Map<Fluid,FluidModel>> callback){callback.setReturnValue(AllFluidModels.bake(baker,callback.getReturnValue()));}
}

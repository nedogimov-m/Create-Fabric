package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.fluids.FluidPhysics;
import java.util.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
abstract class EntityFluidMixin {
    @Shadow @Final private EntityFluidInteraction fluidInteraction;

    @ModifyArg(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityFluidInteraction;<init>(Ljava/util/Set;)V"),index=0)
    private Set<TagKey<Fluid>> create$trackCurrent(Set<TagKey<Fluid>> original){
        var tags=new HashSet<>(original);tags.add(FluidPhysics.PLACEABLE);return Set.copyOf(tags);
    }
    @Inject(method="updateFluidInteraction",at=@At("RETURN"))
    private void create$physicalFluid(CallbackInfoReturnable<Boolean> callback){
        var entity=(Entity)(Object)this;
        if(!fluidInteraction.isInFluid(FluidPhysics.PLACEABLE))return;
        entity.fallDistance*=0.5;
        if(entity.isPushedByFluid())fluidInteraction.applyCurrentTo(FluidPhysics.PLACEABLE,entity,0.014);
    }
    @Inject(method="isInLiquid",at=@At("RETURN"),cancellable=true)
    private void create$inLiquid(CallbackInfoReturnable<Boolean> callback){
        if(!callback.getReturnValueZ()&&fluidInteraction.isInFluid(FluidPhysics.PLACEABLE))callback.setReturnValue(true);
    }
    @Inject(method="updateSwimming",at=@At("HEAD"),cancellable=true)
    private void create$swim(CallbackInfo callback){
        var entity=(Entity)(Object)this;
        if(entity.isInWater()||!fluidInteraction.isInFluid(FluidPhysics.PLACEABLE))return;
        entity.setSwimming(entity.isSprinting()&&!entity.isPassenger()&&(entity.isSwimming()||fluidInteraction.isEyeInFluid(FluidPhysics.PLACEABLE)));
        callback.cancel();
    }
}

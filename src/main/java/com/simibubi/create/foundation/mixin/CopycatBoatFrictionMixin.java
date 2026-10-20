package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.decoration.copycat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractBoat.class)
abstract class CopycatBoatFrictionMixin {
    @WrapOperation(method="getGroundFriction",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float create$materialBoatFriction(Block block,Operation<Float> original,@Local BlockPos.MutableBlockPos pos){var boat=(AbstractBoat)(Object)this;return original.call(block instanceof CopycatBlock?CopycatMaterialHooks.material(boat.level(),pos,boat.level().getBlockState(pos)).getBlock():block);}
}

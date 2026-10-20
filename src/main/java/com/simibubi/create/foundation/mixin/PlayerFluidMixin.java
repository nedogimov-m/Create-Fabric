package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.fluids.FluidPhysics;
import net.minecraft.tags.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class PlayerFluidMixin {
    @WrapOperation(method={"updateIsUnderwater","getDestroySpeed"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean create$submergedPlayer(Player player,TagKey<Fluid> tag,Operation<Boolean> original){return original.call(player,tag)||(tag==FluidTags.WATER&&FluidPhysics.eyesImmersed(player));}
}

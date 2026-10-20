package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.fluids.FluidPhysics;
import net.minecraft.client.gui.Hud;
import net.minecraft.tags.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
abstract class HudFluidMixin {
    @WrapOperation(method="extractAirBubbles",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean create$airSupply(Player player,TagKey<Fluid> tag,Operation<Boolean> original){return original.call(player,tag)||(tag==FluidTags.WATER&&FluidPhysics.eyesImmersed(player));}
}

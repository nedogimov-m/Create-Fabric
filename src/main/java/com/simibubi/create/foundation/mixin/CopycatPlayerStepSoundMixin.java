package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class CopycatPlayerStepSoundMixin {
    @WrapOperation(method="playStepSound",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;playMuffledStepSound(Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void create$materialMuffled(Player player,BlockState state,Operation<Void> original,BlockPos pos,BlockState floor){original.call(player,CopycatMaterialHooks.material(player.level(),pos,state));}
    @WrapOperation(method="playStepSound",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;playCombinationStepSounds(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void create$materialCombination(Player player,BlockState primary,BlockState secondary,Operation<Void> original,BlockPos pos,BlockState floor,@Local(ordinal=1) BlockPos primaryPos){original.call(player,CopycatMaterialHooks.material(player.level(),primaryPos,primary),CopycatMaterialHooks.material(player.level(),pos,secondary));}
}

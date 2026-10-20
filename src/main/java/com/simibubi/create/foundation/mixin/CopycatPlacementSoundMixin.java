package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Runs after actual setPlacedBy/offhand payment; preserves native event choice and volume/pitch formulas. */
@Mixin(BlockItem.class)
abstract class CopycatPlacementSoundMixin {
    @WrapOperation(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType create$materialPlacementSoundType(BlockState queried,Operation<SoundType> original,@Local Level level,@Local BlockPos pos){return original.call(CopycatMaterialHooks.material(level,pos,queried));}
    @WrapOperation(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/BlockItem;getPlaceSound(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/sounds/SoundEvent;"))
    private SoundEvent create$materialPlacementSound(BlockItem item,BlockState queried,Operation<SoundEvent> original,@Local Level level,@Local BlockPos pos){return original.call(item,CopycatMaterialHooks.material(level,pos,queried));}
}

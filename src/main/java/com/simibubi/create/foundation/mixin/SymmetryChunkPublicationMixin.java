package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
public abstract class SymmetryChunkPublicationMixin {
    @WrapOperation(method="setBlockState",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState create$paidNativePublication(LevelChunkSection section,int x,int y,int z,BlockState state,Operation<BlockState> original,@Local(argsOnly=true) BlockPos pos){var previous=original.call(section,x,y,z,state);SymmetryPlacement.published((LevelChunk)(Object)this,pos,state,previous);com.simibubi.create.content.equipment.symmetryWand.SymmetryHandler.publishedByCustomNative((LevelChunk)(Object)this,pos,state,previous);return previous;}
}

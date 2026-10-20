package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.deployer.DeployerSchematicPublication;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
public abstract class DeployerSchematicPublicationMixin {
    @WrapOperation(method="setBlockState",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState create$movingSchematicPublication(LevelChunkSection section,int x,int y,int z,BlockState state,Operation<BlockState> original,@Local(argsOnly=true) BlockPos pos){var previous=original.call(section,x,y,z,state);DeployerSchematicPublication.published((LevelChunk)(Object)this,pos,state,previous);return previous;}
}

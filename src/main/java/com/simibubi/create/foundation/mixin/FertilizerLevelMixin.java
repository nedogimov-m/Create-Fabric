package com.simibubi.create.foundation.mixin;
import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Exact-thread/level native dream view. No world constructor, storage, chunks or engine owner replacement. */
@Mixin(value=Level.class,priority=2000)
abstract class FertilizerLevelMixin {
 @Inject(method="getBlockState",at=@At("HEAD"),cancellable=true)private void create$treeState(BlockPos pos,CallbackInfoReturnable<BlockState> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.blockState(pos));}
 @Inject(method="getFluidState",at=@At("HEAD"),cancellable=true)private void create$treeFluid(BlockPos pos,CallbackInfoReturnable<FluidState> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.blockState(pos).getFluidState());}
 @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)private void create$treeWrite(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.setBlock(pos,state));}
 @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)private void create$treeDestroy(BlockPos pos,boolean drops,Entity entity,int recursion,CallbackInfoReturnable<Boolean> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.destroyBlock(pos));}
 @Inject(method="getBlockEntity",at=@At("HEAD"),cancellable=true)private void create$treeBlockEntity(BlockPos pos,CallbackInfoReturnable<BlockEntity> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.blockEntity(pos));}
 @Inject(method="setBlockEntity",at=@At("HEAD"))private void create$noTreeBlockEntityAdmission(BlockEntity entity,CallbackInfo ci){if(TreeFertilizerSimulation.active(this)!=null)throw new UnsupportedOperationException("Native fertilizer may not admit a simulated block entity into an actual chunk");}
 @Inject(method="getRandom",at=@At("HEAD"),cancellable=true)private void create$treeRandom(CallbackInfoReturnable<RandomSource> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.random);}
 @Inject(method="nextSubTickCount",at=@At("HEAD"),cancellable=true)private void create$treeSubTick(CallbackInfoReturnable<Long> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.nextSubTick());}
 @Inject(method="getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I",at=@At("HEAD"),cancellable=true)private void create$treeHeight(Heightmap.Types type,int x,int z,CallbackInfoReturnable<Integer> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.height(type,x,z));}
 @Inject(method="getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)private void create$noTreeEntityLookup(java.util.UUID id,CallbackInfoReturnable<Entity> ci){if(TreeFertilizerSimulation.active(this)!=null)ci.setReturnValue(null);}
}

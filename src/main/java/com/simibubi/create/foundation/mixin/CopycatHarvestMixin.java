package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native checks the tool after removal: carry only an immutable material state in this invocation's locals. */
@Mixin(ServerPlayerGameMode.class)
abstract class CopycatHarvestMixin {
    @WrapOperation(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity create$captureActualMaterial(ServerLevel level,BlockPos pos,Operation<BlockEntity> original,@Share("create$copycatMaterial") LocalRef<BlockState> material){var entity=original.call(level,pos);material.set(entity instanceof CopycatBlockEntity copycat?copycat.getMaterial():null);return entity;}
    @WrapOperation(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean create$materialHarvest(ServerPlayer player,BlockState state,Operation<Boolean> original,@Share("create$copycatMaterial") LocalRef<BlockState> material){return original.call(player,material.get()==null?state:material.get());}
}

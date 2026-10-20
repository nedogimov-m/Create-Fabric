package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The real BlockItem placement invokes its real block callback once, then records its actual placer. */
@Mixin(BlockItem.class)
abstract class SmartBlockEntityPlacementMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void create$recordActualPlacer(Block block, Level level, BlockPos pos, BlockState state,
            LivingEntity placer, ItemStack stack, Operation<Void> original) {
        var owner = LoadedLevelAccess.blockEntity(level, pos);
        original.call(block, level, pos, state, placer, stack);
        if (owner instanceof SmartBlockEntity smart && !smart.isRemoved()
                && LoadedLevelAccess.blockEntity(level, pos) == smart
                && LoadedLevelAccess.blockState(level, pos).getBlock() == block)
            AdvancementBehaviour.setPlacedBy(level, pos, placer);
    }
}

package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** Defers only witnessed ItemStack source events until native returned-hand ownership is published. */
@Mixin(ServerPlayerGameMode.class)
abstract class SymmetryNativeUseCompletionMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult create$afterActualHandTransformation(ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        try (var capture = SymmetryHandler.captureNativeUse(player, level)) {
            var result = original.call(player, level, stack, hand, hit);
            SymmetryHandler.completedNativeUse(capture);
            return result;
        }
    }
}

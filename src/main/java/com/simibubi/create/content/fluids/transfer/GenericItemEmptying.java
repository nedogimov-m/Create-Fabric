package com.simibubi.create.content.fluids.transfer;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.createmod.catnip.data.Pair;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

public final class GenericItemEmptying {
    private GenericItemEmptying() {}
    public static boolean canItemBeEmptied(Level level, ItemStack stack) { return !emptyItem(level, stack, true).getFirst().isEmpty(); }

    public static Pair<FluidStack, ItemStack> emptyItem(Level level, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return Pair.of(FluidStack.EMPTY, ItemStack.EMPTY);
        if(com.simibubi.create.content.fluids.potion.PotionFluidHandler.isPotionItem(stack))return com.simibubi.create.content.fluids.potion.PotionFluidHandler.emptyPotion(stack,simulate);
        if (level instanceof ServerLevel server) {
            var recipe = server.getServer().getRecipeManager().getRecipeFor(AllRecipeTypes.EMPTYING, new SingleRecipeInput(stack), level);
            if (recipe.isPresent()) {
                var emptying = recipe.get().value();
                var output = emptying.itemResult().map(r -> simulate ? r.stack() : r.rollOutput(level.getRandom())).orElse(ItemStack.EMPTY);
                if (!simulate) stack.shrink(1);
                return Pair.of(emptying.getResultingFluid(), output);
            }
        }
        var item = new ItemFluidTransfer(stack);
        var storage = item.context.find(FluidStorage.ITEM);
        if (storage != null) for (var view : storage) {
            if (view.isResourceBlank() || view.getAmount() == 0) continue;
            var variant = view.getResource();
            try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
                long drained = storage.extract(variant, FluidConstants.BUCKET, tx);
                if (drained == 0) continue;
                var output = item.result();
                if (!simulate) stack.shrink(1);
                return Pair.of(new FluidStack(variant, drained), output);
            }
        }
        return Pair.of(FluidStack.EMPTY, ItemStack.EMPTY);
    }
}

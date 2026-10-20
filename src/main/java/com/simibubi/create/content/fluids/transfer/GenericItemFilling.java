package com.simibubi.create.content.fluids.transfer;

import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

public final class GenericItemFilling {
    private GenericItemFilling() {}
    public static final long BOTTLE = 250 * 81L;
    public record Result(long amount, ItemStack item) {}

    public static boolean canItemBeFilled(Level level, ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.MILK_BUCKET)) return false;
        if (stack.is(Items.GLASS_BOTTLE)) return true;
        var storage = new ItemFluidTransfer(stack).context.find(FluidStorage.ITEM);
        if (storage == null || !storage.supportsInsertion()) return false;
        for (var view : storage) if (view.getCapacity() > view.getAmount()) return true;
        return false;
    }

    public static Result preview(ItemStack stack, FluidStack available) {
        if (stack.isEmpty() || available.isEmpty() || stack.is(Items.MILK_BUCKET)) return null;
        if (stack.is(Items.GLASS_BOTTLE) && available.variant().getFluid().isSame(Fluids.WATER))
            return new Result(BOTTLE, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        if(stack.is(Items.GLASS_BOTTLE)&&available.variant().isOf(com.simibubi.create.AllFluids.POTION))return new Result(BOTTLE,com.simibubi.create.content.fluids.potion.PotionFluidHandler.fillBottle(available.variant()));
        if(stack.is(Items.GLASS_BOTTLE)&&available.variant().isOf(com.simibubi.create.AllFluids.TEA))return new Result(BOTTLE,new ItemStack(com.simibubi.create.AllItems.BUILDERS_TEA));
        var output = new ItemStack[]{ItemStack.EMPTY};
        long filled = ItemFluidTransfer.fill(stack, available.variant(), stack.is(Items.BUCKET)
            ? FluidConstants.BUCKET : available.getAmount(), item -> output[0] = item);
        return filled == 0 ? null : new Result(filled, output[0]);
    }

    public static long getRequiredAmountForItem(Level level, ItemStack stack, FluidStack available) {
        var result = preview(stack, available); return result == null ? -1 : result.amount();
    }

    public static ItemStack fillItem(Level level, long required, ItemStack stack, FluidStack available) {
        var result = preview(stack, available);
        if (result == null || result.amount() != required || required > available.getAmount()) return ItemStack.EMPTY;
        stack.shrink(1); available.shrink(required); return result.item();
    }
}

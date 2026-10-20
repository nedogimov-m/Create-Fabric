package com.simibubi.create.content.fluids.spout;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public final class FillingBySpout {
    private FillingBySpout() {}
    private static java.util.stream.Stream<FillingRecipe> recipes(Level level, ItemStack stack) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) return java.util.stream.Stream.empty();
        var input = new SingleRecipeInput(stack);
        return server.getServer().getRecipeManager().getRecipes().stream().map(r -> r.value())
            .filter(r -> r instanceof FillingRecipe).map(r -> (FillingRecipe)r).filter(r -> r.matches(input, level));
    }
    public static boolean canItemBeFilled(Level level, ItemStack stack) {
        return com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipe(level,stack,AllRecipeTypes.FILLING,r->true).isPresent()||recipes(level, stack).findAny().isPresent() || GenericItemFilling.canItemBeFilled(level, stack);
    }
    public static long getRequiredAmountForItem(Level level, ItemStack stack, FluidStack available) {
        var assembly=com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipe(level,stack,AllRecipeTypes.FILLING,r->((FillingRecipe)r).getRequiredFluid().test(available.variant()));if(assembly.isPresent())return ((FillingRecipe)assembly.get().recipe()).getRequiredFluid().amount();
        return recipes(level, stack).filter(r -> r.getRequiredFluid().test(available.variant())).findFirst()
            .map(r -> r.getRequiredFluid().amount()).orElseGet(() -> GenericItemFilling.getRequiredAmountForItem(level, stack, available));
    }
    public static ItemStack fillItem(Level level, long required, ItemStack stack, FluidStack available) {
        if (stack.isEmpty() || required <= 0 || required > available.getAmount()) return ItemStack.EMPTY;
        var assembly=com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipe(level,stack,AllRecipeTypes.FILLING,r->((FillingRecipe)r).getRequiredFluid().test(available.variant())&&((FillingRecipe)r).getRequiredFluid().amount()==required);
        if(assembly.isPresent()){var result=assembly.get().roll(level.getRandom()).getFirst();stack.shrink(1);available.shrink(required);return result;}
        var recipe = recipes(level, stack).filter(r -> r.getRequiredFluid().test(available.variant()) && r.getRequiredFluid().amount() == required).findFirst();
        if (recipe.isPresent()) {
            stack.shrink(1); available.shrink(required);
            return recipe.get().results().getFirst().rollOutput(level.getRandom());
        }
        return GenericItemFilling.fillItem(level, required, stack, available);
    }
}

package com.simibubi.create.content.processing.basin;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import java.util.List;

public record BasinRecipeInput(List<ItemStack> items, List<FluidAmount> fluids, HeatCondition heat) implements RecipeInput {
    public record FluidAmount(FluidVariant variant, long amount) {}
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty) && fluids.stream().allMatch(f -> f.amount() == 0); }
}

package com.simibubi.create.content.processing.basin;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record BasinRecipe(boolean mixing, List<Either<Ingredient, FluidIngredient>> ingredients,
                          List<Either<ProcessingOutput, FluidOutput>> results,
                          HeatCondition heat, int processingTime) implements Recipe<BasinRecipeInput> {
    public record Consumption(long[] items, long[] fluids) {}
    public static MapCodec<BasinRecipe> codec(boolean mixing) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.either(Ingredient.CODEC, FluidIngredient.CODEC).listOf(1, 32).fieldOf("ingredients").forGetter(BasinRecipe::ingredients),
                Codec.either(ProcessingOutput.CODEC, FluidOutput.CODEC).listOf(1, 16).fieldOf("results").forGetter(BasinRecipe::results),
                HeatCondition.CODEC.optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(BasinRecipe::heat),
                Codec.intRange(1, 1000000).optionalFieldOf("processing_time", 100).forGetter(BasinRecipe::processingTime)
        ).apply(i, (ingredients, results, heat, time) -> new BasinRecipe(mixing, ingredients, results, heat, time)));
    }

    public Consumption consumption(BasinRecipeInput input) {
        if (!heat.test(input.heat())) return null;
        var items = ingredients.stream().flatMap(i -> i.left().stream()).toList();
        var fluids = ingredients.stream().flatMap(i -> i.right().stream()).toList();
        long[] itemDemand = new long[items.size()]; java.util.Arrays.fill(itemDemand, 1);
        long[] consumedItems = IngredientAllocation.allocate(input.items().stream().mapToLong(ItemStack::getCount).toArray(),
                itemDemand, (ingredient, slot) -> items.get(ingredient).test(input.items().get(slot)));
        if (consumedItems == null) return null;
        long[] consumedFluids = IngredientAllocation.allocate(input.fluids().stream().mapToLong(BasinRecipeInput.FluidAmount::amount).toArray(),
                fluids.stream().mapToLong(FluidIngredient::amount).toArray(),
                (ingredient, tank) -> fluids.get(ingredient).test(input.fluids().get(tank).variant()));
        return consumedFluids == null ? null : new Consumption(consumedItems, consumedFluids);
    }
    @Override public boolean matches(BasinRecipeInput input, Level level) { return consumption(input) != null; }
    @Override public ItemStack assemble(BasinRecipeInput input) { return results.stream().flatMap(o -> o.left().stream()).findFirst().map(ProcessingOutput::stack).orElse(ItemStack.EMPTY); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<BasinRecipe> getSerializer() { return mixing ? AllRecipeTypes.MIXING_SERIALIZER : AllRecipeTypes.COMPACTING_SERIALIZER; }
    @Override public RecipeType<BasinRecipe> getType() { return mixing ? AllRecipeTypes.MIXING : AllRecipeTypes.COMPACTING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

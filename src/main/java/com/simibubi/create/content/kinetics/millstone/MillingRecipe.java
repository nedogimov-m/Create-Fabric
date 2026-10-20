package com.simibubi.create.content.kinetics.millstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.List;

public record MillingRecipe(List<Ingredient> ingredients, List<ProcessingOutput> results,
                            int processingTime) implements Recipe<SingleRecipeInput> {
    public static final com.mojang.serialization.MapCodec<MillingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.listOf(1, 1).fieldOf("ingredients").forGetter(MillingRecipe::ingredients),
            ProcessingOutput.CODEC.listOf(1, 4).fieldOf("results").forGetter(MillingRecipe::results),
            Codec.intRange(1, 1000000).optionalFieldOf("processing_time", 100).forGetter(MillingRecipe::processingTime)
    ).apply(instance, MillingRecipe::new));

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.isEmpty() && ingredients.getFirst().test(input.item());
    }

    public List<ItemStack> rollResults(RandomSource random) {
        return results.stream().map(output -> output.rollOutput(random)).filter(stack -> !stack.isEmpty()).toList();
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return results.getFirst().stack();
    }

    @Override
    public boolean isSpecial() { return true; }
    @Override
    public boolean showNotification() { return false; }
    @Override
    public String group() { return ""; }
    @Override
    public RecipeSerializer<MillingRecipe> getSerializer() { return AllRecipeTypes.MILLING_SERIALIZER; }
    @Override
    public RecipeType<MillingRecipe> getType() { return AllRecipeTypes.MILLING; }
    @Override
    public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override
    public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

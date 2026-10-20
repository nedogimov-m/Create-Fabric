package com.simibubi.create.content.kinetics.saw;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record CuttingRecipe(List<Ingredient> ingredients, List<ProcessingOutput> results, int processingTime) implements Recipe<SingleRecipeInput>,com.simibubi.create.content.processing.sequenced.IAssemblyRecipe {
    public static final MapCodec<CuttingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, 1).fieldOf("ingredients").forGetter(CuttingRecipe::ingredients),
            ProcessingOutput.CODEC.listOf(1, 4).fieldOf("results").forGetter(CuttingRecipe::results),
            Codec.intRange(1, 1000000).optionalFieldOf("processing_time", 100).forGetter(CuttingRecipe::processingTime)
    ).apply(i, CuttingRecipe::new));
    @Override public boolean matches(SingleRecipeInput input, Level level) { return !input.isEmpty() && ingredients.getFirst().test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return results.getFirst().stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<CuttingRecipe> getSerializer() { return AllRecipeTypes.CUTTING_SERIALIZER; }
    @Override public RecipeType<CuttingRecipe> getType() { return AllRecipeTypes.CUTTING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

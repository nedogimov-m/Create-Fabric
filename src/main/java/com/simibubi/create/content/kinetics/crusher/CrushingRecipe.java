package com.simibubi.create.content.kinetics.crusher;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record CrushingRecipe(List<Ingredient> ingredients, List<ProcessingOutput> results, int processingTime) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<CrushingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, 1).fieldOf("ingredients").forGetter(CrushingRecipe::ingredients),
            ProcessingOutput.CODEC.listOf(1, 7).fieldOf("results").forGetter(CrushingRecipe::results),
            Codec.intRange(1, 1000000).optionalFieldOf("processing_time", 100).forGetter(CrushingRecipe::processingTime)
    ).apply(i, CrushingRecipe::new));
    @Override public boolean matches(SingleRecipeInput input, Level level) { return !input.isEmpty() && ingredients.getFirst().test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return results.getFirst().stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<CrushingRecipe> getSerializer() { return AllRecipeTypes.CRUSHING_SERIALIZER; }
    @Override public RecipeType<CrushingRecipe> getType() { return AllRecipeTypes.CRUSHING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

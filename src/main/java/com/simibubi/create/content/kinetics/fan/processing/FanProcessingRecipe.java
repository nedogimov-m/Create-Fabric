package com.simibubi.create.content.kinetics.fan.processing;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record FanProcessingRecipe(boolean splashing, List<Ingredient> ingredients, List<ProcessingOutput> results) implements Recipe<SingleRecipeInput> {
    public static MapCodec<FanProcessingRecipe> codec(boolean splashing) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC.listOf(1, 1).fieldOf("ingredients").forGetter(FanProcessingRecipe::ingredients),
                ProcessingOutput.CODEC.listOf(1, 12).fieldOf("results").forGetter(FanProcessingRecipe::results)
        ).apply(i, (ingredients, results) -> new FanProcessingRecipe(splashing, ingredients, results)));
    }
    @Override public boolean matches(SingleRecipeInput input, Level level) { return ingredients.getFirst().test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return results.getFirst().stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<FanProcessingRecipe> getSerializer() { return splashing ? AllRecipeTypes.SPLASHING_SERIALIZER : AllRecipeTypes.HAUNTING_SERIALIZER; }
    @Override public RecipeType<FanProcessingRecipe> getType() { return splashing ? AllRecipeTypes.SPLASHING : AllRecipeTypes.HAUNTING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

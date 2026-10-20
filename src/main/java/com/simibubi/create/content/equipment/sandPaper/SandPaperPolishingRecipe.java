package com.simibubi.create.content.equipment.sandPaper;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record SandPaperPolishingRecipe(List<Ingredient> ingredients, List<ProcessingOutput> results) implements Recipe<SingleRecipeInput> {
    public static final com.mojang.serialization.MapCodec<SandPaperPolishingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.listOf(1, 1).fieldOf("ingredients").forGetter(SandPaperPolishingRecipe::ingredients),
            ProcessingOutput.CODEC.listOf(1, 1).fieldOf("results").forGetter(SandPaperPolishingRecipe::results)
    ).apply(instance, SandPaperPolishingRecipe::new));
    @Override public boolean matches(SingleRecipeInput input, Level level) { return !input.isEmpty() && ingredients.getFirst().test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return results.getFirst().stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<SandPaperPolishingRecipe> getSerializer() { return AllRecipeTypes.SANDPAPER_POLISHING_SERIALIZER; }
    @Override public RecipeType<SandPaperPolishingRecipe> getType() { return AllRecipeTypes.SANDPAPER_POLISHING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

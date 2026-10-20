package com.simibubi.create.content.kinetics.press;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record PressingRecipe(List<Ingredient> ingredients, List<ProcessingOutput> results) implements Recipe<SingleRecipeInput>,com.simibubi.create.content.processing.sequenced.IAssemblyRecipe {
    public static final com.mojang.serialization.MapCodec<PressingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.listOf(1, 1).fieldOf("ingredients").forGetter(PressingRecipe::ingredients),
            ProcessingOutput.CODEC.listOf(1, 2).fieldOf("results").forGetter(PressingRecipe::results)
    ).apply(instance, PressingRecipe::new));
    @Override public boolean matches(SingleRecipeInput input, Level level) { return !input.isEmpty() && ingredients.getFirst().test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return results.getFirst().stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<PressingRecipe> getSerializer() { return AllRecipeTypes.PRESSING_SERIALIZER; }
    @Override public RecipeType<PressingRecipe> getType() { return AllRecipeTypes.PRESSING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

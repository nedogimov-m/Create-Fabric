package com.simibubi.create.content.fluids.transfer;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record FillingRecipe(List<Either<Ingredient, FluidIngredient>> ingredients,
                            List<ProcessingOutput> results) implements Recipe<SingleRecipeInput>,com.simibubi.create.content.processing.sequenced.IAssemblyRecipe {
    public static final MapCodec<FillingRecipe> CODEC = RecordCodecBuilder.<FillingRecipe>mapCodec(i -> i.group(
        Codec.either(Ingredient.CODEC, FluidIngredient.CODEC).listOf(2, 2).fieldOf("ingredients").forGetter(FillingRecipe::ingredients),
        ProcessingOutput.CODEC.listOf(1, 1).fieldOf("results").forGetter(FillingRecipe::results)
    ).apply(i, FillingRecipe::new)).validate(recipe -> recipe.ingredients.stream().filter(e -> e.left().isPresent()).count() == 1
        ? DataResult.success(recipe) : DataResult.error(() -> "Filling requires one item and one fluid ingredient"));

    public FluidIngredient getRequiredFluid() { return ingredients.stream().flatMap(e -> e.right().stream()).findFirst().orElseThrow(); }
    @Override public boolean matches(SingleRecipeInput input, Level level) { return !input.isEmpty() && ingredients.stream().flatMap(e -> e.left().stream()).findFirst().orElseThrow().test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return results.getFirst().stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<FillingRecipe> getSerializer() { return AllRecipeTypes.FILLING_SERIALIZER; }
    @Override public RecipeType<FillingRecipe> getType() { return AllRecipeTypes.FILLING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

package com.simibubi.create.content.fluids.transfer;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

public record EmptyingRecipe(List<Ingredient> ingredients, List<Either<ProcessingOutput, FluidOutput>> results)
    implements Recipe<SingleRecipeInput> {
    public static final MapCodec<EmptyingRecipe> CODEC = RecordCodecBuilder.<EmptyingRecipe>mapCodec(i -> i.group(
        Ingredient.CODEC.listOf(1, 1).fieldOf("ingredients").forGetter(EmptyingRecipe::ingredients),
        Codec.either(ProcessingOutput.CODEC, FluidOutput.CODEC).listOf(1, 2).fieldOf("results").forGetter(EmptyingRecipe::results)
    ).apply(i, EmptyingRecipe::new)).validate(recipe -> recipe.results.stream().filter(e -> e.right().isPresent()).count() == 1
        ? DataResult.success(recipe) : DataResult.error(() -> "Emptying requires one fluid output and at most one item output"));

    public FluidStack getResultingFluid() { var fluid = results.stream().flatMap(e -> e.right().stream()).findFirst().orElseThrow(); return new FluidStack(fluid.variant(), fluid.amount()); }
    public java.util.Optional<ProcessingOutput> itemResult() { return results.stream().flatMap(e -> e.left().stream()).findFirst(); }
    @Override public boolean matches(SingleRecipeInput input, Level level) { return !input.isEmpty() && ingredients.getFirst().test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return itemResult().map(ProcessingOutput::stack).orElse(ItemStack.EMPTY); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<EmptyingRecipe> getSerializer() { return AllRecipeTypes.EMPTYING_SERIALIZER; }
    @Override public RecipeType<EmptyingRecipe> getType() { return AllRecipeTypes.EMPTYING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}

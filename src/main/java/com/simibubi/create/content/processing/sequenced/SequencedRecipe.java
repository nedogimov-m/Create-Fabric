package com.simibubi.create.content.processing.sequenced;

import com.mojang.serialization.*;
import net.minecraft.world.item.crafting.Recipe;

public record SequencedRecipe(Recipe<?> recipe) {
    public static final Codec<SequencedRecipe> CODEC=Recipe.DIRECT_CODEC.comapFlatMap(recipe->recipe instanceof IAssemblyRecipe&&(!(recipe instanceof com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe application)||application.deploying())?DataResult.success(new SequencedRecipe(recipe)):DataResult.error(()->"Recipe does not support sequenced assembly: "+recipe.getType()),SequencedRecipe::recipe);
}

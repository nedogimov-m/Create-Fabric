package com.simibubi.create.api.data.recipe;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class PolishingRecipeGen extends StandardProcessingRecipeGen<SandPaperPolishingRecipe> {
    public PolishingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<SandPaperPolishingRecipe> getRecipeType() { return AllRecipeTypes.SANDPAPER_POLISHING; }
}

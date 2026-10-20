package com.simibubi.create.api.data.recipe;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.Recipe;

/** Native recipes are immutable records; the common builder serializes their genuine registered schemas. */
public abstract class StandardProcessingRecipeGen<R extends Recipe<?>> extends ProcessingRecipeGen<R> {
    public StandardProcessingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
}

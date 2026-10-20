package com.simibubi.create.api.data.recipe;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

/** Registry-aware data output, including conditional references to optional mods absent during generation. */
public interface RecipeDataOutput extends RecipeOutput {
    HolderLookup.Provider registries();
    void acceptJson(Identifier id, JsonObject recipe);
    default RegistryOps<com.google.gson.JsonElement> ops() { return registries().createSerializationContext(JsonOps.INSTANCE); }
    @Override default <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> registry) { return registries().lookupOrThrow(registry); }
    @Override default <S> java.util.stream.Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> registry) { return registries().lookupOrThrow(registry).listElements(); }
}

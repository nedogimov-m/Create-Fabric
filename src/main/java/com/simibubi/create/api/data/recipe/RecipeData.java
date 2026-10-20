package com.simibubi.create.api.data.recipe;

import com.google.gson.*;
import com.mojang.serialization.*;
import java.util.*;
import net.fabricmc.fabric.api.resource.conditions.v1.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.*;
import net.minecraft.world.item.crafting.Recipe;

/** Shared real recipe/condition codec bridge. Never resolves an absent item to the default registry entry. */
public final class RecipeData {
    public static <T> JsonElement encode(Codec<T> codec, T value, HolderLookup.Provider lookup) {
        return codec.encodeStart(lookup.createSerializationContext(JsonOps.INSTANCE), value).getOrThrow();
    }
    public static void emit(RecipeOutput output, Identifier id, JsonObject data, List<ResourceCondition> conditions) {
        if (!(output instanceof RecipeDataOutput nativeOutput))
            throw new IllegalArgumentException("Registry-aware Create RecipeDataOutput is required for modern data generation");
        var saved = data.deepCopy();
        if (!conditions.isEmpty()) saved.add("fabric:load_conditions", ResourceCondition.LIST_CODEC
            .encodeStart(nativeOutput.ops(), List.copyOf(conditions)).getOrThrow());
        nativeOutput.acceptJson(id, saved);
    }
    public static Recipe<?> decode(JsonObject data, HolderLookup.Provider lookup) {
        return Recipe.DIRECT_CODEC.parse(lookup.createSerializationContext(JsonOps.INSTANCE), data).getOrThrow();
    }
    public static JsonObject object(String key, String value) { var json = new JsonObject(); json.addProperty(key, value); return json; }
    public static Identifier type(Recipe<?> recipe) { return net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()); }
    private RecipeData() {}
}

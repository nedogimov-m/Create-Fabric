package com.simibubi.create.api.data.recipe;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.*;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.foundation.data.recipe.*;
import com.simibubi.create.content.decoration.palettes.AllPaletteStoneTypes;
import com.simibubi.create.foundation.block.CopperBlockSet;
import com.simibubi.create.foundation.block.CopperBlockSet.Variant;
import java.util.*;
import java.util.function.*;
import net.createmod.catnip.lang.Lang;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.material.Fluids;

import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class ItemApplicationRecipeGen extends StandardProcessingRecipeGen<ItemApplicationRecipe> {
    protected GeneratedRecipe woodCasing(String type, Supplier<ItemLike> ingredient, Supplier<ItemLike> output) { return woodCasingIngredient(type, () -> Ingredient.of(ingredient.get()), output); }
    protected GeneratedRecipe woodCasingTag(String type, Supplier<TagKey<Item>> ingredient, Supplier<ItemLike> output) {
        create(type + "_casing_from_log", b -> b.require(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "stripped_logs"))).require(ingredient.get()).output(output.get()));
        return create(type + "_casing_from_wood", b -> b.require(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "stripped_woods"))).require(ingredient.get()).output(output.get()));
    }
    protected GeneratedRecipe woodCasingIngredient(String type, Supplier<Ingredient> ingredient, Supplier<ItemLike> output) {
        create(type + "_casing_from_log", b -> b.require(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "stripped_logs"))).require(ingredient.get()).output(output.get()));
        return create(type + "_casing_from_wood", b -> b.require(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "stripped_woods"))).require(ingredient.get()).output(output.get()));
    }

    public ItemApplicationRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<ItemApplicationRecipe> getRecipeType() { return AllRecipeTypes.ITEM_APPLICATION; }
}

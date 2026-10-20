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

import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class MillingRecipeGen extends StandardProcessingRecipeGen<MillingRecipe> {
	/**
	 * @deprecated poor API. Requires an ItemEntry, and uses a string to create a tag. Unused by Create.
	 */
	@Deprecated(since = "6.0.7", forRemoval = true)
	protected GeneratedRecipe metalOre(String name, Supplier<? extends Item> crushed, int duration) {
		return create(name + "_ore", b -> b.duration(duration)
			.withCondition(ResourceConditions.tagsPopulated(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores/" + name))))
			.require(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores/" + name)))
			.output(crushed.get()));
	}

	protected GeneratedRecipe moddedSandstone(DatagenMod mod, String name) {
		String sandstone = name + "_sandstone";
		return create(mod.recipeId(sandstone), b -> b.duration(150)
			.require(mod, sandstone)
			.output(mod, name + "_sand")
			.whenModLoaded(mod.getId()));
	}


    public MillingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<MillingRecipe> getRecipeType() { return AllRecipeTypes.MILLING; }
}

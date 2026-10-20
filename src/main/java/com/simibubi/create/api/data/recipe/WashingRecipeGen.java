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

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class WashingRecipeGen extends StandardProcessingRecipeGen<FanProcessingRecipe> {
	public GeneratedRecipe convert(Block block, Block result) {
		return create(() -> block, b -> b.output(result));
	}

	public GeneratedRecipe crushedOre(Supplier<? extends ItemLike> crushed, Supplier<ItemLike> nugget, Supplier<ItemLike> secondary,
																 float secondaryChance) {
		return create(crushed::get, b -> b.output(nugget.get(), 9)
			.output(secondaryChance, secondary.get(), 1));
	}

	protected GeneratedRecipe simpleModded(DatagenMod mod, String input, String output) {
		return create(mod.getId() + "/" + output, b -> b.require(mod, input)
			.output(mod, output).whenModLoaded(mod.getId()));
	}


    public WashingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<FanProcessingRecipe> getRecipeType() { return AllRecipeTypes.SPLASHING; }
}

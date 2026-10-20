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

import com.simibubi.create.content.kinetics.press.PressingRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class PressingRecipeGen extends StandardProcessingRecipeGen<PressingRecipe> {
	protected GeneratedRecipe moddedCompacting(DatagenMod mod, String input, String output) {
		return create("compat/" + mod.getId() + "/" + output, b -> b.require(mod, input)
			.output(mod, output)
			.whenModLoaded(mod.getId()));
	}

	protected GeneratedRecipe moddedPaths(DatagenMod mod, String... blocks) {
		for (String block : blocks) {
			moddedCompacting(mod, block, block + "_path");
		}
		return null;
	}


    public PressingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<PressingRecipe> getRecipeType() { return AllRecipeTypes.PRESSING; }
}

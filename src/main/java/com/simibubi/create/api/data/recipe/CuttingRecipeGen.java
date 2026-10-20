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

import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class CuttingRecipeGen extends StandardProcessingRecipeGen<CuttingRecipe> {
	protected GeneratedRecipe stripAndMakePlanks(Block wood, Block stripped, Block planks) {
		return stripAndMakePlanks(wood, stripped, planks, 6);
	}

	protected GeneratedRecipe stripAndMakePlanks(Block wood, Block stripped, Block planks, int planksAmount) {
		create(() -> wood, b -> b.duration(50)
			.output(stripped));
		return create(() -> stripped, b -> b.duration(50)
			.output(planks, planksAmount));
	}

	protected GeneratedRecipe cuttingCompat(DatagenMod mod, String... woodtypes) {
		for (String type : woodtypes) {
			String planks = type + "_planks";

			if (mod == Mods.ARS_N && type.contains("archwood"))
				planks = "archwood_planks";

			String strippedPre = mod.strippedIsSuffix() ? "" : "stripped_";
			String strippedPost = mod.strippedIsSuffix() ? "_stripped" : "";
			stripAndMakePlanks(mod, type + "_log", strippedPre + type + "_log" + strippedPost, planks);

			String wood = type + (mod.omitWoodSuffix() ? "" : "_wood");
			stripAndMakePlanks(mod, wood, strippedPre + wood + strippedPost, planks);
		}
		return null;
	}

	protected GeneratedRecipe cuttingCompatLogOnly(DatagenMod mod, String... woodtypes) {
		for (String type : woodtypes) {
			String planks = type + "_planks";
			String strippedPre = mod.strippedIsSuffix() ? "" : "stripped_";
			String strippedPost = mod.strippedIsSuffix() ? "_stripped" : "";
			stripAndMakePlanks(mod, type + "_log", strippedPre + type + "_log" + strippedPost, planks);
		}
		return null;
	}

	protected GeneratedRecipe stripOnlyDiffModId(DatagenMod mod1, String wood, DatagenMod mod2, String stripped) {
		create("compat/" + mod1.getId() + "/" + wood, b -> b.duration(50)
			.require(mod1, wood)
			.output(1, mod2, stripped, 1)
			.whenModLoaded(mod1.getId()));
		return null;
	}

	protected GeneratedRecipe stripAndMakePlanksDiffPlanksModId(DatagenMod mod1, String log, String stripped, DatagenMod mod2, String planks) {
		if (log != null)
			create("compat/" + mod1.getId() + "/" + log, b -> b.duration(50)
				.require(mod1, log)
				.output(1, mod1, stripped, 1)
				.whenModLoaded(mod1.getId()));
		if (planks != null) // Shouldn't be needed as stripAndMakePlanks can already do what this method does if planks is null
			create("compat/" + mod1.getId() + "/" + stripped, b -> b.duration(50)
				.require(mod1, stripped)
				.output(1, mod2, planks, 6)
				.whenModLoaded(mod1.getId()));
		return null;
	}

	protected GeneratedRecipe stripAndMakePlanks(DatagenMod mod, String wood, String stripped, String planks) {
		if (wood != null)
			create("compat/" + mod.getId() + "/" + wood, b -> b.duration(50)
				.require(mod, wood)
				.output(1, mod, stripped, 1)
				.whenModLoaded(mod.getId()));
		if (planks != null)
			if (!Objects.equals(mod.getId(), Mods.VH.getId())) {
				create("compat/" + mod.getId() + "/" + stripped, b -> b.duration(50)
					.require(mod, stripped)
					.output(1, mod, planks, 6)
					.whenModLoaded(mod.getId()));
			} else {
				create("compat/" + mod.getId() + "/" + stripped, b -> b.duration(50)
					.require(mod, stripped)
					.output(1, mod, planks, 4)
					.whenModLoaded(mod.getId()));
			}
		return null;
	}


    public CuttingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<CuttingRecipe> getRecipeType() { return AllRecipeTypes.CUTTING; }
}

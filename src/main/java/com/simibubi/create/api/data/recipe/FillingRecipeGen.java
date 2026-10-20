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

import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class FillingRecipeGen extends StandardProcessingRecipeGen<FillingRecipe> {
	protected GeneratedRecipe moddedGrass(DatagenMod mod, String name) {
		String grass = name + "_grass_block";
		return create(mod.recipeId(grass), b -> b.require(Fluids.WATER, 500L * 81)
			.require(mod, name + "_dirt")
			.output(mod, grass)
			.whenModLoaded(mod.getId()));
	}


    public FillingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<FillingRecipe> getRecipeType() { return AllRecipeTypes.FILLING; }
}

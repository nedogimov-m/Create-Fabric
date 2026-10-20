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
public abstract class DeployingRecipeGen extends StandardProcessingRecipeGen<ItemApplicationRecipe> {
	public GeneratedRecipe copperChain(CopperBlockSet set) {
		for (Variant<?> variant : set.getVariants()) {
			List<Supplier<ItemLike>> chain = new ArrayList<>(4);
			List<Supplier<ItemLike>> waxedChain = new ArrayList<>(4);

			for (WeatherState state : WeatherState.values()) {
				waxedChain.add(() -> set.get(variant, state, true));
				chain.add(() -> set.get(variant, state, false));
			}

			oxidizationChain(chain, waxedChain);
		}
		return null;
	}

	public GeneratedRecipe addWax(Supplier<ItemLike> waxed, Supplier<ItemLike> nonWaxed) {
		createWithDeferredId(idWithSuffix(nonWaxed, "_from_removing_wax"), b -> b.require(waxed.get())
			.require(ItemTags.AXES)
			.toolNotConsumed()
			.output(nonWaxed.get()));

		return createWithDeferredId(idWithSuffix(waxed, "_from_adding_wax"), b -> b.require(nonWaxed.get())
			.require(Items.HONEYCOMB_BLOCK)
			.toolNotConsumed()
			.output(waxed.get()));
	}

	public GeneratedRecipe oxidizationChain(List<Supplier<ItemLike>> chain, List<Supplier<ItemLike>> waxedChain) {
		for (int i = 0; i < chain.size() - 1; i++) {
			Supplier<ItemLike> to = chain.get(i);
			Supplier<ItemLike> from = chain.get(i + 1);
			createWithDeferredId(idWithSuffix(to, "_from_deoxidising"), b -> b.require(from.get())
				.require(ItemTags.AXES)
				.toolNotConsumed()
				.output(to.get()));
		}

		for (int i = 0; i < chain.size(); i++)
			addWax(waxedChain.get(i), chain.get(i));

		return null;
	}


    public DeployingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<ItemApplicationRecipe> getRecipeType() { return AllRecipeTypes.DEPLOYING; }
}

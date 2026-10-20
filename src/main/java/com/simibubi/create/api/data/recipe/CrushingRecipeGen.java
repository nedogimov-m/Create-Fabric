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

import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.RecipeType;

/** Source recipe-generator category backed by the native 26.3 serializer. */
public abstract class CrushingRecipeGen extends StandardProcessingRecipeGen<CrushingRecipe> {
	protected GeneratedRecipe mineralRecycling(AllPaletteStoneTypes type, Supplier<ItemLike> crushed,
											   Supplier<ItemLike> nugget, float chance) {
		return mineralRecycling(type, b -> b.duration(250)
			.output(chance, crushed.get(), 1)
			.output(chance, nugget.get(), 1));
	}

	protected GeneratedRecipe mineralRecycling(AllPaletteStoneTypes type, UnaryOperator<ProcessingRecipeBuilder> transform) {
		create(Lang.asId(type.name()) + "_recycling", b -> transform.apply(b.require(type.materialTag)));
		return create(type.getBaseBlock()::get, transform);
	}

	protected GeneratedRecipe stoneOre(Supplier<ItemLike> ore, Supplier<ItemLike> raw, float expectedAmount,
									   int duration) {
		return ore(Blocks.COBBLESTONE, ore, raw, expectedAmount, duration);
	}

	protected GeneratedRecipe deepslateOre(Supplier<ItemLike> ore, Supplier<ItemLike> raw, float expectedAmount,
										   int duration) {
		return ore(Blocks.COBBLED_DEEPSLATE, ore, raw, expectedAmount, duration);
	}

	protected GeneratedRecipe netherOre(Supplier<ItemLike> ore, Supplier<ItemLike> raw, float expectedAmount,
										int duration) {
		return ore(Blocks.NETHERRACK, ore, raw, expectedAmount, duration);
	}

	protected GeneratedRecipe ore(ItemLike stoneType, Supplier<ItemLike> ore, Supplier<ItemLike> raw,
								  float expectedAmount, int duration) {
		return create(ore, b -> {
			b.duration(duration)
				.output(raw.get(), Mth.floor(expectedAmount));
			float extra = expectedAmount - Mth.floor(expectedAmount);
			if (extra > 0)
				b.output(extra, raw.get(), 1);
			b.output(.75f, AllItems.EXP_NUGGET, raw.get() == AllItems.CRUSHED_GOLD ? 2 : 1);
			return b.output(.125f, stoneType);
		});
	}

	protected GeneratedRecipe moddedOre(CommonMetal metal, Supplier<ItemLike> result) {
		TagKey<Item> tag = metal.ores.items();
		return create(metal + "_ore", b -> {
			return b.duration(400)
				.withCondition(ResourceConditions.tagsPopulated(tag))
				.require(tag)
				.output(result.get(), 1)
				.output(.75f, result.get(), 1)
				.output(.75f, AllItems.EXP_NUGGET);
		});
	}

	protected GeneratedRecipe rawOre(String metalName, Supplier<TagKey<Item>> input, Supplier<ItemLike> result, int xpMult) {
		return rawOre(metalName, input, result, false, xpMult);
	}

	protected GeneratedRecipe rawOreBlock(String metalName, Supplier<TagKey<Item>> input, Supplier<ItemLike> result, int xpMult) {
		return rawOre(metalName, input, result, true, xpMult);
	}

	protected GeneratedRecipe rawOre(String metalName, Supplier<TagKey<Item>> input, Supplier<ItemLike> result, boolean block, int xpMult) {
		return create("raw_" + metalName + (block ? "_block" : ""), b -> {
			int amount = block ? 9 : 1;
			return b.duration(400)
				.require(input.get())
				.output(result.get(), amount)
				.output(.75f, AllItems.EXP_NUGGET, amount * xpMult);
		});
	}

	protected GeneratedRecipe moddedRawOre(CommonMetal metal, Supplier<ItemLike> result) {
		return moddedRawOre(metal, result, false);
	}

	protected GeneratedRecipe moddedRawOreBlock(CommonMetal metal, Supplier<ItemLike> result) {
		return moddedRawOre(metal, result, true);
	}

	protected GeneratedRecipe moddedRawOre(CommonMetal metal, Supplier<ItemLike> result, boolean block) {
		return create("raw_" + metal + (block ? "_block" : ""), b -> {
			int amount = block ? 9 : 1;
			TagKey<Item> material = block ? metal.rawStorageBlocks.items() : metal.rawOres;
			return b.duration(400)
				.withCondition(ResourceConditions.tagsPopulated(material))
				.require(material)
				.output(result.get(), amount)
				.output(.75f, AllItems.EXP_NUGGET, amount);
		});
	}


    public CrushingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    @Override protected RecipeType<CrushingRecipe> getRecipeType() { return AllRecipeTypes.CRUSHING; }
}

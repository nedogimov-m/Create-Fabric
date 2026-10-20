package com.simibubi.create.infrastructure.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

public class CRecipes extends NativeConfigBase {

	public final ConfigBool bulkPressing = b(false, "bulkPressing", Comments.bulkPressing);
	public final ConfigBool bulkCutting = b(false, "bulkCutting", Comments.bulkCutting);
	public final ConfigBool allowBrewingInMixer = b(true, "allowBrewingInMixer", Comments.allowBrewingInMixer);
	public final ConfigBool allowShapelessInMixer = b(true, "allowShapelessInMixer", Comments.allowShapelessInMixer);
	public final ConfigBool allowShapedSquareInPress = b(true, "allowShapedSquareInPress", Comments.allowShapedSquareInPress);
	public final ConfigBool allowRegularCraftingInCrafter =
		b(true, "allowRegularCraftingInCrafter", Comments.allowRegularCraftingInCrafter);
	public final ConfigInt maxFireworkIngredientsInCrafter =
		i(9, 1, "maxFireworkIngredientsInCrafter", Comments.maxFireworkIngredientsInCrafter);
	public final ConfigBool allowStonecuttingOnSaw = b(true, "allowStonecuttingOnSaw", Comments.allowStonecuttingOnSaw);
	public final ConfigBool allowCastingBySpout = b(true, "allowCastingBySpout", Comments.allowCastingBySpout);
	public final ConfigBool displayLogStrippingRecipes = b(true, "displayLogStrippingRecipes", Comments.displayLogStrippingRecipes);
	public final ConfigInt lightSourceCountForRefinedRadiance =
		i(10, 1, "lightSourceCountForRefinedRadiance", Comments.refinedRadiance);
	public final ConfigBool enableRefinedRadianceRecipe =
		b(true, "enableRefinedRadianceRecipe", Comments.refinedRadianceRecipe);
	public final ConfigBool enableShadowSteelRecipe = b(true, "enableShadowSteelRecipe", Comments.shadowSteelRecipe);

	@Override
	public String getName() {
		return "recipes";
	}

    /** Accepted native packet/file shape; the rest of the source recipe section is independently wired. */
    public record Settings(int lightSourceCountForRefinedRadiance,boolean enableRefinedRadianceRecipe,boolean enableShadowSteelRecipe){
        public static final Settings DEFAULT=new Settings(10,true,true);
        public static final Codec<Settings> CODEC=RecordCodecBuilder.create(i->i.group(Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("lightSourceCountForRefinedRadiance",10).forGetter(Settings::lightSourceCountForRefinedRadiance),Codec.BOOL.optionalFieldOf("enableRefinedRadianceRecipe",true).forGetter(Settings::enableRefinedRadianceRecipe),Codec.BOOL.optionalFieldOf("enableShadowSteelRecipe",true).forGetter(Settings::enableShadowSteelRecipe)).apply(i,Settings::new));
        public Settings{if(lightSourceCountForRefinedRadiance<1)throw new IllegalArgumentException("Source light threshold must be positive");}
    }
    public Settings serverSettings(){return new Settings(lightSourceCountForRefinedRadiance.getServer(),enableRefinedRadianceRecipe.getServer(),enableShadowSteelRecipe.getServer());}
    public Settings effective(){var actual=store();return new Settings(actual.readClient(lightSourceCountForRefinedRadiance),actual.readClient(enableRefinedRadianceRecipe),actual.readClient(enableShadowSteelRecipe));}
    private Map<String,Object> legacyValues(Settings value){return Map.of(lightSourceCountForRefinedRadiance.key(),value.lightSourceCountForRefinedRadiance(),enableRefinedRadianceRecipe.key(),value.enableRefinedRadianceRecipe(),enableShadowSteelRecipe.key(),value.enableShadowSteelRecipe());}
    public void installClient(Settings actual){store().installRemoteValues(legacyValues(Objects.requireNonNull(actual)));}
    public void clearClient(){store().clearRemoteValues(legacyValues(Settings.DEFAULT).keySet());}
    public void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-legacy-materials.json"));}
    public void reload(Path path){if(store().root()==this)store().reload(path);else NativeConfigFiles.reloadSection(this,path,Set.of("lightSourceCountForRefinedRadiance","enableRefinedRadianceRecipe","enableShadowSteelRecipe"));}

	private static class Comments {
		static String bulkPressing = "Allow the Mechanical Press to process entire stacks at a time.";
		static String bulkCutting = "Allow the Mechanical Saw to process entire stacks at a time.";
		static String allowBrewingInMixer = "Allow supported potions to be brewed by a Mechanical Mixer + Basin.";
		static String allowShapelessInMixer =
			"Allow any shapeless crafting recipes to be processed by a Mechanical Mixer + Basin.";
		static String allowShapedSquareInPress =
			"Allow any single-ingredient 2x2 or 3x3 crafting recipes to be processed by a Mechanical Press + Basin.";
		static String allowRegularCraftingInCrafter =
			"Allow any standard crafting recipes to be processed by Mechanical Crafters.";
		static String maxFireworkIngredientsInCrafter =
			"The Maximum amount of ingredients that can be used to craft Firework Rockets using Mechanical Crafters.";
		static String allowStonecuttingOnSaw =
			"Allow any stonecutting recipes to be processed by a Mechanical Saw.";
		static String allowWoodcuttingOnSaw =
			"Allow any wood related recipes to be processed by a Mechanical Saw.";
		static String allowCastingBySpout =
			"Allow Spouts to interact with Casting Tables and Basins from Tinkers' Construct.";
		static String refinedRadiance =
			"The amount of Light sources destroyed before Chromatic Compound turns into Refined Radiance.";
		static String refinedRadianceRecipe = "Allow the standard in-world Refined Radiance recipes.";
		static String shadowSteelRecipe = "Allow the standard in-world Shadow Steel recipe.";
		static String displayLogStrippingRecipes = "Display vanilla Log-stripping interactions in JEI.";
	}

}

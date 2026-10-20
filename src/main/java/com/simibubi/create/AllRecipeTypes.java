package com.simibubi.create;

import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class AllRecipeTypes {
    public static final java.util.function.Predicate<net.minecraft.world.item.crafting.RecipeHolder<?>>
        CAN_BE_AUTOMATED = recipe -> !recipe.id().identifier().getPath().endsWith("_manual_only");

    public static boolean shouldIgnoreInAutomation(net.minecraft.world.item.crafting.RecipeHolder<?> recipe) {
        var serializer = recipe.value().getSerializer();
        return serializer != null && BuiltInRegistries.RECIPE_SERIALIZER.wrapAsHolder(serializer).is(
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER,
                Create.asResource("automation_ignore"))) || !CAN_BE_AUTOMATED.test(recipe);
    }

    public static final RecipeType<com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe> DEPLOYING=Registry.register(BuiltInRegistries.RECIPE_TYPE,Create.asResource("deploying"),new RecipeType<>(){});
    public static final RecipeType<com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe> ITEM_APPLICATION=Registry.register(BuiltInRegistries.RECIPE_TYPE,Create.asResource("item_application"),new RecipeType<>(){});
    public static final RecipeSerializer<com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe> DEPLOYING_SERIALIZER=Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,Create.asResource("deploying"),new RecipeSerializer<>(com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.codec(true),ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.codec(true).codec())));
    public static final RecipeSerializer<com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe> ITEM_APPLICATION_SERIALIZER=Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,Create.asResource("item_application"),new RecipeSerializer<>(com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.codec(false),ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.codec(false).codec())));
    public static final RecipeType<com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe> SEQUENCED_ASSEMBLY=Registry.register(BuiltInRegistries.RECIPE_TYPE,Create.asResource("sequenced_assembly"),new RecipeType<>(){});
    public static final RecipeSerializer<com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe> SEQUENCED_ASSEMBLY_SERIALIZER=Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,Create.asResource("sequenced_assembly"),new RecipeSerializer<>(com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.CODEC,ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.CODEC.codec())));
    public static final RecipeType<com.simibubi.create.content.fluids.transfer.FillingRecipe> FILLING = Registry.register(BuiltInRegistries.RECIPE_TYPE, Create.asResource("filling"), new RecipeType<>() { @Override public String toString() { return "create:filling"; } });
    public static final RecipeSerializer<com.simibubi.create.content.fluids.transfer.FillingRecipe> FILLING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Create.asResource("filling"), new RecipeSerializer<>(com.simibubi.create.content.fluids.transfer.FillingRecipe.CODEC, ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.fluids.transfer.FillingRecipe.CODEC.codec())));
    public static final RecipeType<com.simibubi.create.content.fluids.transfer.EmptyingRecipe> EMPTYING = Registry.register(BuiltInRegistries.RECIPE_TYPE, Create.asResource("emptying"), new RecipeType<>() { @Override public String toString() { return "create:emptying"; } });
    public static final RecipeSerializer<com.simibubi.create.content.fluids.transfer.EmptyingRecipe> EMPTYING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Create.asResource("emptying"), new RecipeSerializer<>(com.simibubi.create.content.fluids.transfer.EmptyingRecipe.CODEC, ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.fluids.transfer.EmptyingRecipe.CODEC.codec())));
    public static final RecipeType<com.simibubi.create.content.kinetics.saw.CuttingRecipe> CUTTING = Registry.register(BuiltInRegistries.RECIPE_TYPE, Create.asResource("cutting"), new RecipeType<>() { @Override public String toString() { return "create:cutting"; } });
    public static final RecipeSerializer<com.simibubi.create.content.kinetics.saw.CuttingRecipe> CUTTING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Create.asResource("cutting"), new RecipeSerializer<>(com.simibubi.create.content.kinetics.saw.CuttingRecipe.CODEC, ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.kinetics.saw.CuttingRecipe.CODEC.codec())));
    public static final RecipeType<com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe> MECHANICAL_CRAFTING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("mechanical_crafting"), new RecipeType<>() { @Override public String toString() { return "create:mechanical_crafting"; } });
    public static final RecipeSerializer<com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe> MECHANICAL_CRAFTING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("mechanical_crafting"), new RecipeSerializer<>(com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe.CODEC,
                    ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe.CODEC.codec())));
    public static final RecipeType<com.simibubi.create.content.kinetics.crusher.CrushingRecipe> CRUSHING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("crushing"), new RecipeType<>() { @Override public String toString() { return "create:crushing"; } });
    public static final RecipeSerializer<com.simibubi.create.content.kinetics.crusher.CrushingRecipe> CRUSHING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("crushing"), new RecipeSerializer<>(com.simibubi.create.content.kinetics.crusher.CrushingRecipe.CODEC,
                    ByteBufCodecs.fromCodecWithRegistries(com.simibubi.create.content.kinetics.crusher.CrushingRecipe.CODEC.codec())));
    public static final RecipeType<MillingRecipe> MILLING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("milling"), new RecipeType<>() {
                @Override public String toString() { return "create:milling"; }
            });
    public static final RecipeSerializer<MillingRecipe> MILLING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("milling"), new RecipeSerializer<>(MillingRecipe.CODEC,
                    ByteBufCodecs.fromCodecWithRegistries(MillingRecipe.CODEC.codec())));
    public static final RecipeType<PressingRecipe> PRESSING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("pressing"), new RecipeType<>() { @Override public String toString() { return "create:pressing"; } });
    public static final RecipeSerializer<PressingRecipe> PRESSING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("pressing"), new RecipeSerializer<>(PressingRecipe.CODEC,
                    ByteBufCodecs.fromCodecWithRegistries(PressingRecipe.CODEC.codec())));

    public static void register() {
        net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization.synchronizeRecipeSerializer(SEQUENCED_ASSEMBLY_SERIALIZER);
        net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization.synchronizeRecipeSerializer(ITEM_APPLICATION_SERIALIZER);
        for (var name : new String[] {"smelting", "blasting", "smoking"}) {
            var serializer = BuiltInRegistries.RECIPE_SERIALIZER.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(name));
            net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization.synchronizeRecipeSerializer(
                java.util.Objects.requireNonNull(serializer, name));
        }
        net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization.synchronizeRecipeSerializer(SPLASHING_SERIALIZER);
        net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization.synchronizeRecipeSerializer(HAUNTING_SERIALIZER);
    }
    public static final RecipeType<FanProcessingRecipe> SPLASHING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("splashing"), new RecipeType<>() { @Override public String toString() { return "create:splashing"; } });
    public static final RecipeType<FanProcessingRecipe> HAUNTING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("haunting"), new RecipeType<>() { @Override public String toString() { return "create:haunting"; } });
    public static final RecipeSerializer<FanProcessingRecipe> SPLASHING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("splashing"), new RecipeSerializer<>(FanProcessingRecipe.codec(true), ByteBufCodecs.fromCodecWithRegistries(FanProcessingRecipe.codec(true).codec())));
    public static final RecipeSerializer<FanProcessingRecipe> HAUNTING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("haunting"), new RecipeSerializer<>(FanProcessingRecipe.codec(false), ByteBufCodecs.fromCodecWithRegistries(FanProcessingRecipe.codec(false).codec())));

    public static final RecipeType<BasinRecipe> MIXING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("mixing"), new RecipeType<>() { @Override public String toString() { return "create:mixing"; } });
    public static final RecipeType<BasinRecipe> COMPACTING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("compacting"), new RecipeType<>() { @Override public String toString() { return "create:compacting"; } });
    public static final RecipeSerializer<BasinRecipe> MIXING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("mixing"), new RecipeSerializer<>(BasinRecipe.codec(true), ByteBufCodecs.fromCodecWithRegistries(BasinRecipe.codec(true).codec())));
    public static final RecipeSerializer<BasinRecipe> COMPACTING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("compacting"), new RecipeSerializer<>(BasinRecipe.codec(false), ByteBufCodecs.fromCodecWithRegistries(BasinRecipe.codec(false).codec())));

    public static final RecipeType<SandPaperPolishingRecipe> SANDPAPER_POLISHING = Registry.register(BuiltInRegistries.RECIPE_TYPE,
            Create.asResource("sandpaper_polishing"), new RecipeType<>() { @Override public String toString() { return "create:sandpaper_polishing"; } });
    public static final RecipeSerializer<SandPaperPolishingRecipe> SANDPAPER_POLISHING_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Create.asResource("sandpaper_polishing"), new RecipeSerializer<>(SandPaperPolishingRecipe.CODEC,
                    ByteBufCodecs.fromCodecWithRegistries(SandPaperPolishingRecipe.CODEC.codec())));

    private AllRecipeTypes() {}
}

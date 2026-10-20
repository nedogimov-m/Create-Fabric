package com.simibubi.create.test;

import com.google.gson.*;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.api.data.TrainHatInfoProvider;
import com.simibubi.create.api.data.recipe.*;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.content.processing.sequenced.*;
import com.simibubi.create.content.trains.schedule.hat.TrainHatInfo;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.data.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Actual native codec and on-disk DataProvider checks; no fake item, recipe, registry or output codecs. */
public final class NativeDatagenApiGameTests {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("create_datagen_test", path); }
    private static final List<String> TYPES = List.of("milling", "crushing", "cutting", "pressing", "sandpaper_polishing", "splashing", "haunting", "deploying", "item_application", "mixing", "compacting", "filling", "emptying");
    private static ItemStack named() { var stack = new ItemStack(Items.PAPER, 4); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Actual generated components")); return stack; }
    private static ProcessingRecipeBuilder processing(String type) {
        var builder = new ProcessingRecipeBuilder(Identifier.fromNamespaceAndPath("create", type), id("native/" + type)).require(Items.IRON_INGOT);
        if (Set.of("deploying", "item_application").contains(type)) builder.require(Items.REDSTONE).toolNotConsumed();
        if (Set.of("mixing", "compacting", "filling").contains(type)) builder.require(Fluids.WATER, 500L * 81);
        if (Set.of("mixing", "compacting").contains(type)) builder.requiresHeat(HeatCondition.HEATED);
        if (type.equals("emptying")) builder.output(Fluids.WATER, 1000L * 81);
        return builder.output(named());
    }
    private static MechanicalCraftingRecipeBuilder mechanical() {
        var builder = new MechanicalCraftingRecipeBuilder(named()).key('a', Items.IRON_INGOT).disallowMirrored();
        for (int y = 0; y < 9; y++) builder.patternLine("aaaaaaaaa");
        return builder;
    }
    private static SequencedAssemblyRecipeBuilder assembly() {
        return new SequencedAssemblyRecipeBuilder(id("native_assembly")).require(Items.IRON_INGOT).transitionTo(Items.IRON_NUGGET)
            .addStep(AllRecipeTypes.PRESSING, b -> b).addStep(AllRecipeTypes.DEPLOYING, b -> b.require(Items.REDSTONE).toolNotConsumed())
            .addOutput(Items.COMPASS, 120).addOutput(Items.STICK, 8).loops(3);
    }
    private static void removeOwnTemporary(Path root) throws Exception {
        try (var files = Files.walk(root)) { for (var path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    @GameTest public void actualProvidersWriteAllNativeProcessingMechanicalAssemblyAndConditionalResources(GameTestHelper helper) throws Exception {
        var root = Files.createTempDirectory("create-native-datagen-"); var lookup = helper.getLevel().registryAccess();
        try {
            var provider = new BaseRecipeProvider(new PackOutput(root), CompletableFuture.completedFuture(lookup), "create_datagen_test") {{
                for (var type : TYPES) register(output -> processing(type).build(output));
                register(output -> mechanical().build(output, id("mechanical_crafting/nine_grid")));
                register(output -> assembly().build(output));
                register(output -> new ProcessingRecipeBuilder(AllRecipeTypes.MILLING, id("conditional"))
                    .require(Identifier.fromNamespaceAndPath("create_absent_datagen_fixture", "ore"))
                    .output(Identifier.fromNamespaceAndPath("create_absent_datagen_fixture", "dust"))
                    .whenModLoaded("create_absent_datagen_fixture").build(output));
            }};
            provider.run(CachedOutput.NO_CACHE).join();
            int actualRecipes;
            try (var files = Files.walk(root.resolve("data/create_datagen_test/recipe"))) { actualRecipes = (int) files.filter(Files::isRegularFile).count(); }
            helper.assertTrue(actualRecipes == 16, "Every genuine processing category, mechanical pattern, assembly and conditional resource is actually saved");
            for (var type : TYPES) {
                var file = root.resolve("data/create_datagen_test/recipe/" + type + "/native/" + type + ".json");
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject(); var decoded = RecipeData.decode(json, lookup);
                helper.assertTrue(BuiltInRegistries.RECIPE_TYPE.getKey(decoded.getType()).equals(Identifier.fromNamespaceAndPath("create", type)), "Actual native serializer retains the generated category " + type);
                var encoded = Recipe.DIRECT_CODEC.encodeStart(lookup.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), decoded).getOrThrow().getAsJsonObject();
                helper.assertTrue(encoded.getAsJsonArray("results").get(0).getAsJsonObject().has("components"), "Actual registry-aware codec retains original item components " + type);
            }
            var grid = (MechanicalCraftingRecipe) RecipeData.decode(JsonParser.parseString(Files.readString(root.resolve("data/create_datagen_test/recipe/mechanical_crafting/nine_grid.json"))).getAsJsonObject(), lookup);
            helper.assertTrue(grid.pattern().size() == 9 && grid.pattern().getFirst().length() == 9 && !grid.acceptMirrored() && grid.result().create().getCount() == 4 && grid.result().create().has(DataComponents.CUSTOM_NAME), "Real 9x9 pattern and nonmirrored component output survive native decoding");
            var assembled = (SequencedAssemblyRecipe) RecipeData.decode(JsonParser.parseString(Files.readString(root.resolve("data/create_datagen_test/recipe/sequenced_assembly/native_assembly.json"))).getAsJsonObject(), lookup);
            helper.assertTrue(assembled.totalSteps() == 6 && assembled.resultPool().getFirst().chance() == 120 && assembled.sequence().size() == 2, "Actual sequenced steps and weighted results retain source meanings without probability clamping");
            var conditional = JsonParser.parseString(Files.readString(root.resolve("data/create_datagen_test/recipe/milling/conditional.json"))).getAsJsonObject();
            var conditions = ResourceCondition.LIST_CODEC.parse(lookup.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), conditional.get("fabric:load_conditions")).getOrThrow();
            helper.assertTrue(conditions.size() == 1 && conditional.toString().contains("create_absent_datagen_fixture:dust") && !BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("create_absent_datagen_fixture", "dust")), "Genuine Fabric condition and exact optional item IDs persist without fake item registrations");
        } finally { removeOwnTemporary(root); }
        helper.succeed();
    }
    @GameTest public void malformedDefinitionsAndUngatedAbsentItemsRejectBeforeProviderWrites(GameTestHelper helper) throws Exception {
        var root = Files.createTempDirectory("create-native-datagen-reject-"); var lookup = helper.getLevel().registryAccess();
        try {
            var provider = new BaseRecipeProvider(new PackOutput(root), CompletableFuture.completedFuture(lookup), "create_datagen_test") {{
                register(output -> processing("milling").build(output));
                register(output -> new ProcessingRecipeBuilder(AllRecipeTypes.MILLING, id("absent"))
                    .require(Identifier.fromNamespaceAndPath("create_absent_datagen_fixture", "ore")).output(Items.PAPER).build(output));
            }};
            boolean refused = false; try { provider.run(CachedOutput.NO_CACHE).join(); } catch (RuntimeException expected) { refused = true; }
            try (var files = Files.walk(root)) { helper.assertTrue(refused && files.noneMatch(Files::isRegularFile), "An invalid later recipe is rejected before even the valid first recipe can publish"); }
            for (var malformed : List.<Runnable>of(() -> new ProcessingRecipeBuilder(AllRecipeTypes.MILLING, id("bad")).output(Float.NaN, Items.PAPER),
                    () -> mechanical().patternLine("aaaaaaaaa"), () -> new MechanicalCraftingRecipeBuilder(Items.PAPER, 1).key('a', Items.IRON_INGOT).patternLine("b").buildJson(lookup),
                    () -> new ProcessingRecipeBuilder(AllRecipeTypes.FILLING, id("bad_fluid")).require(Fluids.WATER, 0),
                    () -> new SequencedAssemblyRecipeBuilder(id("bad_weight")).require(Items.IRON_INGOT).transitionTo(Items.IRON_NUGGET).addStep(AllRecipeTypes.PRESSING, b -> b).addOutput(Items.PAPER, 0).buildJson(lookup))) {
                boolean rejected = false; try { malformed.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
                helper.assertTrue(rejected, "Native invalid shape/probability/weight/fluid bound cannot produce a recipe");
            }
        } finally { removeOwnTemporary(root); }
        helper.succeed();
    }
    @GameTest public void duplicateGeneratedIdsRejectBeforeAnyFilePublication(GameTestHelper helper) throws Exception {
        var root = Files.createTempDirectory("create-native-datagen-duplicate-");
        try {
            var provider = new BaseRecipeProvider(new PackOutput(root), CompletableFuture.completedFuture(helper.getLevel().registryAccess()), "create_datagen_test") {{
                register(output -> processing("milling").build(output)); register(output -> processing("milling").build(output));
            }};
            boolean refused = false; try { provider.run(CachedOutput.NO_CACHE).join(); } catch (RuntimeException expected) { refused = true; }
            try (var files = Files.walk(root)) { helper.assertTrue(refused && files.noneMatch(Files::isRegularFile), "Duplicate native recipe IDs cannot silently replace a recipe or publish an earlier partial output"); }
        } finally { removeOwnTemporary(root); }
        helper.succeed();
    }
    @GameTest public void genuineCommonHatProviderWritesRealNativeCodecData(GameTestHelper helper) throws Exception {
        var root = Files.createTempDirectory("create-native-hat-datagen-");
        try {
            var provider = new TrainHatInfoProvider(new PackOutput(root), CompletableFuture.completedFuture(helper.getLevel().registryAccess())) {
                @Override protected void createOffsets() { makeInfoFor(EntityTypes.COW, new Vec3(1, -2, 3), "body/head", 2, .75f); }
            };
            provider.run(CachedOutput.NO_CACHE).join();
            var file = root.resolve("assets/minecraft/train_hat_info/cow.json");
            var info = TrainHatInfo.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file))).getOrThrow();
            helper.assertTrue(info.part().equals("body/head") && info.cubeIndex() == 2 && info.offset().equals(new Vec3(1, -2, 3)) && info.scale() == .75f, "Actual common hat DataProvider preserves source model path, cube, offset and scale through the native value codec");
        } finally { removeOwnTemporary(root); }
        helper.succeed();
    }
}

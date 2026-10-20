package com.simibubi.create.test;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.*;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Full data ingestion and native recipe-book events; device earning is checked separately. */
public final class RecipeAdvancementParityGameTests {
    private static Identifier id(String path) { return Create.asResource(path); }
    private static ResourceKey<Recipe<?>> recipeKey(String path) { return ResourceKey.create(Registries.RECIPE, id(path)); }
    private static JsonObject json(Resource resource) throws Exception {
        try (var reader = resource.openAsReader()) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
    private static Map<Identifier, Resource> resources(GameTestHelper h, String directory) {
        return h.getLevel().getServer().getResourceManager().listResources(directory,
            key -> key.getNamespace().equals("create") && key.getPath().endsWith(".json")
                && !key.getPath().contains("/runtime_generated/"));
    }
    private static String path(Identifier file, String directory) {
        return file.getPath().substring(directory.length() + 1, file.getPath().length() - 5);
    }
    private static boolean enabled(GameTestHelper h, JsonObject data) {
        if (!data.has("fabric:load_conditions")) return true;
        var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var lookup = new RegistryOps.RegistryInfoLookup() {
            @Override public <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return ops.getter(key);
            }
        };
        return ResourceCondition.LIST_CODEC.parse(ops, data.get("fabric:load_conditions")).getOrThrow()
            .stream().allMatch(condition -> condition.test(lookup));
    }
    private static boolean done(ServerPlayer player, String path) {
        var entry = player.level().getServer().getAdvancements().get(id(path));
        return entry != null && player.getAdvancements().getOrStartProgress(entry).isDone();
    }
    @GameTest public void entirePinnedResourceCorpusMatchesActualNativeManagerIngestion(GameTestHelper h) throws Exception {
        var recipes = resources(h, "recipe");
        h.assertValueEqual(recipes.size(), 1884, "All pinned recipe resources remain present, including conditional ones");
        for (var entry : recipes.entrySet()) {
            var path = path(entry.getKey(), "recipe");
            h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(recipeKey(path)).isPresent() == enabled(h, json(entry.getValue())),
                "Actual native recipe load respects the resource condition: " + path);
        }
        var advancements = resources(h, "advancement");
        h.assertValueEqual(advancements.size(), 1150, "All pinned advancement resources remain present");
        for (var entry : advancements.entrySet()) {
            var path = path(entry.getKey(), "advancement");
            h.assertTrue((h.getLevel().getServer().getAdvancements().get(id(path)) != null) == enabled(h, json(entry.getValue())),
                "Actual native advancement load respects the resource condition: " + path);
        }
        h.succeed();
    }
    @GameTest public void mudIngredientFollowsNativeBlockTagIncludingDatapackOnlyMember(GameTestHelper h) {
        var mud = (BasinRecipe) h.getLevel().getServer().getRecipeManager().byKey(recipeKey("mixing/mud_by_mixing")).orElseThrow().value();
        var ingredient = mud.ingredients().getFirst().left().orElseThrow();
        var nativeTag = TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("convertible_to_mud"));
        h.assertTrue(net.minecraft.world.level.block.Blocks.SAND.builtInRegistryHolder().is(nativeTag),
            "A genuine test datapack extends the native block tag without extending Create's item bridge");
        h.assertTrue(ingredient.test(new ItemStack(Items.SAND)) && ingredient.test(new ItemStack(Items.DIRT))
            && !ingredient.test(new ItemStack(Items.STONE)), "Loaded mud recipe tracks real native block-tag membership");
        var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var roundTrip = Ingredient.CODEC.parse(ops, Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow()).getOrThrow();
        h.assertTrue(roundTrip.test(new ItemStack(Items.SAND)) && !roundTrip.test(new ItemStack(Items.STONE)),
            "Registry-aware ingredient roundtrip preserves the dynamic block tag");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
            var synced = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            h.assertTrue(synced.test(new ItemStack(Items.SAND)) && !synced.test(new ItemStack(Items.STONE)),
                "Actual native ingredient network codec retains the custom block-tag predicate");
        } finally { buffer.release(); }
        var emptyTag = Ingredient.CODEC.parse(ops, JsonParser.parseString("{\"fabric:type\":\"create:block_tag\",\"tag\":\"create_test:no_blocks_in_this_tag\"}")).getOrThrow();
        h.assertTrue(emptyTag.test(new ItemStack(Items.BARRIER)) && !emptyTag.test(new ItemStack(Items.DIRT)),
            "The original empty-block-tag placeholder semantics remain available");
        h.assertTrue(emptyTag.display() instanceof net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay display
            && display.stack().create().getHoverName().getString().equals("Empty Tag: create_test:no_blocks_in_this_tag"),
            "The original empty-tag label survives the native slot display");
        h.succeed();
    }
    @GameTest public void pathRecipeRetainsEnvironmentalExclusionInActualLoader(GameTestHelper h) throws Exception {
        var resource = h.getLevel().getServer().getResourceManager().getResource(id("recipe/pressing/path.json")).orElseThrow();
        var data = json(resource);
        h.assertTrue(data.has("fabric:load_conditions"), "The pinned Environmental exclusion must not disappear during generation");
        var expected = JsonParser.parseString("[{\"condition\":\"fabric:not\",\"value\":{\"condition\":\"fabric:all_mods_loaded\",\"values\":[\"environmental\"]}}]");
        h.assertTrue(expected.equals(data.get("fabric:load_conditions")), "Native load condition preserves the exact source mod predicate");
        h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(recipeKey("pressing/path")).isPresent()
            == !FabricLoader.getInstance().isModLoaded("environmental"), "Actual recipe ingestion honours the loaded mod set");
        h.succeed();
    }
    @GameTest public void allVanillaInventoryGameplayCriteriaEarnFromActualInventoryEvents(GameTestHelper h) {
        var cases = new LinkedHashMap<String, Item>();
        cases.put("root", Items.STONE); cases.put("copper", Items.COPPER_INGOT);
        cases.put("andesite_alloy", AllItems.ANDESITE_ALLOY); cases.put("brass", AllItems.BRASS_INGOT);
        cases.put("rose_quartz", AllItems.POLISHED_ROSE_QUARTZ); cases.put("chocolate_bucket", AllFluids.CHOCOLATE_FAMILY.bucket);
        cases.put("burner", AllBlocks.BLAZE_BURNER.asItem()); cases.put("track_0", AllBlocks.TRACK.asItem());
        cases.put("precision_mechanism", AllItems.PRECISION_MECHANISM); cases.put("sturdy_sheet", AllItems.STURDY_SHEET);
        cases.put("cardboard", AllItems.CARDBOARD);
        for (var entry : cases.entrySet()) {
            try (var joined = GameplayAdvancementGameTests.join(h)) {
                var player = joined.player();
                player.getInventory().setItem(0, new ItemStack(entry.getValue())); player.inventoryMenu.broadcastChanges();
                h.assertTrue(done(player, entry.getKey()), "Real survival inventory event earns source criterion " + entry.getKey());
            }
        }
        try (var joined = GameplayAdvancementGameTests.join(h)) {
            var player = joined.player();
            player.getInventory().setItem(0, new ItemStack(AllItems.WRENCH)); player.inventoryMenu.broadcastChanges();
            h.assertFalse(done(player, "wrench_goggles"), "Wrench alone cannot complete the two original inventory criteria");
            player.getInventory().setItem(1, new ItemStack(AllItems.GOGGLES)); player.inventoryMenu.broadcastChanges();
            h.assertTrue(done(player, "wrench_goggles"), "Both actual equipment items complete the original requirement");
        }
        h.succeed();
    }
    @GameTest public void ordinaryRecipeUnlockCriteriaUseRealNativeBookAndInventoryDiscovery(GameTestHelper h) throws Exception {
        try (var joined = GameplayAdvancementGameTests.join(h)) {
            var player = joined.player(); int checked = 0;
            for (var entry : resources(h, "advancement").entrySet()) {
                var path = path(entry.getKey(), "advancement");
                if (!path.startsWith("recipes/")) continue;
                var data = json(entry.getValue()); if (!enabled(h, data)) continue;
                for (var reward : data.getAsJsonObject("rewards").getAsJsonArray("recipes")) {
                    var key = ResourceKey.create(Registries.RECIPE, Identifier.parse(reward.getAsString()));
                    var recipe = h.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow();
                    if (recipe.value().isSpecial()) continue;
                    player.awardRecipes(List.of(recipe));
                    h.assertTrue(player.getRecipeBook().contains(key) && done(player, path),
                        "Real recipe-book grant fires the loaded recipe_unlocked criterion and retains its reward: " + path);
                    checked++;
                }
            }
            h.assertTrue(checked > 500, "All loaded ordinary recipe-unlock criteria are exercised, not a representative sample");
        }
        try (var joined = GameplayAdvancementGameTests.join(h)) {
            var player = joined.player(); var cog = recipeKey("crafting/kinetics/cogwheel");
            h.assertFalse(player.getRecipeBook().contains(cog), "A fresh survival player has not discovered the cogwheel recipe");
            player.getInventory().setItem(0, new ItemStack(AllItems.ANDESITE_ALLOY)); player.inventoryMenu.broadcastChanges();
            h.assertTrue(player.getRecipeBook().contains(cog) && done(player, "recipes/misc/crafting/kinetics/cogwheel"),
                "Genuine alloy inventory discovery completes the source recipe advancement and unlocks its real book entry");
        }
        h.succeed();
    }
}

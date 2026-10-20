package com.simibubi.create.test;

import com.google.gson.*;
import com.simibubi.create.*;
import com.simibubi.create.api.registry.CreateRegistries;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Original pinned contributions checked against actual loaded native tag memberships and real recipe matching. */
public final class SourceTagContributionGameTests {
    private static <T> void check(GameTestHelper helper, ResourceKey<? extends Registry<T>> registry,
                                  Identifier tagId, JsonArray values) {
        var lookup = helper.getLevel().registryAccess().lookupOrThrow(registry);
        var tag = TagKey.create(registry, tagId);
        for (var entry : values) {
            var text = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("id").getAsString();
            if (text.startsWith("#")) {
                var members = lookup.getOrThrow(TagKey.create(registry, Identifier.parse(text.substring(1))));
                for (var member : members) helper.assertTrue(member.is(tag), "Real nested source tag member remains in " + tagId + ": " + member);
            } else {
                var member = lookup.getOrThrow(ResourceKey.create(registry, Identifier.parse(text)));
                helper.assertTrue(member.is(tag), "Actual loaded native source tag retains " + text + " in " + tagId);
            }
        }
    }
    @GameTest public void restoredSourceTagsMatchActualLoadedRegistryMemberships(GameTestHelper helper) throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/create_test/source-tag-contributions.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var source = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : source.entrySet()) {
                var pieces = entry.getKey().split("\\|", 2); var tag = Identifier.parse(pieces[1]); var values = entry.getValue().getAsJsonArray();
                switch (pieces[0]) {
                    case "item" -> check(helper, Registries.ITEM, tag, values);
                    case "block" -> check(helper, Registries.BLOCK, tag, values);
                    case "fluid" -> check(helper, Registries.FLUID, tag, values);
                    case "entity_type" -> check(helper, Registries.ENTITY_TYPE, tag, values);
                    case "damage_type" -> check(helper, Registries.DAMAGE_TYPE, tag, values);
                    case "create/contraption_type" -> check(helper, CreateRegistries.CONTRAPTION_TYPE, tag, values);
                    default -> throw new AssertionError("Unreviewed actual tag registry " + pieces[0]);
                }
            }
            helper.assertTrue(source.size() == 58, "Every restored/extended source tag is checked against genuine loaded native registry data");
        }
        assertRealCakeRecipe(helper);
        helper.succeed();
    }
    @GameTest public void realLoadedSourceCakeRecipeAcceptsOriginalDoughAndAllNativeEggColors(GameTestHelper helper) {
        assertRealCakeRecipe(helper);
        helper.succeed();
    }
    private static void assertRealCakeRecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Create.asResource("crafting/curiosities/cake"));
        var recipe = (CraftingRecipe) helper.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow().value();
        for (var egg : List.of(Items.EGG, Items.BROWN_EGG, Items.BLUE_EGG)) {
            var grid = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, new ItemStack(Items.MILK_BUCKET), ItemStack.EMPTY,
                new ItemStack(Items.SUGAR), new ItemStack(egg), new ItemStack(Items.SUGAR),
                ItemStack.EMPTY, new ItemStack(AllItems.DOUGH), ItemStack.EMPTY));
            helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Actual source cake recipe matches its real dough, milk, sugar and native egg: " + egg);
            var cake = recipe.assemble(grid);
            helper.assertTrue(cake.is(Items.CAKE) && cake.getCount() == 1 && grid.getItem(7).is(AllItems.DOUGH), "Loaded native recipe produces exactly the source cake without mutating the input snapshot");
        }
    }
}

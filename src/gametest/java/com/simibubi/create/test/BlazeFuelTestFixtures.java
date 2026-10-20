package com.simibubi.create.test;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Real non-fuel items; only the native test datapack can make them valid burner inputs. */
public final class BlazeFuelTestFixtures implements ModInitializer {
    private static final Map<String, Item> ITEMS = new HashMap<>();

    static Item item(String name) { return ITEMS.get(name); }

    @Override public void onInitialize() {
        for (String name : new String[] {"plain", "superheated", "both", "tagged", "removed", "tag_removed",
            "conditioned", "fabric_conditioned", "false_conditioned", "legacy_normal", "legacy_special", "long_regular", "max_regular"}) {
            var key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("create_test", "burner_" + name));
            ITEMS.put(name, Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key))));
        }
    }
}

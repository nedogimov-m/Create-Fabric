package com.simibubi.create.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.Item;

/** Synthetic addon items exercise runtime generation without requiring another mod. */
public final class RuntimeWoodFixtures implements ModInitializer {
    public static Item ASSEMBLY_BLANK;
    public static com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType ADDON_ATTRIBUTE;
    @Override public void onInitialize() {
        var assemblyKey=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("create_test","assembly_blank"));ASSEMBLY_BLANK=Registry.register(BuiltInRegistries.ITEM,assemblyKey,new Item(new Item.Properties().setId(assemblyKey)));
        ADDON_ATTRIBUTE=Registry.register(com.simibubi.create.api.registry.CreateBuiltInRegistries.ITEM_ATTRIBUTE_TYPE,Identifier.fromNamespaceAndPath("create_test","assembly_blank"),new com.simibubi.create.content.logistics.item.filter.attribute.SingletonItemAttribute.Type(type->new com.simibubi.create.content.logistics.item.filter.attribute.SingletonItemAttribute(type,(stack,level)->stack.is(ASSEMBLY_BLANK),"test_assembly_blank")));
        for (String name : new String[]{"fir_log", "stripped_fir_log", "fir_planks", "fir_slab", "silverwood_log", "silverwood_wood", "silverwood_planks"}) {
            var key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("create_test", name));
            Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
        }
    }
}

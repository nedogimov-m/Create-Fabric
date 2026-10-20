package com.simibubi.create;

import com.simibubi.create.api.contraption.storage.fluid.*;
import com.simibubi.create.api.contraption.storage.item.*;
import com.simibubi.create.api.contraption.storage.item.chest.*;
import com.simibubi.create.api.contraption.storage.item.simple.*;
import com.simibubi.create.api.registry.*;
import com.simibubi.create.content.contraptions.behaviour.dispenser.storage.*;
import com.simibubi.create.content.fluids.tank.storage.*;
import com.simibubi.create.content.fluids.tank.storage.creative.*;
import com.simibubi.create.content.logistics.crate.*;
import com.simibubi.create.content.logistics.depot.storage.*;
import com.simibubi.create.content.logistics.vault.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;

/** Only implemented types are registered. Addons can register their own codecs and block associations. */
public final class AllMountedStorageTypes {
    public static final com.simibubi.create.impl.contraption.storage.FallbackMountedStorageType FALLBACK=item("fallback",new com.simibubi.create.impl.contraption.storage.FallbackMountedStorageType());
    public static final com.simibubi.create.content.equipment.toolbox.ToolboxMountedStorageType TOOLBOX=item("toolbox",new com.simibubi.create.content.equipment.toolbox.ToolboxMountedStorageType());
    public static final SimpleMountedStorageType.Impl SIMPLE = item("simple", new SimpleMountedStorageType.Impl());
    public static final ChestMountedStorageType CHEST = item("chest", new ChestMountedStorageType());
    public static final DispenserMountedStorageType DISPENSER = item("dispenser", new DispenserMountedStorageType());
    public static final ItemVaultMountedStorageType VAULT = item("vault", new ItemVaultMountedStorageType());
    public static final DepotMountedStorageType DEPOT = item("depot", new DepotMountedStorageType());
    public static final CreativeCrateMountedStorageType CREATIVE_CRATE = item("creative_crate", new CreativeCrateMountedStorageType());
    public static final FluidTankMountedStorageType FLUID_TANK = fluid("fluid_tank", new FluidTankMountedStorageType());
    public static final CreativeFluidTankMountedStorageType CREATIVE_FLUID_TANK = fluid("creative_fluid_tank", new CreativeFluidTankMountedStorageType());
    public static final TagKey<MountedItemStorageType<?>> INTERNAL = TagKey.create(CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE, Create.asResource("internal"));
    public static final TagKey<MountedItemStorageType<?>> FUEL_BLACKLIST = TagKey.create(CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE, Create.asResource("fuel_blacklist"));
    private static <T extends MountedItemStorageType<?>> T item(String name, T type) { return Registry.register(CreateBuiltInRegistries.MOUNTED_ITEM_STORAGE_TYPE, Create.asResource(name), type); }
    private static <T extends MountedFluidStorageType<?>> T fluid(String name, T type) { return Registry.register(CreateBuiltInRegistries.MOUNTED_FLUID_STORAGE_TYPE, Create.asResource(name), type); }
    public static void init() {
        AllBlocks.TOOLBOXES.values().forEach(block->MountedItemStorageType.REGISTRY.register(block,TOOLBOX));
        MountedItemStorageType.REGISTRY.registerProvider(SimpleRegistry.Provider.forBlockTag(TagKey.create(Registries.BLOCK, Create.asResource("simple_mounted_storage")), SIMPLE));
        MountedItemStorageType.REGISTRY.registerProvider(SimpleRegistry.Provider.forBlockTag(TagKey.create(Registries.BLOCK, Create.asResource("chest_mounted_storage")), CHEST));
        MountedItemStorageType.REGISTRY.register(Blocks.DISPENSER, DISPENSER);
        MountedItemStorageType.REGISTRY.register(Blocks.DROPPER, DISPENSER);
        MountedItemStorageType.REGISTRY.register(AllBlocks.ITEM_VAULT, VAULT);
        MountedItemStorageType.REGISTRY.register(AllBlocks.DEPOT, DEPOT);
        MountedItemStorageType.REGISTRY.register(AllBlocks.CREATIVE_CRATE, CREATIVE_CRATE);
        MountedFluidStorageType.REGISTRY.register(AllBlocks.FLUID_TANK, FLUID_TANK);
        MountedFluidStorageType.REGISTRY.register(AllBlocks.CREATIVE_FLUID_TANK, CREATIVE_FLUID_TANK);
    }
    private AllMountedStorageTypes() {}
}

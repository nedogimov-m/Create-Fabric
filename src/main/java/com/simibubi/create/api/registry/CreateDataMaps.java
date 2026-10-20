package com.simibubi.create.api.registry;

import com.simibubi.create.api.data.datamaps.BlazeBurnerFuel;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Server datapack data carried by Fabric's native resource store. These keys replace
 * NeoForge DataMapType fields; Fabric holders do not implement NeoForge getData.
 */
public final class CreateDataMaps {
    /** Loaded from data/create/data_maps/item/regular_blaze_burner_fuels.json. */
    public static final DataResourceStore.Key<Map<Item, BlazeBurnerFuel>> REGULAR_BLAZE_BURNER_FUELS =
        new DataResourceStore.Key<>();
    /** Loaded from data/create/data_maps/item/superheated_blaze_burner_fuels.json. */
    public static final DataResourceStore.Key<Map<Item, BlazeBurnerFuel>> SUPERHEATED_BLAZE_BURNER_FUELS =
        new DataResourceStore.Key<>();

    /** Reads the immutable map belonging to the server's current successful reload. */
    public static @Nullable BlazeBurnerFuel get(MinecraftServer server,
        DataResourceStore.Key<Map<Item, BlazeBurnerFuel>> map, Item item) {
        return ((DataResourceStore) server).getOrThrow(map).get(item);
    }

    private CreateDataMaps() {}
}

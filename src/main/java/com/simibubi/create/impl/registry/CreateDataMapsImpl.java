package com.simibubi.create.impl.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.api.data.datamaps.BlazeBurnerFuel;
import com.simibubi.create.api.registry.CreateDataMaps;
import com.simibubi.create.foundation.data.recipe.PendingItemTagContents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** The two actual source data maps, loaded transactionally into Fabric's server resource store. */
public final class CreateDataMapsImpl {
    private static boolean registered;

    public static void register() {
        if (registered) return;
        DataResourceLoader.get().registerReloadListener(Create.asResource("blaze_burner_fuels"), Loader::new);
        registered = true;
    }

    private record Entry(BlazeBurnerFuel value, boolean replace) {
        private static final Codec<Entry> CODEC = Codec.either(
            RecordCodecBuilder.<Entry>create(instance -> instance.group(
                BlazeBurnerFuel.CODEC.fieldOf("value").forGetter(Entry::value),
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(Entry::replace)
            ).apply(instance, Entry::new)),
            BlazeBurnerFuel.CODEC
        ).xmap(value -> value.map(entry -> entry, fuel -> new Entry(fuel, false)),
            entry -> entry.replace() ? Either.left(entry) : Either.right(entry.value()));
    }

    private record Target(Identifier id, boolean tag) {
        static Target parse(String value) {
            boolean tag = value.startsWith("#");
            return new Target(Identifier.parse(tag ? value.substring(1) : value), tag);
        }
    }

    private record FileData(boolean replace, Map<Target, Optional<Entry>> values, List<Target> remove) {}
    private record FuelMaps(Map<Item, BlazeBurnerFuel> regular, Map<Item, BlazeBurnerFuel> superheated) {}

    private static final class Loader extends SimpleReloadListener<FuelMaps> {
        private final HolderGetter<Item> items;
        private final RegistryOps.RegistryInfoLookup conditions;

        Loader(HolderLookup.Provider registries) {
            items = registries.lookupOrThrow(Registries.ITEM);
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            conditions = new RegistryOps.RegistryInfoLookup() {
                @Override public <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                    return ops.getter(key);
                }
            };
        }

        @Override protected FuelMaps prepare(SharedState state) {
            return new FuelMaps(load(state.resourceManager(), "regular_blaze_burner_fuels"),
                load(state.resourceManager(), "superheated_blaze_burner_fuels"));
        }

        @Override protected void apply(FuelMaps prepared, SharedState state) {
            var store = state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY);
            store.put(CreateDataMaps.REGULAR_BLAZE_BURNER_FUELS, prepared.regular());
            store.put(CreateDataMaps.SUPERHEATED_BLAZE_BURNER_FUELS, prepared.superheated());
        }

        private Map<Item, BlazeBurnerFuel> load(ResourceManager manager, String name) {
            var result = new HashMap<Item, BlazeBurnerFuel>();
            var path = Create.asResource("data_maps/item/" + name + ".json");
            // Native resource stacks already run from lowest to highest priority.
            for (var resource : manager.getResourceStack(path)) {
                try (var reader = resource.openAsReader()) {
                    var carrier = BlazeFuelConditions.unwrap(JsonParser.parseReader(reader), conditions);
                    if (carrier.isEmpty()) continue;
                    var file = decode(carrier.get());
                    // A malformed file never applies replace or a partial prefix of its values.
                    if (file.replace()) result.clear();
                    file.values().forEach((target, entry) -> entry.ifPresent(value ->
                        resolve(target, true, item -> result.put(item, value.value()))));
                    for (var target : file.remove()) resolve(target, false, result::remove);
                } catch (Exception failure) {
                    Create.LOGGER.error("Could not read blaze burner data map {}", path, failure);
                }
            }
            return Map.copyOf(result);
        }

        private FileData decode(JsonElement raw) {
            var object = raw.getAsJsonObject();
            boolean replace = object.has("replace")
                && Codec.BOOL.parse(JsonOps.INSTANCE, object.get("replace")).getOrThrow();
            var values = new LinkedHashMap<Target, Optional<Entry>>();
            var keys = new HashSet<Target>();
            for (var value : object.getAsJsonObject("values").entrySet()) {
                var key = Target.parse(value.getKey());
                if (!keys.add(key)) throw new IllegalArgumentException("Duplicate normalized data map key " + key);
                values.put(key, BlazeFuelConditions.unwrap(value.getValue(), conditions)
                    .map(carrier -> Entry.CODEC.parse(JsonOps.INSTANCE, carrier).getOrThrow()));
            }
            var remove = new ArrayList<Target>();
            if (object.has("remove")) {
                for (var value : Codec.STRING.listOf().parse(JsonOps.INSTANCE, object.get("remove")).getOrThrow())
                    remove.add(Target.parse(value));
            }
            return new FileData(replace, values, List.copyOf(remove));
        }

        private void resolve(Target target, boolean required, Consumer<Item> consumer) {
            if (target.tag()) {
                PendingItemTagContents.items(items, TagKey.create(Registries.ITEM, target.id())).forEach(consumer);
            } else {
                var holder = items.get(ResourceKey.create(Registries.ITEM, target.id()));
                if (holder.isPresent()) consumer.accept(holder.get().value());
                else if (required) Create.LOGGER.error("Item {} specified in blaze burner data map does not exist", target.id());
            }
        }
    }

    private CreateDataMapsImpl() {}
}

// Create1.21.1 callback queue semantics, MIT; genuine native Registry/Fabric publication.
package com.simibubi.create.impl.registrate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class CreateRegistrateRegistrationCallbackImpl {
    private static final Map<ResourceKey<?>, Slot<?>> SLOTS = new HashMap<>();
    private static final class Slot<R> {
        Registry<R> registry;
        final Map<Identifier, R> published = new HashMap<>();
        final Map<Identifier, List<Consumer<? super R>>> waiting = new LinkedHashMap<>();
    }
    static {
        RegistryEntryAddedCallback.event(BuiltInRegistries.REGISTRY).register((rawId, id, registry) -> {
            boolean interested;
            synchronized (SLOTS) { interested = SLOTS.containsKey(registry.key()); }
            if (interested) provideRegistry(registry);
        });
    }
    @SuppressWarnings("unchecked")
    private static <R> Slot<R> slot(ResourceKey<?> key) { return (Slot<R>) SLOTS.computeIfAbsent(key, ignored -> new Slot<>()); }

    @SuppressWarnings("unchecked")
    public static <R, T extends R> void register(ResourceKey<? extends Registry<R>> registryKey, Identifier id, Consumer<? super T> callback) {
        Objects.requireNonNull(registryKey, "registryKey"); Objects.requireNonNull(id, "id"); Objects.requireNonNull(callback, "callback");
        Consumer<R> typed = value -> callback.accept((T) value);
        R immediate;
        Registry<R> toBind = null;
        synchronized (SLOTS) {
            Slot<R> slot = slot(registryKey);
            immediate = slot.published.get(id);
            if (immediate == null) slot.waiting.computeIfAbsent(id, ignored -> new ArrayList<>()).add(typed);
            if (slot.registry == null) {
                var nativeRegistry = BuiltInRegistries.REGISTRY.getValue(registryKey.identifier());
                if (nativeRegistry != null) toBind = (Registry<R>) nativeRegistry;
            }
        }
        if (toBind != null) provideRegistry(toBind);
        if (immediate != null) typed.accept(immediate);
    }
    public static <R, T extends R> void register(Registry<R> registry, Identifier id, Consumer<? super T> callback) {
        provideRegistry(Objects.requireNonNull(registry, "registry"));
        CreateRegistrateRegistrationCallbackImpl.<R, T>register(registry.key(), id, callback);
    }
    public static <R> void provideRegistry(Registry<R> registry) {
        Objects.requireNonNull(registry, "registry");
        Slot<R> slot;
        synchronized (SLOTS) {
            slot = slot(registry.key());
            if (slot.registry == registry) return;
            if (slot.registry != null) throw new IllegalArgumentException("Different native registry already bound for key: " + registry.key().identifier());
            slot.registry = registry;
        }
        // Native API installs its real entry event before replaying real existing Holder.Reference values.
        // An entry seen both during that transition and in the replay is deduplicated by actual identity.
        RegistryEntryAddedCallback.allEntries(registry, holder -> published(slot, holder));
    }
    private static <R> void published(Slot<R> slot, Holder.Reference<R> holder) {
        Identifier id = holder.key().identifier(); R value = holder.value();
        List<Consumer<? super R>> callbacks;
        synchronized (SLOTS) {
            R prior = slot.published.putIfAbsent(id, value);
            if (prior != null) {
                if (prior != value) throw new IllegalStateException("Native registry entry identity changed after publication: " + holder.key());
                return;
            }
            var pending = slot.waiting.remove(id); callbacks = pending == null ? List.of() : List.copyOf(pending);
        }
        for (var callback : callbacks) callback.accept(value);
    }
    private CreateRegistrateRegistrationCallbackImpl() {}
}

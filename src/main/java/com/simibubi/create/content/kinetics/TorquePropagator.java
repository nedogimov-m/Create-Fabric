package com.simibubi.create.content.kinetics;

import java.util.HashMap;
import java.util.Map;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.LevelAccessor;

/** The source registry is the only stress network authority for each actual world. */
public class TorquePropagator {
    static Map<LevelAccessor, Map<Long, KineticNetwork>> networks = new HashMap<>();

    public void onLoadWorld(LevelAccessor world) {
        networks.computeIfAbsent(world, ignored -> new HashMap<>());
    }
    public void onUnloadWorld(LevelAccessor world) { networks.remove(world); }
    public KineticNetwork getOrCreateNetworkFor(KineticBlockEntity be) {
        Long id = be.network;
        if (id == null || be.getLevel() == null || be.getLevel().isClientSide() || be.isVirtual()) return null;
        var map = networks.computeIfAbsent(be.getLevel(), ignored -> new HashMap<>());
        return map.computeIfAbsent(id, key -> { var group = new KineticNetwork(); group.id = key; return group; });
    }
    static void removeNetwork(LevelAccessor world, KineticNetwork group) {
        var map = networks.get(world);
        if (map != null) map.remove(group.id, group);
    }
    static java.util.List<KineticNetwork> loadedNetworks(LevelAccessor world) {
        var map = networks.get(world);
        return map == null ? java.util.List.of() : java.util.List.copyOf(map.values());
    }
}

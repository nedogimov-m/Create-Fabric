package com.simibubi.create.content.redstone.link;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;

/** The FULL conversion callback only records work; BE reads happen after the future has returned. */
public final class RedstoneLinkLifecycle {
    private static final Map<ServerLevel, Set<Long>> PENDING = new IdentityHashMap<>();
    private static boolean registered;
    static void register() {
        if (registered) return; registered = true;
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level, chunk, before, after) -> {
            if (before.isOrAfter(FullChunkStatus.FULL) != after.isOrAfter(FullChunkStatus.FULL))
                PENDING.computeIfAbsent(level, ignored -> new HashSet<>()).add(chunk.getPos().pack());
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            var keys = PENDING.remove(level); if (keys == null) return;
            for (long packed : keys) {
                var pos = ChunkPos.unpack(packed); var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
                if (chunk == null) continue;
                for (var be : List.copyOf(chunk.getBlockEntities().values()))
                    if (be instanceof RedstoneLinkBlockEntity link) link.revalidateNetwork();
            }
        });
        ServerLevelEvents.UNLOAD.register((server, level) -> PENDING.remove(level));
    }
    private RedstoneLinkLifecycle() {}
}

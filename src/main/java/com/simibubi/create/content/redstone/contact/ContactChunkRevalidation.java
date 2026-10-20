package com.simibubi.create.content.redstone.contact;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** Defer until FULL conversion returned; inspect available palettes without creating any chunks. */
public final class ContactChunkRevalidation {
    private static final Map<ServerLevel, Set<Long>> pending = new WeakHashMap<>();
    public static void register() {
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level, chunk, before, after) -> {
            if (after.isOrAfter(FullChunkStatus.FULL) && !before.isOrAfter(FullChunkStatus.FULL)) pending.computeIfAbsent(level, key -> new HashSet<>()).add(chunk.getPos().pack());
        });
        ServerTickEvents.END_LEVEL_TICK.register(ContactChunkRevalidation::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> pending.remove(level));
    }
    private static void tick(ServerLevel level) {
        var work = pending.remove(level); if (work == null) return;
        var affected = new HashSet<Long>();
        for (long packed : work) { var pos = ChunkPos.unpack(packed); affected.add(packed); affected.add(ChunkPos.pack(pos.x() + 1, pos.z())); affected.add(ChunkPos.pack(pos.x() - 1, pos.z())); affected.add(ChunkPos.pack(pos.x(), pos.z() + 1)); affected.add(ChunkPos.pack(pos.x(), pos.z() - 1)); }
        for (long packed : affected) {
            var position = ChunkPos.unpack(packed); var chunk = level.getChunkSource().getChunkNow(position.x(), position.z()); if (chunk == null) continue;
            var sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                var section = sections[index]; if (!section.maybeHas(state -> state.getBlock() instanceof RedstoneContactBlock)) continue;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z); if (!(state.getBlock() instanceof RedstoneContactBlock)) continue;
                    var pos = new BlockPos(position.getMinBlockX() + x, chunk.getMinY() + index * 16 + y, position.getMinBlockZ() + z);
                    RedstoneContactBlock.refreshWireConnections(level, pos);
                    level.scheduleTick(pos, state.getBlock(), 1);
                }
            }
        }
    }
    private ContactChunkRevalidation() {}
}

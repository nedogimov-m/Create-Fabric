package com.simibubi.create.content.contraptions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.*;

/** Real level SavedData for world-owned pending cells. Contains no structure, BE NBT or cargo. */
public final class DeferredContraptionRestoration extends SavedData {
    private record Entry(UUID id, List<BlockPos> positions) {
        private Entry { positions = positions.stream().map(BlockPos::immutable).distinct().toList(); }
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(UUIDUtil.CODEC.fieldOf("Id").forGetter(Entry::id), BlockPos.CODEC.listOf().fieldOf("Positions").forGetter(Entry::positions)).apply(instance, Entry::new));
    }
    public static final Codec<DeferredContraptionRestoration> CODEC = Entry.CODEC.listOf().fieldOf("Pending").codec().xmap(DeferredContraptionRestoration::new, data -> List.copyOf(data.entries));
    public static final SavedDataType<DeferredContraptionRestoration> TYPE = new SavedDataType<>(Create.asResource("deferred_contraption_restoration"), DeferredContraptionRestoration::new, CODEC, null);
    private final List<Entry> entries = new ArrayList<>();
    private final Set<BlockPos> pendingCells = new HashSet<>();
    private boolean processing;
    public DeferredContraptionRestoration() {}
    private DeferredContraptionRestoration(List<Entry> entries) { this.entries.addAll(entries); rebuildIndex(); }
    private static DeferredContraptionRestoration get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    private void rebuildIndex() { pendingCells.clear(); for (var entry : entries) pendingCells.addAll(entry.positions()); }
    /** Source recapture must wait for the previous world-transfer finalization to complete. */
    public static boolean isPending(Level level, BlockPos pos) { return level instanceof ServerLevel server && get(server).pendingCells.contains(pos); }
    public static void finishOrQueue(ServerLevel level, Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        var data = get(level); var entry = new Entry(UUID.randomUUID(), List.copyOf(positions)); data.entries.add(entry); data.pendingCells.addAll(entry.positions()); data.setDirty();
        // Index before callbacks: another capture cannot steal newly restored cells mid-finalization.
        if (data.processing) return; data.processing = true;
        try { if (Contraption.finishPairedRestoration(level, entry.positions())) { data.entries.remove(entry); data.rebuildIndex(); data.setDirty(); } }
        finally { data.processing = false; }
    }
    /** Native end-world tick. Snapshots tolerate genuine neighbor callbacks enqueuing another graph. */
    public static void tick(ServerLevel level) {
        var data = get(level); if (data.processing) return; data.processing = true;
        try {
            for (var entry : List.copyOf(data.entries)) if (data.entries.contains(entry) && Contraption.finishPairedRestoration(level, entry.positions())) { data.entries.remove(entry); data.rebuildIndex(); data.setDirty(); }
        } finally { data.processing = false; }
    }
}

package com.simibubi.create.foundation.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Actual native visible holder directory; schematic finish updates never request a new chunk. */
@Mixin(ChunkMap.class)
public interface SchematicLoadedChunksAccessor {
    @Accessor("visibleChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> create$schematicVisibleChunks();
}

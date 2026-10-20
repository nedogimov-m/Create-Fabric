package com.simibubi.create.test.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Observe native I/O and pending unload state without changing either. */
@Mixin(PersistentEntitySectionManager.class)
public interface GameTestEntityLoadStateAccessor {
    @Accessor("chunkLoadStatuses") Long2ObjectMap<?> create$testEntityLoadStatuses();
    @Accessor("chunksToUnload") LongSet create$testEntityChunksToUnload();
}

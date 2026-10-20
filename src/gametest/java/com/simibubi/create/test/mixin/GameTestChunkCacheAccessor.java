package com.simibubi.create.test.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only timeout diagnostics use the already existing native ticket store. */
@Mixin(ServerChunkCache.class)
public interface GameTestChunkCacheAccessor {
    @Accessor("ticketStorage") TicketStorage create$testTicketStorage();
}

package com.simibubi.create.foundation.mixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ChunkMap.class)
public interface NeoForgeWorldChunkTestAccessor {
    @Invoker("upgradeChunkTag") CompoundTag create$upgradeActualOriginalChunk(CompoundTag original);
}

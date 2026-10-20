package com.simibubi.create.foundation.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
public interface EntitySectionVisibilityAccessor {
    @Accessor("chunkVisibility") Long2ObjectMap<Visibility> create$getChunkVisibility();
}

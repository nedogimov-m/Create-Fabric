package com.simibubi.create.foundation.mixin.accessor;

import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only proof of the exact native source, including hidden sections being saved/unloaded. */
@Mixin(PersistentEntitySectionManager.class)
public interface CarriageNativeSourceManagerAccessor {
    @Accessor("knownUuids") Set<UUID> create$getKnownSourceUuids();
    @Accessor("sectionStorage") EntitySectionStorage<Entity> create$getSourceSections();
    @Accessor("permanentStorage") EntityPersistentStorage<Entity> create$getPermanentSourceStorage();
}

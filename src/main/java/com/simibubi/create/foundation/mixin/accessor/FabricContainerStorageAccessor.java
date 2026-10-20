package com.simibubi.create.foundation.mixin.accessor;

import net.fabricmc.fabric.impl.transfer.item.ContainerStorageImpl;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Actual26.3 Fabric storage backend identity; the public ContainerStorage API does not expose it. */
@Mixin(value = ContainerStorageImpl.class, remap = false)
public interface FabricContainerStorageAccessor {
    @Accessor(value = "container", remap = false) Container create$getContainer();
}

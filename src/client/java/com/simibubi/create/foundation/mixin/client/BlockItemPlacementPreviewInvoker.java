package com.simibubi.create.foundation.mixin.client;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Calls the real read-only native state/survival/collision check; never calls place or useOn. */
@Mixin(BlockItem.class)
public interface BlockItemPlacementPreviewInvoker {
    @Invoker("getPlacementState")
    @Nullable BlockState create$kineticPreviewPlacementState(BlockPlaceContext context);
}

package com.simibubi.create.foundation.blockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Source public extension point for combining schematic block-entity data. */
public interface IMergeableBE {
    void accept(BlockEntity other);
}

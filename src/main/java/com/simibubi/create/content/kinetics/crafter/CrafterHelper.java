package com.simibubi.create.content.kinetics.crafter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class CrafterHelper {
    public static MechanicalCrafterBlockEntity getCrafter(Level level, BlockPos pos) { return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity crafter ? crafter : null; }
    private CrafterHelper() {}
}

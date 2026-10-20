package com.simibubi.create.content.decoration.copycat;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class CopycatSpecialCases {
    public static boolean isBarsMaterial(BlockState material){return material.getBlock() instanceof IronBarsBlock&&!(material.getBlock() instanceof StainedGlassPaneBlock)&&material.getBlock()!=Blocks.GLASS_PANE;}
    public static boolean isTrapdoorMaterial(BlockState material){return material.getBlock() instanceof TrapDoorBlock&&material.hasProperty(TrapDoorBlock.HALF)&&material.hasProperty(TrapDoorBlock.OPEN)&&material.hasProperty(TrapDoorBlock.FACING);}
    private CopycatSpecialCases(){}
}

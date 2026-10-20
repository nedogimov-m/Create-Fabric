package com.simibubi.create.content.equipment.toolbox;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** Source UncontainableBlockItem contract prevents recursive toolbox custody. */
public final class ToolboxBlockItem extends BlockItem {
    public ToolboxBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public boolean canFitInsideContainerItems(){return false;}
}

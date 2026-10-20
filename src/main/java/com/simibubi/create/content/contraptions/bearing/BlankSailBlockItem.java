package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.AllBlocks;
import java.util.Map;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

/** All colored canvas blocks share the white sail item, matching their source loot identity. */
public class BlankSailBlockItem extends BlockItem {
    public BlankSailBlockItem(Block block, Properties properties) { super(block, properties); }
    @Override public void registerBlocks(Map<Block, Item> blocks, Item item) {
        super.registerBlocks(blocks, item); var colors = AllBlocks.DYED_SAILS;
        if (colors != null) for (var sail : colors.values()) blocks.put(sail, item);
    }
    /** Call once after the colored blocks exist; white item registration precedes their creation. */
    public static void registerColoredAliases() { for (var sail : AllBlocks.DYED_SAILS.values()) Item.BY_BLOCK.put(sail, AllBlocks.SAIL.asItem()); }
}

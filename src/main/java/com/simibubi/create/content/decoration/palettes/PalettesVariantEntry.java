package com.simibubi.create.content.decoration.palettes;
import java.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
/** Actual six native pattern blocks plus twelve native partials for one source stone type. */
public class PalettesVariantEntry {
    public final List<Block> registeredBlocks,registeredPartials;
    public PalettesVariantEntry(String name,AllPaletteStoneTypes stone){var blocks=new ArrayList<Block>();var partials=new ArrayList<Block>();var base=stone.baseBlock.get();for(var pattern:stone.variantTypes){var block=PaletteStoneRegistry.register(pattern.createName(name),Properties.ofFullCopy(base).requiresCorrectToolForDrops(),pattern.getBlockFactory());blocks.add(block);for(var partial:pattern.getPartials())partials.add(partial.create(name,pattern,block));}registeredBlocks=List.copyOf(blocks);registeredPartials=List.copyOf(partials);}
}

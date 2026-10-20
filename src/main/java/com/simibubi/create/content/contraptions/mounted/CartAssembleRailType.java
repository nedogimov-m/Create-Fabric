package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.AllBlocks;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Source rail choices, resolved lazily so actual native registration does not expose an uninitialized block. */
public enum CartAssembleRailType implements StringRepresentable {
    REGULAR(()->Blocks.RAIL),POWERED_RAIL(()->Blocks.POWERED_RAIL),DETECTOR_RAIL(()->Blocks.DETECTOR_RAIL),ACTIVATOR_RAIL(()->Blocks.ACTIVATOR_RAIL),CONTROLLER_RAIL(()->AllBlocks.CONTROLLER_RAIL);
    private final Supplier<Block> rail;
    CartAssembleRailType(Supplier<Block> rail){this.rail=rail;}
    public Block getBlock(){return rail.get();}
    public Item getItem(){return getBlock().asItem();}
    public boolean matches(BlockState state){return state.is(getBlock());}
    @Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
}

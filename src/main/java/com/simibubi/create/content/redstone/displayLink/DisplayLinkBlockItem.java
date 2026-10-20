package com.simibubi.create.content.redstone.displayLink;

import net.minecraft.world.level.block.Block;

public final class DisplayLinkBlockItem extends ClickToLinkBlockItem {
    public static final int DEFAULT_RANGE=64;
    public DisplayLinkBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public int getMaxDistanceFromSelection(){return com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.displayLinkRange.get();}
    @Override public String getMessageTranslationKey(){return "display_link";}
}

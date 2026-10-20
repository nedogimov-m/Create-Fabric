package com.simibubi.create.content.decoration.palettes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
public class ConnectedGlassPaneBlock extends GlassPaneBlock {
 public ConnectedGlassPaneBlock(Properties properties){super(properties);}
 @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction side){return side.getAxis().isVertical()?adjacent==state:super.skipRendering(state,adjacent,side);}
}

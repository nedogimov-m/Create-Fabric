package com.simibubi.create.content.decoration.palettes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
/** Original solid window culling; the translucent source flag affects side-face removal. */
public class WindowBlock extends ConnectedGlassBlock {
 protected final boolean translucent;
 public WindowBlock(Properties properties,boolean translucent){super(properties);this.translucent=translucent;}
 public boolean isTranslucent(){return translucent;}
 @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction side){if(state.getBlock()==adjacent.getBlock())return true;if(state.getBlock() instanceof WindowBlock window&&adjacent.getBlock() instanceof ConnectedGlassBlock)return !window.isTranslucent()&&side.getAxis().isHorizontal();return super.skipRendering(state,adjacent,side);}
}

package com.simibubi.create.content.decoration.palettes;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Source connected glass culls internal faces across its family, while retaining full collision. */
public class ConnectedGlassBlock extends TransparentBlock {
    public ConnectedGlassBlock(Properties properties){super(properties.noOcclusion().isValidSpawn((state,level,pos,type)->false).isRedstoneConductor((state,level,pos)->false).isSuffocating((state,level,pos)->false).isViewBlocking((state,level,pos,bounds)->false));}
    @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction side){return adjacent.getBlock()instanceof ConnectedGlassBlock||super.skipRendering(state,adjacent,side);}
}

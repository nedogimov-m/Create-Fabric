package com.simibubi.create.content.contraptions.mounted;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;

/** Source itemless rendered anchor helper; the real world assembler remains in place. */
public final class MinecartAnchorBlock extends Block {
    public MinecartAnchorBlock(Properties properties){super(properties);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_AXIS);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return Shapes.empty();}
}

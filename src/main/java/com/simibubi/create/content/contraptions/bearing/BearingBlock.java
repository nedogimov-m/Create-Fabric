package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public abstract class BearingBlock extends DirectionalKineticBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    protected BearingBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(FACING).getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction side){return side==state.getValue(FACING).getOpposite();}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.setValue(FACING,mirror.mirror(state.getValue(FACING)));}
}

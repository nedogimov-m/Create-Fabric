package com.simibubi.create.content.decoration;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class CardboardBlock extends Block {
    public CardboardBlock(Properties properties){super(properties.strength(.2f).mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN).sound(SoundType.CHISELED_BOOKSHELF).ignitedByLava());registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,Direction.Axis.X));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_AXIS);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,context.getHorizontalDirection().getAxis());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(BlockStateProperties.HORIZONTAL_AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,state.getValue(BlockStateProperties.HORIZONTAL_AXIS))).getAxis());}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state;}
}

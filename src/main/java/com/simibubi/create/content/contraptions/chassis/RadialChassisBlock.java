package com.simibubi.create.content.contraptions.chassis;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class RadialChassisBlock extends AbstractChassisBlock {
    public static final BooleanProperty STICKY_NORTH=BooleanProperty.create("sticky_north"),STICKY_SOUTH=BooleanProperty.create("sticky_south"),STICKY_EAST=BooleanProperty.create("sticky_east"),STICKY_WEST=BooleanProperty.create("sticky_west");
    public RadialChassisBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(STICKY_NORTH,false).setValue(STICKY_SOUTH,false).setValue(STICKY_EAST,false).setValue(STICKY_WEST,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder.add(STICKY_NORTH,STICKY_SOUTH,STICKY_EAST,STICKY_WEST));}
    @Override public BooleanProperty getGlueableSide(BlockState state,Direction face){
        var axis=state.getValue(AXIS);if(face.getAxis()==axis)return null;
        if(axis==Direction.Axis.X)return switch(face){case NORTH->STICKY_WEST;case SOUTH->STICKY_EAST;case UP->STICKY_NORTH;case DOWN->STICKY_SOUTH;default->null;};
        return switch(face){case NORTH,UP->STICKY_NORTH;case SOUTH,DOWN->STICKY_SOUTH;case EAST->STICKY_EAST;case WEST->STICKY_WEST;};
    }
}

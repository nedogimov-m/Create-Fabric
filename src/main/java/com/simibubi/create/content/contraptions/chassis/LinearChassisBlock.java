package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class LinearChassisBlock extends AbstractChassisBlock {
    public static final BooleanProperty STICKY_TOP=BooleanProperty.create("sticky_top"),STICKY_BOTTOM=BooleanProperty.create("sticky_bottom");
    public LinearChassisBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(STICKY_TOP,false).setValue(STICKY_BOTTOM,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder.add(STICKY_TOP,STICKY_BOTTOM));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        if(context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown())return super.getStateForPlacement(context);
        var previous=LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        return defaultBlockState().setValue(AXIS,isChassis(previous)?previous.getValue(AXIS):context.getNearestLookingDirection().getAxis());
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){
        var property=getGlueableSide(state,side);return property!=null&&sameKind(state,other)&&state.getValue(AXIS)==other.getValue(AXIS)?state.setValue(property,false):state;
    }
    @Override public BooleanProperty getGlueableSide(BlockState state,Direction face){return face.getAxis()!=state.getValue(AXIS)?null:face.getAxisDirection()==Direction.AxisDirection.POSITIVE?STICKY_TOP:STICKY_BOTTOM;}
    @Override public boolean glueAllowedOnSide(BlockGetter level,BlockPos pos,BlockState state,Direction side){var other=LoadedLevelAccess.blockState(level,pos.relative(side));return !sameKind(state,other)||state.getValue(AXIS)!=other.getValue(AXIS);}
    public static boolean isChassis(BlockState state){return state.getBlock() instanceof LinearChassisBlock;}
    public static boolean sameKind(BlockState first,BlockState second){return first.getBlock()==second.getBlock();}
}

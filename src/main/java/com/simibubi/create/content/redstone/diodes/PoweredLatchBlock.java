package com.simibubi.create.content.redstone.diodes;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.ticks.TickPriority;

/** Back sets; either perpendicular side resets. Edge ordering is pinned source ordering. */
public final class PoweredLatchBlock extends ToggleLatchBlock {
    public static final BooleanProperty POWERED_SIDE=BooleanProperty.create("powered_side");
    public PoweredLatchBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(POWERED_SIDE,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(POWERED_SIDE);}
    private boolean isPoweredOnSides(Level level,BlockPos pos,BlockState state){
        var facing=state.getValue(FACING);var view=new DiodeSignalAccess(level);
        for(var direction:new Direction[]{facing.getClockWise(),facing.getCounterClockWise()}){var next=pos.relative(direction);if(view.getSignal(next,direction)>0)return true;var block=view.getBlockState(next);if(block.is(Blocks.REDSTONE_WIRE)&&block.getValue(RedstoneWireBlock.POWER)>0)return true;}return false;
    }
    @Override protected void checkTickOnNeighbor(Level level,BlockPos pos,BlockState state){
        boolean back=state.getValue(POWERED),side=state.getValue(POWERED_SIDE);
        if(level.getBlockTicks().willTickThisTick(pos,this)||back==shouldTurnOn(level,pos,state)&&side==isPoweredOnSides(level,pos,state))return;
        var priority=shouldPrioritize(level,pos,state)?TickPriority.EXTREMELY_HIGH:side||back?TickPriority.VERY_HIGH:TickPriority.HIGH;level.scheduleTick(pos,this,getDelay(state),priority);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        boolean back=state.getValue(POWERED),shouldBack=shouldTurnOn(level,pos,state),side=state.getValue(POWERED_SIDE),shouldSide=isPoweredOnSides(level,pos,state);var next=state;
        if(back!=shouldBack){next=next.setValue(POWERED,shouldBack);if(shouldBack)next=next.setValue(POWERING,true);else if(side)next=next.setValue(POWERING,false);}
        if(side!=shouldSide){next=next.setValue(POWERED_SIDE,shouldSide);if(shouldSide)next=next.setValue(POWERING,false);else if(back)next=next.setValue(POWERING,true);}
        if(next!=state)writeState(level,pos,next);
    }
    @Override protected InteractionResult activated(Level level,BlockPos pos,BlockState state){return state.getValue(POWERED)!=state.getValue(POWERED_SIDE)?InteractionResult.PASS:super.activated(level,pos,state);}
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=null&&side.getAxis().isHorizontal();}
}

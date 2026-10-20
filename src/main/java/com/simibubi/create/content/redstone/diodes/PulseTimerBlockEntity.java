package com.simibubi.create.content.redstone.diodes;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Autonomous pinned source clock; a physical back input resets/suppresses it. */
public final class PulseTimerBlockEntity extends BrassDiodeBlockEntity {
    public PulseTimerBlockEntity(BlockPos pos,BlockState block){super(AllBlockEntityTypes.PULSE_TIMER,pos,block,20);}
    @Override protected void updateState(boolean powered,boolean powering,boolean atMax,boolean atMin){
        if(powered||state>=maxState-1)state=0;else state++;
        if(level.isClientSide())return;
        boolean shouldPower=!powered&&(maxState==2?state==0:state<=1);
        if(powering!=shouldPower)setPowering(shouldPower);
    }
}

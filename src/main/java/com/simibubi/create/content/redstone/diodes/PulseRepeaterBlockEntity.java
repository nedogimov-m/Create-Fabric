package com.simibubi.create.content.redstone.diodes;
import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class PulseRepeaterBlockEntity extends BrassDiodeBlockEntity {
    public PulseRepeaterBlockEntity(BlockPos pos,BlockState block){super(AllBlockEntityTypes.PULSE_REPEATER,pos,block);}
    @Override protected void updateState(boolean powered,boolean powering,boolean atMax,boolean atMin){
        if(atMin&&!powered)return;
        if(state>maxState+1){if(!powered&&!powering)state=0;return;}
        state++;
        if(level.isClientSide())return;
        if(state==maxState-1&&!powering)setPowering(true);
        if(state==maxState+1&&powering)setPowering(false);
    }
}

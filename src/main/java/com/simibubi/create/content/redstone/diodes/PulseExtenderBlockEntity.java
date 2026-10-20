package com.simibubi.create.content.redstone.diodes;
import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class PulseExtenderBlockEntity extends BrassDiodeBlockEntity {
    public PulseExtenderBlockEntity(BlockPos pos,BlockState block){super(AllBlockEntityTypes.PULSE_EXTENDER,pos,block);}
    @Override protected void updateState(boolean powered,boolean powering,boolean atMax,boolean atMin){
        if(atMin&&!powered)return;
        if(atMin||powered){setPowering(true);state=maxState;return;}
        if(state==1){if(powering)setPowering(false);if(!powered)state=0;return;}
        if(!powered)state--;
    }
}

package com.simibubi.create.content.kinetics.gantry;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;

/** The rack's actual RPM controls translation; powered racks drive stationary pinions instead. */
public class GantryShaftBlockEntity extends KineticBlockEntity {
    @Override protected boolean syncSequenceContext(){return true;}
    @Override protected boolean isNoisy() { return false; }
    public GantryShaftBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.GANTRY_SHAFT,pos,state);}
    public float getPinionMovementSpeed(){return getBlockState().getBlock() instanceof GantryShaftBlock?Math.clamp(-getSpeed()/512f,-.49f,.49f):0;}
    public boolean canAssembleOn(){
        var state=getBlockState();if(!(state.getBlock() instanceof GantryShaftBlock)||state.getValue(GantryShaftBlock.POWERED))return false;
        float speed=getPinionMovementSpeed();return switch(state.getValue(GantryShaftBlock.PART)){case START->speed>0;case END->speed<0;case MIDDLE->speed!=0;case SINGLE->false;};
    }
    public void checkAttachedCarriageBlocks(){
        if(level==null||level.isClientSide()||!canAssembleOn())return;var axis=getBlockState().getValue(GantryShaftBlock.FACING).getAxis();
        for(var face:Direction.values()){
            if(face.getAxis()==axis)continue;var pos=worldPosition.relative(face);if(!LoadedLevelAccess.isLoaded(level,pos))continue;
            var state=LoadedLevelAccess.blockState(level,pos);
            if(state.getBlock() instanceof GantryCarriageBlock&&state.getValue(GantryCarriageBlock.FACING)==face&&LoadedLevelAccess.blockEntity(level,pos) instanceof GantryCarriageBlockEntity carriage)carriage.queueAssembly();
        }
    }
    @Override public void onSpeedChanged(float previous){super.onSpeedChanged(previous);checkAttachedCarriageBlocks();}
    @Override protected void onSequenceContextChanged(SequenceContext previous,SequencePhase previousPhase){super.onSequenceContextChanged(previous,previousPhase);checkAttachedCarriageBlocks();}
}

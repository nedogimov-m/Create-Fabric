package com.simibubi.create.content.fluids.pipes.valve;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class FluidValveBlockEntity extends KineticBlockEntity implements FluidTransportBehaviour.Provider,RenderDataBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"water_supply","cross_streams","honey_drain");
    private float pointer,previousPointer;
    private float lastSpeed=Float.NaN;
    private BlockState previousState;
    private final FluidTransportBehaviour transport=new FluidTransportBehaviour(this){
        @Override public boolean canHaveFlowToward(BlockState state,Direction side){return state.getBlock() instanceof FluidValveBlock&&FluidValveBlock.getPipeAxis(state)==side.getAxis();}
        @Override public boolean canPullFluidFrom(FluidStack fluid,BlockState state,Direction side){return state.hasProperty(FluidValveBlock.ENABLED)&&state.getValue(FluidValveBlock.ENABLED);}
    };
    public FluidValveBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.FLUID_VALVE,pos,state);}
    public float getPointer(float partialTick){return previousPointer+(pointer-previousPointer)*partialTick;}
    @Override public FluidTransportBehaviour transport(){return getBehaviour(FluidTransportBehaviour.TYPE);}
    @Override public Object getRenderData(){return transport.getRenderData();}
    public static void tick(Level level,BlockPos pos,BlockState state,FluidValveBlockEntity valve){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, valve)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,valve);valve.previousPointer=valve.pointer;
        float speed=valve.getSpeed(),step=Math.clamp(Math.abs(speed)/320,0,1),target=speed>0?1:0;
        valve.pointer+=Math.clamp(target-valve.pointer,-step,step);
        if(!level.isClientSide()){
            if(valve.lastSpeed!=speed){valve.lastSpeed=speed;valve.sync();}
            if(valve.previousState!=state){valve.previousState=state;FluidPropagator.propagateChangedPipe(level,pos,state);}
            if(valve.pointer==0&&state.getValue(FluidValveBlock.ENABLED))level.setBlockAndUpdate(pos,state.setValue(FluidValveBlock.ENABLED,false));
            else if(valve.pointer==1&&!state.getValue(FluidValveBlock.ENABLED))level.setBlockAndUpdate(pos,state.setValue(FluidValveBlock.ENABLED,true));
            if(valve.pointer!=valve.previousPointer)valve.setChanged();
        }
        
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);var data=out.child("Pointer");data.putFloat("Value",pointer);data.putFloat("Target",getSpeed()>0?1:0);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);float value=in.childOrEmpty("Pointer").getFloatOr("Value",0);pointer=previousPointer=Float.isFinite(value)?Math.clamp(value,0,1):0;previousState=null;lastSpeed=Float.NaN;}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (transport != null) behaviours.add(transport);
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}

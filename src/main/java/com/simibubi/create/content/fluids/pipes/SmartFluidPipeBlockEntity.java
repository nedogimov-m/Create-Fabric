package com.simibubi.create.content.fluids.pipes;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.foundation.fluid.*;
import net.minecraft.core.*;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class SmartFluidPipeBlockEntity extends FluidPipeBlockEntity implements Clearable {
    private final FluidFilter fluidFilter=new FluidFilter();
    public final com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour filtering=
        new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour(this).forFluids()
            .withCallback(sample->{fluidFilter.setFilter(sample);filterChanged();});
    public SmartFluidPipeBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SMART_FLUID_PIPE,pos,state);}
    @Override protected FluidTransportBehaviour createTransport(){return new FluidTransportBehaviour(this){
        @Override public boolean canHaveFlowToward(BlockState state,Direction side){return state.getBlock() instanceof SmartFluidPipeBlock&&SmartFluidPipeBlock.getPipeAxis(state)==side.getAxis();}
        @Override public boolean canPullFluidFrom(FluidStack fluid,BlockState state,Direction side){return fluid.isEmpty()||fluidFilter!=null&&fluidFilter.test(level,fluid);}
    };}
    public ItemStack getFilter(){return filtering.getFilter();}
    public boolean setFilter(ItemStack stack){
        return filtering.setFilter(stack);
    }
    private void filterChanged(){setChanged();updateNetwork();if(level!=null&&!level.isClientSide()){FluidPropagator.propagateChangedPipe(level,worldPosition,getBlockState());transport().sync();}}
    public boolean test(FluidStack fluid){return fluidFilter.test(level,fluid);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours){super.addNativeBehaviours(behaviours);if(filtering!=null)behaviours.add(filtering);}
    @Override public void clearContent(){setFilter(ItemStack.EMPTY);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);fluidFilter.setFilter(filtering.getFilter());}
}

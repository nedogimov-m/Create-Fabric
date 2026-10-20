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
    private final FluidFilter filter=new FluidFilter();
    public SmartFluidPipeBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SMART_FLUID_PIPE,pos,state);}
    @Override protected FluidTransportBehaviour createTransport(){return new FluidTransportBehaviour(this){
        @Override public boolean canHaveFlowToward(BlockState state,Direction side){return state.getBlock() instanceof SmartFluidPipeBlock&&SmartFluidPipeBlock.getPipeAxis(state)==side.getAxis();}
        @Override public boolean canPullFluidFrom(FluidStack fluid,BlockState state,Direction side){return fluid.isEmpty()||filter!=null&&filter.test(level,fluid);}
    };}
    public ItemStack getFilter(){return filter.getFilter();}
    public boolean setFilter(ItemStack stack){
        if(!filter.setFilter(stack))return false;setChanged();updateNetwork();if(level!=null&&!level.isClientSide()){FluidPropagator.propagateChangedPipe(level,worldPosition,getBlockState());transport().sync();}return true;
    }
    public boolean test(FluidStack fluid){return filter.test(level,fluid);}
    @Override public void clearContent(){setFilter(ItemStack.EMPTY);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);filter.save(out);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);filter.load(in);}
}

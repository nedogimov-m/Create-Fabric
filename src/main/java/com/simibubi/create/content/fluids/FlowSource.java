package com.simibubi.create.content.fluids;

import java.util.function.Predicate;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.createmod.catnip.math.BlockFace;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class FlowSource {
    protected final BlockFace location;
    protected Level world;
    public FlowSource(BlockFace location) { this.location=location; }
    public FluidStack provideFluid(Predicate<FluidStack> predicate) {
        var handler=provideHandler();if(handler==null)return FluidStack.EMPTY;
        try(var tx=Transaction.openOuter()) {
            for(var view:handler) {
                if(view.isResourceBlank()||view.getAmount()==0)continue;
                var fluid=new FluidStack(view.getResource(),81);
                if(predicate.test(fluid)&&view.extract(view.getResource(),81,tx)>0)return fluid;
            }
        }
        return FluidStack.EMPTY;
    }
    public abstract boolean isEndpoint();
    public void manageSource(Level world,BlockEntity owner){this.world=world;}
    public void whileFlowPresent(Level world,boolean pulling){}
    public Storage<FluidVariant> provideHandler(){return null;}
    public void keepAlive(){}
    public static final class FluidHandler extends FlowSource {
        public FluidHandler(BlockFace location){super(location);}
        @Override public boolean isEndpoint(){return true;}
        @Override public Storage<FluidVariant> provideHandler(){return world==null||!world.isLoaded(location.getConnectedPos())?null:FluidPropagator.fluidStorageAt(world,location.getConnectedPos(),location.getOppositeFace());}
    }
    public static final class OtherPipe extends FlowSource {
        public OtherPipe(BlockFace location){super(location);}
        @Override public boolean isEndpoint(){return false;}
        @Override public FluidStack provideFluid(Predicate<FluidStack> predicate){
            var pipe=world==null?null:FluidPropagator.getPipe(world,location.getConnectedPos());
            var fluid=pipe==null?FluidStack.EMPTY:pipe.getProvidedOutwardFluid(location.getOppositeFace());return predicate.test(fluid)?fluid:FluidStack.EMPTY;
        }
    }
    public static final class Blocked extends FlowSource {
        public Blocked(BlockFace location){super(location);}
        @Override public boolean isEndpoint(){return false;}
    }
}

package com.simibubi.create.content.kinetics.belt.behaviour;

import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Native transported-item callback API, with transactional built-in belt/depot adapters. */
public class TransportedItemStackHandlerBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<TransportedItemStackHandlerBehaviour> TYPE=new BehaviourType<TransportedItemStackHandlerBehaviour>();
    @FunctionalInterface public interface ProcessingCallback {void applyToAllItems(float distance,Function<TransportedItemStack,TransportedResult> function);}
    @FunctionalInterface public interface PositionGetter {Vec3 getWorldPositionVector(TransportedItemStack stack);}
    public static final class TransportedResult {
        private final List<TransportedItemStack> outputs;
        private final TransportedItemStack held;
        private static final TransportedResult DO_NOTHING=new TransportedResult(null,null),REMOVE=new TransportedResult(List.of(),null);
        private TransportedResult(List<TransportedItemStack> outputs,TransportedItemStack held){this.outputs=outputs==null?null:List.copyOf(outputs);this.held=held;}
        public static TransportedResult doNothing(){return DO_NOTHING;}
        public static TransportedResult removeItem(){return REMOVE;}
        public static TransportedResult convertTo(TransportedItemStack output){return convertTo(List.of(output));}
        public static TransportedResult convertTo(List<TransportedItemStack> outputs){return new TransportedResult(outputs,null);}
        public static TransportedResult convertToAndLeaveHeld(List<TransportedItemStack> outputs,TransportedItemStack held){return new TransportedResult(outputs,held);}
        public boolean doesNothing(){return outputs==null;}
        public boolean didntChangeFrom(ItemStack before){return doesNothing()||outputs.size()==1&&ItemStack.matches(outputs.getFirst().stack,before)&&!hasHeldOutput();}
        public List<TransportedItemStack> getOutputs(){if(doesNothing())throw new IllegalStateException("No output for an unchanged item");return outputs;}
        public boolean hasHeldOutput(){return held!=null;}
        public TransportedItemStack getHeldOutput(){if(held==null)throw new IllegalStateException("No held output");return held;}
    }
    private final ProcessingCallback callback;
    private PositionGetter position;
    public TransportedItemStackHandlerBehaviour(BlockEntity entity,ProcessingCallback callback){super(entity);this.callback=callback;position=stack->Vec3.atCenterOf(getPos());}
    public TransportedItemStackHandlerBehaviour withStackPlacement(PositionGetter getter){position=getter;return this;}
    public void handleProcessingOnAllItems(Function<TransportedItemStack,TransportedResult> function){handleCenteredProcessingOnAllItems(.51f,function);}
    public void handleProcessingOnItem(TransportedItemStack item,TransportedResult output){handleCenteredProcessingOnAllItems(.51f,stack->stack.id.equals(item.id)?output:null);}
    public void handleCenteredProcessingOnAllItems(float distance,Function<TransportedItemStack,TransportedResult> function){callback.applyToAllItems(distance,function);}
    public Vec3 getWorldPositionOf(TransportedItemStack stack){return position.getWorldPositionVector(stack);}
    @Override public BehaviourType<?> getType(){return TYPE;}
    private static boolean metadataChanged(TransportedItemStack before,TransportedItemStack after){
        if(before.beltPosition!=after.beltPosition||before.prevBeltPosition!=after.prevBeltPosition||before.processingTime!=after.processingTime||before.processedBy!=after.processedBy||!Objects.equals(before.unresolvedFanProcessingType,after.unresolvedFanProcessingType)||!before.pendingFanInput.equals(after.pendingFanInput)||before.pendingFanCount!=after.pendingFanCount||before.lastProcessingTick!=after.lastProcessingTick||before.angle!=after.angle||before.insertedAt!=after.insertedAt||before.insertedFrom!=after.insertedFrom||!Objects.equals(before.heldBy,after.heldBy))return true;
        var first=before.pendingFanResults;var second=after.pendingFanResults;if(first==null||second==null)return first!=second;if(first.size()!=second.size())return true;for(int i=0;i<first.size();i++)if(!ItemStack.matches(first.get(i),second.get(i)))return true;return false;
    }
    public static TransportedItemStackHandlerBehaviour createNative(BlockEntity entity){
        if(entity instanceof BeltBlockEntity belt)return new TransportedItemStackHandlerBehaviour(belt,(distance,function)->{
            var controller=belt.getControllerBE();if(controller==null||!controller.chainLoaded())return;
            var inventory=controller.getInventory();for(var saved:inventory.getTransportedItems()){
                var current=inventory.byId(saved.id);if(current==null||Math.abs(current.beltPosition-belt.index-.5f)>=distance)continue;
                var before=current.stack.copy();var result=function.apply(current);if(result==null||result.didntChangeFrom(before))continue;
                try(var tx=Transaction.openOuter()){if(inventory.applyTransportedResult(current.id,result,belt.index+.5f,tx))tx.commit();}
            }
        }).withStackPlacement(stack->{var controller=belt.getControllerBE();return controller==null?Vec3.atCenterOf(belt.getBlockPos()):BeltHelper.getVectorForOffset(controller,stack.beltPosition);});
        var depot = BlockEntityBehaviour.get(entity, com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
        if (depot != null) return depot.transportedHandler;
        return null;
    }
}

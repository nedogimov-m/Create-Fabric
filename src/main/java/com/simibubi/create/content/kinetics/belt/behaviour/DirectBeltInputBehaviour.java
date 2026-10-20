package com.simibubi.create.content.kinetics.belt.behaviour;

import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.function.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Direct machine input with native transaction ownership. Direction denotes item movement. */
public class DirectBeltInputBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<DirectBeltInputBehaviour> TYPE=new BehaviourType<DirectBeltInputBehaviour>();
    private Predicate<Direction> canInsert=side->true,occupied=side->false;
    private Supplier<Boolean> supports=()->false;
    private InsertionCallback insertion=this::defaultInsert;
    @FunctionalInterface public interface InsertionCallback {long insert(TransportedItemStack stack,Direction side,TransactionContext tx);}
    public DirectBeltInputBehaviour(BlockEntity entity){super(entity);}
    public DirectBeltInputBehaviour allowingBeltFunnels(){supports=()->true;return this;}
    public DirectBeltInputBehaviour allowingBeltFunnelsWhen(Supplier<Boolean> supplier){supports=supplier;return this;}
    public DirectBeltInputBehaviour onlyInsertWhen(Predicate<Direction> predicate){canInsert=predicate;return this;}
    public DirectBeltInputBehaviour considerOccupiedWhen(Predicate<Direction> predicate){occupied=predicate;return this;}
    public DirectBeltInputBehaviour setInsertionHandler(InsertionCallback handler){insertion=handler;return this;}
    public boolean canInsertFromSide(Direction side){return !blockEntity.isRemoved()&&canInsert.test(side);}
    public boolean canSupportBeltFunnels(){return supports.get();}
    public boolean isOccupied(Direction side){return occupied.test(side);}
    public long insert(ItemStack stack,Direction side,TransactionContext tx){return stack.isEmpty()||!canInsertFromSide(side)||isOccupied(side)?0:insertion.insert(new TransportedItemStack(stack.copy(),0),side,tx);}
    private long defaultInsert(TransportedItemStack stack,Direction side,TransactionContext tx){var storage=FunnelTransfer.find(getWorld(),getPos(),side.getOpposite());return storage==null?0:storage.insert(ItemVariant.of(stack.stack),stack.stack.getCount(),tx);}
    public ItemStack handleInsertion(ItemStack stack,Direction side,boolean simulate){
        try(var tx=Transaction.openOuter()){
            // Original source scene entrypoint invokes the actual insertion callback directly.
            boolean virtualOwner=blockEntity.isVirtual()&&!blockEntity.isRemoved()&&getWorld()!=null
                &&LoadedLevelAccess.blockEntity(getWorld(),getPos())==blockEntity;
            long accepted=stack.isEmpty()?0:virtualOwner
                ?insertion.insert(new TransportedItemStack(stack.copy(),0),side,tx)
                :insert(stack,side,tx);
            if(!simulate)tx.commit();
            return stack.copyWithCount(stack.getCount()-(int)accepted);
        }
    }
    public net.fabricmc.fabric.api.transfer.v1.storage.Storage<ItemVariant> exportingFunnel(Direction side){
        var pos=getPos().above();var state=LoadedLevelAccess.blockState(getWorld(),pos);
        if(!(state.getBlock() instanceof BeltFunnelBlock)||state.getValue(BeltFunnelBlock.SHAPE)!=BeltFunnelBlock.Shape.PULLING||side!=null&&AbstractFunnelBlock.getFunnelFacing(state)!=side||!(LoadedLevelAccess.blockEntity(getWorld(),pos) instanceof FunnelBlockEntity funnel))return null;
        return new net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage<>(){
            @Override public long insert(ItemVariant variant,long amount,TransactionContext tx){return funnel.insert(variant,amount,false,tx);}
        };
    }
    public ItemStack tryExportingToBeltFunnel(ItemStack stack,Direction side,boolean simulate){var target=exportingFunnel(side);if(target==null)return null;try(var tx=Transaction.openOuter()){long moved=stack.isEmpty()?0:target.insert(ItemVariant.of(stack),stack.getCount(),tx);if(!simulate)tx.commit();return stack.copyWithCount(stack.getCount()-(int)moved);}}
    @Override public BehaviourType<?> getType(){return TYPE;}
    public static DirectBeltInputBehaviour createNative(BlockEntity entity){
        if(entity instanceof com.simibubi.create.content.logistics.chute.ChuteBlockEntity chute)return new DirectBeltInputBehaviour(entity).onlyInsertWhen(side->chute.canDirectlyInsertCached());
        if(entity instanceof BeltBlockEntity belt)return new DirectBeltInputBehaviour(entity).onlyInsertWhen(side->belt.getSpeed()!=0&&BeltBlock.canTransportObjects(belt.getBlockState())&&belt.getMovementFacing()!=side.getOpposite());
        if(entity instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity)return new DirectBeltInputBehaviour(entity).allowingBeltFunnels();
        if(entity instanceof com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity)return new DirectBeltInputBehaviour(entity).allowingBeltFunnels().onlyInsertWhen(side->side.getAxis().isHorizontal());
        if(entity instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity)return new DirectBeltInputBehaviour(entity).allowingBeltFunnelsWhen(()->entity.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)==Direction.UP);
        if(entity instanceof com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity||entity instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity)return new DirectBeltInputBehaviour(entity);
        if(entity instanceof com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity)return new DirectBeltInputBehaviour(entity).onlyInsertWhen(side->{var direction=entity.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);return direction==Direction.DOWN||direction==side;});
        if(entity instanceof FunnelBlockEntity funnel)return new DirectBeltInputBehaviour(entity).onlyInsertWhen(side->funnel.getBlockState().getBlock() instanceof FunnelBlock&&AbstractFunnelBlock.getFunnelFacing(funnel.getBlockState())==Direction.UP&&!funnel.getBlockState().getValue(FunnelBlock.EXTRACTING)).setInsertionHandler((stack,side,tx)->funnel.insert(ItemVariant.of(stack.stack),stack.stack.getCount(),false,tx));
        return null;
    }
}

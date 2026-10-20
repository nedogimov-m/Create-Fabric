package com.simibubi.create.content.fluids.drain;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.fluid.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;

public final class ItemDrainBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"drain","chained_drain");
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){return containedFluidTooltip(tooltip,sneaking,tank);}
    public static final int FILLING_TIME=20;
    public static final long CAPACITY=1500*81L;
    public final SmartFluidTank tank=new SmartFluidTank(CAPACITY,fluid->changed());
    public final Storage<FluidVariant> fluidStorage=new ExtractionOnlyStorage<>(){
        @Override public long extract(FluidVariant fluid,long max,TransactionContext tx){return tank.extract(fluid,max,tx);}
        @Override public Iterator<StorageView<FluidVariant>> iterator(){return tank.iterator();}
    };
    private TransportedItemStack heldItem;
    public int processingTicks;
    private FluidStack processingFluid=FluidStack.EMPTY;
    private float previousLevel,fluidLevel;
    private record Contents(TransportedItemStack held,int ticks,FluidStack fluid){}
    private final SnapshotParticipant<Contents> contents=new SnapshotParticipant<>(){
        @Override protected Contents createSnapshot(){return new Contents(heldItem==null?null:heldItem.copy(),processingTicks,processingFluid.copy());}
        @Override protected void readSnapshot(Contents snapshot){heldItem=snapshot.held==null?null:snapshot.held.copy();processingTicks=snapshot.ticks;processingFluid=snapshot.fluid.copy();}
        @Override protected void onFinalCommit(){changed();}
    };
    public ItemDrainBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.ITEM_DRAIN,pos,state);}
    public ItemStack getHeldItemStack(){return heldItem==null?ItemStack.EMPTY:heldItem.stack.copy();}
    public TransportedItemStack getHeldItem(){return heldItem==null?null:heldItem.copy();}
    public FluidStack getProcessingFluid(){return processingFluid.copy();}
    public float getFluidLevel(float partial){return previousLevel+(fluidLevel-previousLevel)*partial;}
    private void changed(){setChanged();if(level!=null&&!level.isClientSide()){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}}
    public SingleSlotStorage<ItemVariant> storage(Direction side){return new SingleSlotStorage<>(){
        @Override public boolean isResourceBlank(){return heldItem==null||heldItem.stack.isEmpty();}
        @Override public ItemVariant getResource(){return isResourceBlank()?ItemVariant.blank():ItemVariant.of(heldItem.stack);}
        @Override public long getAmount(){return isResourceBlank()?0:heldItem.stack.getCount();}
        @Override public long getCapacity(){return isResourceBlank()?64:heldItem.stack.getMaxStackSize();}
        @Override public long insert(ItemVariant variant,long max,TransactionContext tx){return insertItem(variant,max,side,false,tx);}
        @Override public long extract(ItemVariant variant,long max,TransactionContext tx){
            StoragePreconditions.notBlankNotNegative(variant,max);
            if(!getResource().equals(variant)||max==0)return 0;
            long taken=Math.min(max,getAmount());contents.updateSnapshots(tx);heldItem.stack.shrink((int)taken);
            if(heldItem.stack.isEmpty()){heldItem=null;processingTicks=0;processingFluid=FluidStack.EMPTY;}return taken;
        }
    };}
    private long insertItem(ItemVariant variant,long max,Direction side,boolean centered,TransactionContext tx){
        StoragePreconditions.notBlankNotNegative(variant,max);if(heldItem!=null||max==0)return 0;
        var stack=variant.toStack();long accepted=Math.min(max,GenericItemEmptying.canItemBeEmptied(level,stack)?1:stack.getMaxStackSize());
        contents.updateSnapshots(tx);heldItem=new TransportedItemStack(variant.toStack((int)accepted),centered?0.5f:0);
        heldItem.insertedFrom=side.getOpposite();processingTicks=0;processingFluid=FluidStack.EMPTY;
        if(centered&&GenericItemEmptying.canItemBeEmptied(level,heldItem.stack)){processingTicks=FILLING_TIME;processingFluid=GenericItemEmptying.emptyItem(level,heldItem.stack,true).getFirst();}
        return accepted;
    }
    private boolean fits(FluidStack fluid){if(fluid.isEmpty())return false;try(var tx=Transaction.openOuter()){return tank.insert(fluid.variant(),fluid.getAmount(),tx)==fluid.getAmount();}}
    private boolean finishEmptying(){
        var preview=GenericItemEmptying.emptyItem(level,heldItem.stack,true);
        if(!fits(preview.getFirst()))return false;
        var input=heldItem.stack.copy();var result=GenericItemEmptying.emptyItem(level,input,false);var fluid=result.getFirst();
        if(fluid.isEmpty()||!input.isEmpty())return false;
        try(var tx=Transaction.openOuter()){
            if(tank.insert(fluid.variant(),fluid.getAmount(),tx)!=fluid.getAmount())return false;
            contents.updateSnapshots(tx);heldItem.stack=result.getSecond().copy();if(heldItem.stack.isEmpty())heldItem=null;
            processingFluid=fluid.copy();tx.addOuterCloseCallback(closeResult->{if(closeResult.wasCommitted())sourceAdvancements.award("drain");});tx.commit();
        }return true;
    }
    private void export(){
        var side=heldItem.insertedFrom;
        var above=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,worldPosition.above());
        if(above.getBlock() instanceof com.simibubi.create.content.logistics.funnel.BeltFunnelBlock&&above.getValue(com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.SHAPE)==com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape.PULLING){
            var facing=above.getValue(com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.HORIZONTAL_FACING);
            if(facing!=side.getOpposite()){
                exportWithoutFunnel();return;
            }
            if(LoadedLevelAccess.blockEntity(level,worldPosition.above()) instanceof com.simibubi.create.content.logistics.funnel.FunnelBlockEntity funnel){
                try(var tx=Transaction.openOuter()){
                    long moved=funnel.insert(ItemVariant.of(heldItem.stack),heldItem.stack.getCount(),false,tx);
                    if(moved>0){contents.updateSnapshots(tx);heldItem.stack.shrink((int)moved);if(heldItem.stack.isEmpty())heldItem=null;tx.commit();}
                }
            }
            return;
        }
        exportWithoutFunnel();
    }
    private void exportWithoutFunnel(){
        var side=heldItem.insertedFrom;var pos=worldPosition.relative(side);
        if(!level.isLoaded(pos))return;
        var state=LoadedLevelAccess.blockState(level,pos);
        var storage=com.simibubi.create.foundation.item.LoadedItemStorage.find(level,pos,side.getOpposite());
        if(storage!=null){
            try(var tx=Transaction.openOuter()){
                long moved=storage.insert(ItemVariant.of(heldItem.stack),heldItem.stack.getCount(),tx);
                if(moved>0){contents.updateSnapshots(tx);heldItem.stack.shrink((int)moved);if(heldItem.stack.isEmpty()){heldItem=null;if(LoadedLevelAccess.blockEntity(level,pos) instanceof ItemDrainBlockEntity)tx.addOuterCloseCallback(result->{if(result.wasCommitted())sourceAdvancements.award("chained_drain");});}tx.commit();}
            }return;
        }
        if(state.isFaceSturdy(level,pos,side.getOpposite()))return;
        var at=Vec3.atCenterOf(worldPosition).add(side.getStepX()*0.75,6f/16,side.getStepZ()*0.75);
        var item=new ItemEntity(level,at.x,at.y,at.z,heldItem.stack.copy(),side.getStepX()/8d,1/8d,side.getStepZ()/8d);item.setDefaultPickUpDelay();
        level.addFreshEntity(item);heldItem=null;changed();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ItemDrainBlockEntity drain){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, drain)) {
            if (!createTick.shouldTick()) return;

        drain.previousLevel=drain.fluidLevel;drain.fluidLevel+=(drain.tank.getAmount()/(float)CAPACITY-drain.fluidLevel)*0.25f;
        if((!level.isClientSide() || drain.isVirtual())&&drain.heldItem==null)for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(0,0.25,0))){
            if(entity.isRemoved()||entity.getY()<pos.getY()+0.7)continue;
            var movement=entity.getDeltaMovement();var direction=Math.abs(movement.x)>Math.abs(movement.z)?movement.x>0?Direction.EAST:Direction.WEST:movement.z>0?Direction.SOUTH:Direction.NORTH;
            try(var tx=Transaction.openOuter()){
                long accepted=drain.insertItem(ItemVariant.of(entity.getItem()),entity.getItem().getCount(),direction.getOpposite(),true,tx);
                if(accepted==0)continue;tx.commit();var remaining=entity.getItem().copy();remaining.shrink((int)accepted);if(remaining.isEmpty())entity.discard();else entity.setItem(remaining);
            }break;
        }
        if(drain.heldItem==null){drain.processingTicks=0;return;}
        drain.heldItem.prevBeltPosition=drain.heldItem.beltPosition;
        if(drain.processingTicks>0){
            if(level.isClientSide() && !drain.isVirtual()){if(drain.processingTicks<FILLING_TIME)drain.processingTicks--;return;}
            int previous=drain.processingTicks;
            if(drain.processingTicks>5){
                var preview=GenericItemEmptying.emptyItem(level,drain.heldItem.stack,true).getFirst();
                if(preview.isEmpty()){drain.processingTicks=0;drain.changed();return;}
                if(!drain.fits(preview)){drain.processingTicks=FILLING_TIME;if(previous!=FILLING_TIME)drain.changed();return;}
            }
            drain.processingTicks--;
            if(drain.processingTicks==5&&!drain.finishEmptying())drain.processingTicks=FILLING_TIME;
            if(previous==FILLING_TIME||drain.processingTicks==5)drain.changed();else drain.setChanged();return;
        }
        float previous=drain.heldItem.beltPosition;drain.heldItem.beltPosition=Math.min(1,previous+1/8f);
        if(previous<0.5f&&drain.heldItem.beltPosition>=0.5f){
            drain.heldItem.beltPosition=0.5f;
            if((!level.isClientSide() || drain.isVirtual())){
                var fluid=GenericItemEmptying.emptyItem(level,drain.heldItem.stack,true).getFirst();
                if(!fluid.isEmpty()){drain.processingTicks=FILLING_TIME;drain.processingFluid=fluid;drain.changed();}
            }
        }else if(drain.heldItem.beltPosition>=1&&(!level.isClientSide() || drain.isVirtual()))drain.export();
        if((!level.isClientSide() || drain.isVirtual()))drain.setChanged();
    
        }
    }
    public boolean emptyPlayerItem(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        var held=player.getItemInHand(hand);var preview=GenericItemEmptying.emptyItem(level,held,true);if(!fits(preview.getFirst()))return false;
        var copy=held.copy();var result=GenericItemEmptying.emptyItem(level,copy,false);
        try(var tx=Transaction.openOuter()){if(tank.insert(result.getFirst().variant(),result.getFirst().getAmount(),tx)!=result.getFirst().getAmount())return false;tx.commit();}
        if(!player.isCreative()){
            if(copy.isEmpty())player.setItemInHand(hand,result.getSecond());else{player.setItemInHand(hand,copy);player.getInventory().placeItemBackInInventory(result.getSecond(),net.minecraft.util.Prediction.SERVER_ONLY);}
        }return true;
    }
    public ItemStack takeHeldItem(){if(heldItem==null)return ItemStack.EMPTY;var result=heldItem.stack.copy();heldItem=null;processingTicks=0;processingFluid=FluidStack.EMPTY;changed();return result;}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()&&heldItem!=null){Block.popResource(level,worldPosition,heldItem.stack);heldItem=null;}super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);tank.save(out.child("Tank"));out.putInt("ProcessingTicks",processingTicks);if(heldItem!=null)out.store("HeldItem",TransportedItemStack.CODEC,heldItem);if(!processingFluid.isEmpty()){out.store("ProcessingFluid",FluidVariant.CODEC,processingFluid.variant());out.putLong("ProcessingAmount",processingFluid.getAmount());}}
    @Override protected void read(ValueInput in, boolean clientPacket){tank.load(in.childOrEmpty("Tank"));processingTicks=Math.clamp(in.getIntOr("ProcessingTicks",0),0,FILLING_TIME);heldItem=in.read("HeldItem",TransportedItemStack.CODEC).orElse(null);if(heldItem!=null){heldItem.beltPosition=heldItem.prevBeltPosition=Math.clamp(heldItem.beltPosition,0,1);if(heldItem.insertedFrom.getAxis().isVertical())heldItem.insertedFrom=Direction.SOUTH;}processingFluid=new FluidStack(in.read("ProcessingFluid",FluidVariant.CODEC).orElse(FluidVariant.blank()),Math.clamp(in.getLongOr("ProcessingAmount",0),0,FluidConstants.BUCKET));
        super.read(in, clientPacket);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (directBeltInput != null) behaviours.add(directBeltInput);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("drain");
        ids.add("chained_drain");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}

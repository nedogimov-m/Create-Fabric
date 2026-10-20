package com.simibubi.create.content.logistics.funnel;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Native Fabric transactions replace NeoForge item handlers; no inventory is owned by a funnel. */
public final class FunnelBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider, Clearable {
    public enum Mode { INVALID, PAUSED, COLLECT, PUSHING_TO_BELT, TAKING_FROM_BELT, EXTRACT }
    public static final int EXTRACTION_COOLDOWN=8;
    public final com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour inventoryTracker=new com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour(this);
    public final FilteringBehaviour filtering=new FilteringBehaviour(this).onlyActiveWhen(this::supportsFiltering).showCountWhen(this::supportsAmountOnFilter).withCallback(stack->inventoryTracker.reset());
    public int extractionCooldown;
    private WeakReference<net.minecraft.world.entity.Entity> lastObserved;
    private float previousFlap=.25f,flap=.25f;
    private long flapSequence;
    private boolean flapInward,receivedFlap;
    // LevelChunk recreates a BE on a change of block, including the normal/belt conversion.
    // This handoff is consumed synchronously by the new block factory. It retains no world or BE.
    private record Conversion(WeakReference<Level> level,BlockPos pos,boolean brass,CompoundTag tag) {}
    private static final ThreadLocal<Conversion> CONVERSION=new ThreadLocal<>();

    public FunnelBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.FUNNEL,pos,state);}
    public static FunnelBlockEntity create(BlockPos pos,BlockState state){
        var conversion=CONVERSION.get();CONVERSION.remove();
        if(conversion!=null&&conversion.pos.equals(pos)&&state.getBlock() instanceof AbstractFunnelBlock block&&block.brass==conversion.brass){
            var world=conversion.level.get();
            if(world!=null&&BlockEntity.loadStatic(pos,state,conversion.tag,world.registryAccess()) instanceof FunnelBlockEntity restored)return restored;
        }
        return new FunnelBlockEntity(pos,state);
    }
    public boolean supportsFiltering(){return getBlockState().getBlock() instanceof AbstractFunnelBlock block&&block.brass;}
    public boolean supportsAmountOnFilter(){
        var state=getBlockState();
        if(state.getBlock() instanceof FunnelBlock)return state.getValue(FunnelBlock.EXTRACTING);
        return state.getBlock() instanceof BeltFunnelBlock&&(state.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.PUSHING||level!=null&&LoadedLevelAccess.blockEntity(level,worldPosition.below()) instanceof BeltBlockEntity);
    }
    public int getAmountToExtract(){return !supportsAmountOnFilter()?64:filtering.isActive()?filtering.count:1;}
    public boolean isExact(){return supportsAmountOnFilter()&&filtering.isActive()&&!filtering.upTo;}
    public Mode determineCurrentMode(){
        var state=getBlockState();
        if(!(state.getBlock() instanceof AbstractFunnelBlock))return Mode.INVALID;
        if(state.getValue(AbstractFunnelBlock.POWERED))return Mode.PAUSED;
        if(state.getBlock() instanceof FunnelBlock)return state.getValue(FunnelBlock.EXTRACTING)?Mode.EXTRACT:Mode.COLLECT;
        return switch(state.getValue(BeltFunnelBlock.SHAPE)){
            case PULLING->Mode.TAKING_FROM_BELT;
            case PUSHING->Mode.PUSHING_TO_BELT;
            default->{
                if(level!=null&&LoadedLevelAccess.blockEntity(level,worldPosition.below()) instanceof BeltBlockEntity belt){
                    var controller=belt.getControllerBE();
                    if(controller!=null){var motion=controller.getDirectionAwareBeltMovementSpeed()>=0?controller.getBeltFacing():controller.getBeltFacing().getOpposite();yield motion==facing()?Mode.PUSHING_TO_BELT:Mode.TAKING_FROM_BELT;}
                }
                yield Mode.INVALID;
            }
        };
    }
    private Direction facing(){return AbstractFunnelBlock.getFunnelFacing(getBlockState());}
    public Storage<ItemVariant> attachedStorage(){var facing=facing();return level==null||facing==null?null:FunnelTransfer.find(level,worldPosition.relative(facing.getOpposite()),facing);}
    public long insert(ItemVariant variant,long amount,boolean exact,TransactionContext tx){
        StoragePreconditions.notBlankNotNegative(variant,amount);
        if(amount==0||level==null||isRemoved()||determineCurrentMode()==Mode.PAUSED||!filtering.test(variant.toStack()))return 0;
        var destination=attachedStorage();if(destination==null)return 0;
        try(var attempt=tx.openNested()){
            long accepted=destination.insert(variant,amount,attempt);
            if(accepted==0||exact&&accepted!=amount)return 0;
            attempt.addOuterCloseCallback(result->{if(result.wasCommitted())transferred(variant.toStack((int)accepted),true,false);});
            attempt.commit();return accepted;
        }
    }
    public ItemStack insert(ItemStack stack,boolean simulate){
        if(stack.isEmpty())return ItemStack.EMPTY;
        try(var tx=Transaction.openOuter()){
            long accepted=insert(ItemVariant.of(stack),stack.getCount(),false,tx);
            if(!simulate)tx.commit();return stack.copyWithCount(stack.getCount()-(int)accepted);
        }
    }
    /** Only an upward collecting funnel accepts a belt's direct output, as in Create. */
    public final Storage<ItemVariant> directInput=new InsertionOnlyStorage<>(){
        @Override public long insert(ItemVariant item,long amount,TransactionContext tx){return facing()==Direction.UP&&determineCurrentMode()==Mode.COLLECT?FunnelBlockEntity.this.insert(item,amount,false,tx):0;}
    };
    public static void tick(Level level,BlockPos pos,BlockState state,FunnelBlockEntity funnel){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, funnel)) {
            if (!createTick.shouldTick()) return;

        funnel.previousFlap=funnel.flap;funnel.flap*=.95f;
        if(level.isClientSide())return;
        var mode=funnel.determineCurrentMode();
        if(mode==Mode.PAUSED){funnel.extractionCooldown=0;return;}
        if(mode==Mode.TAKING_FROM_BELT){funnel.pullFromDepot();return;}
        if(funnel.extractionCooldown>0){funnel.extractionCooldown--;funnel.setChanged();return;}
        if(mode==Mode.EXTRACT)funnel.extractToWorld();
        if(mode==Mode.PUSHING_TO_BELT)funnel.pushToSurface();
    
        }
    }
    private void pushToSurface(){
        if(LoadedLevelAccess.blockEntity(level,worldPosition.below()) instanceof BeltBlockEntity belt&&belt.getSpeed()==0)return;
        var source=attachedStorage();
        var input=BlockEntityBehaviour.get(level,worldPosition.below(),com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE);
        if(source==null||inventoryTracker.stillWaiting(source)||input==null||!input.canInsertFromSide(facing()))return;
        Storage<ItemVariant> destination=new InsertionOnlyStorage<>(){
            @Override public long insert(ItemVariant variant,long amount,TransactionContext tx){return input.insert(variant.toStack((int)Math.min(amount,variant.toStack().getMaxStackSize())),facing(),tx);}
        };
        try(var tx=Transaction.openOuter()){
            var moved=FunnelTransfer.move(source,destination,filtering::test,getAmountToExtract(),isExact(),true,tx);
            if(!moved.isEmpty()){tx.addOuterCloseCallback(result->{if(result.wasCommitted())transferred(moved,false,true);});tx.commit();return;}
        }
        if(!hasExtractableBatch(source))inventoryTracker.awaitNewVersion(source);
    }
    private boolean hasExtractableBatch(Storage<ItemVariant> source){try(var tx=Transaction.openOuter()){return !FunnelTransfer.extract(source,filtering::test,getAmountToExtract(),isExact(),tx).isEmpty();}}
    private void pullFromDepot(){
        if(!(LoadedLevelAccess.blockEntity(level,worldPosition.below()) instanceof DepotBlockEntity depot))return;
        var destination=attachedStorage();if(destination==null)return;
        // Processed outputs take precedence over the displayed input; pulling ignores count.
        var slots=new ArrayList<Storage<ItemVariant>>();
        depot.inventory.outputs.forEach(slot->slots.add(slot));
        depot.inventory.inputs.forEach(slot->slots.add(slot));
        for(var entry:slots){
            try(var tx=Transaction.openOuter()){
                var moved=FunnelTransfer.move(entry,destination,filtering::test,64,false,false,tx);
                if(moved.isEmpty())continue;
                tx.addOuterCloseCallback(result->{if(result.wasCommitted())transferred(moved,true,false);});tx.commit();return;
            }
        }
    }
    private AABB overflowArea(){var box=new AABB(worldPosition);return facing()==Direction.UP?box:box.expandTowards(0,-1,0);}
    private void extractToWorld(){
        var source=attachedStorage();if(source==null||inventoryTracker.stillWaiting(source))return;
        if(!hasExtractableBatch(source)){inventoryTracker.awaitNewVersion(source);return;}
        var previous=lastObserved==null?null:lastObserved.get();var area=overflowArea();
        if(previous!=null&&previous.isAlive()&&previous.getBoundingBox().intersects(area))return;
        lastObserved=null;
        try(var tx=Transaction.openOuter()){
            var moved=FunnelTransfer.extract(source,filtering::test,getAmountToExtract(),isExact(),tx);
            if(moved.isEmpty())return;
            for(var item:level.getEntities((net.minecraft.world.entity.Entity)null,area,entity->entity instanceof ItemEntity||entity instanceof com.simibubi.create.content.logistics.box.PackageEntity))if(item.isAlive()){lastObserved=new WeakReference<>(item);return;}
            var direction=facing();boolean vertical=direction.getAxis().isVertical();
            var output=Vec3.atCenterOf(worldPosition).add(direction.getUnitVec3().scale(vertical?direction==Direction.UP?.15:.5:.25));
            if(!vertical)output=output.subtract(0,.45,0);
            var item=new ItemEntity(level,output.x,output.y,output.z,moved.copy());item.setDefaultPickUpDelay();item.setDeltaMovement(direction==Direction.UP?new Vec3(0,.25,0):Vec3.ZERO);
            tx.addOuterCloseCallback(result->{if(result.wasCommitted()){level.addFreshEntity(item);lastObserved=new WeakReference<>(item);transferred(moved,false,true);}});tx.commit();
        }
    }
    private void transferred(ItemStack stack,boolean inward,boolean cooldown){inventoryTracker.reset();com.simibubi.create.AllBlocks.SMART_OBSERVER.onFunnelTransfer(level,worldPosition,stack);if(level!=null&&!level.isClientSide()&&!isRemoved()&&LoadedLevelAccess.blockEntity(level,worldPosition)==this)award(com.simibubi.create.foundation.advancement.AllAdvancements.FUNNEL);if(cooldown)extractionCooldown=com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.defaultExtractionTimer.get();flap(inward);setChanged();}
    public void flap(boolean inward){
        flapInward=inward;flapSequence++;flap=inward?-1:1;previousFlap=flap;
        if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    public float getFlap(float partial){return previousFlap+(flap-previousFlap)*partial;}
    public boolean hasFlap(){return facing()!=null&&facing().getAxis().isHorizontal();}
    public float getFlapOffset(){return !(getBlockState().getBlock() instanceof BeltFunnelBlock)?-1/16f:switch(getBlockState().getValue(BeltFunnelBlock.SHAPE)){case EXTENDED->8/16f;case PUSHING,PULLING->-2/16f;default->0;};}
    @Override public void clearContent(){filtering.setFilter(ItemStack.EMPTY);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        var next=level==null?null:LoadedLevelAccess.blockState(level,pos);
        if(next!=null&&state.getBlock() instanceof AbstractFunnelBlock old&&next.getBlock() instanceof AbstractFunnelBlock replacement&&old.brass==replacement.brass){CONVERSION.set(new Conversion(new WeakReference<>(level),pos.immutable(),old.brass,saveWithFullMetadata(level.registryAccess())));}
        else 
        super.preRemoveSideEffects(pos,state);
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("TransferCooldown",extractionCooldown);out.putLong("FlapSequence",flapSequence);out.putBoolean("FlapInward",flapInward);}
    @Override protected void read(ValueInput in, boolean clientPacket){
        super.read(in, clientPacket);extractionCooldown=Math.max(0,in.getIntOr("TransferCooldown",0));
        long sequence=in.getLongOr("FlapSequence",0);boolean inward=in.getBooleanOr("FlapInward",false);
        if(level!=null&&level.isClientSide()&&receivedFlap&&sequence!=flapSequence){flap=previousFlap=inward?-1:1;level.playLocalSound(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,AllSoundEvents.FUNNEL_FLAP,SoundSource.BLOCKS,1,1,false);}
        flapSequence=sequence;flapInward=inward;receivedFlap=true;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (inventoryTracker != null) behaviours.add(inventoryTracker);
        if (filtering != null) behaviours.add(filtering);
        if (directBeltInput != null) behaviours.add(directBeltInput);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("funnel");
    }
}

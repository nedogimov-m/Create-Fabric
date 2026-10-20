package com.simibubi.create.content.redstone.thresholdSwitch;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.item.ProcessingInventory;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Real observable/Transfer API measurements. Fluid thresholds retain the source millibucket unit. */
public final class ThresholdSwitchBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider,Clearable {
    public enum ThresholdType {UNSUPPORTED,ITEM,FLUID,CUSTOM}
    public int onWhenAbove=128,offWhenBelow=64,currentMinLevel,currentLevel=-1,currentMaxLevel;
    public boolean inStacks;
    public final VersionedInventoryTrackerBehaviour inventoryTracker=new VersionedInventoryTrackerBehaviour(this);
    public final FilteringBehaviour filtering=new FilteringBehaviour(this).withCallback(stack->{resetInventoryTracking();updateCurrentLevel();});
    private boolean redstoneState,inverted,poweredAfterDelay,sampleReady;
    private ThresholdType targetType=ThresholdType.UNSUPPORTED;
    private BlockPos lastTarget;
    private long itemAmount,itemCapacity;
    private int refresh;
    public ThresholdSwitchBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.THRESHOLD_SWITCH,pos,state);}
    public BlockPos getTargetPos(){return worldPosition.relative(ThresholdSwitchBlock.getTargetDirection(getBlockState()));}
    public void resetInventoryTracking(){inventoryTracker.reset();refresh=0;}
    public static void tick(Level level,BlockPos pos,BlockState state,ThresholdSwitchBlockEntity sensor){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, sensor)) {
            if (!createTick.shouldTick()) return;
if(!level.isClientSide()&&--sensor.refresh<=0){sensor.refresh=10;sensor.updateCurrentLevel();}
        }
    }
    public boolean isSampleReady(){return sampleReady&&level!=null&&targetAvailable();}
    public boolean hasValidSample(){return isSampleReady()&&targetType!=ThresholdType.UNSUPPORTED;}
    public ThresholdType getTypeOfCurrentTarget(){return targetType;}
    public int getCurrentLevel(){return currentLevel;}
    public int getStockLevel(){return currentLevel;}
    public int getMinLevel(){return currentMinLevel;}
    public int getMaxLevel(){return currentMaxLevel;}
    public int getLevelForDisplay(){return targetType==ThresholdType.UNSUPPORTED?0:currentLevel;}
    public boolean getState(){return redstoneState;}
    public boolean isInverted(){return inverted;}
    public boolean shouldBePowered(){return inverted!=redstoneState;}
    public boolean isPowered(){return poweredAfterDelay;}
    private static long add(long a,long b){return a>=Integer.MAX_VALUE||b>=Integer.MAX_VALUE-a?Integer.MAX_VALUE:a+Math.max(0,b);}
    private static long addFluid(long a,long b){return Long.MAX_VALUE-a<Math.max(0,b)?Long.MAX_VALUE:a+Math.max(0,b);}
    public void updateCurrentLevel(){
        if(level==null||level.isClientSide()||isRemoved())return;
        var target=getTargetPos();if(!target.equals(lastTarget)){lastTarget=target;inventoryTracker.reset();}
        // Unknown is not empty: leave persisted measurements and external output untouched.
        if(!targetAvailable()){boolean ready=sampleReady;sampleReady=false;inventoryTracker.reset();if(ready){DisplayLinkBlock.notifyGatherers(level,worldPosition);sync();}return;}
        var entity=LoadedLevelAccess.blockEntity(level,target);int min=0,current=0,max=0;var type=ThresholdType.UNSUPPORTED;
        if(entity instanceof ThresholdSwitchObservable observable){min=observable.getMinValue();max=observable.getMaxValue();if(max>=min){current=Math.clamp(observable.getCurrentValue(),min,max);type=ThresholdType.CUSTOM;}}
        else if(entity!=null&&!(entity instanceof StockTickerBlockEntity)){
            var items=SmartObserverBlockEntity.findItems(level,target);boolean processing=false;
            if(items!=null)for(var view:items)if(view.getUnderlyingView()instanceof ProcessingInventory.Slot){processing=true;break;}
            if(!processing){
                long count=0,capacity=0;
                if(items!=null){type=ThresholdType.ITEM;if(!inventoryTracker.stillWaiting(items))measureItems(items);inventoryTracker.awaitNewVersion(items);count=itemAmount;capacity=itemCapacity;}else inventoryTracker.reset();
                var fluids=SmartObserverBlockEntity.findFluids(level,target);
                if(fluids!=null){if(type==ThresholdType.UNSUPPORTED)type=ThresholdType.FLUID;long fluidAmount=0,fluidCapacity=0;var seen=Collections.newSetFromMap(new IdentityHashMap<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<?>,Boolean>());for(var view:fluids){if(!seen.add(view.getUnderlyingView())||view.getCapacity()<=0)continue;fluidCapacity=addFluid(fluidCapacity,view.getCapacity());if(!view.isResourceBlank()&&filtering.test(new FluidStack(view.getResource(),view.getAmount())))fluidAmount=addFluid(fluidAmount,view.getAmount());}count=add(count,fluidAmount/81);capacity=add(capacity,fluidCapacity/81);}
                current=(int)count;max=(int)capacity;
            }else inventoryTracker.reset();
        }else inventoryTracker.reset();
        if(type!=ThresholdType.UNSUPPORTED)current=Math.clamp(current,min,max);
        boolean previousReady=sampleReady,oldState=redstoneState;var oldType=targetType;
        if(type==ThresholdType.UNSUPPORTED){min=max=current=-1;redstoneState=false;}
        else if(redstoneState){if(current<=offWhenBelow)redstoneState=false;}
        else if(current>=onWhenAbove)redstoneState=true;
        boolean changed=current!=currentLevel||min!=currentMinLevel||max!=currentMaxLevel||type!=oldType||!previousReady;
        currentMinLevel=min;currentLevel=current;currentMaxLevel=max;targetType=type;sampleReady=true;
        int display=0;if(type!=ThresholdType.UNSUPPORTED&&current>0){double fraction=max==min?1:(current-(double)min)/(max-(double)min);display=Math.clamp(1+(int)(Math.clamp(fraction,0,1)*4),1,5);}
        var state=getBlockState();if(state.getValue(ThresholdSwitchBlock.LEVEL)!=display)level.setBlock(worldPosition,state.setValue(ThresholdSwitchBlock.LEVEL,display),oldState!=redstoneState?Block.UPDATE_ALL:Block.UPDATE_CLIENTS);
        if(oldState!=redstoneState)scheduleBlockTick();
        if(changed||oldState!=redstoneState){DisplayLinkBlock.notifyGatherers(level,worldPosition);sync();}
    }
    private void measureItems(Storage<ItemVariant> items){
        itemAmount=itemCapacity=0;var seen=Collections.newSetFromMap(new IdentityHashMap<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<?>,Boolean>());
        for(var view:items){if(!seen.add(view.getUnderlyingView()))continue;var stack=view.isResourceBlank()?ItemStack.EMPTY:view.getResource().toStack();long space=Math.min(view.getCapacity(),stack.isEmpty()?64:stack.getMaxStackSize());if(space<=0)continue;itemCapacity=add(itemCapacity,space);if(!stack.isEmpty()&&filtering.test(stack))itemAmount=add(itemAmount,view.getAmount());}
    }
    private boolean targetAvailable(){var pos=getTargetPos();if(!level.isLoaded(pos))return false;var state=LoadedLevelAccess.blockState(level,pos);if(state.getBlock()instanceof net.minecraft.world.level.block.ChestBlock&&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE&&!level.isLoaded(net.minecraft.world.level.block.ChestBlock.getConnectedBlockPos(pos,state)))return false;var entity=LoadedLevelAccess.blockEntity(level,pos);if(entity instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity vault){var controller=vault.getControllerBE();return controller!=null&&controller.structureLoaded();}if(entity instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank)return tank.getControllerBE()!=null&&tank.structureLoaded()&&(!(level instanceof net.minecraft.server.level.ServerLevel server)||!com.simibubi.create.content.fluids.tank.TankRecoveryData.get(server).contains(pos));return true;}
    public boolean configure(int upper,int lower,boolean stacks,boolean invert){updateCurrentLevel();if(!hasValidSample()||lower<currentMinLevel||upper>currentMaxLevel||lower>upper||currentMaxLevel>currentMinLevel&&lower==upper||stacks&&targetType!=ThresholdType.ITEM)return false;onWhenAbove=upper;offWhenBelow=lower;inStacks=stacks;setInverted(invert);updateCurrentLevel();sync();return true;}
    public void setInverted(boolean value){if(inverted==value)return;inverted=value;updatePowerAfterDelay();}
    private void scheduleBlockTick(){if(level!=null&&!level.getBlockTicks().willTickThisTick(worldPosition,getBlockState().getBlock()))level.scheduleTick(worldPosition,getBlockState().getBlock(),2);}
    public void updatePowerAfterDelay(){if(level==null||level.isClientSide())return;boolean next=shouldBePowered();if(poweredAfterDelay==next)return;poweredAfterDelay=next;level.updateNeighborsAt(worldPosition,getBlockState().getBlock());sync();}
    public ItemStack getDisplayItemForScreen(){return level==null||!level.isLoaded(getTargetPos())?ItemStack.EMPTY:new ItemStack(LoadedLevelAccess.blockState(level,getTargetPos()).getBlock());}
    public MutableComponent format(int value,boolean stacks){if(level!=null&&level.isLoaded(getTargetPos())&&targetType==ThresholdType.CUSTOM&&LoadedLevelAccess.blockEntity(level,getTargetPos())instanceof ThresholdSwitchObservable observable)return observable.format(value);return Component.literal(value+" ").append(Component.translatable("create.schedule.condition.threshold."+(targetType==ThresholdType.ITEM?stacks?"stacks":"items":"buckets")));}
    private void sync(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void clearContent(){filtering.setFilter(ItemStack.EMPTY);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()&&poweredAfterDelay)level.updateNeighborsAt(pos,state.getBlock());super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("OnAboveAmount",onWhenAbove);out.putInt("OffBelowAmount",offWhenBelow);out.putInt("CurrentAmount",currentLevel);out.putInt("CurrentMinAmount",currentMinLevel);out.putInt("CurrentMaxAmount",currentMaxLevel);out.putBoolean("InStacks",inStacks);out.putBoolean("Powered",redstoneState);out.putBoolean("Inverted",inverted);out.putBoolean("PoweredAfterDelay",poweredAfterDelay);out.putInt("ThresholdType",targetType.ordinal());out.putBoolean("SampleReady",sampleReady);}
    @Override protected void read(ValueInput in, boolean clientPacket){onWhenAbove=numeric(in,"OnAboveAmount",128);offWhenBelow=numeric(in,"OffBelowAmount",64);currentLevel=in.getIntOr("CurrentAmount",-1);currentMinLevel=in.getIntOr("CurrentMinAmount",0);currentMaxLevel=in.getIntOr("CurrentMaxAmount",0);inStacks=in.getBooleanOr("InStacks",false);redstoneState=in.getBooleanOr("Powered",false);inverted=in.getBooleanOr("Inverted",false);poweredAfterDelay=in.getBooleanOr("PoweredAfterDelay",false);targetType=ThresholdType.values()[Math.clamp(in.getIntOr("ThresholdType",0),0,3)];sampleReady=in.getBooleanOr("SampleReady",false);lastTarget=null;resetInventoryTracking();
        super.read(in, clientPacket);
    }
    private static int numeric(ValueInput in,String key,int fallback){double value=in.getDoubleOr(key,fallback);return Double.isFinite(value)?(int)Math.clamp(value,Integer.MIN_VALUE,Integer.MAX_VALUE):fallback;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (inventoryTracker != null) behaviours.add(inventoryTracker);
        if (filtering != null) behaviours.add(filtering);
    }
}

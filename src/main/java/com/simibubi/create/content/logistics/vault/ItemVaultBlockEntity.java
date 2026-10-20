package com.simibubi.create.content.logistics.vault;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.item.ProcessingInventory;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.*;

/** Inventory remains owned by each component block through every merge, split and unload. */
public final class ItemVaultBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements IMultiBlockEntityContainer.Inventory,Clearable,RenderDataBlockEntity {
    public static final int SLOTS=20;
    public final ProcessingInventory inventory=new ProcessingInventory(SLOTS,0,variant->true,this::contentsChanged,true,false,ignored->new LocalStorage());
    private final List<ProcessingInventory> additionalInventories=new ArrayList<>();
    private int observedConfiguredSlots=SLOTS;
    public static int configuredSlots(){return com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.vaultCapacity.get();}
    private void ensureSlots(int count){if(count<0||count>2048)throw new IllegalArgumentException("Native source vault slot bound");int allocated=SLOTS;for(var page:additionalInventories)allocated+=page.inputs.size();while(allocated<count){int added=Math.min(SLOTS,2048-allocated);additionalInventories.add(new ProcessingInventory(added,0,variant->true,this::contentsChanged,true));allocated+=added;}}
    /** Stable canonical slot owners; the original twenty slots never change identity. */
    public List<ProcessingInventory.Slot> custodySlots(){ensureSlots(configuredSlots());var result=new ArrayList<>(inventory.inputs);for(var page:additionalInventories)result.addAll(page.inputs);return List.copyOf(result);}
    public List<ProcessingInventory.Slot> mountedSlots(){var slots=custodySlots();int count=configuredSlots();for(int i=count;i<slots.size();i++)if(!slots.get(i).isResourceBlank())count=i+1;return List.copyOf(slots.subList(0,count));}
    public void restoreMounted(com.simibubi.create.foundation.item.MountedItemInventory actual){
        com.simibubi.create.foundation.item.MountedItemInventory.requireNoTransaction();ensureSlots(actual.getSlotCount());var slots=custodySlots();
        try(var tx=Transaction.openOuter()){for(int i=0;i<slots.size();i++)slots.get(i).setStack(i<actual.getSlotCount()?actual.getStack(i):ItemStack.EMPTY,tx);tx.commit();}
    }
    private final VaultStorage storage=new VaultStorage();
    private BlockPos controller,lastKnownPos;
    private boolean updateConnectivity=true,comparatorsDirty;
    private int radius=1,length=1;
    private long localVersion,topologyVersion;
    public record VaultRenderData(BlockPos controller,int width,int length){}
    @Override public Object getRenderData(){return new VaultRenderData(getController(),radius,length);}
    public ItemVaultBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.ITEM_VAULT,pos,state);}
    private void contentsChanged(){localVersion++;comparatorsDirty=true;setChanged();}
    public Storage<ItemVariant> storage(){return storage;}
    public Storage<ItemVariant> getInventoryOfBlock(){return inventory.automation;}
    @Override public void invalidate(){topologyVersion++;super.invalidate();}
    public boolean isLive(){return level!=null&&!isRemoved()&&LoadedLevelAccess.blockEntity(level,worldPosition)==this;}
    @Override public BlockPos getController(){return controller==null?worldPosition:controller;}
    @Override public boolean isController(){return controller==null||controller.equals(worldPosition);}
    private boolean includes(BlockPos pos){var root=getController();int x=pos.getX()-root.getX(),y=pos.getY()-root.getY(),z=pos.getZ()-root.getZ();return x>=0&&y>=0&&z>=0&&y<radius&&(getMainConnectionAxis()==Direction.Axis.X?x<length&&z<radius:x<radius&&z<length);}
    @Override @SuppressWarnings("unchecked") public ItemVaultBlockEntity getControllerBE(){
        if(!isLive())return null;if(isController())return this;var be=LoadedLevelAccess.blockEntity(level,controller);return be instanceof ItemVaultBlockEntity master&&!master.isRemoved()&&master.isController()&&master.getMainConnectionAxis()==getMainConnectionAxis()&&master.includes(worldPosition)?master:null;
    }
    public List<BlockPos> memberPositions(){
        var list=new ArrayList<BlockPos>();var origin=getController();for(int along=0;along<length;along++)for(int across=0;across<radius;across++)for(int vertical=0;vertical<radius;vertical++)list.add(getMainConnectionAxis()==Direction.Axis.X?origin.offset(along,across,vertical):origin.offset(across,vertical,along));return list;
    }
    public boolean structureLoaded(){return memberPositions().stream().allMatch(level::isLoaded);}
    private List<ItemVaultBlockEntity> members(){
        var master=getControllerBE();if(master==null)return List.of();var result=new ArrayList<ItemVaultBlockEntity>();
        for(var pos:master.memberPositions()){
            if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof ItemVaultBlockEntity member)||member.isRemoved()||!member.getController().equals(master.worldPosition)||member.getMainConnectionAxis()!=master.getMainConnectionAxis())return List.of();result.add(member);
        }
        return result;
    }
    public InventoryIdentifier getInvId(){var master=getControllerBE();if(master==null||members().isEmpty())return null;var far=master.getMainConnectionAxis()==Direction.Axis.X?master.worldPosition.offset(master.length-1,master.radius-1,master.radius-1):master.worldPosition.offset(master.radius-1,master.radius-1,master.length-1);return new InventoryIdentifier.Bounds(BoundingBox.fromCorners(master.worldPosition,far));}
    public void requestConnectivityUpdate(){updateConnectivity=true;setChanged();}
    @Override public void setController(BlockPos pos){if(Objects.equals(controller,pos))return;controller=pos.immutable();topologyVersion++;notifyMultiUpdated();}
    @Override public void removeController(boolean keepContents){if(level==null||level.isClientSide())return;controller=null;radius=length=1;updateConnectivity=true;topologyVersion++;notifyMultiUpdated();}
    @Override public void preventConnectivityUpdate(){updateConnectivity=false;}
    @Override public void notifyMultiUpdated(){
        topologyVersion++;comparatorsDirty=true;setChanged();if(level==null||level.isClientSide()||!(getBlockState().getBlock() instanceof ItemVaultBlock))return;
        LoadedLevelAccess.setBlockQuiet(level,worldPosition,getBlockState().setValue(ItemVaultBlock.LARGE,radius>2));level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ItemVaultBlockEntity vault){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, vault)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide())return;
        if(vault.observedConfiguredSlots!=configuredSlots()){vault.observedConfiguredSlots=configuredSlots();vault.comparatorsDirty=true;}
        if(vault.lastKnownPos!=null&&!vault.lastKnownPos.equals(pos)){vault.removeController(true);vault.lastKnownPos=pos;return;}vault.lastKnownPos=pos;
        if(!vault.isController()&&level.isLoaded(vault.getController())&&vault.getControllerBE()==null)vault.removeController(true);
        if(vault.isController()&&vault.structureLoaded()&&vault.members().isEmpty())ConnectivityHandler.splitMulti(vault);
        if(vault.updateConnectivity&&vault.isController()&&vault.structureLoaded()){vault.updateConnectivity=false;ConnectivityHandler.formMulti(vault);}
        if(vault.comparatorsDirty){vault.comparatorsDirty=false;vault.updateComparators();}
    
        }
    }
    private void updateComparators(){
        var master=getControllerBE();if(master==null)return;
        for(var pos:master.memberPositions())if(level.isLoaded(pos)){
            boolean surface=false;for(var side:Direction.values())if(!master.includes(pos.relative(side))){surface=true;break;}
            if(surface)level.updateNeighbourForOutputSignal(pos,getBlockState().getBlock());
        }
    }
    public int comparatorOutput(){var members=members();if(members.isEmpty())return 0;double filled=0;int slots=0;boolean any=false;for(var member:members)for(var slot:member.mountedSlots()){slots++;if(slot.isResourceBlank())continue;any=true;filled+=(double)slot.getAmount()/slot.getCapacity();}return (int)Math.floor(filled/slots*14)+(any?1:0);}
    @Override public void clearContent(){try(var tx=Transaction.openOuter()){for(var slot:custodySlots())if(!slot.isResourceBlank())slot.extract(slot.getResource(),slot.getAmount(),tx);tx.commit();}}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        if(level!=null&&!level.isClientSide()){
            var drops=custodySlots().stream().map(ProcessingInventory.Slot::stack).filter(stack->!stack.isEmpty()).toList();clearContent();ConnectivityHandler.removePart(this);
            for(var stack:drops)level.addFreshEntity(new ItemEntity(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack));
            for(var side:Direction.values())if(LoadedLevelAccess.blockEntity(level,pos.relative(side)) instanceof ItemVaultBlockEntity adjacent)adjacent.requestConnectivityUpdate();
        }
        super.preRemoveSideEffects(pos,state);
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);
        writeStructure(out);
        if(!clientPacket){
            out.putString("StorageType","CombinedInv");
            inventory.save(out.child("Inventory"));
            var slots=custodySlots();
            out.putInt("NativeVaultSlotCount",slots.size());
            for(int i=0;i<additionalInventories.size();i++)additionalInventories.get(i).save(out.child("InventoryPage"+i));
        }
    }
    private void writeStructure(ValueOutput out){out.putBoolean("Uninitialized",updateConnectivity);out.putInt("Size",radius);out.putInt("Length",length);if(controller!=null)out.store("Controller",BlockPos.CODEC,controller);if(lastKnownPos!=null)out.store("LastKnownPos",BlockPos.CODEC,lastKnownPos);}
    @Override protected void read(ValueInput in, boolean clientPacket){
        var previous=getController();int oldWidth=radius,oldLength=length;super.read(in, clientPacket);controller=in.read("Controller",BlockPos.CODEC).orElse(null);lastKnownPos=in.read("LastKnownPos",BlockPos.CODEC).orElse(null);radius=Math.clamp(in.getIntOr("Size",1),1,3);length=Math.clamp(in.getIntOr("Length",1),1,radius*3);updateConnectivity=in.getBooleanOr("Uninitialized",false);if(!clientPacket){
            int savedSlots=in.getIntOr("NativeVaultSlotCount",SLOTS);
            if(savedSlots<SLOTS||savedSlots>2048)throw new IllegalArgumentException("Invalid persisted actual vault slot count");
            ensureSlots(Math.max(savedSlots,configuredSlots()));
            inventory.load(in.childOrEmpty("Inventory"));
            for(int i=0;i<additionalInventories.size();i++)additionalInventories.get(i).load(in.childOrEmpty("InventoryPage"+i));
        }
        topologyVersion++;
        if(level!=null&&level.isClientSide()&&(!previous.equals(getController())||oldWidth!=radius||oldLength!=length))level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_ALL);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public BlockPos getLastKnownPos(){return lastKnownPos;}
    @Override public Direction.Axis getMainConnectionAxis(){return getBlockState().getValue(ItemVaultBlock.HORIZONTAL_AXIS);}
    @Override public int getMaxLength(Direction.Axis axis,int width){return axis==Direction.Axis.Y?getMaxWidth():width*3;}
    @Override public int getMaxWidth(){return 3;}
    @Override public int getHeight(){return length;}
    @Override public int getWidth(){return radius;}
    @Override public void setHeight(int height){length=height;}
    @Override public void setWidth(int width){radius=width;}
    @Override public boolean hasInventory(){return true;}
    private final class LocalStorage implements Storage<ItemVariant>{
        @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);if(amount==0)return 0;var slots=custodySlots();long moved=0;for(int i=0;i<configuredSlots()&&moved<amount;i++)moved+=slots.get(i).insert(resource,amount-moved,tx);return moved;}
        @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;for(var slot:custodySlots()){moved+=slot.extract(resource,amount-moved,tx);if(moved==amount)break;}return moved;}
        @Override public Iterator<StorageView<ItemVariant>> iterator(){var result=new ArrayList<StorageView<ItemVariant>>();var slots=custodySlots();for(int i=0;i<slots.size();i++)if(i<configuredSlots()||!slots.get(i).isResourceBlank())result.add(slots.get(i));return result.iterator();}
        @Override public long getVersion(){return localVersion*31+configuredSlots();}
    }
    private final class VaultStorage implements Storage<ItemVariant> {
        @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;for(var member:members()){moved+=member.inventory.automation.insert(resource,amount-moved,tx);if(moved==amount)break;}return moved;}
        @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;for(var member:members()){moved+=member.inventory.automation.extract(resource,amount-moved,tx);if(moved==amount)break;}return moved;}
        @Override public Iterator<StorageView<ItemVariant>> iterator(){var master=getControllerBE();var members=members();var result=new ArrayList<StorageView<ItemVariant>>();if(master==null)return result.iterator();long generation=master.topologyVersion;for(var member:members)for(var slot:member.mountedSlots())result.add(new StorageView<>(){
            private boolean valid(){return isLive()&&getControllerBE()==master&&master.topologyVersion==generation&&member.isLive()&&members().size()==members.size();}
            @Override public boolean isResourceBlank(){return !valid()||slot.isResourceBlank();}
            @Override public ItemVariant getResource(){return valid()?slot.getResource():ItemVariant.blank();}
            @Override public long getAmount(){return valid()?slot.getAmount():0;}
            @Override public long getCapacity(){return valid()?slot.getCapacity():0;}
            @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return valid()?slot.extract(resource,amount,tx):0;}
        });return result.iterator();}
        @Override public long getVersion(){long version=topologyVersion*31+configuredSlots();var members=members();for(var member:members)version=version*31+member.localVersion+System.identityHashCode(member);return version;}
    }
}

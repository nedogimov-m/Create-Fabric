package com.simibubi.create.content.logistics.packagePort;

import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public abstract class PackagePortBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements Clearable {
    public static final int RANGE=5;
    public final PackagePortInventory inventory=new PackagePortInventory(this);
    public final PackagePortAutomationInventoryWrapper itemHandler=new PackagePortAutomationInventoryWrapper(this);
    public PackagePortTarget target;
    public String addressFilter="";
    public boolean acceptsPackages=true;
    protected PackagePortBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public String getFilterString(){return acceptsPackages?addressFilter:null;}
    public boolean isBackedUp(){return inventory.full();}
    public int getComparatorOutput(){int filled=0;for(int i=0;i<inventory.getContainerSize();i++)if(!inventory.getItem(i).isEmpty())filled++;return filled==0?0:1+14*filled/inventory.getContainerSize();}
    public void inventoryChanged(){notifyUpdate();if(level!=null&&!level.isClientSide())level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}
    public void notifyUpdate(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public void filterChanged(){if(target!=null){target.deregister(this);target.register(this);}inventory.setChanged();}
    public boolean setTarget(PackagePortTarget next){
        if(level==null||next==null||!next.valid(this))return false;var point=next.getExactTargetLocation(this);var diff=point.subtract(net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition));if(diff.y<0&&!(this instanceof com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity)||diff.length()>com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.packagePortRange.get())return false;
        if(target!=null)target.deregister(this);target=next;next.setup(this);next.register(this);notifyUpdate();return true;
    }
    public void refreshTarget(){if(target!=null)target.register(this);}
    public void drop(ItemStack item){if(level!=null&&!level.isClientSide()&&!item.isEmpty())Block.popResource(level,worldPosition,item);}
    @Override public void clearContent(){inventory.clearContent();}
    public abstract void startOpen();
    public abstract void stopOpen();
    public abstract boolean use(net.minecraft.world.entity.player.Player player);
    protected boolean retainTargetOnUnload(){return false;}
    @Override public void invalidate(){if(level!=null&&!level.isClientSide()&&target!=null&&!retainTargetOnUnload())target.deregister(this);super.invalidate();}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){if(target!=null)target.deregister(this);for(int i=0;i<inventory.getContainerSize();i++)drop(inventory.getItem(i));inventory.clearContent();}super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);inventory.write(out);out.putString("AddressFilter",addressFilter);out.putBoolean("AcceptsPackages",acceptsPackages);if(target!=null)out.store("Target",PackagePortTarget.CODEC,target);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);inventory.read(in);addressFilter=in.getStringOr("AddressFilter","");acceptsPackages=in.getBooleanOr("AcceptsPackages",true);target=in.read("Target",PackagePortTarget.CODEC).orElse(null);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
}

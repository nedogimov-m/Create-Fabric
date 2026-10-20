package com.simibubi.create.content.schematics.table;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Real two-slot table and durable original blank receipt; live menu/session owns each upload. */
public final class SchematicTableBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements Container,ExtendedMenuProvider<SchematicTableMenu.Opening> {
    public final SchematicInventory inventory=new SchematicInventory(2,(slot,item)->slot==0&&!hasPendingWork()&&item.is(AllItems.EMPTY_SCHEMATIC),slot->1,this::notifyUpdate,this::canPlayerUse);
    public boolean isUploading;
    public String uploadingSchematic="";
    public float uploadingProgress;
    private UUID uploadId;
    private ItemStack uploadReceipt=ItemStack.EMPTY;
    private ServerSchematicLoader authority;
    public SchematicTableBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SCHEMATIC_TABLE,pos,state);}
    public UUID uploadId(){return uploadId;}
    public boolean hasPendingWork(){return uploadId!=null||!uploadReceipt.isEmpty();}
    public boolean hasOriginalBlankReceipt(){return !uploadReceipt.isEmpty()&&uploadReceipt.getCount()==1&&uploadReceipt.is(AllItems.EMPTY_SCHEMATIC);}
    public boolean canPlayerUse(Player player){return level!=null&&player.level()==level&&!isRemoved()&&!player.isSpectator()&&player.mayBuild()&&player.isWithinBlockInteractionRange(worldPosition,1)&&level.mayInteract(player,worldPosition)&&(level.isClientSide()||com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,worldPosition)==this);}
    public boolean beginUpload(ServerSchematicLoader loader,UUID token,String name){
        if(level==null||level.isClientSide()||isRemoved()||com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,worldPosition)!=this||loader.server()!=level.getServer()||hasPendingWork()||!inventory.getItem(0).is(AllItems.EMPTY_SCHEMATIC)||!inventory.getItem(1).isEmpty())return false;
        var file=SchematicFiles.fileName(name);uploadId=Objects.requireNonNull(token);authority=loader;uploadingSchematic=file;uploadReceipt=inventory.getItem(0).split(1);isUploading=true;uploadingProgress=0;inventory.setChanged();return true;
    }
    public boolean ownsUpload(ServerSchematicLoader loader,UUID token){return level!=null&&!level.isClientSide()&&!isRemoved()&&com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,worldPosition)==this&&authority==loader&&Objects.equals(uploadId,token)&&hasOriginalBlankReceipt()&&inventory.getItem(1).isEmpty();}
    public void uploadProgress(ServerSchematicLoader loader,UUID token,long received,long expected){if(ownsUpload(loader,token)){uploadingProgress=(float)(received/(double)expected);notifyUpdate();}}
    public boolean completeUpload(ServerSchematicLoader loader,UUID token,ItemStack schematic){
        if(!ownsUpload(loader,token)||schematic.getCount()!=1||!schematic.is(AllItems.SCHEMATIC)||!uploadingSchematic.equals(schematic.get(AllDataComponents.SCHEMATIC_FILE)))return false;
        // The exact paid blank is consumed only when a complete real file can become this output.
        uploadReceipt=ItemStack.EMPTY;uploadId=null;authority=null;isUploading=false;uploadingProgress=0;uploadingSchematic="";inventory.setItem(1,schematic);return true;
    }
    public void cancelUpload(ServerSchematicLoader loader,UUID token){
        if(authority!=loader||!Objects.equals(uploadId,token))return;uploadId=null;authority=null;isUploading=false;uploadingProgress=0;uploadingSchematic="";refundOriginalBlank();notifyUpdate();
    }
    private void refundOriginalBlank(){
        if(uploadReceipt.isEmpty()||level==null||level.isClientSide()||isRemoved()||com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,worldPosition)!=this)return;
        var present=inventory.getItem(0);if(!present.isEmpty()&&(!ItemStack.isSameItemSameComponents(present,uploadReceipt)||present.getCount()>=present.getMaxStackSize()))return;
        var original=uploadReceipt;uploadReceipt=ItemStack.EMPTY;
        if(present.isEmpty())inventory.setItem(0,original);else{present.grow(original.getCount());inventory.setChanged();}
    }
    public static void tick(Level level,BlockPos pos,BlockState state,SchematicTableBlockEntity table){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, table)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide())return;
        if(table.uploadId!=null){
            if(table.authority==null||!table.authority.isActive(table,table.uploadId)){table.uploadId=null;table.authority=null;table.isUploading=false;table.uploadingSchematic="";table.uploadingProgress=0;table.refundOriginalBlank();table.notifyUpdate();}
            else table.authority.tick();
        }else table.refundOriginalBlank();
    
        }
    }
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide()&&!isRemoved())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override protected void write(ValueOutput output, boolean clientPacket){super.write(output, clientPacket);inventory.write(output);if(uploadId!=null)output.store("UploadId",UUIDUtil.CODEC,uploadId);if(!uploadReceipt.isEmpty())output.store("UploadReceipt",ItemStack.CODEC,uploadReceipt);output.putBoolean("Uploading",isUploading);output.putString("Schematic",uploadingSchematic);output.putFloat("Progress",uploadingProgress);}
    @Override protected void read(ValueInput input, boolean clientPacket){
        inventory.read(input);uploadId=input.read("UploadId",UUIDUtil.CODEC).orElse(null);var original=input.read("UploadReceipt",ItemStack.CODEC);if(input.read("UploadReceipt",com.mojang.serialization.Codec.PASSTHROUGH).isPresent()&&original.isEmpty())throw new IllegalArgumentException("Malformed original native table upload receipt");uploadReceipt=original.orElse(ItemStack.EMPTY);isUploading=input.getBooleanOr("Uploading",false);uploadingSchematic=input.getStringOr("Schematic","");uploadingProgress=Math.clamp(input.getFloatOr("Progress",0),0,1);authority=null;
        if((level==null||!level.isClientSide())&&(!uploadReceipt.isEmpty()&&!hasOriginalBlankReceipt()||uploadId!=null&&!hasOriginalBlankReceipt()))throw new IllegalArgumentException("Invalid original native table upload receipt");
    
        super.read(input, clientPacket);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);tag.remove("UploadReceipt");return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        if(level!=null&&!level.isClientSide()){
            if(authority!=null&&uploadId!=null)authority.cancel(this,uploadId);
            for(int slot=0;slot<inventory.getContainerSize();slot++){var original=inventory.removeItemNoUpdate(slot);if(!original.isEmpty())Block.popResource(level,worldPosition,original);}
            if(!uploadReceipt.isEmpty()){var original=uploadReceipt;uploadReceipt=ItemStack.EMPTY;Block.popResource(level,worldPosition,original);}
        }super.preRemoveSideEffects(pos,state);
    }
    @Override public Component getDisplayName(){return Component.translatable("create.gui.schematicTable.title");}
    @Override public SchematicTableMenu.Opening getScreenOpeningData(ServerPlayer player){return new SchematicTableMenu.Opening(worldPosition,getUpdateTag(player.registryAccess()),SchematicConfig.get().maxSchematicPacketSize(),SchematicConfig.get().maxTotalSchematicSize()*1000L);}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new SchematicTableMenu(id,inv,getScreenOpeningData((ServerPlayer)player));}
    @Override public int getContainerSize(){return inventory.getContainerSize();}
    @Override public boolean isEmpty(){return inventory.isEmpty();}
    @Override public ItemStack getItem(int slot){return inventory.getItem(slot);}
    @Override public ItemStack removeItem(int slot,int count){return inventory.removeItem(slot,count);}
    @Override public ItemStack removeItemNoUpdate(int slot){return inventory.removeItemNoUpdate(slot);}
    @Override public void setItem(int slot,ItemStack item){inventory.setItem(slot,item);}
    @Override public boolean canPlaceItem(int slot,ItemStack item){return inventory.canPlaceItem(slot,item);}
    @Override public boolean stillValid(Player player){return canPlayerUse(player);}
    @Override public void clearContent(){inventory.clearContent();}
}

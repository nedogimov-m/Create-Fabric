package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.contraptions.AssemblyException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** The native settings board owns its transient preview; ScrollValue remains source-compatible NBT. */
public class ChassisBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private int range=Math.clamp(ChassisSettings.maximumForCurrentSide()/2,1,Math.max(1,ChassisSettings.maximumForCurrentSide()));
    public int currentlySelectedRange=range;
    public ChassisBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.CHASSIS,pos,state);}
    public int getRange(){return range;}
    public void setRange(int value){int next=Math.clamp(value,1,Math.max(1,ChassisSettings.maximumForCurrentSide()));if(range==next)return;range=next;currentlySelectedRange=next;setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public List<ChassisBlockEntity> collectChassisGroup()throws AssemblyException{return ChassisAttachmentLogic.collectGroup(this);}
    public void addAttachedChasses(Queue<BlockPos> frontier,Set<BlockPos> visited)throws AssemblyException{ChassisAttachmentLogic.addAttachedChasses(this,frontier,visited);}
    public List<BlockPos> getIncludedBlockPositions(Direction forced,boolean visualize)throws AssemblyException{return ChassisAttachmentLogic.included(this,forced,visualize?Math.max(1,currentlySelectedRange):getRange());}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("ScrollValue",getRange());}
    @Override protected void read(ValueInput input, boolean clientPacket){super.read(input, clientPacket);range=input.getIntOr("ScrollValue",Math.max(1,ChassisSettings.maximumForCurrentSide()/2));if(range<1)throw new IllegalArgumentException("Invalid saved chassis range");currentlySelectedRange=range;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}

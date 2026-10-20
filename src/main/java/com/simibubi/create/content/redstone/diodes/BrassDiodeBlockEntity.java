package com.simibubi.create.content.redstone.diodes;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Source state machine clock; new edits use2..72000 ticks while actual saved settings and timer survive. */
public abstract class BrassDiodeBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements com.simibubi.create.content.equipment.clipboard.ClipboardCloneable {
    @Override public String getClipboardKey(){return "Block";}
    @Override public boolean writeToClipboard(HolderLookup.Provider registries,CompoundTag tag,Direction side){tag.putBoolean("Inverted",getBlockState().getOptionalValue(BrassDiodeBlock.INVERTED).orElse(false));return true;}
    @Override public boolean readFromClipboard(HolderLookup.Provider registries,CompoundTag tag,net.minecraft.world.entity.player.Player player,Direction side,boolean simulate){if(!tag.contains("Inverted"))return false;if(!simulate&&getBlockState().getValue(BrassDiodeBlock.INVERTED)!=tag.getBooleanOr("Inverted",false))level.setBlockAndUpdate(worldPosition,getBlockState().cycle(BrassDiodeBlock.INVERTED));return true;}
    public static final int MIN_DELAY=2,MAX_DELAY=60*20*60;
    protected int state,maxState=2;
    private boolean initializedInput;
    private final int defaultMaxState;
    protected BrassDiodeBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState block){this(type,pos,block,2);}
    protected BrassDiodeBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState block,int defaultMaxState){super(type,pos,block);if(defaultMaxState<MIN_DELAY||defaultMaxState>MAX_DELAY)throw new IllegalArgumentException("Invalid default diode interval");this.defaultMaxState=this.maxState=defaultMaxState;}
    public int getState(){return state;}
    public int getMaxState(){return maxState;}
    public boolean isIdle(){return state==0;}
    public float getProgress(){int max=Math.max(2,maxState);return Math.clamp(state,0,max)/(float)max;}
    public void setMaxState(int value){if(value<MIN_DELAY||value>MAX_DELAY)throw new IllegalArgumentException("Diode interval must be2..72000 ticks");if(maxState==value)return;maxState=value;state=Math.clamp(state,0,value);setChanged();sync();}
    public String format(int value){return value<60?value+"t":value<1200?value/20+"s":value/1200+"m";}
    public static void tick(Level level,BlockPos pos,BlockState block,BrassDiodeBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, block, entity)) {
            if (!createTick.shouldTick()) return;

        if(!level.isClientSide()&&!entity.initializedInput){entity.initializedInput=true;((BrassDiodeBlock)block.getBlock()).refreshInput(level,pos,block);}
        int previous=entity.state;entity.updateState(block.getValue(BrassDiodeBlock.POWERED),block.getValue(BrassDiodeBlock.POWERING),entity.state>=entity.maxState,entity.state<=0);
        if(previous!=entity.state&&!level.isClientSide())entity.setChanged();
    
        }
    }
    protected void setPowering(boolean value){if(!level.isClientSide())((BrassDiodeBlock)getBlockState().getBlock()).setPowering(level,worldPosition,value);}
    protected abstract void updateState(boolean powered,boolean powering,boolean atMax,boolean atMin);
    protected void sync(){if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("State",state);out.putInt("ScrollValue",maxState);}
    @Override protected void read(ValueInput in, boolean clientPacket){maxState=in.getIntOr("ScrollValue",defaultMaxState);state=in.getIntOr("State",0);initializedInput=false;
        super.read(in, clientPacket);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}

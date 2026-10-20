package com.simibubi.create.content.redstone.analogLever;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Visible strength changes immediately; source notifies redstone15 ticks after the last change. */
public final class AnalogLeverBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements IHaveGoggleInformation {
    private int state,lastChange;
    private float previousClientState,clientState;
    public AnalogLeverBlockEntity(BlockPos pos,BlockState block){super(AllBlockEntityTypes.ANALOG_LEVER,pos,block);}
    public int getState(){return state;}
    public int getChangeTimer(){return lastChange;}
    public float getClientState(float partial){return previousClientState+(clientState-previousClientState)*partial;}
    public void changeState(boolean back){int previous=state;state=Math.clamp(state+(back?-1:1),0,15);if(previous!=state)lastChange=15;setChanged();sync();}
    public static void tick(Level level,BlockPos pos,BlockState block,AnalogLeverBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, block, entity)) {
            if (!createTick.shouldTick()) return;

        if(entity.lastChange>0){entity.lastChange--;if(!level.isClientSide())entity.setChanged();if(entity.lastChange==0&&!level.isClientSide())AnalogLeverBlock.updateNeighbors(block,level,pos);}
        if(level.isClientSide()){entity.previousClientState=entity.clientState;entity.clientState+=(entity.state-entity.clientState)*.2f;}
    
        }
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){GoggleTooltip.line(tooltip,GoggleTooltip.translate("tooltip.analogStrength",state),0);return true;}
    private void sync(){if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("State",state);out.putInt("ChangeTimer",lastChange);}
    @Override protected void read(ValueInput in, boolean clientPacket){state=Math.clamp(in.getIntOr("State",0),0,15);lastChange=Math.clamp(in.getIntOr("ChangeTimer",0),0,15);
        super.read(in, clientPacket);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}

package com.simibubi.create.content.redstone.link;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

public final class RedstoneLinkBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider {
    private LinkBehaviour link;
    private boolean transmitter;
    private int transmittedSignal, receivedSignal;
    private boolean receivedSignalChanged;
    public final FactoryPanelSupportBehaviour panelSupport;

    public RedstoneLinkBlockEntity(BlockPos pos,BlockState state){
        super(AllBlockEntityTypes.REDSTONE_LINK,pos,state);
        transmitter=!state.getValue(RedstoneLinkBlock.RECEIVER);
        link=createLink();
        panelSupport=new FactoryPanelSupportBehaviour(this,()->link.isListening(),()->receivedSignal>0,
            ()->{if(level!=null&&getBlockState().getBlock() instanceof RedstoneLinkBlock block)
                block.updateTransmittedSignal(getBlockState(),level,worldPosition);});
    }
    private LinkBehaviour createLink(){return transmitter?LinkBehaviour.transmitter(this,this::getSignal):LinkBehaviour.receiver(this,this::setSignal);}
    public LinkBehaviour getLink(){return link;}
    @SuppressWarnings("unchecked")
    public int getSignal(){return transmittedSignal;}
    public int getReceivedSignal(){return receivedSignal;}
    public void transmit(int power){int next=Math.clamp(power,0,15);if(transmittedSignal==next)return;transmittedSignal=next;setChanged();link.notifySignalChange();}
    public void setSignal(int power){int next=Math.clamp(power,0,15);if(receivedSignal!=next){receivedSignalChanged=true;setChanged();}receivedSignal=next;}
    public void revalidateNetwork(){if(getBehaviour(LinkBehaviour.TYPE)!=link)return;if(link.isAlive())link.ensureNetworkRegistration();else link.unload();}
    private void updateMode(){
        boolean next=!getBlockState().getValue(RedstoneLinkBlock.RECEIVER);if(next==transmitter)return;
        var previous=link;removeBehaviour(LinkBehaviour.TYPE);transmitter=next;link=createLink();link.copyItemsFrom(previous);
        receivedSignalChanged=true;attachBehaviourLate(link);setChanged();sync();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,RedstoneLinkBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide())return;
        entity.updateMode();entity.revalidateNetwork();
        if(entity.transmitter)return;
        var actual=entity.getBlockState();boolean powered=entity.receivedSignal>0;
        if(actual.getValue(RedstoneLinkBlock.POWERED)!=powered){
            entity.receivedSignalChanged=true;
            LoadedLevelAccess.setBlockQuiet(level,pos,actual.setValue(RedstoneLinkBlock.POWERED,powered));
        }
        if(entity.receivedSignalChanged){RedstoneLinkBlock.notifySelfAndSupport(level,pos,entity.getBlockState());
            entity.receivedSignalChanged=false;entity.panelSupport.notifyPanels();entity.sync();}
    
        }
    }
    public void refreshMode(){if(level!=null&&!level.isClientSide())updateMode();}
    private void sync(){if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override protected void write(ValueOutput output, boolean clientPacket){
        super.write(output, clientPacket);output.putBoolean("Transmitter",transmitter);output.putInt("Receive",receivedSignal);
        output.putInt("Transmit",transmittedSignal);output.putBoolean("ReceivedChanged",receivedSignalChanged);

    }
    @Override protected void read(ValueInput input, boolean clientPacket){
        boolean next=input.getBooleanOr("Transmitter",!getBlockState().getValue(RedstoneLinkBlock.RECEIVER));
        if(next!=transmitter){var previous=link;transmitter=next;link=createLink();replaceNativeBehaviourForRead(previous,link);}
        receivedSignal=Math.clamp(input.getIntOr("Receive",0),0,15);
        receivedSignalChanged=input.getBooleanOr("ReceivedChanged",false);transmittedSignal=Math.clamp(input.getIntOr("Transmit",0),0,15);
    
        super.read(input, clientPacket);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (link != null) behaviours.add(link);
        if (panelSupport != null) behaviours.add(panelSupport);
    }
}

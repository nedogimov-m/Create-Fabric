package com.simibubi.create.content.kinetics.steamEngine;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class SteamEngineBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){var shaft=getShaft();return shaft!=null&&shaft.addToEngineTooltip(tooltip,sneaking);}
    public static java.util.function.Consumer<SteamEngineBlockEntity> CLIENT_TICK=engine->{};
    private boolean counterClockwise;
    public SteamEngineBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.STEAM_ENGINE,pos,state);}
    public boolean isCounterClockwise(){return counterClockwise;}
    public void reverse(){counterClockwise=!counterClockwise;setChanged();sync();}
    public PoweredShaftBlockEntity getShaft(){
        if(level==null)return null;var pos=SteamEngineBlock.getShaftPos(getBlockState(),worldPosition);
        return level.isLoaded(pos)&&LoadedLevelAccess.blockEntity(level,pos) instanceof PoweredShaftBlockEntity shaft&&shaft.canBePoweredBy(worldPosition)?shaft:null;
    }
    public FluidTankBlockEntity getTank(){
        if(level==null)return null;var pos=worldPosition.relative(SteamEngineBlock.getFacing(getBlockState()).getOpposite());
        return level.isLoaded(pos)&&LoadedLevelAccess.blockEntity(level,pos) instanceof FluidTankBlockEntity tank?tank.getControllerBE():null;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,SteamEngineBlockEntity engine){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, engine)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide()){CLIENT_TICK.accept(engine);return;}
        var shaftPos=SteamEngineBlock.getShaftPos(state,pos);
        if(!level.isLoaded(shaftPos))return;var shaftState=LoadedLevelAccess.blockState(level,shaftPos);
        if(shaftState.is(com.simibubi.create.AllBlocks.SHAFT)&&SteamEngineBlock.isShaftValid(state,shaftState)){level.setBlockAndUpdate(shaftPos,PoweredShaftBlock.getEquivalent(shaftState));return;}
        var shaft=engine.getShaft();if(shaft==null)return;
        var tank=engine.getTank();
        if(tank==null||!tank.structureLoaded()||!level.shouldTickBlocksAt(tank.getBlockPos())){shaft.update(pos,0,0);return;}
        float efficiency=Math.clamp(tank.boiler.getEngineEfficiency(tank.getTotalTankSize()),0,1);
        if(efficiency>0)engine.award(com.simibubi.create.foundation.advancement.AllAdvancements.STEAM_ENGINE);
        var axis=shaft.getBlockState().getValue(PoweredShaftBlock.AXIS);var facing=SteamEngineBlock.getFacing(state);
        if(facing.getAxis().isVertical())facing=state.getValue(SteamEngineBlock.FACING);
        int direction=efficiency==0||axis==Direction.Axis.Y?1:facing.getAxisDirection().getStep();
        if(axis==Direction.Axis.Z)direction*=-1;if(engine.counterClockwise)direction*=-1;
        // Faster existing sources determine the direction, as with the original generating shaft.
        if(Math.abs(shaft.getTheoreticalSpeed())>Math.abs(shaft.getDirectionalGeneratedSpeed())&&shaft.getTheoreticalSpeed()!=0&&(shaft.getTheoreticalSpeed()>0)!=(direction>0)){engine.reverse();direction*=-1;}
        shaft.update(pos,direction,efficiency);
    
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){var shaft=getShaft();if(shaft!=null)shaft.remove(pos);}super.preRemoveSideEffects(pos,state);}
    private void sync(){if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("MovementDirection",counterClockwise?1:0);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);counterClockwise=in.getIntOr("MovementDirection",0)!=0;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registry){return super.getUpdateTag(registry);}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("steam_engine");
    }
}

package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.decoration.slidingDoor.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Native floor names, door mode, arrival marker and load-before-level column target persistence. */
public class ElevatorContactBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider {
    private static final List<Consumer<ElevatorContactBlockEntity>> DISPLAY_LISTENERS=new ArrayList<>();
    public final DoorControlBehaviour doorControls=new DoorControlBehaviour(this);
    public ElevatorColumn.ColumnCoords columnCoords;
    public boolean activateBlock;
    public String shortName="",longName="",lastReportedCurrentFloor="";
    private boolean initialized,deferNames,pendingActive;
    private Integer pendingTarget;
    private UUID pendingOwner;
    private BlockPos pendingController;
    public ElevatorContactBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.ELEVATOR_CONTACT,pos,state);}
    public static void registerFloorDisplayListener(Consumer<ElevatorContactBlockEntity> listener){DISPLAY_LISTENERS.add(Objects.requireNonNull(listener));}
    public void updateDisplayedFloor(String floor){if(Objects.equals(floor,lastReportedCurrentFloor))return;lastReportedCurrentFloor=floor;notifyUpdate();for(var listener:List.copyOf(DISPLAY_LISTENERS))listener.accept(this);}
    public void updateName(String shortName,String longName){this.shortName=Objects.requireNonNull(shortName);this.longName=Objects.requireNonNull(longName);deferNames=false;notifyUpdate();if(level!=null&&columnCoords!=null){var column=ElevatorColumn.get(level,columnCoords);if(column!=null)column.namesChanged();}}
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public void initialize(){super.initialize();registerColumn();}
    private void registerColumn(){
        if(level==null||level.isClientSide()||initialized||isRemoved()||LoadedLevelAccess.blockEntity(level,worldPosition)!=this)return;initialized=true;columnCoords=ElevatorContactBlock.getColumnCoords(level,worldPosition);if(columnCoords==null)return;var column=ElevatorColumn.getOrCreate(level,columnCoords);column.add(worldPosition);deferNames=shortName.isBlank();
        if(pendingTarget!=null){column.restoreSavedTarget(pendingTarget,pendingActive,pendingOwner,pendingController);pendingTarget=null;pendingOwner=null;pendingController=null;}
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ElevatorContactBlockEntity contact){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, contact)) {
            if (!createTick.shouldTick()) return;
if(level.isClientSide())return;var actual=ElevatorContactBlock.getColumnCoords(level,pos);if(contact.initialized&&!Objects.equals(actual,contact.columnCoords)){var previous=contact.columnCoords==null?null:ElevatorColumn.get(level,contact.columnCoords);if(previous!=null)previous.remove(pos);contact.initialized=false;}contact.registerColumn();if(contact.deferNames&&contact.columnCoords!=null){contact.deferNames=false;ElevatorColumn.getOrCreate(level,contact.columnCoords).initNames();}
        }
    }
    @Override public void invalidate(){if(level!=null&&!level.isClientSide()&&columnCoords!=null){var column=ElevatorColumn.get(level,columnCoords);if(column!=null)column.remove(worldPosition);}initialized=false;super.invalidate();}
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);out.putString("ShortName",shortName);out.putString("LongName",longName);out.putString("LastReportedCurrentFloor",lastReportedCurrentFloor);out.putBoolean("Activate",activateBlock);
        var column=level==null||columnCoords==null?null:ElevatorColumn.get(level,columnCoords);
        if(column!=null&&column.isTargetAvailable()){out.putInt("ColumnTarget",column.getTargetedYLevel());out.putBoolean("ColumnActive",column.isActive());if(column.activeOwner()!=null)out.store("ColumnOwner",UUIDUtil.CODEC,column.activeOwner());if(column.activeController()!=null)out.store("ColumnController",BlockPos.CODEC,column.activeController());}
        else if(pendingTarget!=null){out.putInt("ColumnTarget",pendingTarget);out.putBoolean("ColumnActive",pendingActive);if(pendingOwner!=null)out.store("ColumnOwner",UUIDUtil.CODEC,pendingOwner);if(pendingController!=null)out.store("ColumnController",BlockPos.CODEC,pendingController);}
    }
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);shortName=in.getStringOr("ShortName","");longName=in.getStringOr("LongName","");lastReportedCurrentFloor=in.getStringOr("LastReportedCurrentFloor","");activateBlock=in.getBooleanOr("Activate",false);pendingTarget=in.getInt("ColumnTarget").orElse(null);pendingActive=in.getBooleanOr("ColumnActive",false);pendingOwner=in.read("ColumnOwner",UUIDUtil.CODEC).orElse(null);pendingController=in.read("ColumnController",BlockPos.CODEC).orElse(null);initialized=false;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (doorControls != null) behaviours.add(doorControls);
    }
}

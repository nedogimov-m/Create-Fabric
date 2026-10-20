package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import com.simibubi.create.content.contraptions.pulley.PulleyContraption;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.Pair;

/** A genuine source cabin: exactly one horizontal moving contact defines its world column. */
public class ElevatorContraption extends PulleyContraption {
    private ElevatorColumn.ColumnCoords column;
    private int contacts,contactYOffset;
    public int minContactY,maxContactY,clientYTarget;
    public boolean arrived;
    public List<ElevatorColumn.Floor> namesList=List.of();
    public ElevatorContraption(){}
    public ElevatorContraption(int offset){super(offset);}
    public ElevatorColumn.ColumnCoords getGlobalColumn(){return column==null?null:column.relative(anchor);}
    public int getContactYOffset(){return contactYOffset;}
    public boolean isTargetUnreachable(int y){return y<minContactY||y>maxContactY;}
    public Integer getCurrentTargetY(Level level){if(level.isClientSide())return clientYTarget;var coords=getGlobalColumn();var known=coords==null?null:ElevatorColumn.get(level,coords);if(known==null||!known.isTargetAvailable()||isTargetUnreachable(known.getTargetedYLevel()))return null;return known.getTargetedYLevel();}
    @Override public boolean assemble(Level level,BlockPos pos)throws AssemblyException{
        contacts=0;column=null;if(!searchMovedStructure(level,pos,null)||getBlocks().isEmpty())return false;
        if(contacts!=1)throw new AssemblyException(Component.translatable(contacts==0?"create.gui.assembly.exception.no_contacts":"create.gui.assembly.exception.too_many_contacts"));
        var known=ElevatorColumn.get(level,getGlobalColumn());if(known!=null&&known.isActive())throw new AssemblyException(Component.translatable("create.gui.assembly.exception.column_conflict"));startMoving(level);invalidateColliders();clientYTarget=anchor.getY()+contactYOffset;return true;
    }
    @Override protected Pair<StructureBlockInfo,BlockEntity> capture(Level level,BlockPos pos){
        var state=LoadedLevelAccess.blockState(level,pos);if(state.is(AllBlocks.REDSTONE_CONTACT)&&state.getValue(RedstoneContactBlock.FACING).getAxis().isHorizontal()){
            var facing=state.getValue(RedstoneContactBlock.FACING);var local=toLocalPos(pos.relative(facing));column=new ElevatorColumn.ColumnCoords(local.getX(),local.getZ(),facing.getOpposite());contactYOffset=local.getY();contacts++;
        }return super.capture(level,pos);
    }
    public void broadcastFloorData(Level level,BlockPos pos){var column=ElevatorColumn.get(level,getGlobalColumn());if(column!=null&&LoadedLevelAccess.blockEntity(level,pos) instanceof ElevatorContactBlockEntity contact)column.floorReached(contact.shortName);}
    public void setClientYTarget(int y){if(clientYTarget==y)return;clientYTarget=y;syncControlDisplays();}
    public void syncControlDisplays(){for(int i=0;i<namesList.size();i++)if(namesList.get(i).y()==clientYTarget){setAllControlsToFloor(i);return;}}
    public void setAllControlsToFloor(int index){for(var actor:getActors())if(actor.right!=null&&actor.right.temporaryData instanceof ContraptionControlsMovement.ElevatorFloorSelection selection)selection.currentIndex=index;}
    @Override public boolean supportsActorControls(){return false;}
    @Override public ContraptionType getType(){return AllContraptionTypes.ELEVATOR.value();}
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries,boolean spawn){var tag=super.writeNBT(registries,spawn);if(column==null)throw new IllegalStateException("Elevator cabin has no captured contact column");tag.put("Column",column.write());tag.putInt("ContactY",contactYOffset);tag.putInt("MinContactY",minContactY);tag.putInt("MaxContactY",maxContactY);tag.putBoolean("Arrived",arrived);tag.putInt("CurrentTarget",clientYTarget);return tag;}
    @Override public void readNBT(Level level,CompoundTag tag,boolean spawn){column=ElevatorColumn.ColumnCoords.read(tag.getCompoundOrEmpty("Column"));contactYOffset=tag.getIntOr("ContactY",0);minContactY=tag.getIntOr("MinContactY",Integer.MIN_VALUE);maxContactY=tag.getIntOr("MaxContactY",Integer.MAX_VALUE);if(minContactY>maxContactY)throw new IllegalArgumentException("Invalid elevator travel range");arrived=tag.getBooleanOr("Arrived",false);clientYTarget=tag.getIntOr("CurrentTarget",0);super.readNBT(level,tag,spawn);}
}

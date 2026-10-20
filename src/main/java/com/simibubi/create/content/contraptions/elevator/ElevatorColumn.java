package com.simibubi.create.content.contraptions.elevator;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;

/** Actual loaded contacts index a world/x/z/facing column; cabin cargo is never stored here. */
public final class ElevatorColumn {
    public record ColumnCoords(int x,int z,Direction side){
        public ColumnCoords{if(!side.getAxis().isHorizontal())throw new IllegalArgumentException("Elevator column requires horizontal contact facing");}
        public ColumnCoords relative(BlockPos anchor){return new ColumnCoords(x+anchor.getX(),z+anchor.getZ(),side);}
        public BlockPos contactAt(int y){return new BlockPos(x,y,z);}
        public CompoundTag write(){var tag=new CompoundTag();tag.putInt("X",x);tag.putInt("Z",z);tag.putString("Side",side.name());return tag;}
        public static ColumnCoords read(CompoundTag tag){return new ColumnCoords(tag.getIntOr("X",0),tag.getIntOr("Z",0),Direction.valueOf(tag.getStringOr("Side","SOUTH").toUpperCase(Locale.ROOT)));}
    }
    public record Floor(int y,String shortName,String longName){}
    private static final Map<Level,Map<ColumnCoords,ElevatorColumn>> COLUMNS=new WeakHashMap<>();
    private final WeakReference<Level> level;
    private final ColumnCoords coords;
    private final NavigableSet<Integer> contacts=new TreeSet<>();
    private int targetedY,namesVersion;private boolean targetAvailable,legacyActive;
    private UUID activeOwner;private BlockPos activeController;
    private ElevatorColumn(Level level,ColumnCoords coords){this.level=new WeakReference<>(level);this.coords=coords;}
    public static ElevatorColumn get(Level level,ColumnCoords coords){var map=COLUMNS.get(level);return map==null?null:map.get(coords);}
    public static ElevatorColumn getOrCreate(Level level,ColumnCoords coords){return COLUMNS.computeIfAbsent(level,key->new HashMap<>()).computeIfAbsent(coords,key->new ElevatorColumn(level,key));}
    public static void clear(Level level){COLUMNS.remove(level);}
    public ColumnCoords coordinates(){return coords;}
    public BlockPos contactAt(int y){return coords.contactAt(y);}
    public Collection<BlockPos> getContacts(){return contacts.stream().map(this::contactAt).toList();}
    public boolean contains(int y){return contacts.contains(y);}
    public int namesListVersion(){return namesVersion;}
    public boolean isActive(){reconcileOwner();return activeOwner!=null||legacyActive;}
    private void reconcileOwner(){
        var world=level.get();if(activeOwner==null||!(world instanceof net.minecraft.server.level.ServerLevel server))return;var owner=server.getEntity(activeOwner);
        if(owner instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity moving){if(moving.getContraption()!=null&&!moving.hasOwnedStructure())release(activeOwner);return;}
        if(activeController==null||!LoadedLevelAccess.isLoaded(server,activeController))return;if(LoadedLevelAccess.blockEntity(server,activeController) instanceof ElevatorPulleyBlockEntity controller&&controller.retainsColumnOwner(activeOwner))return;
        try{com.simibubi.create.foundation.utility.LoadedEntityAccess.requireReady(server,activeController);release(activeOwner);}catch(com.simibubi.create.content.contraptions.AssemblyException unavailable){}
    }
    public static void tick(Level level){var columns=COLUMNS.get(level);if(columns!=null)for(var column:List.copyOf(columns.values()))column.reconcileOwner();}
    public UUID activeOwner(){return activeOwner;}
    public BlockPos activeController(){return activeController;}
    public boolean isTargetAvailable(){return targetAvailable;}
    public int getTargetedYLevel(){return targetedY;}
    public void target(int y){targetedY=y;targetAvailable=true;markDirty();}
    public boolean claim(UUID owner,BlockPos controller){if(Objects.equals(activeOwner,owner)&&Objects.equals(activeController,controller)&&!legacyActive)return true;if(isActive()&&!Objects.equals(activeOwner,owner))return false;activeOwner=Objects.requireNonNull(owner);activeController=controller.immutable();legacyActive=false;markDirty();return true;}
    boolean adoptOriginalActive(com.simibubi.create.content.contraptions.LinearContraptionEntity actual,ElevatorPulleyBlockEntity controller){
        var world=level.get();
        if(!legacyActive||activeOwner!=null||!(world instanceof net.minecraft.server.level.ServerLevel server)
            ||controller.getLevel()!=world||LoadedLevelAccess.blockEntity(world,controller.getBlockPos())!=controller
            ||actual.level()!=world||actual.isRemoved()||server.getEntity(actual.getUUID())!=actual
            ||!actual.hasOwnedStructure()||controller.getMovedContraption()!=actual
            ||!(actual.getContraption() instanceof ElevatorContraption cabin)||!coords.equals(cabin.getGlobalColumn()))return false;
        activeOwner=actual.getUUID();activeController=controller.getBlockPos().immutable();legacyActive=false;markDirty();return true;
    }
    public void release(UUID owner){if(!Objects.equals(activeOwner,owner))return;activeOwner=null;activeController=null;legacyActive=false;markDirty();checkEmpty();}
    /** Pinned public API; internal native ownership uses claim/release with the actual UUID. */
    public void setActive(boolean active){legacyActive=active;if(!active){activeOwner=null;activeController=null;}markDirty();checkEmpty();}
    public void restoreSavedTarget(int target,boolean active,UUID owner,BlockPos controller){if(!targetAvailable)target(target);if(active&&!isActive()){activeOwner=owner;activeController=controller;legacyActive=owner==null;}}
    public void add(BlockPos pos){if(contacts.add(pos.getY()))namesChanged();}
    public void remove(BlockPos pos){if(contacts.remove(pos.getY()))namesChanged();checkEmpty();}
    public void namesChanged(){namesVersion++;}
    private void checkEmpty(){var world=level.get();if(world!=null&&contacts.isEmpty()&&!isActive()){var map=COLUMNS.get(world);if(map!=null)map.remove(coords);}}
    public void markDirty(){var world=level.get();if(world==null)return;for(var pos:getContacts())if(LoadedLevelAccess.blockEntity(world,pos) instanceof ElevatorContactBlockEntity contact)contact.setChanged();}
    public void floorReached(String name){var world=level.get();if(world==null)return;for(var pos:getContacts())if(LoadedLevelAccess.blockEntity(world,pos) instanceof ElevatorContactBlockEntity contact)contact.updateDisplayedFloor(name);}
    public List<Floor> compileNamesList(){var world=level.get();if(world==null)return List.of();var result=new ArrayList<Floor>();for(var pos:getContacts())if(LoadedLevelAccess.blockEntity(world,pos) instanceof ElevatorContactBlockEntity contact)result.add(new Floor(pos.getY(),contact.shortName,contact.longName));return List.copyOf(result);}
    public void gatherAll(){
        var world=level.get();if(world==null||world.isClientSide())return;var first=contactAt(world.getMinY());if(!LoadedLevelAccess.isLoaded(world,first))return;
        for(int y=world.getMinY();y<world.getMinY()+world.getHeight();y++){
            var pos=contactAt(y);var state=LoadedLevelAccess.blockState(world,pos);if(state.getBlock() instanceof ElevatorContactBlock&&state.getValue(ElevatorContactBlock.FACING)==coords.side()){add(pos);continue;}
            if(state.getBlock() instanceof RedstoneContactBlock&&state.getValue(RedstoneContactBlock.FACING)==coords.side()){world.setBlockAndUpdate(pos,AllBlocks.ELEVATOR_CONTACT.defaultBlockState().setValue(ElevatorContactBlock.FACING,coords.side()).setValue(ElevatorContactBlock.POWERED,state.getValue(RedstoneContactBlock.POWERED)));add(pos);}
        }
    }
    private static Integer number(String name){try{return Integer.valueOf(name);}catch(NumberFormatException ignored){return null;}}
    public void initNames(){
        var world=level.get();if(world==null)return;Integer previous=null;var sorted=List.copyOf(contacts);
        for(int i=0;i<sorted.size();i++){
            if(!(LoadedLevelAccess.blockEntity(world,contactAt(sorted.get(i))) instanceof ElevatorContactBlockEntity contact))continue;Integer current=null;
            if(!contact.shortName.isBlank()){current=number(contact.shortName);if(current==null)continue;}
            if(previous!=null)current=previous+1;Integer next=null;
            for(int j=i+1;j<sorted.size();j++)if(LoadedLevelAccess.blockEntity(world,contactAt(sorted.get(j))) instanceof ElevatorContactBlockEntity other){var value=number(other.shortName);if(value==null)continue;if(current!=null&&current>=value){other.shortName="";other.setChanged();break;}next=value;break;}
            if(current==null)current=next!=null?next-1:0;contact.updateName(Integer.toString(current),contact.longName);previous=current;
        }
    }
}

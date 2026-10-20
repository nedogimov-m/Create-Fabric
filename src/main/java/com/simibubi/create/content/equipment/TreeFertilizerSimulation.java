package com.simibubi.create.content.equipment;

import com.simibubi.create.content.equipment.zapper.ZapperWorldAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Constructor-free source tree dream: native world calls see only this exact thread/level's journal. */
public final class TreeFertilizerSimulation implements AutoCloseable {
 private static final ThreadLocal<TreeFertilizerSimulation> ACTIVE=new ThreadLocal<>();
 public static final BlockPos ORIGIN=new BlockPos(0,10,0);
 private final ServerLevel level;
 private final Object cache,blockTicks,fluidTicks;
 private final BlockState soil;
 public final RandomSource random=RandomSource.create();
 private final Map<BlockPos,BlockState> blocks=new HashMap<>();
 private final Map<BlockPos,BeehiveBlockEntity> hives=new HashMap<>();
 private final Map<Entity,Vec3Position> entities=new IdentityHashMap<>();
 private final List<Entity> entityOrder=new ArrayList<>();
 private long subTick;
 private boolean closed;
 private record Vec3Position(double x,double y,double z){}
 public record Placement(BlockPos virtualPos,BlockState state,boolean conductor,List<BeehiveBlockEntity.Occupant> bees){}
 private TreeFertilizerSimulation(ServerLevel level,BlockState soil){this.level=level;this.soil=soil;cache=level.getChunkSource();blockTicks=level.getBlockTicks();fluidTicks=level.getFluidTicks();}
 public static TreeFertilizerSimulation open(ServerLevel level,BlockState soil){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested native fertilizer dream");var simulation=new TreeFertilizerSimulation(level,soil);ACTIVE.set(simulation);return simulation;}
 public static TreeFertilizerSimulation active(Object level){var active=ACTIVE.get();return active!=null&&active.level==level?active:null;}
 public static boolean ownsCache(Object cache){var active=ACTIVE.get();return active!=null&&active.cache==cache;}
 public static boolean ownsTicks(Object ticks){var active=ACTIVE.get();return active!=null&&(active.blockTicks==ticks||active.fluidTicks==ticks);}
 public BlockState blockState(BlockPos pos){return pos.getY()<=9?soil:blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState());}
 public boolean setBlock(BlockPos pos,BlockState state){if(state.is(Blocks.PODZOL))return true;var key=pos.immutable();var previous=blocks.put(key,state);if(previous==null||previous.getBlock()!=state.getBlock())hives.remove(key);return true;}
 public boolean destroyBlock(BlockPos pos){if(blockState(pos).isAir())return false;return setBlock(pos,Blocks.AIR.defaultBlockState());}
 public BlockEntity blockEntity(BlockPos pos){var state=blockState(pos);if(!state.hasBlockEntity())return null;if(!state.is(Blocks.BEE_NEST)&&!state.is(Blocks.BEEHIVE))throw new UnsupportedOperationException("Unreviewed native fertilizer block entity: "+state);return hives.computeIfAbsent(pos.immutable(),key->new BeehiveBlockEntity(key,state));}
 public long nextSubTick(){return subTick++;}
 public int height(Heightmap.Types type,int x,int z){int highest=9;for(var pos:blocks.keySet())if(pos.getX()==x&&pos.getZ()==z)highest=Math.max(highest,pos.getY());for(int y=Math.min(highest,level.getMaxY());y>=level.getMinY();y--)if(type.isOpaque().test(blockState(new BlockPos(x,y,z))))return y+1;return level.getMinY();}
 public boolean defer(Entity entity){if(entity.level()!=level)throw new IllegalArgumentException("Native fertilizer entity belongs to another actual level");if(!entities.containsKey(entity)){entities.put(entity,new Vec3Position(entity.getX(),entity.getY(),entity.getZ()));entityOrder.add(entity);}return true;}
 public List<Placement> placements(){if(ACTIVE.get()!=this)throw new IllegalStateException("Native fertilizer journal frozen outside its dream");var result=new ArrayList<Placement>();for(var entry:blocks.entrySet()){var pos=entry.getKey();var state=entry.getValue();var hive=hives.get(pos);var bees=hive==null?List.<BeehiveBlockEntity.Occupant>of():List.copyOf(hive.collectComponents().getOrDefault(DataComponents.BEES,net.minecraft.world.item.component.Bees.EMPTY).bees());result.add(new Placement(pos,state,state.isRedstoneConductor(level,pos),bees));}return List.copyOf(result);}
 @Override public void close(){if(closed)return;if(ACTIVE.get()!=this)throw new IllegalStateException("Native fertilizer dream ownership changed");ACTIVE.remove();closed=true;}
 /** Real accepted native writes own all later effects. Original stock/components are never rewritten. */
 public int replay(List<Placement> placements,BlockPos sapling,Player player){
  if(!closed||ACTIVE.get()!=null)throw new IllegalStateException("Cannot replay while a native fertilizer dream is active");var accepted=new HashSet<BlockPos>();
  for(var placement:placements){var pos=placement.virtualPos.offset(sapling).below(10);if(!ZapperWorldAccess.canEdit(level,player,pos))continue;var before=LoadedLevelAccess.blockState(level,pos);var owner=LoadedLevelAccess.blockEntity(level,pos);if(before.getDestroySpeed(level,pos)==-1||!placement.conductor&&!before.getCollisionShape(level,pos).isEmpty())continue;
   if(player instanceof ServerPlayer actual&&!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,actual,pos,before,owner))continue;
   if(!ZapperWorldAccess.canEdit(level,player,pos)||LoadedLevelAccess.blockState(level,pos)!=before||LoadedLevelAccess.blockEntity(level,pos)!=owner)continue;
   if(!before.isAir()&&(!level.destroyBlock(pos,true,player)||!LoadedLevelAccess.blockState(level,pos).isAir()))continue;
   if(!level.setBlockAndUpdate(pos,placement.state)||LoadedLevelAccess.blockState(level,pos)!=placement.state)continue;accepted.add(placement.virtualPos);
   if(!placement.bees.isEmpty()&&LoadedLevelAccess.blockEntity(level,pos) instanceof BeehiveBlockEntity hive&&hive.getBlockState()==placement.state&&hive.isEmpty()){for(var bee:placement.bees)hive.storeBee(bee);hive.setChanged();}
  }
  for(var entity:entityOrder){var virtual=entities.get(entity);var anchor=BlockPos.containing(virtual.x,virtual.y,virtual.z);if(!accepted.contains(anchor)&&!accepted.contains(anchor.below()))continue;if(entity.isRemoved()||entity.level()!=level||level.getEntity(entity.getUUID())!=null)continue;var actual=new net.minecraft.world.phys.Vec3(virtual.x+sapling.getX(),virtual.y+sapling.getY()-10,virtual.z+sapling.getZ());if(!ZapperWorldAccess.loaded(level,BlockPos.containing(actual)))continue;entity.snapTo(actual);level.addFreshEntity(entity);}
  return accepted.size();
 }
}

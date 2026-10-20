package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;

/** Original layered preview of possible hostile-mob spawn positions; it creates no entities. */
public final class SoulPulseEffect {
    public static final int MAX_DISTANCE=11,TICKS_PER_LAYER=6,WAITING_TICKS=100;
    private static final List<List<BlockPos>> LAYERS=layers();
    private int ticks;
    public final BlockPos pos;
    public final int distance;
    public final List<BlockPos> added;
    public SoulPulseEffect(BlockPos pos,int distance,boolean overlap) { if(distance<1||distance>MAX_DISTANCE)throw new IllegalArgumentException("Invalid pulse radius");this.pos=pos.immutable();this.distance=distance;ticks=TICKS_PER_LAYER*distance;added=overlap?null:new ArrayList<>(); }
    public boolean canOverlap() { return added==null; }
    public boolean finished() { return ticks<=-WAITING_TICKS; }
    public int currentLayerIdx() { return distance-ticks/TICKS_PER_LAYER-1; }
    public List<BlockPos> tick(Level level) {
        if(finished())return null;ticks--;if(ticks<0||ticks%TICKS_PER_LAYER!=0)return null;
        var result=getPotentialSoulSpawns(level);
        while(result.isEmpty()&&ticks>0){ticks-=TICKS_PER_LAYER;result.addAll(getPotentialSoulSpawns(level));}
        return result;
    }
    public List<BlockPos> getPotentialSoulSpawns(Level level) { var result=new ArrayList<BlockPos>();if(level!=null)getLayer(currentLayerIdx()).map(p->p.offset(pos)).filter(p->canSpawnSoulAt(level,p,true)).forEach(result::add);return result; }
    public static boolean isDark(Level level,BlockPos pos) { return LoadedLevelAccess.isLoaded(level,pos)&&level.getBrightness(LightLayer.BLOCK,pos)<1; }
    public static boolean canSpawnSoulAt(Level level,BlockPos pos,boolean ignoreLight) {
        if(level==null||!level.isInValidBounds(pos)||!LoadedLevelAccess.isLoaded(level,pos))return false;
        var reader=new LoadedLevelReader(level);
        if(!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(reader,pos,EntityTypes.ZOMBIE)||!ignoreLight&&!isDark(level,pos))return false;
        for(var collision:reader.getBlockCollisions(null,new AABB(pos.getX()+.4,pos.getY(),pos.getZ()+.4,pos.getX()+.6,pos.getY()+.75,pos.getZ()+.6)))if(!collision.isEmpty())return false;
        return true;
    }
    public void spawnParticles(Level level,BlockPos at) {
        if(level==null||!level.isClientSide())return;
        if(canOverlap())level.addAlwaysVisibleParticle(Math.round(Math.sqrt(pos.distSqr(at)))>=distance?AllParticleTypes.SOUL_PERIMETER:AllParticleTypes.SOUL_EXPANDING_PERIMETER,at.getX()+.5,at.getY()+.5,at.getZ()+.5,0,0,0);
        if(isDark(level,at)){level.addAlwaysVisibleParticle(AllParticleTypes.SOUL,at.getX()+.5,at.getY()+.5,at.getZ()+.5,0,0,0);level.addParticle(AllParticleTypes.SOUL_BASE,at.getX()+.5,at.getY()+.01,at.getZ()+.5,0,0,0);}
    }
    public static Stream<BlockPos> getLayer(int index) { return index<0||index>=MAX_DISTANCE?Stream.empty():LAYERS.get(index).stream(); }
    private static List<List<BlockPos>> layers() {
        var layers=new ArrayList<List<BlockPos>>();for(int i=0;i<MAX_DISTANCE;i++)layers.add(new ArrayList<>());
        for(int x=0;x<MAX_DISTANCE;x++)for(int y=0;y<MAX_DISTANCE;y++)for(int z=0;z<MAX_DISTANCE;z++){
            int distance=(int)Math.round(Math.sqrt(x*x+y*y+z*z));if(distance>MAX_DISTANCE)continue;var layer=layers.get(Math.max(1,distance)-1);
            for(int sx:x==0?new int[]{1}:new int[]{1,-1})for(int sy:y==0?new int[]{1}:new int[]{1,-1})for(int sz:z==0?new int[]{1}:new int[]{1,-1})layer.add(new BlockPos(x*sx,y*sy,z*sz));
        }
        return layers;
    }
}

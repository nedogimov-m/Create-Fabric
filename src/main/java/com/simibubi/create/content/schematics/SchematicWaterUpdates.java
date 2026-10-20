package com.simibubi.create.content.schematics;

import com.simibubi.create.foundation.mixin.SchematicLoadedChunksAccessor;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

/** Original six-face water updates, restricted to genuine currently loaded chunks and bounded engine tick work. */
public final class SchematicWaterUpdates {
    private record Region(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        Iterator<BlockPos> positions(){return BlockPos.betweenClosed(minX,minY,minZ,maxX,maxY,maxZ).iterator();}
    }
    private static final class Task {
        final ServerLevel level;final long minX,minY,minZ,maxX,maxY,maxZ;
        final Iterator<ChunkPos> chunks;final Deque<Region> regions=new ArrayDeque<>();
        Iterator<BlockPos> current=Collections.emptyIterator();
        Task(ServerLevel level,BlockPos anchor,BoundingBox bounds){
            this.level=level;minX=anchor.getX()+(long)bounds.minX()-1;minY=anchor.getY()+(long)bounds.minY()-1;minZ=anchor.getZ()+(long)bounds.minZ()-1;maxX=anchor.getX()+(long)bounds.maxX()+1;maxY=anchor.getY()+(long)bounds.maxY()+1;maxZ=anchor.getZ()+(long)bounds.maxZ()+1;
            chunks=((SchematicLoadedChunksAccessor)level.getChunkSource().chunkMap).create$schematicVisibleChunks().values().stream().map(holder->holder.getPos()).filter(pos->level.getChunkSource().getChunkNow(pos.x(),pos.z())!=null).toList().iterator();
        }
        private void region(long x0,long y0,long z0,long x1,long y1,long z1){if(x0>x1||y0>y1||z0>z1||x0<Integer.MIN_VALUE||x1>Integer.MAX_VALUE||z0<Integer.MIN_VALUE||z1>Integer.MAX_VALUE)return;regions.add(new Region((int)x0,(int)y0,(int)z0,(int)x1,(int)y1,(int)z1));}
        BlockPos next(){
            while(true){if(current.hasNext())return current.next();if(!regions.isEmpty()){current=regions.removeFirst().positions();continue;}if(!chunks.hasNext())return null;
                var chunk=chunks.next();if(level.getChunkSource().getChunkNow(chunk.x(),chunk.z())==null)continue;
                long x0=Math.max(minX,chunk.getMinBlockX()),x1=Math.min(maxX,chunk.getMinBlockX()+15L),z0=Math.max(minZ,chunk.getMinBlockZ()),z1=Math.min(maxZ,chunk.getMinBlockZ()+15L);if(x0>x1||z0>z1)continue;
                long y0=Math.max(minY,level.getMinY()),y1=Math.min(maxY,level.getMaxY());if(y0>y1)continue;
                if(minY>=y0&&minY<=y1)region(x0,minY,z0,x1,minY,z1);if(maxY!=minY&&maxY>=y0&&maxY<=y1)region(x0,maxY,z0,x1,maxY,z1);
                long innerY0=Math.max(y0,minY+1),innerY1=Math.min(y1,maxY-1);
                if(minZ>=z0&&minZ<=z1)region(x0,innerY0,minZ,x1,innerY1,minZ);if(maxZ!=minZ&&maxZ>=z0&&maxZ<=z1)region(x0,innerY0,maxZ,x1,innerY1,maxZ);
                long innerZ0=Math.max(z0,minZ+1),innerZ1=Math.min(z1,maxZ-1);
                if(minX>=x0&&minX<=x1)region(minX,innerY0,innerZ0,minX,innerY1,innerZ1);if(maxX!=minX&&maxX>=x0&&maxX<=x1)region(maxX,innerY0,innerZ0,maxX,innerY1,innerZ1);
            }
        }
    }
    private static final Map<ServerLevel,Deque<Task>> TASKS=new IdentityHashMap<>();
    public static void enqueue(ServerLevel level,BlockPos anchor,BoundingBox bounds){TASKS.computeIfAbsent(level,ignored->new ArrayDeque<>()).add(new Task(level,anchor,bounds));}
    public static int pending(ServerLevel level){var queue=TASKS.get(level);return queue==null?0:queue.size();}
    private static void tick(ServerLevel level){
        var queue=TASKS.get(level);if(queue==null)return;
        for(int budget=0;budget<1000&&!queue.isEmpty();budget++){
            var pos=queue.getFirst().next();if(pos==null){queue.removeFirst();continue;}
            if(level.isLoaded(pos)&&level.isInValidBounds(pos)&&level.getWorldBorder().isWithinBounds(pos)&&level.getFluidState(pos).is(Fluids.WATER))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        }
        if(queue.isEmpty())TASKS.remove(level);
    }
    public static void register(){ServerTickEvents.END_LEVEL_TICK.register(SchematicWaterUpdates::tick);ServerLifecycleEvents.SERVER_STOPPING.register(server->TASKS.keySet().removeIf(level->level.getServer()==server));}
    private SchematicWaterUpdates(){}
}

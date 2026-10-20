package com.simibubi.create.test;

import com.simibubi.create.Create;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Diagnostic-only native reads. Never takes tickets, joins futures, mutates a BE or weakens an assertion. */
final class NativeRedstoneUnloadWatch {
    private record Original(BlockPos pos,BlockEntity entity,LevelChunk chunk,ChunkHolder holder){}
    private final List<Original> original;
    private final long initialTime;
    private NativeRedstoneUnloadWatch(ServerLevel level,List<Original> original){this.original=List.copyOf(original);initialTime=level.getGameTime();}
    static NativeRedstoneUnloadWatch capture(ServerLevel level,BlockEntity... entities){
        var original=new ArrayList<Original>();
        for(var entity:entities){
            var pos=entity.getBlockPos();var key=ChunkPos.pack(pos);var source=level.getChunkSource();
            var holder=source.chunkMap.getUpdatingChunkIfPresent(key);
            if(holder==null)holder=(ChunkHolder)mapGet(source.chunkMap,"pendingUnloads",key);
            var chunk=source.getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null&&holder!=null&&holder.getLatestChunk()instanceof LevelChunk retained
                &&retained.getBlockEntities().get(pos)==entity)chunk=retained;
            original.add(new Original(pos,entity,chunk,holder));
        }
        return new NativeRedstoneUnloadWatch(level,original);
    }
    /** Read-only proof that a real pending original has finished its current save dependency. */
    boolean hasCompletedPendingRetirement(ServerLevel level){
        for(var old:original){
            if(old.entity.isRemoved())continue;
            var holder=(ChunkHolder)mapGet(level.getChunkSource().chunkMap,"pendingUnloads",ChunkPos.pack(old.pos));
            if(holder==null||!(holder.getLatestChunk()instanceof LevelChunk chunk)
                ||chunk.getBlockEntities().get(old.pos)!=old.entity)continue;
            var future=holder.getSaveSyncFuture();
            if(future.isDone()&&!future.isCancelled()&&!future.isCompletedExceptionally())return true;
        }
        return false;
    }
    static String client(Minecraft client){return "paused="+client.isPaused()+", guiPausing="+client.gui.isPausing()+", screen="+identity(client.gui.screen())+", overlay="+identity(client.gui.overlay())+", windowActive="+client.isWindowActive()+", windowFocused="+client.getWindow().isFocused()+", position="+(client.player==null?null:client.player.position());}
    static void waitFor(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message,NativeRedstoneUnloadWatch watch,String phase){
        var ready=new AtomicBoolean();
        for(int attempt=0;attempt<ticks&&!ready.get();attempt++){
            int index=attempt;String client=attempt%100==0||attempt==ticks-1?context.computeOnClient(NativeRedstoneUnloadWatch::client):null;
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();ready.set(predicate.test(level));if(client!=null||ready.get())watch.sample(level,phase,index,client==null?"predicate-complete":client);});
            if(!ready.get())context.waitTicks(1);
        }
        if(!ready.get())throw new AssertionError(message);
    }
    void sample(ServerLevel level,String phase,int attempt,String client){
        var source=level.getChunkSource();var chunks=source.chunkMap;
        var world="sameThread="+level.getServer().isSameThread()+", gameTime="+level.getGameTime()+", elapsedServerTicks="+(level.getGameTime()-initialTime)+", serverPaused="+(level.getServer() instanceof IntegratedServer integrated&&integrated.isPaused())+", noSave="+level.noSave()+", tickCounter="+level.getServer().getTickCount()+", players="+level.players().stream().map(player->player.position().toString()).toList();
        for(var old:original){
            long key=ChunkPos.pack(old.pos);var cached=source.getChunkNow(old.pos.getX()>>4,old.pos.getZ()>>4);var updating=chunks.getUpdatingChunkIfPresent(key);var visible=(ChunkHolder)mapGet(chunks,"visibleChunkMap",key);var pending=(ChunkHolder)mapGet(chunks,"pendingUnloads",key);var unloadQueue=field(chunks,"unloadQueue");var toDrop=field(chunks,"toDrop");var holder=updating!=null?updating:pending!=null?pending:old.holder;
            Create.LOGGER.info("Native redstone unload diagnostic: phase={}, attempt={}, client=[{}], server=[{}], pos={}, originalBE={}, removed={}, oldChunk={}, oldChunkBE={}, cached={}, cachedIsOld={}, updating={}, visible={}, pending={}, toDrop={}, unloadQueueSize={}, chosenHolder={}, ticketLevel={}, fullStatus={}, latest={}, latestIsOld={}, saveSync={}, fullFuture={}, tickingFuture={}, entityTickingFuture={}, originalHolderLatest={}, originalHolderSaveSync={}",
                phase,attempt,client,world,old.pos,identity(old.entity),old.entity.isRemoved(),identity(old.chunk),old.chunk==null?null:identity(old.chunk.getBlockEntities().get(old.pos)),identity(cached),cached==old.chunk,identity(updating),identity(visible),identity(pending),toDrop instanceof Set<?> set&&set.contains(key),unloadQueue instanceof Collection<?> collection?collection.size():unloadQueue,identity(holder),holder==null?null:holder.getTicketLevel(),holder==null?null:holder.getFullStatus(),holder==null?null:identity(holder.getLatestChunk()),holder!=null&&holder.getLatestChunk()==old.chunk,holder==null?null:future(holder.getSaveSyncFuture()),holder==null?null:future(holder.getFullChunkFuture()),holder==null?null:future(holder.getTickingChunkFuture()),holder==null?null:future(holder.getEntityTickingChunkFuture()),old.holder==null?null:identity(old.holder.getLatestChunk()),old.holder==null?null:future(old.holder.getSaveSyncFuture()));
        }
    }
    private static Object mapGet(Object instance,String name,long key){var map=field(instance,name);return map instanceof Map<?,?> values?values.get(key):null;}
    private static Object field(Object instance,String name){for(Class<?> type=instance.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(instance);}catch(NoSuchFieldException absent){}catch(ReflectiveOperationException failure){return failure.toString();}return "no native field "+name;}
    private static String future(CompletableFuture<?> future){if(future==null)return "null";return identity(future)+"[done="+future.isDone()+", cancelled="+future.isCancelled()+", exceptional="+future.isCompletedExceptionally()+", value="+(future.isDone()&&!future.isCancelled()&&!future.isCompletedExceptionally()?future.getNow(null):"pending")+"]";}
    private static String identity(Object value){return value==null?"null":value.getClass().getSimpleName()+"@"+Integer.toHexString(System.identityHashCode(value));}
}

package com.simibubi.create.test.mixin;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.mixin.accessor.EntitySectionVisibilityAccessor;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.test.NativeGameTestPreparationWait;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla starts after block preparation; capture fixtures also need published entity sections. */
@Mixin(GameTestInfo.class)
public abstract class GameTestEntityPreparationMixin {
    @Unique private int create$entityPreparationTicks;
    @Unique private long create$entityPreparationStartNanos;
    @Unique private boolean create$entityPreparationFailed;

    @Inject(method="tickInternal",at=@At("HEAD"),cancellable=true)
    private void create$prepareEntityQueriesBeforeTestClock(CallbackInfo ci) {
        var test=(GameTestInfo)(Object)this;
        if(create$entityPreparationFailed){ci.cancel();return;}
        if(test.hasStarted()||test.hasFailed()||!test.id().getNamespace().equals("create_test"))return;
        // Every owned fixture starts from genuinely published native metadata, including
        // schematic, minecart, migration and carriage suites. Only already FULL columns
        // are inspected; no chunks, tickets or synthetic visibility are introduced.
        var bounds=test.getStructureBounds().inflate(32);
        var level=test.getLevel();
        for(int x=((int)Math.floor(bounds.minX))>>4;x<=((int)Math.floor(bounds.maxX))>>4;x++)
            for(int z=((int)Math.floor(bounds.minZ))>>4;z<=((int)Math.floor(bounds.maxZ))>>4;z++) {
                var chunk=level.getChunkSource().getChunkNow(x,z);
                if(chunk==null||chunk.getFullStatus()==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL))continue;
                if(LoadedEntityAccess.isReady(level,x,z))continue;
                if(create$entityPreparationTicks==0)create$entityPreparationStartNanos=Util.getNanos();
                if(++create$entityPreparationTicks>400) {
                    create$logNativePreparationTimeout(test,x,z);
                    create$entityPreparationFailed=true;
                    test.fail(Component.literal("Native entity fixture preparation timed out at "+x+", "+z));
                    ci.cancel();
                    return;
                }
                // Let normal server ticks finish native I/O/tracking. No tickets or fabricated visibility.
                NativeGameTestPreparationWait.request(level);
                ci.cancel();
                return;
            }
    }

    @Unique
    private void create$logNativePreparationTimeout(GameTestInfo test,int x,int z) {
        var level=test.getLevel();var source=level.getChunkSource();long key=ChunkPos.pack(x,z);
        var chunk=source.getChunkNow(x,z);var holder=source.chunkMap.getUpdatingChunkIfPresent(key);
        var manager=((ServerLevelEntityManagerAccessor)level).create$getEntityManager();
        var visibility=((EntitySectionVisibilityAccessor)manager).create$getChunkVisibility();
        var loadState=(GameTestEntityLoadStateAccessor)manager;
        var tickets=((GameTestChunkCacheAccessor)source).create$testTicketStorage();
        Create.LOGGER.error("Native entity fixture preparation timeout: test={}, chunk={}, gameTime={}, preparationTicks={}, preparationElapsedMs={}, bounds={}, cached={}, fullStatus={}, entitiesLoaded={}, visibility={}, visibilityEntry={}, entityLoadStatus={}, pendingEntityUnload={}, holderTicketLevel={}, holderFullStatus={}, fullFuture={}, tickingFuture={}, entityTickingFuture={}, tickets={}, loadingTicket={}, simulationTicket={}, chunkDebug={}, generationDependencies25={}",
                test.id(),new ChunkPos(x,z),level.getGameTime(),create$entityPreparationTicks,(Util.getNanos()-create$entityPreparationStartNanos)/1_000_000,test.getStructureBounds(),
                chunk!=null,chunk==null?null:chunk.getFullStatus(),level.areEntitiesLoaded(key),visibility.get(key),visibility.containsKey(key),
                loadState.create$testEntityLoadStatuses().get(key),loadState.create$testEntityChunksToUnload().contains(key),
                holder==null?null:holder.getTicketLevel(),holder==null?null:holder.getFullStatus(),
                holder==null?"no holder":create$describeFuture(holder.getFullChunkFuture()),
                holder==null?"no holder":create$describeFuture(holder.getTickingChunkFuture()),
                holder==null?"no holder":create$describeFuture(holder.getEntityTickingChunkFuture()),
                tickets.getTickets(key),tickets.getTicketDebugString(key,false),tickets.getTicketDebugString(key,true),
                source.getChunkDebugData(new ChunkPos(x,z)),create$generationDependencies(level,x,z));
    }

    @Unique
    private static List<String> create$generationDependencies(ServerLevel level,int x,int z) {
        var source=level.getChunkSource();var dependencies=new ArrayList<String>(25);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            var position=new ChunkPos(x+dx,z+dz);var holder=source.chunkMap.getUpdatingChunkIfPresent(position.pack());
            dependencies.add(position+": "+(holder==null?"no holder":"ticket="+holder.getTicketLevel()+", target="+holder.getFullStatus()+", latest="+holder.getLatestStatus()+", generationFull="+(holder.getChunkIfPresent(ChunkStatus.FULL)!=null)+", fullFuture="+create$describeFuture(holder.getFullChunkFuture())+", entityTickingFuture="+create$describeFuture(holder.getEntityTickingChunkFuture())));
        }
        return dependencies;
    }

    @Unique
    private static String create$describeFuture(CompletableFuture<? extends ChunkResult<?>> future) {
        if(!future.isDone())return "pending";
        if(future.isCancelled())return "cancelled";
        if(future.isCompletedExceptionally())return "exceptional";
        var result=future.getNow(null);
        return result==null?"complete/null":"complete/success="+result.isSuccess()+"/"+result;
    }
}

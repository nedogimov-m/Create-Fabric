package com.simibubi.create.content.trains.track;

import com.simibubi.create.content.contraptions.DeferredContraptionRestoration;
import com.simibubi.create.content.contraptions.ContraptionPassengerHandoff;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;

/** Actual one-tick native frame writes with bounded native callbacks and owned-cell rollback. */
public final class NetherPortalFrameTransaction implements AutoCloseable {
    /** Known actual site obstruction; carries no speculative owner/item/payment state. */
    public static final class Refused extends RuntimeException {
        private final BlockPos position;
        Refused(BlockPos position,String message){super(message+" at "+position);this.position=position.immutable();}
        public BlockPos position(){return position;}
    }
    private enum Phase { PREPARED,APPLYING,APPLIED,COMMITTED,ROLLED_BACK }
    private record Callbacks(boolean passive,boolean snowy){}
    private static final ThreadLocal<NetherPortalFrameTransaction> ACTIVE=new ThreadLocal<>();
    private static final ClassValue<Callbacks> CALLBACKS=new ClassValue<>() {
        @Override protected Callbacks computeValue(Class<?> type){
            var shape=owner(type,"updateShape",BlockState.class,LevelReader.class,ScheduledTickAccess.class,BlockPos.class,Direction.class,BlockPos.class,BlockState.class,RandomSource.class);
            boolean passive=owner(type,"onPlace",BlockState.class,Level.class,BlockPos.class,BlockState.class,boolean.class)==BlockBehaviour.class
                &&owner(type,"affectNeighborsAfterRemoval",BlockState.class,ServerLevel.class,BlockPos.class,boolean.class)==BlockBehaviour.class
                &&owner(type,"neighborChanged",BlockState.class,Level.class,BlockPos.class,Block.class,Orientation.class,boolean.class)==BlockBehaviour.class
                &&owner(type,"updateIndirectNeighbourShapes",BlockState.class,LevelAccessor.class,BlockPos.class,int.class,int.class)==BlockBehaviour.class;
            return new Callbacks(passive&&(shape==BlockBehaviour.class||shape==SnowyBlock.class),shape==SnowyBlock.class);
        }
    };
    private final NetherPortalFramePlan plan;
    private final ServerLevel level;
    private final long tick;
    private final Map<BlockPos,BlockState> original=new LinkedHashMap<>(),expected=new LinkedHashMap<>();
    private final Map<ChunkPos,LevelChunk> chunks=new LinkedHashMap<>();
    private final Set<BlockPos> written=new LinkedHashSet<>();
    private Phase phase=Phase.PREPARED;
    private NetherPortalFrameTransaction(NetherPortalFramePlan plan){this.plan=plan;level=plan.level();tick=level.getGameTime();}
    public static NetherPortalFrameTransaction prepare(NetherPortalFramePlan plan){
        if(!plan.level().getServer().isSameThread())throw new IllegalStateException("Native portal frame transaction requires actual server thread");
        if(ACTIVE.get()!=null)throw new IllegalStateException("Reentrant native portal frame transaction");
        if(net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getLifecycle()!=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.Lifecycle.NONE)throw new IllegalStateException("Native portal world writes cannot run inside an item/fluid transfer transaction");
        var transaction=new NetherPortalFrameTransaction(plan);ACTIVE.set(transaction);
        try{transaction.preflight();return transaction;}catch(RuntimeException error){ACTIVE.remove();throw error;}
    }
    private void preflight(){
        plan.requireUnchanged();var footprint=plan.writePositions();var positions=new LinkedHashSet<>(footprint);
        for(var pos:footprint)for(var side:Direction.values())positions.add(pos.relative(side));
        var entityChecks=new HashSet<Long>();
        for(var pos:positions){
            if(!level.isInValidBounds(pos))throw new Refused(pos,"Native frame dependency is outside world bounds");
            var state=TrackPortalAccess.state(level,pos);var cp=ChunkPos.containing(pos);var chunk=level.getChunkSource().getChunkNow(cp.x(),cp.z());chunks.putIfAbsent(cp,chunk);
            requireNoOwner(pos,state);
            if(!LoadedEntityAccess.isReady(level,pos))throw new UnloadedTrackException(level,pos);
            try{LoadedEntityAccess.requireGlueQueriesReady(level,pos,entityChecks);}catch(com.simibubi.create.content.contraptions.AssemblyException pending){throw new UnloadedTrackException(level,pos);}
            var callbacks=CALLBACKS.get(state.getBlock().getClass());
            if(!callbacks.passive()||state.hasAnalogOutputSignal()||state.isSignalSource())throw new Refused(pos,"Actual native support/device callback is not bounded for frame rollback");
            if(callbacks.snowy())for(var write:plan.writes())if(write.position().equals(pos.above())&&write.state().is(BlockTags.SNOW)!=state.getValue(SnowyBlock.SNOWY))throw new Refused(pos,"Actual native snowy support property would change outside owned frame writes");
            original.put(pos.immutable(),state);expected.put(pos.immutable(),state);
        }
        requireEntitiesClear();TrackPropagator.preflightChanges(level,footprint);plan.requireUnchanged();requireExpected(Map.of());
    }
    private void requireNoOwner(BlockPos pos,BlockState state){
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)throw new UnloadedTrackException(level,pos);
        if(DeferredContraptionRestoration.isPending(level,pos)||ContraptionPassengerHandoff.isPending(level,pos))throw new UnloadedTrackException(level,pos);
        if(state.hasBlockEntity()||state.getBlock() instanceof EntityBlock||state.getBlock() instanceof ITrackBlock||TrackBlockEntityAccess.existing(level,pos)!=null||chunk.getBlockEntityNbt(pos)!=null)throw new Refused(pos,"Actual native frame or dependency owns block/entity cargo");
    }
    private void requireEntitiesClear(){
        var footprint=plan.writePositions();var first=footprint.iterator().next();AABB bounds=new AABB(first);for(var pos:footprint)bounds=bounds.minmax(new AABB(pos));
        // Genuine entity visibility was checked above. The larger query includes actual glue
        // centres up to32blocks away, then filters only real overlapping physical frame cells.
        for(var entity:level.getEntities((net.minecraft.world.entity.Entity)null,bounds.inflate(32),entity->!entity.isRemoved()))
            for(var pos:footprint)if(entity.getBoundingBox().intersects(new AABB(pos)))throw new Refused(pos,"Actual native entity or glue owner intersects frame write");
    }
    public void apply(){
        requireActive();if(phase!=Phase.PREPARED)throw new IllegalStateException("Native frame apply requires fresh real preflight");plan.requireUnchanged();requireExpected(Map.of());requireEntitiesClear();phase=Phase.APPLYING;
        for(var write:plan.writes()){
            requireExpected(Map.of());requireEntitiesClear();var pos=write.position();written.add(pos);
            // Preserve exact vanilla flags/encounter order, including same-state/repeated writes.
            try{level.setBlock(pos,write.state(),write.flags());}finally{var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk!=null&&chunk.getBlockState(pos)==write.state())expected.put(pos,write.state());}
            if(TrackPortalAccess.state(level,pos)!=write.state())throw new Refused(pos,"Actual native frame write was refused");
            expected.put(pos,write.state());requireExpected(Map.of());
        }
        phase=Phase.APPLIED;
    }
    /** Commit only after the caller's genuine reciprocal native track owners are published. */
    public void commit(TrackBlockEntity... counterparts){
        requireActive();if(phase!=Phase.APPLIED)throw new IllegalStateException("Native frame commit requires all actual frame writes");var published=new LinkedHashMap<BlockPos,TrackBlockEntity>();
        for(var owner:counterparts){requirePublishedCounterpart(owner);if(published.put(owner.getBlockPos(),owner)!=null)throw new IllegalStateException("Repeated native counterpart owner");}
        requireExpected(published);phase=Phase.COMMITTED;ACTIVE.remove();
    }
    private void requirePublishedCounterpart(TrackBlockEntity owner){
        var pos=owner.getBlockPos();if(owner.getLevel()!=level||!original.containsKey(pos)||written.contains(pos)&&!expected.get(pos).isAir()||owner.getPortalBinding()==null||owner.boundLocation==null||!(owner.getBlockState().getBlock() instanceof TrackBlock track)||!owner.getBlockState().getValue(TrackBlock.SHAPE).isPortal()||TrackBlockEntityAccess.existing(level,pos)!=owner)throw new IllegalStateException("Only actual new reciprocal halo track owner can finish a frame transaction");
        var facing=TrackPortalConnection.facing(owner.getBlockState());var portal=pos.relative(facing);
        if(plan.writes().stream().noneMatch(write->write.position().equals(portal)&&write.state().is(Blocks.NETHER_PORTAL))||!TrackPortalAccess.state(level,portal).is(Blocks.NETHER_PORTAL))throw new IllegalStateException("Actual counterpart does not enter this genuine generated frame");
        var source=level.getServer().getLevel(owner.boundLocation.getFirst());if(source==null)throw new UnloadedTrackException(owner.boundLocation.getFirst(),List.of(owner.boundLocation.getSecond()));
        var existing=TrackBlockEntityAccess.existing(source,owner.boundLocation.getSecond());if(!(existing instanceof TrackBlockEntity other)||other.boundLocation==null||!other.boundLocation.getFirst().equals(level.dimension())||!other.boundLocation.getSecond().equals(pos)||!owner.getPortalBinding().equals(other.getPortalBinding())||!(other.getBlockState().getBlock() instanceof TrackBlock otherTrack)||otherTrack.getMaterial()!=track.getMaterial()||!other.getBlockState().getValue(TrackBlock.SHAPE).isPortal()||TrackPortalAccess.state(source,other.getBlockPos())!=other.getBlockState())throw new IllegalStateException("Actual generated-frame track ownership is not reciprocal");
    }
    private void requireExpected(Map<BlockPos,TrackBlockEntity> published){
        requireActive();
        for(var entry:chunks.entrySet()){var cp=entry.getKey();var current=level.getChunkSource().getChunkNow(cp.x(),cp.z());if(current==null)throw new UnloadedTrackException(level.dimension(),List.of(cp.getMiddleBlockPosition(plan.search().getY())));if(current!=entry.getValue())throw new IllegalStateException("Actual native frame chunk owner changed during commit");}
        for(var entry:expected.entrySet()){
            var pos=entry.getKey();var actual=TrackPortalAccess.state(level,pos);var owner=published.get(pos);
            if(owner!=null){if(actual!=owner.getBlockState()||TrackBlockEntityAccess.existing(level,pos)!=owner)throw new IllegalStateException("Actual published native counterpart changed");continue;}
            if(actual!=entry.getValue())throw new Refused(pos,"Actual native frame/dependency state changed during commit");requireNoOwner(pos,actual);
        }
    }
    private void requireActive(){if(ACTIVE.get()!=this||!level.getServer().isSameThread()||level.getGameTime()!=tick)throw new IllegalStateException("Native frame transaction cannot leave its original real server tick/thread");}
    /** Restore only still-owned actual cells; never erase a replacement BE/cargo to fake rollback. */
    public void rollback(){
        requireActive();if(phase==Phase.COMMITTED||phase==Phase.ROLLED_BACK)throw new IllegalStateException("Native frame lifecycle already finished");var conflict=new ArrayList<BlockPos>();
        try{for(var pos:List.copyOf(written).reversed()){
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null){conflict.add(pos);continue;}var actual=chunk.getBlockState(pos);
            if(actual!=expected.get(pos)||actual.hasBlockEntity()||actual.getBlock() instanceof EntityBlock||TrackBlockEntityAccess.existing(level,pos)!=null||chunk.getBlockEntityNbt(pos)!=null){conflict.add(pos);continue;}
            var before=original.get(pos);if(actual!=before){level.setBlock(pos,before,18);if(TrackPortalAccess.state(level,pos)!=before)conflict.add(pos);}
        }
        for(var pos:written)if(!conflict.contains(pos)&&TrackPortalAccess.state(level,pos)!=original.get(pos))conflict.add(pos);
        phase=Phase.ROLLED_BACK;
        }finally{ACTIVE.remove();}
        if(!conflict.isEmpty())throw new IllegalStateException("Native frame rollback preserved changed external owner/cargo; unresolved real cells "+conflict);
    }
    @Override public void close(){if(phase!=Phase.COMMITTED&&phase!=Phase.ROLLED_BACK)rollback();}
    private static Class<?> owner(Class<?> type,String name,Class<?>... parameters){
        for(var current=type;current!=null;current=current.getSuperclass())try{Method method=current.getDeclaredMethod(name,parameters);return method.getDeclaringClass();}catch(NoSuchMethodException absent){}
        throw new IllegalStateException("Actual native26.3 callback signature changed: "+name);
    }
}

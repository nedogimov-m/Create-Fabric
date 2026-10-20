package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.createmod.catnip.data.Pair;

/** Curves are stored at both real endpoint BEs; only the primary renders. No unloaded endpoint is treated as air. */
public class TrackBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity {
    private final Map<BlockPos,BezierConnection> connections=new LinkedHashMap<>();
    boolean cancelDrops;
    private boolean removing;
    private int validationDelay=1,portalValidationDelay=1;
    private long portalResyncAt=-1;
    private UUID portalBinding;
    boolean portalCounterpartRemoval;
    public Pair<ResourceKey<Level>,BlockPos> boundLocation;
    public final TrackBlockEntityTilt tilt=new TrackBlockEntityTilt(this);
    public record TrackRenderData(Optional<Double> smoothingAngle){}
    @Override public Object getRenderData(){return new TrackRenderData(tilt.smoothingAngle);}
    public TrackBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.TRACK,pos,state);}
    public Map<BlockPos,BezierConnection> getConnections(){return Collections.unmodifiableMap(connections);}
    public boolean isTilted(){return tilt.smoothingAngle.isPresent();}
    public boolean hasInteractableConnections(){return connections.values().stream().anyMatch(BezierConnection::isPrimary);}
    public UUID getPortalBinding(){return portalBinding;}
    public void bind(ResourceKey<Level> dimension,BlockPos pos){
        if(portalBinding!=null)throw new IllegalStateException("Actual portal ownership requires paired binding, not independent rebinding");
        boundLocation=Pair.of(dimension,pos.immutable());notifyUpdate();scheduleGraph();portalValidationDelay=1;
    }
    void bindPortal(ResourceKey<Level> dimension,BlockPos pos,UUID binding){
        boundLocation=Pair.of(dimension,pos.immutable());portalBinding=Objects.requireNonNull(binding);
        // A normal BlockItem predicts a straight rail without a conditional BE.
        // Native block updates can precede its prediction ACK, so the first BE
        // packet may arrive before that real client owner exists. Reannounce the
        // authoritative owner through normal chunk updates after two server ticks.
        if(level instanceof ServerLevel server)portalResyncAt=server.getGameTime()+2;
        notifyUpdate();scheduleGraph();portalValidationDelay=1;
    }
    void finishRollback(){removing=false;notifyUpdate();}
    public void tickClient(){if(!getBlockState().getOptionalValue(TrackBlock.HAS_BE).orElse(false))level.removeBlockEntity(worldPosition);}
    public void tick(){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

        if(!getBlockState().getOptionalValue(TrackBlock.HAS_BE).orElse(false)){cancelDrops=true;preRemoveSideEffects(worldPosition,getBlockState());level.removeBlockEntity(worldPosition);return;}
        if(--portalValidationDelay<=0){portalValidationDelay=20;TrackPortalConnection.tick(this);if(isRemoved())return;}
        if(portalResyncAt>=0&&level.getGameTime()>=portalResyncAt){portalResyncAt=-1;if(boundLocation!=null&&portalBinding!=null)notifyUpdate();}
        tilt.tick();
        if(--validationDelay<=0){validationDelay=100;validateConnections();if(!isRemoved()){tilt.tryApplySmoothing();for(var curve:List.copyOf(connections.values()))if(curve.isPrimary())manageFakeTracksAlong(curve,false);}}
    
        }
    }
    public void validateConnections(){
        if(!(level instanceof ServerLevel server)||removing)return;
        var repaired=new LinkedHashSet<TrackBlockEntity>();
        for(var entry:List.copyOf(connections.entrySet())){
            var curve=entry.getValue();var target=entry.getKey();
            if(!target.equals(curve.getKey())||!worldPosition.equals(curve.bePositions.getFirst()))throw new IllegalStateException("Misbound curve at "+worldPosition);
            if(TrackConnectionCleanup.isPending(server,curve)){removeConnection(target);continue;}
            if(!LoadedLevelAccess.isLoaded(level,target))continue;
            var state=LoadedLevelAccess.blockState(level,target);
            if(!(state.getBlock() instanceof ITrackBlock track)){removeConnection(target);continue;}
            var endAxis=TrackBlockEntityTilt.axisBeforeSmoothing(curve,false);boolean aligns=track.getTrackAxes(level,target,state).stream().anyMatch(axis->axis.normalize().distanceTo(endAxis)<1/1024f||axis.normalize().distanceTo(endAxis.scale(-1))<1/1024f);
            if(!aligns||!state.hasProperty(TrackBlock.HAS_BE)){removeConnection(target);continue;}
            if(!state.getValue(TrackBlock.HAS_BE))level.setBlock(target,state.setValue(TrackBlock.HAS_BE,true),Block.UPDATE_ALL);
            if(LoadedLevelAccess.blockEntity(level,target) instanceof TrackBlockEntity other){
                var reverse=other.connections.get(worldPosition);
                if(reverse==null){other.addConnection(curve.secondary());repaired.add(other);}
                else if(!reverse.equalsSansMaterial(curve)||reverse.getMaterial()!=curve.getMaterial())removeConnection(target);
            }
        }
        for(var other:repaired)if(!other.isRemoved())other.tilt.tryApplySmoothing();
    }
    public void addConnection(BezierConnection curve){
        if(!worldPosition.equals(curve.bePositions.getFirst())||curve.getKey().equals(worldPosition))throw new IllegalArgumentException("Curve endpoint identity");
        var old=connections.get(curve.getKey());
        if(old!=null){if(old.equalsSansMaterial(curve)&&old.getMaterial()==curve.getMaterial())return;throw new IllegalStateException("Different curve already owns endpoint pair");}
        connections.put(curve.getKey().immutable(),curve);TrackCurveIndex.track(this);notifyUpdate();scheduleGraph();if(curve.isPrimary())manageFakeTracksAlong(curve,false);
    }
    public void removeConnection(BlockPos target){
        if(isTilted())tilt.captureSmoothingHandles();var removed=connections.remove(target);if(removed==null)return;tilt.connectionsChanged();manageFakeTracksAlong(removed,true);notifyUpdate();scheduleGraph();
        // Native conditional BE removal must be explicit for a same-block property transition.
        if(connections.isEmpty()&&boundLocation==null&&!removing&&level!=null&&!level.isClientSide()){
            var state=LoadedLevelAccess.blockState(level,worldPosition);
            if(state.getBlock() instanceof TrackBlock&&state.getValue(TrackBlock.HAS_BE)){
                removing=true;level.setBlock(worldPosition,state.setValue(TrackBlock.HAS_BE,false),Block.UPDATE_ALL);level.removeBlockEntity(worldPosition);removing=false;
            }
        }
    }
    void replaceSmoothingConnections(Map<BlockPos,BezierConnection> replacements){connections.clear();connections.putAll(replacements);}
    void finishSmoothingUpdate(){TrackCurveIndex.track(this);notifyUpdate();scheduleGraph();}
    void suppressRemovalForRollback(){removing=true;}
    private void scheduleGraph(){if(level instanceof ServerLevel)level.scheduleTick(worldPosition,getBlockState().getBlock(),1);}
    public void notifyUpdate(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    /** Actual native destruction callback; chunk unload never executes this path. */
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        if(level instanceof ServerLevel server&&!removing){
            if(!cancelDrops)for(var curve:connections.values())TrackCurveItems.requireMaterials(curve);
            TrackPortalCleanup.removed(this);
            removing=true;
            for(var curve:List.copyOf(connections.values())){
                // Queue before changing the reciprocal BE. Matching the full geometry protects a replacement rail.
                TrackConnectionCleanup.removeOrQueue(server,curve.secondary());
                manageFakeTracksAlong(curve,true);
                if(!cancelDrops)TrackCurveItems.spawn(server,curve);
            }
            connections.clear();TrackCurveIndex.untrack(this);setChanged();removing=false;
        }
        super.preRemoveSideEffects(pos,state);
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);out.store("Connections",CompoundTag.CODEC.listOf(),connections.values().stream().map(curve->curve.write(worldPosition)).toList());
        tilt.smoothingAngle.ifPresent(angle->out.putDouble("Smoothing",angle));
        if(portalBinding!=null)out.store("PortalBinding",UUIDUtil.CODEC,portalBinding);
        if(boundLocation!=null){out.store("BoundLocation",BlockPos.CODEC,boundLocation.getSecond());out.putString("BoundDimension",boundLocation.getFirst().identifier().toString());}
    }
    @Override protected void read(ValueInput input, boolean clientPacket){
        var previousAngle=tilt.smoothingAngle;super.read(input, clientPacket);connections.clear();
        for(var encoded:input.read("Connections",CompoundTag.CODEC.listOf()).orElse(List.of())){
            var curve=new BezierConnection(encoded,worldPosition);
            if(!curve.bePositions.getFirst().equals(worldPosition)||curve.getKey().equals(worldPosition)||connections.putIfAbsent(curve.getKey(),curve)!=null)throw new IllegalArgumentException("Invalid saved curve endpoint binding");
        }
        tilt.smoothingAngle=input.read("Smoothing",com.mojang.serialization.Codec.DOUBLE);tilt.afterLoad();boundLocation=null;
        var target=input.read("BoundLocation",BlockPos.CODEC);if(target.isPresent())boundLocation=Pair.of(ResourceKey.create(Registries.DIMENSION,Identifier.parse(input.getStringOr("BoundDimension","minecraft:overworld"))),target.get());
        portalBinding=input.read("PortalBinding",UUIDUtil.CODEC).orElse(null);portalValidationDelay=1;
        if(portalBinding!=null&&boundLocation==null)throw new IllegalArgumentException("Saved portal binding without actual dimensional endpoint");
        validationDelay=1;if(level!=null){TrackCurveIndex.track(this);if(level.isClientSide()&&!previousAngle.equals(tilt.smoothingAngle))level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_ALL);}
    }
    public void manageFakeTracksAlong(BezierConnection curve,boolean remove){TrackCurveCoverage.update(level,curve,remove);}
    @Override public void setLevel(Level level){super.setLevel(level);TrackCurveIndex.track(this);}
    @Override public void invalidate(){TrackCurveIndex.untrack(this);super.invalidate();}
    @Override public void clearRemoved(){super.clearRemoved();TrackCurveIndex.track(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}

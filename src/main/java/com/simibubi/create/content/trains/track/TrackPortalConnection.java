package com.simibubi.create.content.trains.track;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.graph.TrackNodeLocation.DiscoveredLocation;

/** Genuine paired native portal rails, with actual ownership/frontier discovery before lifecycle edits. */
public final class TrackPortalConnection {
    private record Peer(ServerLevel level,BlockPos pos,BlockState state,TrackBlockEntity owner){}
    private record Snapshot(ServerLevel level,BlockPos pos,BlockState state,BlockEntity owner,CompoundTag data){}
    private record Mutation(Snapshot original,BlockState installed){}
    private static final class PlacementRefused extends RuntimeException {PlacementRefused(String message){super(message);}}
    public static Direction facing(BlockState state){return switch(state.getValue(TrackBlock.SHAPE)){case TE->Direction.EAST;case TW->Direction.WEST;case TN->Direction.NORTH;case TS->Direction.SOUTH;default->null;};}
    private static void retry(ServerLevel level,BlockPos pos){var state=TrackPortalAccess.state(level,pos);if(state.getBlock() instanceof TrackBlock block&&!level.getBlockTicks().hasScheduledTick(pos,block))level.scheduleTick(pos,block,20);}
    public static void tryConnect(ServerLevel level,BlockPos pos,BlockState state){
        var shape=state.getValue(TrackBlock.SHAPE);var axis=shape==TrackShape.XO?Direction.Axis.X:shape==TrackShape.ZO?Direction.Axis.Z:null;if(axis==null)return;
        if(TrackPortalAccess.state(level,pos)!=state)return;var oldOwner=TrackBlockEntityAccess.existing(level,pos);
        if(state.getValue(TrackBlock.HAS_BE)&&!(oldOwner instanceof TrackBlockEntity)){retry(level,pos);return;}
        if(oldOwner instanceof TrackBlockEntity owner&&(!owner.getConnections().isEmpty()||owner.boundLocation!=null||owner.isTilted()))return;
        String failure=null;BlockPos failedAt=null;
        for(var sign:new Direction.AxisDirection[]{Direction.AxisDirection.POSITIVE,Direction.AxisDirection.NEGATIVE}){
            var side=Direction.get(sign,axis);PortalTrackProvider.Exit exit;NetherPortalTrackProvider.MissingFrame creation=null;
            try{
                var portal=TrackPortalAccess.state(level,pos.relative(side));if(!PortalTrackProvider.isSupportedPortal(portal))continue;var inbound=new BlockFace(pos,side);exit=PortalTrackProvider.getOtherSide(level,inbound);
                // Creation is reserved for the genuine registered vanilla provider. An addon
                // override retains its own actual provider contract and is never impersonated.
                if(exit==null&&PortalTrackProvider.REGISTRY.get(portal)==AllPortalTracks.VANILLA_NETHER)creation=NetherPortalTrackProvider.prepareMissingFrame(level,inbound);
            }catch(UnloadedTrackException missing){retry(level,pos);return;}
            if(exit==null&&creation==null){failure="missing";continue;}
            var other=exit!=null?exit.level():creation.frame().level();var otherFace=exit!=null?exit.face():creation.projectedTrack();var target=otherFace.getPos();
            if(other==level||!otherFace.getFace().getAxis().isHorizontal()){failure="missing";continue;}
            BlockState before;
            try{
                before=TrackPortalAccess.state(other,target);TrackPortalAccess.neighborhood(level,pos,2);TrackPortalAccess.neighborhood(other,target,2);
                if(creation==null&&!PortalTrackProvider.isSupportedPortal(TrackPortalAccess.state(other,otherFace.getConnectedPos()))){failure="missing";continue;}
                // Actual native fallback may clear this real target cell. This is explicit plan
                // data; no existing portal/BE is fabricated before the genuine frame writes.
                var planned=before;if(creation!=null)for(var write:creation.frame().writes())if(write.position().equals(target))planned=write.state();
                if(!other.isInValidBounds(target)||!planned.canBeReplaced()||planned.hasBlockEntity()||before.hasBlockEntity()||TrackBlockEntityAccess.existing(other,target)!=null){failure="blocked";failedAt=target;continue;}
                TrackPropagator.preflightChanges(level,List.of(pos));TrackPropagator.preflightChanges(other,List.of(target));
            }catch(UnloadedTrackException missing){retry(level,pos);return;}
            if(TrackPortalAccess.state(level,pos)!=state||TrackBlockEntityAccess.existing(level,pos)!=oldOwner||TrackPortalAccess.state(other,target)!=before||TrackBlockEntityAccess.existing(other,target)!=null)return;
            var mutations=new ArrayList<Mutation>();var id=UUID.randomUUID();
            try(var frame=creation==null?null:NetherPortalFrameTransaction.prepare(creation.frame())){
                if(frame!=null){frame.apply();exit=NetherPortalTrackProvider.finishCreation(creation);if(exit.level()!=other||!exit.face().getPos().equals(target)||exit.face().getFace()!=otherFace.getFace())throw new PlacementRefused("Actual created native exit changed after frame preflight");}
                // Snapshot the actual post-frame target. Pair rollback restores this real cell,
                // then the still-open frame transaction can restore its own earlier terrain.
                var targetBefore=TrackPortalAccess.state(other,target);
                if(TrackPortalAccess.state(level,pos)!=state||TrackBlockEntityAccess.existing(level,pos)!=oldOwner||!targetBefore.canBeReplaced()||targetBefore.hasBlockEntity()||TrackBlockEntityAccess.existing(other,target)!=null)throw new PlacementRefused("Actual native source or target cargo changed after frame creation");
                var snapshots=List.of(snapshot(level,pos),snapshot(other,target));
                try{
                    var sourceState=state.setValue(TrackBlock.SHAPE,TrackShape.asPortal(side)).setValue(TrackBlock.HAS_BE,true);
                    var targetState=state.setValue(TrackBlock.SHAPE,TrackShape.asPortal(otherFace.getFace())).setValue(TrackBlock.HAS_BE,true).setValue(TrackBlock.WATERLOGGED,targetBefore.getFluidState().getType()==Fluids.WATER);
                    mutations.add(new Mutation(snapshots.get(0),sourceState));place(level,pos,sourceState);
                    if(TrackPortalAccess.state(other,target)!=targetBefore||TrackBlockEntityAccess.existing(other,target)!=null)throw new PlacementRefused("Actual target changed during native source placement");
                    mutations.add(new Mutation(snapshots.get(1),targetState));place(other,target,targetState);
                    if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof TrackBlockEntity first)||!(LoadedLevelAccess.blockEntity(other,target) instanceof TrackBlockEntity second))throw new IllegalStateException("Native portal track BE creation failed");
                    if(oldOwner!=null&&first!=oldOwner)throw new IllegalStateException("Native portal placement replaced an existing genuine owner");
                    if(!PortalTrackProvider.isSupportedPortal(TrackPortalAccess.state(level,pos.relative(side)))||!PortalTrackProvider.isSupportedPortal(TrackPortalAccess.state(other,otherFace.getConnectedPos())))throw new PlacementRefused("Actual native portal changed during track placement");
                    first.bindPortal(other.dimension(),target,id);second.bindPortal(level.dimension(),pos,id);if(frame!=null)frame.commit(second);return;
                }catch(RuntimeException error){restore(mutations,id,error);throw error;}
            }catch(RuntimeException error){
                // A failed real rollback is never hidden as successful deferral or known refusal.
                if(error.getSuppressed().length!=0)throw error;
                if(error instanceof UnloadedTrackException){retry(level,pos);return;}
                if(error instanceof PlacementRefused||error instanceof NetherPortalFrameTransaction.Refused){failure="blocked";failedAt=error instanceof NetherPortalFrameTransaction.Refused refused?refused.position():target;continue;}
                throw error;
            }
        }
        if(failure==null)return;
        try{TrackPropagator.preflightChanges(level,List.of(pos));}catch(UnloadedTrackException missing){retry(level,pos);return;}
        if(TrackPortalAccess.state(level,pos)!=state||TrackBlockEntityAccess.existing(level,pos)!=oldOwner)return;
        if(!level.destroyBlock(pos,true))return;
        var nearest=level.getNearestPlayer(pos.getX(),pos.getY(),pos.getZ(),10,false);
        if(nearest!=null){nearest.sendSystemMessage(Component.literal("<!> ").append(Component.translatable("create.portal_track.failed")).withStyle(ChatFormatting.GOLD));nearest.sendSystemMessage(failedAt==null?Component.translatable("create.portal_track."+failure):Component.translatable("create.portal_track."+failure,failedAt.getX(),failedAt.getY(),failedAt.getZ()));}
    }
    private static Snapshot snapshot(ServerLevel level,BlockPos pos){var be=TrackBlockEntityAccess.existing(level,pos);return new Snapshot(level,pos,TrackPortalAccess.state(level,pos),be,be==null?null:be.saveWithFullMetadata(level.registryAccess()));}
    private static void place(ServerLevel level,BlockPos pos,BlockState state){if(!level.setBlock(pos,state,Block.UPDATE_ALL)||TrackPortalAccess.state(level,pos)!=state)throw new PlacementRefused("Native portal track placement refused at "+pos);}
    private static void restore(List<Mutation> mutations,UUID binding,RuntimeException cause){
        // Roll back only actual writes still owned by this transaction. A changed native BE/cargo
        // must never be erased to manufacture the alleged original snapshot.
        for(var mutation:mutations){var before=mutation.original();var actual=TrackPortalAccess.state(before.level(),before.pos());var owner=TrackBlockEntityAccess.existing(before.level(),before.pos());
            if(actual==before.state()&&owner==before.owner())continue;
            if(actual!=mutation.installed()||owner!=null&&(!(owner instanceof TrackBlockEntity track)||!track.getConnections().isEmpty()||track.isTilted()||track.getPortalBinding()!=null&&!binding.equals(track.getPortalBinding())||track.boundLocation!=null&&!binding.equals(track.getPortalBinding())))throw new IllegalStateException("Native portal rollback cannot overwrite changed owner/cargo at "+before.pos(),cause);
        }
        for(var mutation:mutations){var before=mutation.original();if(TrackBlockEntityAccess.existing(before.level(),before.pos()) instanceof TrackBlockEntity owner)owner.suppressRemovalForRollback();}
        for(var mutation:mutations.reversed()){var before=mutation.original();var level=before.level();var pos=before.pos();level.setBlock(pos,before.state(),Block.UPDATE_ALL);level.removeBlockEntity(pos);
            if(before.owner()!=null){var original=before.owner();original.clearRemoved();original.setBlockState(before.state());original.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),before.data()));level.setBlockEntity(original);if(original instanceof TrackBlockEntity track)track.finishRollback();}
        }
        for(var mutation:mutations){var before=mutation.original();if(TrackPortalAccess.state(before.level(),before.pos())!=before.state()||TrackBlockEntityAccess.existing(before.level(),before.pos())!=before.owner())throw new IllegalStateException("Cannot restore original native portal placement owner "+before.pos(),cause);}
    }
    /** Returns only an actual reciprocal native owner; never obtains an owner from block state alone. */
    private static Peer peer(TrackBlockEntity owner){
        if(!(owner.getLevel() instanceof ServerLevel level)||owner.boundLocation==null)return null;
        var other=level.getServer().getLevel(owner.boundLocation.getFirst());var pos=owner.boundLocation.getSecond();if(other==null)throw new UnloadedTrackException(owner.boundLocation.getFirst(),List.of(pos));
        var state=TrackPortalAccess.state(other,pos);
        if(!(state.getBlock() instanceof TrackBlock track)||track.getMaterial()!=((ITrackBlock)owner.getBlockState().getBlock()).getMaterial()||!state.getValue(TrackBlock.SHAPE).isPortal()||!state.getValue(TrackBlock.HAS_BE))return null;
        var value=TrackBlockEntityAccess.existing(other,pos);if(!(value instanceof TrackBlockEntity target))throw new UnloadedTrackException(other,pos);
        if(target.boundLocation==null||!target.boundLocation.getFirst().equals(level.dimension())||!target.boundLocation.getSecond().equals(owner.getBlockPos()))return null;
        if(owner.getPortalBinding()!=null&&target.getPortalBinding()!=null&&!owner.getPortalBinding().equals(target.getPortalBinding()))return null;
        return new Peer(other,pos,state,target);
    }
    private static boolean portalPresent(ServerLevel level,BlockPos pos,BlockState state){var face=facing(state);return face!=null&&PortalTrackProvider.isSupportedPortal(TrackPortalAccess.state(level,pos.relative(face)));}
    public static void tick(TrackBlockEntity owner){
        if(!(owner.getLevel() instanceof ServerLevel level)||owner.boundLocation==null)return;
        if(!LoadedLevelAccess.isLoaded(level,owner.getBlockPos()))return;
        if(TrackPortalAccess.state(level,owner.getBlockPos())!=owner.getBlockState()||TrackBlockEntityAccess.existing(level,owner.getBlockPos())!=owner)return;
        var data=TrackPortalCleanup.get(level.getServer());if(data.isRetired(owner.getPortalBinding()))return;
        try{
            var peer=peer(owner);
            if(peer==null||!portalPresent(level,owner.getBlockPos(),owner.getBlockState())||!portalPresent(peer.level(),peer.pos(),peer.state())){
                TrackPropagator.preflightChanges(level,List.of(owner.getBlockPos()));
                // Invalid saved ownership must not destroy a replacement counterpart or mint a refund.
                level.destroyBlock(owner.getBlockPos(),false);return;
            }
            if(owner.getPortalBinding()==null||peer.owner().getPortalBinding()==null){
                TrackPropagator.preflightChanges(level,List.of(owner.getBlockPos()));TrackPropagator.preflightChanges(peer.level(),List.of(peer.pos()));
                if(TrackBlockEntityAccess.existing(level,owner.getBlockPos())!=owner||TrackBlockEntityAccess.existing(peer.level(),peer.pos())!=peer.owner())throw new IllegalStateException("Legacy native portal owner changed during validation");
                var id=owner.getPortalBinding()!=null?owner.getPortalBinding():peer.owner().getPortalBinding()!=null?peer.owner().getPortalBinding():UUID.randomUUID();owner.bindPortal(peer.level().dimension(),peer.pos(),id);peer.owner().bindPortal(level.dimension(),owner.getBlockPos(),id);
            }
        }catch(UnloadedTrackException missing){retry(level,owner.getBlockPos());}
    }
    public static void appendConnections(ServerLevel level,BlockPos pos,BlockState state,TrackBlockEntity owner,TrackNodeLocation from,Collection<DiscoveredLocation> result){
        if(owner.boundLocation==null){if(state.getValue(TrackBlock.SHAPE).isPortal())throw new UnloadedTrackException(level,pos);return;}
        if(TrackPortalCleanup.get(level.getServer()).isRetired(owner.getPortalBinding()))return;
        var peer=peer(owner);if(peer==null||!portalPresent(level,pos,state)||!portalPresent(peer.level(),peer.pos(),peer.state()))return;
        var localTrack=(ITrackBlock)state.getBlock();var otherTrack=(ITrackBlock)peer.state().getBlock();var center=Vec3.atBottomCenterOf(pos).add(0,localTrack.getElevationAtCenter(level,pos,state),0);var otherCenter=Vec3.atBottomCenterOf(peer.pos()).add(0,otherTrack.getElevationAtCenter(peer.level(),peer.pos(),peer.state()),0);var otherAxis=otherTrack.getTrackAxes(peer.level(),peer.pos(),peer.state()).getFirst();
        for(var axis:localTrack.getTrackAxes(level,pos,state))ITrackBlock.addToListIfConnected(from,result,(distance,first)->(first?axis:otherAxis).scale(distance).add(first?center:otherCenter),first->(first?state:peer.state()).getValue(TrackBlock.SHAPE).getNormal(),first->first?level.dimension():peer.level().dimension(),v->0,axis,null,(first,v)->ITrackBlock.getMaterialSimple(first?level:peer.level(),v));
    }
    private TrackPortalConnection(){}
}

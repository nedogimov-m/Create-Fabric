package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.graph.RailMath;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.*;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Source turn/S-curve/slope sizing, followed by complete loaded-world/inventory preflight. */
public final class TrackPlacement {
    public static int maxTrackPlacementLength=32;
    public record ConnectingFrom(BlockPos pos,Vec3 axis,Vec3 normal,Vec3 end,net.minecraft.resources.ResourceKey<Level> dimension){
        public ConnectingFrom(BlockPos pos,Vec3 axis,Vec3 normal,Vec3 end){this(pos,axis,normal,end,Level.OVERWORLD);}
        public static final Codec<ConnectingFrom> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("pos").forGetter(ConnectingFrom::pos),Vec3.CODEC.fieldOf("axis").forGetter(ConnectingFrom::axis),Vec3.CODEC.fieldOf("normal").forGetter(ConnectingFrom::normal),Vec3.CODEC.fieldOf("end").forGetter(ConnectingFrom::end),Level.RESOURCE_KEY_CODEC.optionalFieldOf("dimension",Level.OVERWORLD).forGetter(ConnectingFrom::dimension)).apply(i,ConnectingFrom::new));
        public static final StreamCodec<FriendlyByteBuf,ConnectingFrom> STREAM_CODEC=new StreamCodec<>(){
            @Override public ConnectingFrom decode(FriendlyByteBuf b){return new ConnectingFrom(b.readBlockPos(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,b.readIdentifier()));}
            @Override public void encode(FriendlyByteBuf b,ConnectingFrom v){b.writeBlockPos(v.pos());for(var a:List.of(v.axis(),v.normal(),v.end())){b.writeDouble(a.x);b.writeDouble(a.y);b.writeDouble(a.z);}b.writeIdentifier(v.dimension().identifier());}
        };
    }
    public static final class PlacementInfo {
        public final TrackMaterial trackMaterial;public BezierConnection curve;public boolean valid,hasRequiredTracks,hasRequiredGirders,hasRequiredPavement;public int requiredTracks,requiredGirders,requiredPavement,end1Extent,end2Extent;public Item pavingItem;public String message;
        public BlockPos pos1,pos2;public Vec3 axis1,axis2,end1,end2,normal1,normal2;public Map<BlockPos,BlockState> desiredTracks=Map.of(),desiredPavement=Map.of();
        PlacementInfo(TrackMaterial material){trackMaterial=material;}
        PlacementInfo withMessage(String message){valid=false;this.message="create.track."+message;return this;}
        PlacementInfo tooJumbly(){curve=null;return this;}
    }
    public static PlacementInfo tryConnect(Level level,Player player,BlockPos pos2,BlockState state2,ItemStack stack,boolean girder,boolean maximiseTurn){
        var info=plan(level,player,pos2,state2,stack,girder,maximiseTurn);
        if(!info.valid)return info;
        if(level.isClientSide())return complete(level,player,stack,info,state2,true);
        return commit((ServerLevel)level,player,stack,info,state2);
    }
    /** Advisory native client/server simulation. Uses the same world/inventory preflight without committing anything. */
    public static PlacementInfo preview(Level level,Player player,BlockPos pos,BlockState state,ItemStack stack,boolean girder,boolean maximise){var info=plan(level,player,pos,state,stack,girder,maximise);return info.valid?complete(level,player,stack,info,state,true):info;}
    private static PlacementInfo plan(Level level,Player player,BlockPos pos2,BlockState state2,ItemStack stack,boolean girder,boolean maximiseTurn){
        var info=new PlacementInfo(TrackMaterial.fromItem(stack.getItem()));
        var connecting=stack.get(AllDataComponents.TRACK_CONNECTING_FROM);
        if(connecting==null || !(state2.getBlock() instanceof ITrackBlock))return info.withMessage("original_missing");
        if(!player.mayBuild()||player.isSpectator())return info.withMessage("forbidden");
        if(!LoadedLevelAccess.isLoaded(level,pos2))return info.withMessage("unloaded");
        var actualTarget=LoadedLevelAccess.blockState(level,pos2);var targetOwner=TrackBlockEntityAccess.existing(level,pos2);
        if(actualTarget.getOptionalValue(TrackBlock.HAS_BE).orElse(false)&&!(targetOwner instanceof TrackBlockEntity))return info.withMessage("unloaded");
        for(var vector:List.of(connecting.axis(),connecting.normal(),connecting.end()))if(!Double.isFinite(vector.x)||!Double.isFinite(vector.y)||!Double.isFinite(vector.z))return info.withMessage("original_missing");
        if(((ITrackBlock)state2.getBlock()).getTrackAxes(level,pos2,state2).stream().noneMatch(axis->axis.lengthSqr()>1e-12))return info.withMessage("original_missing");
        int maxLength=Mth.clamp(maxTrackPlacementLength,16,128);var lookVec=player.getLookAngle();
		ITrackBlock track = (ITrackBlock) state2.getBlock();
		Pair<Vec3, AxisDirection> nearestTrackAxis = track.getNearestTrackAxis(level, pos2, state2, lookVec);
		Vec3 axis2 = nearestTrackAxis.getFirst()
			.scale(nearestTrackAxis.getSecond() == AxisDirection.POSITIVE ? -1 : 1);
		Vec3 normal2 = track.getUpNormal(level, pos2, state2)
			.normalize();
		Vec3 normedAxis2 = axis2.normalize();
		Vec3 end2 = track.getCurveStart(level, pos2, state2, axis2);

		ConnectingFrom connectingFrom = connecting;

		BlockPos pos1 = connectingFrom.pos();
		Vec3 axis1 = connectingFrom.axis();
		Vec3 normedAxis1 = axis1.normalize();
		Vec3 end1 = connectingFrom.end();
		Vec3 normal1 = connectingFrom.normal();
		if (!connectingFrom.dimension().equals(level.dimension())) return info.withMessage("different_dimension");
        if (!LoadedLevelAccess.isLoaded(level,pos1) || !LoadedLevelAccess.isLoaded(level,pos2)) return info.withMessage("unloaded");
        BlockState state1 = LoadedLevelAccess.blockState(level,pos1);
        if (!(state1.getBlock() instanceof ITrackBlock source) || !state1.hasProperty(TrackBlock.HAS_BE)) return info.withMessage("original_missing");
        var sourceOwner=TrackBlockEntityAccess.existing(level,pos1);
        if(state1.getValue(TrackBlock.HAS_BE)&&!(sourceOwner instanceof TrackBlockEntity))return info.withMessage("unloaded");
        boolean axisFound=false; for(var a:source.getTrackAxes(level,pos1,state1))if(a.distanceTo(axis1)<1e-6 || a.scale(-1).distanceTo(axis1)<1e-6)axisFound=true; if(!axisFound)return info.withMessage("original_missing");
        if (source.getCurveStart(level,pos1,state1,axis1).distanceTo(end1)>1e-6 || source.getUpNormal(level,pos1,state1).normalize().distanceTo(normal1)>1e-6) return info.withMessage("original_missing");
        if (sourceOwner instanceof TrackBlockEntity old && old.isTilted())return info.withMessage("turn_start");
        if (state1.getValue(TrackBlock.SHAPE).isPortal() || state2.getValue(TrackBlock.SHAPE).isPortal()) return info.withMessage("portal_pending");
        if (source.getTrackAxes(level,pos1,state1).size()!=1 || track.getTrackAxes(level,pos2,state2).size()!=1) return info.withMessage("junction_start");

		

		if (pos1.equals(pos2))
			return info.withMessage("second_point");
		if (pos1.distSqr(pos2) > maxLength * maxLength)
			return info.withMessage("too_far")
				.tooJumbly();
		if (!state1.hasProperty(TrackBlock.HAS_BE))
			return info.withMessage("original_missing");
		if (targetOwner instanceof TrackBlockEntity tbe && tbe.isTilted())
			return info.withMessage("turn_start");

		if (axis1.dot(end2.subtract(end1)) < 0) {
			axis1 = axis1.scale(-1);
			normedAxis1 = normedAxis1.scale(-1);
			end1 = source.getCurveStart(level, pos1, state1, axis1);
			
		}

		double[] intersect = RailMath.intersect(end1, end2, normedAxis1, normedAxis2, Axis.Y);
		boolean parallel = intersect == null;
		boolean skipCurve = false;

		if ((parallel && normedAxis1.dot(normedAxis2) > 0) || (!parallel && (intersect[0] < 0 || intersect[1] < 0))) {
			axis2 = axis2.scale(-1);
			normedAxis2 = normedAxis2.scale(-1);
			end2 = track.getCurveStart(level, pos2, state2, axis2);
			
		}

		Vec3 cross2 = normedAxis2.cross(new Vec3(0, 1, 0));

		double a1 = Mth.atan2(normedAxis2.z, normedAxis2.x);
		double a2 = Mth.atan2(normedAxis1.z, normedAxis1.x);
		double angle = a1 - a2;
		double ascend = end2.subtract(end1).y;
		double absAscend = Math.abs(ascend);
		boolean slope = !normal1.equals(normal2);

		

		// S curve or Straight

		double dist = 0;

		if (parallel) {
			double[] sTest = RailMath.intersect(end1, end2, normedAxis1, cross2, Axis.Y);
			if (sTest != null) {
				double t = Math.abs(sTest[0]);
				double u = Math.abs(sTest[1]);

				skipCurve = Mth.equal(u, 0);

				if (!skipCurve && sTest[0] < 0)
					return info.withMessage("perpendicular")
						.tooJumbly();

				if (skipCurve) {
					dist = Vec3.atCenterOf(pos1)
						.distanceTo(Vec3.atCenterOf(pos2));
					info.end1Extent = (int) Math.round((dist + 1) / axis1.length());

				} else {
					if (!Mth.equal(ascend, 0) || normedAxis1.y != 0)
						return info.withMessage("ascending_s_curve");

					double targetT = u <= 1 ? 3 : u * 2;

					if (t < targetT)
						return info.withMessage("too_sharp");

					// This is for standardising s curve sizes
					if (t > targetT) {
						int correction = (int) ((t - targetT) / axis1.length());
						info.end1Extent = maximiseTurn ? 0 : correction / 2 + (correction % 2);
						info.end2Extent = maximiseTurn ? 0 : correction / 2;
					}
				}
			}
		}

		// Slope

		if (slope) {
			if (!skipCurve)
				return info.withMessage("slope_turn");
			if (Mth.equal(normal1.dot(normal2), 0))
				return info.withMessage("opposing_slopes");
			if ((axis1.y < 0 || axis2.y > 0) && ascend > 0)
				return info.withMessage("leave_slope_ascending");
			if ((axis1.y > 0 || axis2.y < 0) && ascend < 0)
				return info.withMessage("leave_slope_descending");

			skipCurve = false;
			info.end1Extent = 0;
			info.end2Extent = 0;

			Axis plane = Mth.equal(axis1.x, 0) ? Axis.X : Axis.Z;
			intersect = RailMath.intersect(end1, end2, normedAxis1, normedAxis2, plane);
			if(intersect==null)return info.withMessage("too_sharp");
            double dist1 = Math.abs(intersect[0] / axis1.length());
			double dist2 = Math.abs(intersect[1] / axis2.length());

			if (dist1 > dist2)
				info.end1Extent = (int) Math.round(dist1 - dist2);
			if (dist2 > dist1)
				info.end2Extent = (int) Math.round(dist2 - dist1);

			double turnSize = Math.min(dist1, dist2);
			if (intersect[0] < 0 || intersect[1] < 0)
				return info.withMessage("too_sharp")
					.tooJumbly();
			if (turnSize < 2)
				return info.withMessage("too_sharp");

			// This is for standardising curve sizes
			if (turnSize > 2 && !maximiseTurn) {
				info.end1Extent += turnSize - 2;
				info.end2Extent += turnSize - 2;
				turnSize = 2;
			}
		}

		// Straight ascend

		if (skipCurve && !Mth.equal(ascend, 0)) {
			int hDistance = info.end1Extent;
			if (axis1.y == 0 || !Mth.equal(absAscend + 1, dist / axis1.length())) {

				if (axis1.y != 0 && axis1.y == -axis2.y)
					return info.withMessage("ascending_s_curve");

				info.end1Extent = 0;
				double minHDistance = Math.max(absAscend < 4 ? absAscend * 4 : absAscend * 3, 6) / axis1.length();
				if (hDistance < minHDistance)
					return info.withMessage("too_steep");
				if (hDistance > minHDistance) {
					int correction = (int) (hDistance - minHDistance);
					info.end1Extent = maximiseTurn ? 0 : correction / 2 + (correction % 2);
					info.end2Extent = maximiseTurn ? 0 : correction / 2;
				}

				skipCurve = false;
			}
		}

		// Turn

		if (!parallel) {
			float absAngle = Math.abs((float)Math.toDegrees(angle));
			if (absAngle < 60 || absAngle > 300)
				return info.withMessage("turn_90")
					.tooJumbly();

			intersect = RailMath.intersect(end1, end2, normedAxis1, normedAxis2, Axis.Y);
			double dist1 = Math.abs(intersect[0]);
			double dist2 = Math.abs(intersect[1]);
			float ex1 = 0;
			float ex2 = 0;

			if (dist1 > dist2)
				ex1 = (float) ((dist1 - dist2) / axis1.length());
			if (dist2 > dist1)
				ex2 = (float) ((dist2 - dist1) / axis2.length());

			double turnSize = Math.min(dist1, dist2) - .1d;
			boolean ninety = (absAngle + .25f) % 90 < 1;

			if (intersect[0] < 0 || intersect[1] < 0)
				return info.withMessage("too_sharp")
					.tooJumbly();

			double minTurnSize = ninety ? 7 : 3.25;
			double turnSizeToFitAscend =
				minTurnSize + (ninety ? Math.max(0, absAscend - 3) * 2f : Math.max(0, absAscend - 1.5f) * 1.5f);

			if (turnSize < minTurnSize)
				return info.withMessage("too_sharp");
			if (turnSize < turnSizeToFitAscend)
				return info.withMessage("too_steep");

			// This is for standardising curve sizes
			if (!maximiseTurn) {
				ex1 += (turnSize - turnSizeToFitAscend) / axis1.length();
				ex2 += (turnSize - turnSizeToFitAscend) / axis2.length();
			}
			info.end1Extent = Mth.floor(ex1);
			info.end2Extent = Mth.floor(ex2);
			turnSize = turnSizeToFitAscend;
		}

		Vec3 offset1 = axis1.scale(info.end1Extent);
		Vec3 offset2 = axis2.scale(info.end2Extent);
		BlockPos targetPos1 = pos1.offset(BlockPos.containing(offset1));
		BlockPos targetPos2 = pos2.offset(BlockPos.containing(offset2));

		info.curve = skipCurve ? null
			: new BezierConnection(Couple.create(targetPos1, targetPos2),
			Couple.create(end1.add(offset1), end2.add(offset2)), Couple.create(normedAxis1, normedAxis2),
			Couple.create(normal1, normal2), true, girder, TrackMaterial.fromItem(stack.getItem()));

		info.valid = true;

		info.pos1 = pos1;
		info.pos2 = pos2;
		info.axis1 = axis1;
		info.axis2 = axis2;info.end1=end1;info.end2=end2;info.normal1=normal1;info.normal2=normal2;

        return info;
    }
    private record Snapshot(BlockState state,CompoundTag blockEntity){}
    private static PlacementInfo commit(ServerLevel level,Player player,ItemStack held,PlacementInfo info,BlockState state2){return complete(level,player,held,info,state2,false);}
    private static PlacementInfo complete(Level level,Player player,ItemStack held,PlacementInfo info,BlockState state2,boolean simulate){
        var desired=new LinkedHashMap<BlockPos,BlockState>();boolean curveExists=false;
        var source=LoadedLevelAccess.blockState(level,info.pos1);
        if(source.getOptionalValue(TrackBlock.HAS_BE).orElse(false)&&!(TrackBlockEntityAccess.existing(level,info.pos1) instanceof TrackBlockEntity))return info.withMessage("unloaded");
        for(boolean first:new boolean[]{true,false}){
            int extent=first?info.end1Extent:info.end2Extent;Vec3 axis=first?info.axis1:info.axis2;BlockPos pos=first?info.pos1:info.pos2;BlockState shaped=first?source:state2;
            for(int i=0;i<(info.curve==null?extent:extent+1);i++){
                BlockPos at=pos.offset(BlockPos.containing(axis.scale(i)));
                if(!LoadedLevelAccess.isLoaded(level,at))return info.withMessage("unloaded");
                var existing=desired.getOrDefault(at,LoadedLevelAccess.blockState(level,at));
                if(existing.getOptionalValue(TrackBlock.HAS_BE).orElse(false)&&!(TrackBlockEntityAccess.existing(level,at) instanceof TrackBlockEntity))return info.withMessage("unloaded");
                var placed=info.trackMaterial.getBlock().defaultBlockState().setValue(TrackBlock.SHAPE,shaped.getValue(TrackBlock.SHAPE));
                if(existing.getBlock() instanceof ITrackBlock track){
                    var requested=placed;placed=track.overlay(level,at,existing,placed);
                    if(placed==existing&&!track.getTrackAxes(level,at,existing).containsAll(((ITrackBlock)requested.getBlock()).getTrackAxes(level,at,requested)))return info.withMessage("blocked");
                    // An incompatible existing line may not silently swallow a connection endpoint.
                    if(existing.getBlock()!=placed.getBlock())return info.withMessage("blocked");
                }else if(!existing.canBeReplaced() && !existing.is(net.minecraft.tags.BlockTags.FLOWERS))return info.withMessage("blocked");
                if(!level.mayInteract(player,at)||!level.isInValidBounds(at))return info.withMessage("forbidden");
                desired.put(at,placed.setValue(TrackBlock.WATERLOGGED,existing.getFluidState().getType()==Fluids.WATER));
            }
        }
        if(info.curve!=null){
            for(var endpoint:info.curve.bePositions){
                if(!LoadedLevelAccess.isLoaded(level,endpoint))return info.withMessage("unloaded");
                if(level instanceof ServerLevel server&&TrackConnectionCleanup.isPendingEndpoint(server,endpoint))return info.withMessage("unloaded");
                var shaped=desired.getOrDefault(endpoint,LoadedLevelAccess.blockState(level,endpoint));
                if(!(shaped.getBlock() instanceof ITrackBlock)||!shaped.hasProperty(TrackBlock.HAS_BE))return info.withMessage("blocked");
                desired.put(endpoint,shaped.setValue(TrackBlock.HAS_BE,true));
            }
            var existing=TrackBlockEntityAccess.existing(level,info.curve.bePositions.getFirst());
            if(existing instanceof TrackBlockEntity track){
                var same=track.getConnections().get(info.curve.getKey());
                if(same!=null && (!same.equalsSansMaterial(info.curve)||same.getMaterial()!=info.curve.getMaterial()))return info.withMessage("blocked");
                if(same==null)info.requiredTracks+=info.curve.getTrackItemCost();else curveExists=true;
            }else info.requiredTracks+=info.curve.getTrackItemCost();
        }
        for(var entry:desired.entrySet())if(!(LoadedLevelAccess.blockState(level,entry.getKey()).getBlock() instanceof ITrackBlock))info.requiredTracks++;
        info.desiredTracks=Collections.unmodifiableMap(new LinkedHashMap<>(desired));
        TrackPaver.Plan pavement=new TrackPaver.Plan(Map.of(),Set.of(),0);BlockItem paveItem;
        try{paveItem=TrackPaver.pavingItem(level,info.pos1,player.getOffhandItem());if(paveItem!=null)pavement=TrackPaver.plan(level,info,paveItem.getBlock());}
        catch(UnloadedTrackException unavailable){return info.withMessage("unloaded");}
        info.pavingItem=paveItem==null?null:paveItem;
        info.requiredPavement=pavement.items();
        if(paveItem!=null&&paveItem.getBlock()==com.simibubi.create.AllBlocks.METAL_GIRDER&&curveExists&&info.curve!=null)info.requiredPavement-=info.curve.getGirderItemCost();
        int extraGirders=info.curve!=null&&info.curve.hasGirder&&!curveExists&&(paveItem==null||paveItem.getBlock()!=com.simibubi.create.AllBlocks.METAL_GIRDER)?info.curve.getGirderItemCost():0;
        info.requiredGirders=paveItem!=null&&paveItem.getBlock()==com.simibubi.create.AllBlocks.METAL_GIRDER?info.requiredPavement:extraGirders;
        info.desiredPavement=pavement.blocks();
        var allDesired=new LinkedHashMap<>(pavement.blocks());
        for(var entry:desired.entrySet()){var previous=allDesired.putIfAbsent(entry.getKey(),entry.getValue());if(previous!=null&&previous!=entry.getValue())return info.withMessage("blocked");}
        var checkedChunks=new HashSet<Long>();
        for(var at:allDesired.keySet()){
            if(!level.mayInteract(player,at)||!level.isInValidBounds(at))return info.withMessage("forbidden");
            // Native placement/neighbor callbacks inspect these real cells; unavailable terrain is not air.
            for(int x:new int[]{-2,2})for(int z:new int[]{-2,2}){var neighbor=at.offset(x,0,z);long chunk=net.minecraft.world.level.ChunkPos.pack(neighbor.getX()>>4,neighbor.getZ()>>4);if(level.isInValidBounds(neighbor)&&checkedChunks.add(chunk)&&!LoadedLevelAccess.isLoaded(level,neighbor))return info.withMessage("unloaded");}
        }
        var inventory=player.getInventory();var payment=payment(inventory,held.getItem(),info.requiredTracks,info.pavingItem,info.requiredPavement,extraGirders,!simulate);
        info.hasRequiredTracks=player.isCreative()||payment.tracks();info.hasRequiredPavement=player.isCreative()||payment.pavement();info.hasRequiredGirders=player.isCreative()||(paveItem!=null&&paveItem.getBlock()==com.simibubi.create.AllBlocks.METAL_GIRDER?payment.pavement():payment.girders());
        if(!info.hasRequiredTracks)return info.withMessage("not_enough_tracks");if(!info.hasRequiredPavement||!info.hasRequiredGirders)return info.withMessage("not_enough_pavement");
        if(level instanceof ServerLevel server)try{TrackPropagator.preflightChanges(server,allDesired.keySet());}catch(UnloadedTrackException unavailable){return info.withMessage("unloaded");}
        if(simulate)return info;
        if(!payment.unchanged(inventory))return info.withMessage("blocked");
        var before=new LinkedHashMap<BlockPos,Snapshot>();
        for(var at:allDesired.keySet()){var be=TrackBlockEntityAccess.existing(level,at);before.put(at,new Snapshot(LoadedLevelAccess.blockState(level,at),be==null?null:be.saveWithFullMetadata(level.registryAccess())));}
        try{
            for(var entry:allDesired.entrySet())if(LoadedLevelAccess.blockState(level,entry.getKey())!=entry.getValue()){
                if(!level.setBlock(entry.getKey(),entry.getValue(),Block.UPDATE_ALL)||LoadedLevelAccess.blockState(level,entry.getKey()).getBlock()!=entry.getValue().getBlock()||(desired.containsKey(entry.getKey())&&LoadedLevelAccess.blockState(level,entry.getKey())!=entry.getValue()))throw new IllegalStateException("Track/pavement world placement refused at "+entry.getKey());
            }
            if(info.curve!=null){
                if(!(LoadedLevelAccess.blockEntity(level,info.curve.bePositions.getFirst()) instanceof TrackBlockEntity first)||!(LoadedLevelAccess.blockEntity(level,info.curve.bePositions.getSecond()) instanceof TrackBlockEntity second))throw new IllegalStateException("Native conditional curve BE creation failed");
                first.addConnection(info.curve);second.addConnection(info.curve.secondary());
            }
            if(!player.isCreative())payment.apply(inventory);
        }catch(RuntimeException error){
            if(info.curve!=null&&!curveExists)TrackCurveCoverage.update(level,info.curve,true);
            for(var entry:before.entrySet())if(LoadedLevelAccess.blockEntity(level,entry.getKey()) instanceof TrackBlockEntity be)be.suppressRemovalForRollback();
            for(var entry:before.entrySet()){
                var at=entry.getKey();var snapshot=entry.getValue();level.setBlock(at,snapshot.state(),Block.UPDATE_ALL);level.removeBlockEntity(at);
                if(snapshot.blockEntity()!=null){var restored=BlockEntity.loadStatic(at,snapshot.state(),snapshot.blockEntity(),level.registryAccess());if(restored==null)throw new IllegalStateException("Cannot restore original track/pavement BE "+at,error);level.setBlockEntity(restored);}
            }
            for(var entry:before.entrySet())if(LoadedLevelAccess.blockState(level,entry.getKey())!=entry.getValue().state())throw new IllegalStateException("Cannot restore original native track/pavement state "+entry.getKey(),error);
            return info.withMessage("blocked");
        }
        info.hasRequiredTracks=true;
        if(info.curve!=null){if(LoadedLevelAccess.blockEntity(level,info.curve.bePositions.getFirst()) instanceof TrackBlockEntity first)first.tilt.tryApplySmoothing();if(LoadedLevelAccess.blockEntity(level,info.curve.bePositions.getSecond()) instanceof TrackBlockEntity second)second.tilt.tryApplySmoothing();}
        return info;
    }
    private record Payment(Map<Integer,ItemStack> before,Map<Integer,ItemStack> after,boolean tracks,boolean pavement,boolean girders){
        boolean unchanged(Inventory inventory){for(var entry:before.entrySet()){var actual=inventory.getItem(entry.getKey());if(actual.getCount()!=entry.getValue().getCount()||!ItemStack.isSameItemSameComponents(actual,entry.getValue()))return false;}return true;}
        void apply(Inventory inventory){if(!unchanged(inventory))throw new IllegalStateException("Actual source inventory changed during native world placement");if(after.containsKey(inventory.getSelectedSlot()))inventory.getSelectedItem().remove(AllDataComponents.TRACK_CONNECTING_FROM);for(var entry:after.entrySet())inventory.setItem(entry.getKey(),entry.getValue());inventory.setChanged();}
    }
    /** Pinned source spends other native main slots, then the selected stack, then actual offhand. */
    private static Payment payment(Inventory inventory,Item track,int requiredTracks,Item pavement,int requiredPavement,int requiredGirders,boolean capture){
        var order=new ArrayList<Integer>();int selected=inventory.getSelectedSlot();for(int i=0;i<inventory.getNonEquipmentItems().size();i++)if(i!=selected)order.add(i);order.add(selected);order.add(Inventory.SLOT_OFFHAND);
        var before=new LinkedHashMap<Integer,ItemStack>();var after=new LinkedHashMap<Integer,ItemStack>();int tracks=requiredTracks,paving=requiredPavement,girders=requiredGirders;
        for(int slot:order){var actual=inventory.getItem(slot);if(capture)before.put(slot,actual.copy());if(actual.isEmpty())continue;int debit=0;
            if(actual.is(track)){debit=Math.min(tracks,actual.getCount());tracks-=debit;}
            else if(pavement!=null&&actual.is(pavement)){debit=Math.min(paving,actual.getCount());paving-=debit;}
            else if(actual.is(com.simibubi.create.AllBlocks.METAL_GIRDER.asItem())){debit=Math.min(girders,actual.getCount());girders-=debit;}
            if(debit>0&&capture){var next=actual.copy();if(slot==selected)next.remove(AllDataComponents.TRACK_CONNECTING_FROM);next.setCount(actual.getCount()-debit);after.put(slot,next);}
        }
        return new Payment(before,after,tracks==0,paving==0,girders==0);
    }
    private TrackPlacement(){}
}

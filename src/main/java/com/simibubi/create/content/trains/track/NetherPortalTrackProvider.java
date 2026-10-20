package com.simibubi.create.content.trains.track;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import java.util.*;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native vanilla discovery and loaded frame proposals; TrackPortalConnection owns the actual frame/pair transaction. */
public final class NetherPortalTrackProvider {
    public static PortalTrackProvider.@Nullable Exit findExit(ServerLevel source,BlockFace inbound){
        if(!inbound.getFace().getAxis().isHorizontal())return null;
        var sourcePos=inbound.getConnectedPos();var sourceState=TrackPortalAccess.state(source,sourcePos);if(!sourceState.is(Blocks.NETHER_PORTAL))return null;
        var dimension=source.dimension().equals(Level.NETHER)?Level.OVERWORLD:Level.NETHER;var target=source.getServer().getLevel(dimension);
        if(target==null||!source.isAllowedToEnterPortal(target))return null;
        // Exact source probe: real registered glue entity, genuine1cube bounds, never spawned.
        var probe=new SuperGlueEntity(source,new AABB(sourcePos));probe.setYRot(inbound.getFace().toYRot());
        double scale=DimensionType.getTeleportationScale(source.dimensionType(),target.dimensionType());var border=target.getWorldBorder();var search=border.clampToBounds(probe.getX()*scale,probe.getY(),probe.getZ()*scale);int radius=target.dimension().equals(Level.NETHER)?16:128;
        // Keep native ChunkPos encounter order, while excluding chunks whose positions cannot pass
        // vanilla's final square/world-border filters. Every possible actual candidate must be known.
        var candidateChunks=ChunkPos.rangeClosed(ChunkPos.containing(search),Math.floorDiv(radius,16)+1).filter(chunk->chunk.getMaxBlockX()>=search.getX()-radius&&chunk.getMinBlockX()<=search.getX()+radius&&chunk.getMaxBlockZ()>=search.getZ()-radius&&chunk.getMinBlockZ()<=search.getZ()+radius&&border.isWithinBounds(chunk)).toList();
        var missing=new LinkedHashSet<BlockPos>();for(var chunk:candidateChunks)if(target.getChunkSource().getChunkNow(chunk.x(),chunk.z())==null)missing.add(new BlockPos(chunk.getMiddleBlockX(),search.getY(),chunk.getMiddleBlockZ()));
        if(!missing.isEmpty())throw new UnloadedTrackException(target.dimension(),missing);
        // Genuine native POI data. Avoid ensureLoadedAndValid, which requests EMPTY chunks/tickets.
        // Actual FULL chunk lifecycle already validated its real POI sections from real block data.
        var candidates=new ArrayList<BlockPos>();var manager=target.getPoiManager();
        for(var chunk:candidateChunks)manager.getInChunk(type->type.is(PoiTypes.NETHER_PORTAL),chunk,PoiManager.Occupancy.ANY).map(PoiRecord::getPos).filter(pos->Math.abs(pos.getX()-search.getX())<=radius&&Math.abs(pos.getZ()-search.getZ())<=radius&&border.isWithinBounds(pos)).filter(pos->TrackPortalAccess.state(target,pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS)).forEach(candidates::add);
        var nearest=candidates.stream().min(Comparator.comparingDouble((BlockPos pos)->pos.distSqr(search)).thenComparingInt(BlockPos::getY));
        if(nearest.isEmpty())return null; // Missing frames are planned below and committed with the native track pair in TrackPortalConnection.
        var targetPortal=nearest.get();var targetState=TrackPortalAccess.state(target,targetPortal);if(!targetState.is(sourceState.getBlock()))return null;
        var sourceAxis=sourceState.getValue(BlockStateProperties.HORIZONTAL_AXIS);var targetAxis=targetState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        var sourceRectangle=BlockUtil.getLargestRectangleAround(sourcePos,sourceAxis,21,Direction.Axis.Y,21,pos->TrackPortalAccess.state(source,pos)==sourceState);
        var targetRectangle=BlockUtil.getLargestRectangleAround(targetPortal,targetAxis,21,Direction.Axis.Y,21,pos->TrackPortalAccess.state(target,pos)==targetState);
        var relative=probe.getRelativePortalPosition(sourceAxis,sourceRectangle);var dimensions=probe.getDimensions(probe.getPose());
        // Native26.3 createDimensionTransition position formula, with actual native rectangles,
        // relative entity position/dimensions and its public native collision-free query.
        double horizontal=dimensions.width()/2d+((double)targetRectangle.axis1Size-dimensions.width())*relative.x;
        double vertical=((double)targetRectangle.axis2Size-dimensions.height())*relative.y;double perpendicular=.5+relative.z;var corner=targetRectangle.minCorner;
        var candidate=new Vec3(corner.getX()+(targetAxis==Direction.Axis.X?horizontal:perpendicular),corner.getY()+vertical,corner.getZ()+(targetAxis==Direction.Axis.X?perpendicular:horizontal));
        TrackPortalAccess.neighborhood(target,BlockPos.containing(candidate),2);
        var exit=BlockPos.containing(PortalShape.findCollisionFreePosition(candidate,target,probe,dimensions));var actual=TrackPortalAccess.state(target,exit);if(!actual.is(sourceState.getBlock()))return null;
        var direction=inbound.getFace();if(direction.getAxis()==actual.getValue(BlockStateProperties.HORIZONTAL_AXIS))direction=direction.getClockWise();
        return new PortalTrackProvider.Exit(target,new BlockFace(exit.relative(direction),direction.getOpposite()));
    }
    /** Proposed native rectangle/target only; never an alleged existing portal Exit. */
    static record MissingFrame(ServerLevel source,BlockFace inbound,BlockPos sourcePortal,net.minecraft.world.level.block.state.BlockState sourceState,SuperGlueEntity probe,NetherPortalFramePlan frame,BlockPos projectedPortal,BlockFace projectedTrack){}
    static MissingFrame prepareMissingFrame(ServerLevel source,BlockFace inbound){
        if(findExit(source,inbound)!=null)return null;
        var sourcePortal=inbound.getConnectedPos();var sourceState=TrackPortalAccess.state(source,sourcePortal);if(!sourceState.is(Blocks.NETHER_PORTAL))return null;
        var dimension=source.dimension().equals(Level.NETHER)?Level.OVERWORLD:Level.NETHER;var target=source.getServer().getLevel(dimension);if(target==null||!source.isAllowedToEnterPortal(target))return null;
        var probe=new SuperGlueEntity(source,new AABB(sourcePortal));probe.setYRot(inbound.getFace().toYRot());double scale=DimensionType.getTeleportationScale(source.dimensionType(),target.dimensionType());var search=target.getWorldBorder().clampToBounds(probe.getX()*scale,probe.getY(),probe.getZ()*scale);var axis=sourceState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        var selected=NetherPortalFramePlan.find(target,search,axis);if(selected.isEmpty())return null;var frame=selected.get();
        var candidate=plannedPosition(source,sourcePortal,sourceState,probe,frame.rectangle(),axis);var portal=BlockPos.containing(candidate);var direction=inbound.getFace();if(direction.getAxis()==axis)direction=direction.getClockWise();
        TrackPortalAccess.neighborhood(target,portal,2);
        return new MissingFrame(source,inbound,sourcePortal,sourceState,probe,frame,portal,new BlockFace(portal.relative(direction),direction.getOpposite()));
    }
    /** Called only after the genuine native frame has actually been installed in its real level. */
    static PortalTrackProvider.Exit finishCreation(MissingFrame creation){
        var source=creation.source();var target=creation.frame().level();if(TrackPortalAccess.state(source,creation.sourcePortal())!=creation.sourceState())throw new NetherPortalFrameTransaction.Refused(creation.sourcePortal(),"Actual source portal changed during frame creation");
        var axis=creation.frame().axis();var portalState=TrackPortalAccess.state(target,creation.projectedPortal());if(!portalState.is(Blocks.NETHER_PORTAL)||portalState.getValue(BlockStateProperties.HORIZONTAL_AXIS)!=axis)throw new NetherPortalFrameTransaction.Refused(creation.projectedPortal(),"Actual installed native portal does not match its source axis");
        var rectangle=BlockUtil.getLargestRectangleAround(creation.projectedPortal(),axis,21,Direction.Axis.Y,21,pos->TrackPortalAccess.state(target,pos)==portalState);var planned=creation.frame().rectangle();
        if(!rectangle.minCorner.equals(planned.minCorner)||rectangle.axis1Size!=planned.axis1Size||rectangle.axis2Size!=planned.axis2Size)throw new NetherPortalFrameTransaction.Refused(creation.projectedPortal(),"Actual native frame rectangle changed during installation");
        var probe=creation.probe();var candidate=plannedPosition(source,creation.sourcePortal(),creation.sourceState(),probe,rectangle,axis);var dimensions=probe.getDimensions(probe.getPose());TrackPortalAccess.neighborhood(target,BlockPos.containing(candidate),2);var actual=BlockPos.containing(PortalShape.findCollisionFreePosition(candidate,target,probe,dimensions));
        if(!actual.equals(creation.projectedPortal())||TrackPortalAccess.state(target,actual)!=portalState)throw new NetherPortalFrameTransaction.Refused(actual,"Actual installed native collision-free portal exit changed");
        return new PortalTrackProvider.Exit(target,creation.projectedTrack());
    }
    private static Vec3 plannedPosition(ServerLevel source,BlockPos sourcePortal,net.minecraft.world.level.block.state.BlockState sourceState,SuperGlueEntity probe,BlockUtil.FoundRectangle rectangle,Direction.Axis targetAxis){
        var sourceAxis=sourceState.getValue(BlockStateProperties.HORIZONTAL_AXIS);var sourceRectangle=BlockUtil.getLargestRectangleAround(sourcePortal,sourceAxis,21,Direction.Axis.Y,21,pos->TrackPortalAccess.state(source,pos)==sourceState);var relative=probe.getRelativePortalPosition(sourceAxis,sourceRectangle);var dimensions=probe.getDimensions(probe.getPose());
        double horizontal=dimensions.width()/2d+((double)rectangle.axis1Size-dimensions.width())*relative.x;double vertical=((double)rectangle.axis2Size-dimensions.height())*relative.y;double perpendicular=.5+relative.z;var corner=rectangle.minCorner;
        return new Vec3(corner.getX()+(targetAxis==Direction.Axis.X?horizontal:perpendicular),corner.getY()+vertical,corner.getZ()+(targetAxis==Direction.Axis.X?perpendicular:horizontal));
    }
    private NetherPortalTrackProvider(){}
}

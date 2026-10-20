package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.trains.graph.RailMath;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Source pavement footprints and quantities, collected against genuine loaded native terrain before mutation. */
public final class TrackPaver {
    public static final TagKey<Item> INVALID_FOR_TRACK_PAVING=TagKey.create(Registries.ITEM,Create.asResource("invalid_for_track_paving"));
    public record Plan(Map<BlockPos,BlockState> blocks,Set<BlockPos> footprint,int items){
        public Plan{blocks=Collections.unmodifiableMap(new LinkedHashMap<>(blocks));footprint=Collections.unmodifiableSet(new LinkedHashSet<>(footprint));}
    }
    public static BlockItem pavingItem(Level level,BlockPos start,ItemStack stack){
        if(stack.isEmpty()||!(stack.getItem() instanceof BlockItem item)||stack.is(INVALID_FOR_TRACK_PAVING))return null;
        var block=item.getBlock();if(block instanceof EntityBlock)return null;
        requireLoaded(level,start);for(var side:Direction.values())requireLoaded(level,start.relative(side));
        return block.defaultBlockState().getCollisionShape(level,start).isEmpty()?null:item;
    }
    public static Plan plan(Level level,TrackPlacement.PlacementInfo info,Block block){
        var collector=new Collector(level,new LinkedHashSet<>());
        for(boolean first:new boolean[]{true,false})collector.straight((first?info.pos1:info.pos2).below(),first?info.axis1:info.axis2,(first?info.end1Extent:info.end2Extent)+(info.curve!=null?1:0),block);
        if(info.curve!=null)collector.curve(info.curve,block);
        return collector.result();
    }
    /** Source API: callers own inventory/payment. New native placement uses one combined plan/transaction. */
    public static int paveStraight(Level level,BlockPos start,Vec3 direction,int extent,Block block,boolean simulate,Set<BlockPos> visited){
        var collector=new Collector(level,new LinkedHashSet<>(visited));collector.straight(start,direction,extent,block);var plan=collector.result();
        if(!simulate)applyNative(level,plan);visited.addAll(plan.footprint());return plan.items();
    }
    /** Source API, preserving the pinned full-block curve quantity quirk as well as slab/girder rules. */
    public static int paveCurve(Level level,BezierConnection curve,Block block,boolean simulate,Set<BlockPos> visited){
        var collector=new Collector(level,new LinkedHashSet<>(visited));collector.curve(curve,block);var plan=collector.result();
        if(!simulate)applyNative(level,plan);visited.addAll(plan.footprint());return plan.items();
    }
    private static void applyNative(Level level,Plan plan){
        if(level.isClientSide())throw new IllegalArgumentException("Native pavement mutation requires the server");
        for(var at:plan.footprint())requireLoaded(level,at);
        var checkedChunks=new HashSet<Long>();
        for(var at:plan.blocks().keySet()){
            if(!level.isInValidBounds(at))throw new IllegalArgumentException("Native pavement cell outside world bounds: "+at);
            for(int x:new int[]{-2,2})for(int z:new int[]{-2,2}){var neighbor=at.offset(x,0,z);if(level.isInValidBounds(neighbor)&&checkedChunks.add(net.minecraft.world.level.ChunkPos.pack(neighbor.getX()>>4,neighbor.getZ()>>4)))requireLoaded(level,neighbor);}
        }
        if(level instanceof ServerLevel server)TrackPropagator.preflightChanges(server,plan.blocks().keySet());
        var before=new LinkedHashMap<BlockPos,BlockState>();for(var at:plan.blocks().keySet())before.put(at,LoadedLevelAccess.blockState(level,at));
        try{for(var entry:plan.blocks().entrySet())if(!level.setBlock(entry.getKey(),entry.getValue(),Block.UPDATE_ALL)||LoadedLevelAccess.blockState(level,entry.getKey()).getBlock()!=entry.getValue().getBlock())throw new IllegalStateException("Native pavement placement refused at "+entry.getKey());}
        catch(RuntimeException error){for(var entry:before.entrySet())level.setBlock(entry.getKey(),entry.getValue(),Block.UPDATE_ALL);for(var entry:before.entrySet())if(LoadedLevelAccess.blockState(level,entry.getKey())!=entry.getValue())throw new IllegalStateException("Cannot restore original pavement cell "+entry.getKey(),error);throw error;}
    }
    private static void requireLoaded(Level level,BlockPos pos){if(!LoadedLevelAccess.isLoaded(level,pos))throw new UnloadedTrackException(level,pos);}
    private static boolean wallLike(Block block){return block instanceof WallBlock||block==AllBlocks.METAL_GIRDER;}
    private record Column(int x,int z){}
    private static final class Collector {
        final Level level;final Set<BlockPos> visited;final Set<BlockPos> footprint=new LinkedHashSet<>();final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();int items;
        Collector(Level level,Set<BlockPos> visited){this.level=level;this.visited=visited;}
        Plan result(){return new Plan(blocks,footprint,items);}
        boolean cell(BlockPos pos,BlockState state,int cost){
            if(!visited.add(pos))return false;footprint.add(pos.immutable());requireLoaded(level,pos);
            var existing=LoadedLevelAccess.blockState(level,pos);
            // Existing blocks, genuine BEs and their cargo are never replacement pavement.
            if(existing.getBlock()==state.getBlock()||!existing.canBeReplaced())return true;
            var owner=TrackBlockEntityAccess.existing(level,pos);if(owner!=null)return true;
            if(existing.hasBlockEntity()&&(!(existing.getBlock() instanceof TrackBlock)||existing.getOptionalValue(TrackBlock.HAS_BE).orElse(false)))throw new UnloadedTrackException(level,pos);
            if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,existing.getFluidState().getType()==Fluids.WATER);
            blocks.put(pos.immutable(),state);items+=cost;return true;
        }
        void straight(BlockPos startPos,Vec3 direction,int extent,Block block){
            var state=block.defaultBlockState();boolean slab=state.hasProperty(SlabBlock.TYPE),wall=wallLike(block);if(slab)state=state.setValue(SlabBlock.TYPE,SlabType.DOUBLE);
            if(block instanceof GirderBlock)for(var side:Direction.Plane.HORIZONTAL)if(side.getUnitVec3().equals(direction))state=state.setValue(GirderBlock.TOP,false).setValue(GirderBlock.BOTTOM,false).setValue(GirderBlock.AXIS,side.getAxis()).setValue(side.getAxis()==Direction.Axis.X?GirderBlock.X:GirderBlock.Z,true);
            var toPlace=new LinkedHashSet<BlockPos>();var start=Vec3.atCenterOf(startPos);var normal=direction.cross(new Vec3(0,1,0));var unitNormal=normal.normalize();var unitDirection=direction.normalize();float filler=.45f;
            for(int i=0;i<extent;i++){
                var main=start.add(direction.scale(i));toPlace.add(BlockPos.containing(main.add(normal)));toPlace.add(BlockPos.containing(main.subtract(normal)));if(wall)continue;toPlace.add(BlockPos.containing(main));
                if(i<extent-1)for(int sign:new int[]{1,-1})toPlace.add(BlockPos.containing(main.add(unitNormal.scale(sign*filler)).add(unitDirection.scale(filler))));
                if(i>0)for(int sign:new int[]{1,-1})toPlace.add(BlockPos.containing(main.add(unitNormal.scale(sign*filler)).add(unitDirection.scale(-filler))));
            }
            for(var at:toPlace)cell(at,state,slab?2:1);
        }
        void curve(BezierConnection curve,Block block){
            var state=block.defaultBlockState();boolean slab=state.hasProperty(SlabBlock.TYPE);if(slab)state=state.setValue(SlabBlock.TYPE,SlabType.DOUBLE);
            if(wallLike(block)){if(block==AllBlocks.METAL_GIRDER)items+=curve.getGirderItemCost();return;}
            var heights=new LinkedHashMap<Column,Double>();var origin=curve.bePositions.getFirst();var base=Vec3.atLowerCornerOf(origin);var a=curve.starts.getFirst().subtract(base).add(0,3/16f,0);var b=curve.starts.getSecond().subtract(base).add(0,3/16f,0);double handle=curve.getHandleLength();var qa=curve.axes.getFirst().scale(handle).add(a);var qb=curve.axes.getSecond().scale(handle).add(b);var na=curve.normals.getFirst();var nb=curve.normals.getSecond();int segments=curve.getSegmentCount();var lut=curve.getStepLUT();
            for(int i=0;i<segments;i++){
                float t=i*lut[i]/segments;t+=.5f/segments;var point=RailMath.bezier(a,b,qa,qb,t);var derivative=RailMath.bezierDerivative(a,b,qa,qb,t).normalize();var face=na.equals(nb)?na:RailMath.slerp(t,na,nb);var side=face.cross(derivative).normalize();var below=point.add(face.scale(-1.125f));var r1=below.add(side.scale(.97f));var r2=below.subtract(side.scale(.97f));
                for(var at:new Vec3[]{r1,r2,r1.add(r2).scale(.5)}){var pos=BlockPos.containing(at);heights.merge(new Column(pos.getX(),pos.getZ()),at.y,Math::min);}
            }
            for(var entry:heights.entrySet()){
                double y=entry.getValue();int floor=Mth.floor(y);boolean half=slab&&y-floor>=.5;var at=new BlockPos(entry.getKey().x(),floor,entry.getKey().z()).offset(origin).above(half?1:0);var placed=half?state.setValue(SlabBlock.TYPE,SlabType.BOTTOM):state;
                // Pinned source charges TWO ordinary full blocks too. Do not silently "correct" this.
                if(cell(at,placed,half?1:2)&&half)cell(at.below(),placed.setValue(SlabBlock.TYPE,SlabType.TOP),1);
            }
        }
    }
    private TrackPaver(){}
}

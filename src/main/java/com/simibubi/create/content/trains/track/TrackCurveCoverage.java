package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/** Source rasterisation and three-random-tick coverage lifecycle, using only immediately available chunks. */
public final class TrackCurveCoverage {
    public static Set<BlockPos> positions(BezierConnection curve){
        var positions=new LinkedHashSet<BlockPos>();for(var entry:curve.rasterise().entrySet())positions.add(new BlockPos(entry.getKey().getFirst(),Mth.floor(entry.getValue()),entry.getKey().getSecond()).offset(curve.bePositions.getFirst()).above());return Set.copyOf(positions);
    }
    public static void update(Level level,BezierConnection curve,boolean remove){
        if(level==null||level.isClientSide())return;
        var retained=new HashSet<BlockPos>();
        if(remove)for(var endpoint:TrackCurveIndex.loaded(level))for(var other:endpoint.getConnections().values())
            if(other.primary&&(!other.equalsSansMaterial(curve)||other.getMaterial()!=curve.getMaterial()))retained.addAll(positions(other));
        for(var pos:positions(curve)){
            if(!LoadedLevelAccess.isLoaded(level,pos))continue;var state=LoadedLevelAccess.blockState(level,pos);boolean present=state.getBlock() instanceof FakeTrackBlock;
            if(remove){if(present&&!retained.contains(pos))LoadedLevelAccess.setBlockQuiet(level,pos,state.getFluidState().createLegacyBlock());continue;}
            var fluid=state.getFluidState();if(!fluid.isEmpty()&&!fluid.isSourceOfType(Fluids.WATER))continue;
            if(!present&&state.canBeReplaced()&&LoadedLevelAccess.blockEntity(level,pos)==null){
                var replacement=AllBlocks.FAKE_TRACK.defaultBlockState().setValue(FakeTrackBlock.WATERLOGGED,fluid.isSourceOfType(Fluids.WATER));
                if(!LoadedLevelAccess.setBlockQuiet(level,pos,replacement))continue;
            }
            FakeTrackBlock.keepAlive(level,pos);
        }
    }
    private TrackCurveCoverage(){}
}

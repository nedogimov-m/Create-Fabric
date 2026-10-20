package com.simibubi.create.content.trains.track;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Strict native portal reads. Missing real terrain throws; known native world bounds are void. */
public final class TrackPortalAccess {
    public static BlockState state(ServerLevel level,BlockPos pos){
        if(!level.isInValidBounds(pos))return Blocks.VOID_AIR.defaultBlockState();
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null)throw new UnloadedTrackException(level,pos);return chunk.getBlockState(pos);
    }
    public static void neighborhood(ServerLevel level,BlockPos pos,int radius){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            var at=pos.offset(x,0,z);if(!level.isInValidBounds(at))continue;
            if(level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null)throw new UnloadedTrackException(level,at);
        }
    }
    private TrackPortalAccess(){}
}

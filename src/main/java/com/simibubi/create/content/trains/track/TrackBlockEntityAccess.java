package com.simibubi.create.content.trains.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Existing native track data only: no chunk request or empty default block entity creation. */
public final class TrackBlockEntityAccess {
    public static BlockEntity existing(BlockGetter reader,BlockPos pos){
        if(reader instanceof Level level){
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            // CHECK can promote genuine saved pending NBT; it cannot create an empty default BE.
            return chunk==null?null:chunk.getBlockEntity(pos,LevelChunk.EntityCreationType.CHECK);
        }
        return reader.getBlockEntity(pos);
    }
    private TrackBlockEntityAccess(){}
}

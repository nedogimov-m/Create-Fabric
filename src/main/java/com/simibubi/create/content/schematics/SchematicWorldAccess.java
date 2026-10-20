package com.simibubi.create.content.schematics;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;

/** Genuine saved/live native data only: CHECK promotes saved NBT but never creates an empty owner. */
public final class SchematicWorldAccess {
    public static BlockEntity blockEntity(BlockGetter reader,BlockPos pos){
        if(reader instanceof Level level){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk==null?null:chunk.getBlockEntity(pos,LevelChunk.EntityCreationType.CHECK);}
        return reader.getBlockEntity(pos);
    }
    private static boolean ready(Level level,BlockPos pos){
        if(!LoadedLevelAccess.isLoaded(level,pos))return false;var state=LoadedLevelAccess.blockState(level,pos);
        boolean expected=state.hasBlockEntity()&&(!(state.getBlock() instanceof TrackBlock)||state.getValue(TrackBlock.HAS_BE));return !expected||blockEntity(level,pos)!=null;
    }
    public static boolean readyForPlacement(Level level,BlockState source,BlockPos pos){
        if(!ready(level,pos))return false;
        if(source.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&source.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER&&!ready(level,pos.above()))return false;
        return !source.hasProperty(BlockStateProperties.BED_PART)||source.getValue(BlockStateProperties.BED_PART)!=BedPart.FOOT||ready(level,pos.relative(source.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
    private SchematicWorldAccess(){}
}

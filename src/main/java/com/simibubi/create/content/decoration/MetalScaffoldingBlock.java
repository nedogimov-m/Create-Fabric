package com.simibubi.create.content.decoration;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Original non-falling metal scaffold, including its eight-voxel upper half. */
public final class MetalScaffoldingBlock extends ScaffoldingBlock {
    public static final VoxelShape HALF=box(0,8,0,16,16,16);
    public static final VoxelShape FULL=Shapes.or(HALF,box(0,0,0,2,16,2),box(0,0,14,2,16,16),box(14,0,0,16,16,2),box(14,0,14,16,16,16));
    public MetalScaffoldingBlock(Properties properties){super(properties);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return true;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(BOTTOM)?HALF:super.getCollisionShape(state,level,pos,context);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        // Fabric bakes shapes at block registration, before the block item is registered.
        // A live lookup must not memoize that temporary AIR result in Block.asItem().
        return state.getValue(BOTTOM)?HALF:context.isHoldingItem(net.minecraft.world.item.Item.byBlock(this))?Shapes.block():FULL;
    }
    @Override protected VoxelShape getInteractionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.block();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,
            Direction face,BlockPos neighborPos,BlockState neighbor,RandomSource random){
        super.updateShape(state,level,ticks,pos,face,neighborPos,neighbor,random);
        var below=LoadedLevelAccess.blockState(level,pos.below());
        return face==Direction.DOWN?state.setValue(BOTTOM,!below.is(this)&&!below.isFaceSturdy(level,pos.below(),Direction.UP)):state;
    }
    @Override protected boolean skipRendering(BlockState state,BlockState other,Direction face){
        return other.getBlock() instanceof MetalScaffoldingBlock&&face.getAxis().isHorizontal()
                &&!(other.getValue(BOTTOM)&&!state.getValue(BOTTOM));
    }
}

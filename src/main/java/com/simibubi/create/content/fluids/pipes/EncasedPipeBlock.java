package com.simibubi.create.content.fluids.pipes;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
public final class EncasedPipeBlock extends FluidPipeBlock {
    public EncasedPipeBlock(Properties properties){super(properties);}
    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type){return createTickerHelper(type,com.simibubi.create.AllBlockEntityTypes.ENCASED_FLUID_PIPE,FluidPipeBlockEntity::tick);}
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return net.minecraft.world.phys.shapes.Shapes.block();}
    @Override public BlockState updateBlockState(BlockState state,Direction preferred,Direction ignore,LevelReader level,BlockPos pos){return state;}
}

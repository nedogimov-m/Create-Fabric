package com.simibubi.create.content.kinetics.fan;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.crank.ValveHandleGeometry;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.*;

public final class NozzleBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public NozzleBlock(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(FACING,Direction.UP)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getClickedFace()); }
    @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType type) { return false; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING);
        return Shapes.join(Shapes.or(ValveHandleGeometry.box(facing,2,0,2,14,14,14),ValveHandleGeometry.box(facing,1,13,1,15,15,15)),
                ValveHandleGeometry.box(facing,3,13,3,13,15,13),BooleanOp.ONLY_FIRST);
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var facing = state.getValue(FACING);
        return LoadedLevelAccess.blockEntity(level,pos.relative(facing.getOpposite())) instanceof IAirCurrentSource source
                && source.getAirflowOriginSide() == facing;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        if (!level.isClientSide() && !canSurvive(state,level,pos)) level.destroyBlock(pos,true);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING,mirror.mirror(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NozzleBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.NOZZLE,NozzleBlockEntity::tick); }
}

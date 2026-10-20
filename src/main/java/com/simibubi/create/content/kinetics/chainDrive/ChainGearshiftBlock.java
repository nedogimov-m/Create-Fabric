package com.simibubi.create.content.kinetics.chainDrive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.item.context.BlockPlaceContext;

public final class ChainGearshiftBlock extends ChainDriveBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ChainGearshiftBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return createTickerHelper(type, com.simibubi.create.AllBlockEntityTypes.ADJUSTABLE_CHAIN_GEARSHIFT, ChainGearshiftBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, orientation, moved);
        if (!level.isClientSide() && state.getValue(POWERED) != level.hasNeighborSignal(pos)) {
            level.setBlock(pos, state.setValue(POWERED, level.hasNeighborSignal(pos)), Block.UPDATE_CLIENTS);
        }
        if (com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level, pos) instanceof ChainGearshiftBlockEntity owner)
            owner.neighbourChanged();
    }
}

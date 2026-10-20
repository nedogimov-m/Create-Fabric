package com.simibubi.create.content.kinetics.transmission;

import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;

public class GearshiftBlock extends KineticBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public GearshiftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, orientation, moved);
        if (level instanceof ServerLevel server && state.getValue(POWERED) != level.hasNeighborSignal(pos)) {
            // Detach descendants using the old ratio before exposing the changed source topology.
            detachKinetics(level, pos, true);
            if (com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level, pos) != state) return;
            level.setBlock(pos, state.setValue(POWERED, level.hasNeighborSignal(pos)), Block.UPDATE_CLIENTS);
            KineticNetworks.markDirty(server);
        }
    }

    /** Source public detach/re-attach hook; redstone changes never compare against stale descendants. */
    public void detachKinetics(Level level, BlockPos pos, boolean reAttachNextTick) {
        if (!(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level, pos) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity owner)) return;
        com.simibubi.create.content.kinetics.RotationPropagator.handleRemoved(level, pos, owner);
        if (reAttachNextTick)
            level.scheduleTick(pos, this, 1, net.minecraft.world.ticks.TickPriority.EXTREMELY_HIGH);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level, pos) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity owner)
            com.simibubi.create.content.kinetics.RotationPropagator.handleAdded(level, pos, owner);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if ((rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
                && state.getValue(AXIS) != Direction.Axis.Y) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }
}

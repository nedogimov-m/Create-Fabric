package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public abstract class RotatedPillarKineticBlock extends KineticBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    protected RotatedPillarKineticBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        if ((rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
                && state.getValue(AXIS).isHorizontal())
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return state;
    }
    public static Direction.Axis getPreferredAxis(BlockPlaceContext context) {
        Direction.Axis preferred = null;
        for (var side : Direction.values()) {
            var adjacent = context.getClickedPos().relative(side);
            var state = LoadedLevelAccess.blockState(context.getLevel(), adjacent);
            if (!(state.getBlock() instanceof IRotate rotate)
                    || !rotate.hasShaftTowards(context.getLevel(), adjacent, state, side.getOpposite())) continue;
            if (preferred != null && preferred != side.getAxis()) return null;
            preferred = side.getAxis();
        }
        return preferred;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var preferred = getPreferredAxis(context);
        boolean shift = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        return defaultBlockState().setValue(AXIS, preferred != null
                ? shift ? context.getClickedFace().getAxis() : preferred : context.getNearestLookingDirection().getAxis());
    }
}

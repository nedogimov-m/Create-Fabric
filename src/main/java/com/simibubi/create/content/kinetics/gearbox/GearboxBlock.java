package com.simibubi.create.content.kinetics.gearbox;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.AllItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class GearboxBlock extends KineticBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public GearboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
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
    protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return state.getValue(AXIS) == Direction.Axis.Y ? super.getDrops(state, params)
                : java.util.List.of(new ItemStack(AllItems.VERTICAL_GEARBOX));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return state.getValue(AXIS) == Direction.Axis.Y ? super.getCloneItemStack(level, pos, state, includeData)
                : new ItemStack(AllItems.VERTICAL_GEARBOX);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() != getRotationAxis(state);
    }

    /** Canonical port signs reproduce upstream input-relative gearbox rotation rules. */
    public double getPortModifier(BlockState state, Direction face) {
        Direction.Axis first = getRotationAxis(state) == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        return face.getAxisDirection().getStep() * (face.getAxis() == first ? 1 : -1);
    }
}

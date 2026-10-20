package com.simibubi.create.content.kinetics.chainDrive;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class ChainDriveBlock extends KineticBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty CONNECTED_ALONG_FIRST_COORDINATE = BooleanProperty.create("axis_along_first");

    public enum Part implements StringRepresentable {
        START, MIDDLE, END, NONE;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public ChainDriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(PART, Part.NONE)
                .setValue(CONNECTED_ALONG_FIRST_COORDINATE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, PART, CONNECTED_ALONG_FIRST_COORDINATE);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face.getAxis() == state.getValue(AXIS); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
        for (Direction face : Direction.values()) state = updateConnection(state, face, context.getLevel().getBlockState(context.getClickedPos().relative(face)));
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction face, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return updateConnection(state, face, neighbor);
    }

    /** Ported from ChainDriveBlock.updateShape; the chain occupies a single transverse axis. */
    private static BlockState updateConnection(BlockState state, Direction face, BlockState neighbor) {
        var part = state.getValue(PART);
        var axis = state.getValue(AXIS);
        boolean alongFirst = state.getValue(CONNECTED_ALONG_FIRST_COORDINATE);
        var connectionAxis = getConnectionAxis(state);
        var faceAxis = face.getAxis();
        boolean faceAlongFirst = axis == Direction.Axis.X ? faceAxis.isVertical() : faceAxis == Direction.Axis.X;
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        if (axis == faceAxis) return state;
        if (!(neighbor.getBlock() instanceof ChainDriveBlock)) {
            if (faceAlongFirst != alongFirst || part == Part.NONE) return state;
            if (part == Part.MIDDLE) return state.setValue(PART, positive ? Part.END : Part.START);
            if ((part == Part.START) == positive) return state.setValue(PART, Part.NONE);
            return state;
        }
        var otherPart = neighbor.getValue(PART);
        if (neighbor.getValue(AXIS) == faceAxis) return state;
        if (otherPart != Part.NONE && getConnectionAxis(neighbor) != faceAxis) return state;
        if (part == Part.NONE) {
            part = positive ? Part.START : Part.END;
            alongFirst = faceAlongFirst;
        } else if (connectionAxis != faceAxis) return state;
        if ((part == Part.START) != positive) part = Part.MIDDLE;
        return state.setValue(PART, part).setValue(CONNECTED_ALONG_FIRST_COORDINATE, alongFirst);
    }

    public static Direction.Axis getConnectionAxis(BlockState state) {
        var axis = state.getValue(AXIS);
        return state.getValue(CONNECTED_ALONG_FIRST_COORDINATE)
                ? axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X
                : axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    public static boolean areBlocksConnected(BlockState state, BlockState other, Direction facing) {
        if (getConnectionAxis(state) != getConnectionAxis(other) || facing.getAxis() != getConnectionAxis(state)) return false;
        var part = state.getValue(PART);
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? part == Part.MIDDLE || part == Part.START : part == Part.MIDDLE || part == Part.END;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction connection = rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, getConnectionAxis(state)));
        Direction shaft = rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS)));
        if (connection.getAxisDirection() == Direction.AxisDirection.NEGATIVE) state = reversePart(state);
        boolean first = shaft.getAxis() == Direction.Axis.X ? connection.getAxis() == Direction.Axis.Y : connection.getAxis() == Direction.Axis.X;
        return state.setValue(AXIS, shaft.getAxis()).setValue(CONNECTED_ALONG_FIRST_COORDINATE, first);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror.mirror(Direction.get(Direction.AxisDirection.POSITIVE, getConnectionAxis(state))).getAxisDirection()
                == Direction.AxisDirection.NEGATIVE ? reversePart(state) : state;
    }

    private static BlockState reversePart(BlockState state) {
        return switch (state.getValue(PART)) {
            case START -> state.setValue(PART, Part.END);
            case END -> state.setValue(PART, Part.START);
            default -> state;
        };
    }

    public static double speedModifier(LevelReader level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChainGearshiftBlock) || !(level instanceof net.minecraft.world.level.Level world)) return 1;
        var owner = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(world, pos);
        if (owner instanceof ChainGearshiftBlockEntity gearshift) return gearshift.getModifier();
        int power = com.simibubi.create.foundation.utility.LoadedLevelAccess.getBestNeighborSignal(world, pos);
        return power == 0 ? 1 : 1 + (power + 1) / 16.0;
    }
}

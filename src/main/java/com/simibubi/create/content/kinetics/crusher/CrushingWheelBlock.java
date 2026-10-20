package com.simibubi.create.content.kinetics.crusher;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class CrushingWheelBlock extends KineticBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public CrushingWheelBlock(Properties properties) {
        super(properties.strength(2).sound(SoundType.STONE));
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis preferred = null;
        for (var side : Direction.values()) {
            var next = context.getClickedPos().relative(side); var neighbor = context.getLevel().getBlockState(next);
            if (neighbor.getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate rotate
                    && rotate.hasShaftTowards(context.getLevel(), next, neighbor, side.getOpposite())) {
                if (preferred != null && preferred != side.getAxis()) { preferred = null; break; }
                preferred = side.getAxis();
            }
        }
        boolean shifting = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        return defaultBlockState().setValue(AXIS, preferred != null && !shifting ? preferred
                : preferred != null ? context.getClickedFace().getAxis() : context.getNearestLookingDirection().getAxis());
    }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face.getAxis() == getRotationAxis(state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CrushingWheelBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.CRUSHING_WHEEL, CrushingWheelBlockEntity::tick); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (var side : Direction.values()) {
            var next = pos.relative(side);
            if (!level.hasChunkAt(next)) continue;
            var neighbor = level.getBlockState(next);
            if (neighbor.is(AllBlocks.CRUSHING_WHEEL_CONTROLLER) && side.getAxis() != state.getValue(AXIS)) return false;
            if (neighbor.is(this) && (neighbor.getValue(AXIS) != state.getValue(AXIS) || side.getAxis() != state.getValue(AXIS))) return false;
        }
        return true;
    }
    public static void updateControllers(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel)) return;
        for (var side : Direction.values()) {
            if (side.getAxis() == state.getValue(AXIS)) continue;
            var center = pos.relative(side); var other = pos.relative(side, 2);
            if (!level.isLoaded(center) || !level.isLoaded(other)) continue;
            var neighbor = level.getBlockState(other); var current = level.getBlockState(center);
            if (neighbor.is(AllBlocks.CRUSHING_WHEEL) && neighbor.getValue(AXIS) == state.getValue(AXIS)) {
                if (current.canBeReplaced() && !current.is(AllBlocks.CRUSHING_WHEEL_CONTROLLER)) {
                    level.setBlockAndUpdate(center, AllBlocks.CRUSHING_WHEEL_CONTROLLER.defaultBlockState());
                }
                if (level.getBlockEntity(center) instanceof CrushingWheelControllerBlockEntity controller) controller.refreshDrive();
            } else if (current.is(AllBlocks.CRUSHING_WHEEL_CONTROLLER) && level.getBlockEntity(center) instanceof CrushingWheelControllerBlockEntity controller) controller.refreshDrive();
        }
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { updateControllers(level, pos, state); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos next, BlockState neighbor, RandomSource random) {
        ticks.scheduleTick(pos, this, 1); return state;
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        for (var side : Direction.values()) {
            var center = pos.relative(side);
            if (side.getAxis() != state.getValue(AXIS) && level.isLoaded(center) && level.getBlockEntity(center) instanceof CrushingWheelControllerBlockEntity controller) controller.refreshDrive();
        }
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean wasInside) {
        if (entity.getY() < pos.getY() + 1.25 || !entity.onGround() || !(level.getBlockEntity(pos) instanceof CrushingWheelBlockEntity wheel)) return;
        double speed = wheel.getSpeed();
        var axis = state.getValue(AXIS);
        if (axis == Direction.Axis.X) entity.addDeltaMovement(new Vec3((pos.getX() + 0.5 - entity.getX()) * 0.1, 0, speed / 20));
        if (axis == Direction.Axis.Z) entity.addDeltaMovement(new Vec3(speed / -20, 0, (pos.getZ() + 0.5 - entity.getZ()) * 0.1));
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) return state.setValue(AXIS,
                state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : state.getValue(AXIS) == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Y);
        return state;
    }
}

package com.simibubi.create.content.kinetics.saw;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SawBlock extends KineticBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE = BooleanProperty.create("axis_along_first"), FLIPPED = BooleanProperty.create("flipped");
    public static final ResourceKey<DamageType> SAW_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Create.asResource("mechanical_saw"));
    public SawBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(AXIS_ALONG_FIRST_COORDINATE, false).setValue(FLIPPED, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, AXIS_ALONG_FIRST_COORDINATE, FLIPPED); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis().isHorizontal() ? state.getValue(FACING).getAxis() : state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? Direction.Axis.X : Direction.Axis.Z; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return state.getValue(FACING).getAxis().isHorizontal() ? face == state.getValue(FACING).getOpposite() : face.getAxis() == getRotationAxis(state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return DrillBlock.casingShape(state.getValue(FACING)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite(); if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) facing = facing.getOpposite();
        boolean alongFirst = context.getHorizontalDirection().getAxis() == Direction.Axis.X;
        if (facing.getAxis().isVertical()) {
            Direction preferred = null;
            for (var side : Direction.Plane.HORIZONTAL) {
                var next = context.getClickedPos().relative(side); var neighbor = context.getLevel().getBlockState(next);
                if (neighbor.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(context.getLevel(), next, neighbor, side.getOpposite())) {
                    if (preferred != null && preferred.getAxis() != side.getAxis()) { preferred = null; break; } preferred = side;
                }
            }
            if (preferred != null) alongFirst = preferred.getAxis() == Direction.Axis.X;
        } else alongFirst = facing.getAxis() == Direction.Axis.Z;
        return defaultBlockState().setValue(FACING, facing).setValue(AXIS_ALONG_FIRST_COORDINATE, alongFirst).setValue(FLIPPED, facing.getAxis().isVertical() && context.getHorizontalDirection().getAxisDirection() == Direction.AxisDirection.POSITIVE);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        var original = state;
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) state = state.cycle(AXIS_ALONG_FIRST_COORDINATE);
        state = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        if (original.getValue(FACING).getAxis().isVertical()) {
            if (rotation == Rotation.CLOCKWISE_180 || (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
                    && (rotation == Rotation.CLOCKWISE_90) != original.getValue(AXIS_ALONG_FIRST_COORDINATE)) state = state.cycle(FLIPPED);
        }
        return state;
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        var facing = mirror.mirror(state.getValue(FACING)); state = state.setValue(FACING, facing);
        if (facing.getAxis().isVertical() && (state.getValue(AXIS_ALONG_FIRST_COORDINATE) && mirror == Mirror.FRONT_BACK || !state.getValue(AXIS_ALONG_FIRST_COORDINATE) && mirror == Mirror.LEFT_RIGHT)) state = state.cycle(FLIPPED);
        return state;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SawBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.SAW, SawBlockEntity::tick); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) { super.neighborChanged(state, level, pos, neighbor, orientation, moved); if (level.getBlockEntity(pos) instanceof SawBlockEntity saw) saw.destroyNextTick(); }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean inside) { if (level.getBlockEntity(pos) instanceof SawBlockEntity saw) DrillBlock.hurtInside(level, pos, entity, saw.getSpeed(), SAW_DAMAGE); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator() || state.getValue(FACING) != Direction.UP) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SawBlockEntity saw) {
            if (player.isShiftKeyDown()) saw.getFilteringBehaviour().applyPlayerSample(player, InteractionHand.MAIN_HAND);
            else saw.inventory.collect().forEach(stack -> player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.isShiftKeyDown() && player.mayBuild() && stack.getItem() instanceof SawBlockItem sawItem) {
            var directions = java.util.Arrays.stream(Direction.values()).filter(side -> side.getAxis() != state.getValue(FACING).getAxis())
                    .filter(side -> level.isLoaded(pos.relative(side)) && level.getBlockState(pos.relative(side)).canBeReplaced())
                    .sorted(java.util.Comparator.comparingDouble(side -> hit.getLocation().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5)))).toList();
            if (!directions.isEmpty()) {
                var target = pos.relative(directions.getFirst());
                var result = sawItem.place(new SawBlockItem.RowPlacement(player, hand, stack, new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target), hit.getDirection(), target, false), state));
                if (result.consumesAction()) return result;
            }
        }
        if (player.isShiftKeyDown() && player.mayBuild() && !stack.is(AllItems.WRENCH) && state.getValue(FACING) == Direction.UP) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SawBlockEntity saw) saw.getFilteringBehaviour().applyPlayerSample(player, hand);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static BlockState wrenchRotation(BlockState state, Direction targetedFace) {
        var original = state;
        if (state.getValue(FACING).getAxis() == targetedFace.getAxis()) state = state.cycle(AXIS_ALONG_FIRST_COORDINATE);
        else {
            state = state.setValue(FACING, state.getValue(FACING).getClockWise(targetedFace.getAxis()));
            if (targetedFace.getAxis().isVertical()) state = state.cycle(AXIS_ALONG_FIRST_COORDINATE);
        }
        if (state.getValue(FACING).getAxis().isVertical() && targetedFace.getAxis().isVertical() && !original.getValue(AXIS_ALONG_FIRST_COORDINATE)) state = state.cycle(FLIPPED);
        return state;
    }
}

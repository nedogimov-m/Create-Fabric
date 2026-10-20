package com.simibubi.create.content.kinetics.waterwheel;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import java.util.LinkedHashMap;
import java.util.Map;

/** The eight structural cells belong to exactly one centre; they never drop separate items. */
public final class LargeWaterWheelBlock extends RotatedPillarKineticBlock {
    public static final BooleanProperty EXTENSION = BooleanProperty.create("extension");

    public LargeWaterWheelBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.IMMOVEABLE));
        registerDefaultState(defaultBlockState().setValue(EXTENSION, false));
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof WaterWheelBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder.add(EXTENSION)); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face.getAxis() == state.getValue(AXIS); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LargeWaterWheelBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AllBlockEntityTypes.LARGE_WATER_WHEEL, WaterWheelBlockEntity::tick);
    }

    static Direction negative(Direction.Axis axis) { return Direction.get(Direction.AxisDirection.NEGATIVE, axis); }

    /** Cardinal cells point to the centre, corner cells point to one cardinal cell. */
    public static Map<BlockPos, Direction> structure(BlockPos centre, Direction.Axis axis) {
        Map<BlockPos, Direction> cells = new LinkedHashMap<>();
        for (Direction side : Direction.values()) {
            if (side.getAxis() == axis) continue;
            cells.put(centre.relative(side), side.getOpposite());
            Direction turn = side.getClockWise(axis);
            cells.put(centre.relative(side).relative(turn), turn.getOpposite());
        }
        return cells;
    }

    /** Original shared preferred-shaft/camera/sneak policy, independent of footprint acceptance. */
    public Direction.Axis getAxisForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).getValue(AXIS);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var placement = super.getStateForPlacement(context);
        var axis = placement.getValue(AXIS);
        var pos = context.getClickedPos();
        for (var cell : structure(pos, axis).keySet()) {
            if (!LoadedLevelAccess.isLoaded(context.getLevel(),cell)
                    || !LoadedLevelAccess.blockState(context.getLevel(),cell).canBeReplaced()) return null;
        }
        var previous=pos.relative(negative(axis));
        if(!LoadedLevelAccess.isLoaded(context.getLevel(),previous))return null;
        return placement.setValue(EXTENSION, LoadedLevelAccess.blockState(context.getLevel(),previous).is(this));
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction side, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (side == negative(state.getValue(AXIS))) state = state.setValue(EXTENSION, neighbor.is(this));
        return state;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean extension = level.getBlockState(pos.relative(negative(state.getValue(AXIS)))).is(this);
        if (state.getValue(EXTENSION) != extension) level.setBlockAndUpdate(pos, state.setValue(EXTENSION, extension));
        var structure = structure(pos, state.getValue(AXIS));
        // Validate everything before mutation; an obstructed placement must preserve the obstruction.
        for (var entry : structure.entrySet()) {
            if (!level.isLoaded(entry.getKey())) { level.scheduleTick(pos, this, 20); return; }
            var occupied = level.getBlockState(entry.getKey());
            var required = AllBlocks.WATER_WHEEL_STRUCTURAL.defaultBlockState().setValue(WaterWheelStructuralBlock.FACING, entry.getValue());
            if (occupied != required && !occupied.canBeReplaced()) { level.destroyBlock(pos, true); return; }
        }
        structure.forEach((cell, face) -> level.setBlockAndUpdate(cell,
                AllBlocks.WATER_WHEEL_STRUCTURAL.defaultBlockState().setValue(WaterWheelStructuralBlock.FACING, face)));
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (level.getBlockState(pos).is(this)) return;
        structure(pos, state.getValue(AXIS)).forEach((cell, face) -> {
            var part = level.getBlockState(cell);
            if (part.is(AllBlocks.WATER_WHEEL_STRUCTURAL) && part.getValue(WaterWheelStructuralBlock.FACING) == face) {
                level.removeBlock(cell, false);
            }
        });
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(AXIS, rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS))).getAxis());
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return AllBlocks.WATER_WHEEL.useMaterial(stack, level, pos, player);
    }
    @Override public boolean hideStressImpact() { return true; }

}

package com.simibubi.create.content.kinetics.drill;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

public final class DrillBlock extends KineticBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final ResourceKey<DamageType> DRILL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Create.asResource("mechanical_drill"));
    public DrillBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, WATERLOGGED); }
    public static Direction facingForPlacement(BlockPlaceContext context) {
        Direction preferred = null;
        for (var side : Direction.values()) {
            var pos = context.getClickedPos().relative(side); var state = context.getLevel().getBlockState(pos);
            if (state.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(context.getLevel(), pos, state, side.getOpposite())) {
                if (preferred != null && preferred.getAxis() != side.getAxis()) { preferred = null; break; } preferred = side;
            }
        }
        boolean shift = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        return preferred != null && !shift ? preferred.getOpposite() : shift ? context.getNearestLookingDirection() : context.getNearestLookingDirection().getOpposite();
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, facingForPlacement(context)).setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER)); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face == state.getValue(FACING).getOpposite(); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    public static VoxelShape casingShape(Direction facing) {
        return switch (facing) {
            case UP -> box(0, 0, 0, 16, 12, 16); case DOWN -> box(0, 4, 0, 16, 16, 16);
            case NORTH -> box(0, 0, 4, 16, 16, 16); case SOUTH -> box(0, 0, 0, 16, 16, 12);
            case WEST -> box(4, 0, 0, 16, 16, 16); case EAST -> box(0, 0, 0, 12, 16, 16);
        };
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return casingShape(state.getValue(FACING)); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DrillBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.DRILL, DrillBlockEntity::tick); }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState(); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos next, BlockState neighbor, RandomSource random) { if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level)); return state; }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) { super.neighborChanged(state, level, pos, neighbor, orientation, moved); if (level.getBlockEntity(pos) instanceof DrillBlockEntity drill) drill.destroyNextTick(); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    public static double getDamage(float speed) { float rpm = Math.abs(speed); return Math.clamp(Math.min(rpm / 16, 2) + Math.min(rpm / 32, 4) + Math.min(rpm / 64, 4), 1, 10); }
    public static void hurtInside(Level level, BlockPos pos, Entity entity, float speed, ResourceKey<DamageType> type) {
        if (!(level instanceof ServerLevel server) || speed == 0 || entity instanceof ItemEntity || !new AABB(pos).deflate(0.1).intersects(entity.getBoundingBox())) return;
        entity.hurtServer(server, new DamageSource(server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type)), (float) getDamage(speed));
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean inside) { if (level.getBlockEntity(pos) instanceof DrillBlockEntity drill) hurtInside(level, pos, entity, drill.getSpeed(), DRILL_DAMAGE); }
}

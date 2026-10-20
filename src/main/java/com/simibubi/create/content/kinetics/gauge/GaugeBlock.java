package com.simibubi.create.content.kinetics.gauge;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

public final class GaugeBlock extends DirectionalAxisKineticBlock {
    public enum Type { SPEED, STRESS }
    public final Type type;
    public static GaugeBlock speed(Properties properties) { return new GaugeBlock(properties, Type.SPEED); }
    public static GaugeBlock stress(Properties properties) { return new GaugeBlock(properties, Type.STRESS); }
    private GaugeBlock(Properties properties, Type type) {
        super(properties); this.type = type;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(AXIS_ALONG_FIRST_COORDINATE, false));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel(); var face = context.getClickedFace();
        var support = context.getClickedPos().relative(face.getOpposite()); var state = LoadedLevelAccess.blockState(level, support);
        if (state.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(level, support, state, face)) {
            var horizontal = context.getHorizontalDirection();
            boolean positive = context.getNearestLookingDirection().getAxisDirection() == Direction.AxisDirection.POSITIVE;
            return switch (face.getAxis()) {
                case X -> defaultBlockState().setValue(FACING, positive ? Direction.NORTH : Direction.SOUTH).setValue(AXIS_ALONG_FIRST_COORDINATE, true);
                case Y -> defaultBlockState().setValue(FACING, horizontal.getOpposite()).setValue(AXIS_ALONG_FIRST_COORDINATE, horizontal.getAxis() == Direction.Axis.X);
                case Z -> defaultBlockState().setValue(FACING, positive ? Direction.WEST : Direction.EAST).setValue(AXIS_ALONG_FIRST_COORDINATE, false);
            };
        }
        return super.getStateForPlacement(context);
    }
    @Override protected Direction getFacingForPlacement(BlockPlaceContext context) { return context.getClickedFace(); }
    @Override protected boolean getAxisAlignmentForPlacement(BlockPlaceContext context) { return context.getHorizontalDirection().getAxis() != Direction.Axis.X; }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(placer instanceof net.minecraft.server.level.ServerPlayer player&&LoadedLevelAccess.blockEntity(level,pos) instanceof StressGaugeBlockEntity gauge)gauge.placedBy(player);}
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type == Type.SPEED ? new SpeedGaugeBlockEntity(pos, state) : new StressGaugeBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> entityType) {
        return type == Type.SPEED ? createTickerHelper(entityType, AllBlockEntityTypes.SPEEDOMETER, GaugeBlockEntity::tick)
            : createTickerHelper(entityType, AllBlockEntityTypes.STRESSOMETER, StressGaugeBlockEntity::tick);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    public boolean shouldRenderHeadOnFace(Level level, BlockPos pos, BlockState state, Direction face) {
        var axis = getRotationAxis(state); var facing = state.getValue(FACING);
        return face.getAxis().isHorizontal() && face != facing.getOpposite() && face.getAxis() != axis
            && (axis != Direction.Axis.Y || face == facing)
            && Block.shouldRenderFace(state, LoadedLevelAccess.blockState(level, pos.relative(face)), face);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(LoadedLevelAccess.blockEntity(level, pos) instanceof GaugeBlockEntity gauge) || gauge.dialTarget == 0) return;
        for (var face : Direction.Plane.HORIZONTAL) {
            if (!shouldRenderHeadOnFace(level, pos, state, face)) continue;
            int count = gauge.dialTarget > 1 ? 4 : 1;
            if (count == 1 && random.nextFloat() > 0.25f) continue;
            for (int i = 0; i < count; i++) {
                var spread = new Vec3(face.getAxis() == Direction.Axis.X ? 0 : (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.5, face.getAxis() == Direction.Axis.Z ? 0 : (random.nextDouble() - 0.5) * 0.5).normalize().scale(0.3);
                var at = Vec3.atCenterOf(pos).add(face.getStepX()*0.55, face.getStepY()*0.55, face.getStepZ()*0.55).add(spread);
                level.addParticle(new DustParticleOptions(gauge.color, 1), at.x, at.y, at.z, spread.x, spread.y, spread.z);
            }
        }
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING); var shaft = getRotationAxis(state);
        // The original UP shape, oriented by its mounting normal and through-shaft axis.
        double[] low = new double[3], high = new double[3], bodyLow = new double[3], bodyHigh = new double[3];
        for (var axis : Direction.Axis.values()) {
            int i = axis.ordinal(); boolean normal = axis == facing.getAxis(), alongShaft = axis == shaft;
            low[i] = normal ? facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 14 : alongShaft ? 0 : 1;
            high[i] = normal ? facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 2 : 16 : alongShaft ? 16 : 15;
            bodyLow[i] = alongShaft ? 1 : 2; bodyHigh[i] = alongShaft ? 15 : 14;
        }
        return Shapes.or(box(low[0], low[1], low[2], high[0], high[1], high[2]), box(bodyLow[0], bodyLow[1], bodyLow[2], bodyHigh[0], bodyHigh[1], bodyHigh[2]));
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction side) {
        return LoadedLevelAccess.blockEntity(level, pos) instanceof GaugeBlockEntity gauge ? gauge.getComparatorSignal() : 0;
    }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
}

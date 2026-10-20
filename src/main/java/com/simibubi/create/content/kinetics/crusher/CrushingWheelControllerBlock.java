package com.simibubi.create.content.kinetics.crusher;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;

public final class CrushingWheelControllerBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty VALID = BooleanProperty.create("valid");
    public CrushingWheelControllerBlock(Properties properties) {
        super(properties.noLootTable().noOcclusion().air().pushReaction(PushReaction.IMMOVEABLE));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(VALID, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, VALID); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING);
        if (state.getValue(VALID) && context instanceof EntityCollisionContext collision && collision.getEntity() != null) {
            var entity = collision.getEntity();
            if (level.getBlockEntity(pos) instanceof CrushingWheelControllerBlockEntity controller && controller.processingEntity == entity) return Shapes.empty();
            if (facing != Direction.UP && pos.equals(entity.getAttached(CrushingWheelControllerBlockEntity.BYPASS))) return Shapes.empty();
        }
        return switch (facing) {
            case DOWN -> box(0, 0, 0, 16, 13, 16);
            case UP -> box(0, 3, 0, 16, 16, 16);
            case EAST -> box(3, 0, 0, 16, 16, 16);
            case WEST -> box(0, 0, 0, 13, 16, 16);
            case SOUTH -> box(0, 0, 3, 16, 16, 16);
            case NORTH -> box(0, 0, 0, 16, 16, 13);
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CrushingWheelControllerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.CRUSHING_WHEEL_CONTROLLER, CrushingWheelControllerBlockEntity::tick); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos next, BlockState neighbor, RandomSource random) { ticks.scheduleTick(pos, this, 1); return state; }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { if (level.getBlockEntity(pos) instanceof CrushingWheelControllerBlockEntity crusher) crusher.refreshDrive(); }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean wasInside) {
        if (level.getBlockEntity(pos) instanceof CrushingWheelControllerBlockEntity crusher) crusher.tryStartCrushing(entity);
    }
}

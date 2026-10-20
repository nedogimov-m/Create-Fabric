package com.simibubi.create.content.contraptions.actors.trainControls;

import java.util.EnumMap;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Actual source world train-controls block. Native carriage input/actors are separate real
 * consumers.
 */
public final class ControlsBlock extends HorizontalDirectionalBlock
    implements SimpleWaterloggedBlock {
  public static final BooleanProperty OPEN = BlockStateProperties.OPEN,
      VIRTUAL = BooleanProperty.create("virtual"),
      WATERLOGGED = BlockStateProperties.WATERLOGGED;
  private static final EnumMap<Direction, VoxelShape> OUTLINE = new EnumMap<>(Direction.class),
      COLLISION = new EnumMap<>(Direction.class);

  static {
    var collision = box(0, 0, 6, 16, 16, 16);
    var outline = Shapes.or(collision, box(0, 0, 4, 16, 2, 16));
    for (var facing : Direction.Plane.HORIZONTAL) {
      COLLISION.put(facing, rotate(collision, facing));
      OUTLINE.put(facing, rotate(outline, facing));
    }
  }

  public ControlsBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(OPEN, false)
            .setValue(VIRTUAL, false)
            .setValue(WATERLOGGED, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, OPEN, VIRTUAL, WATERLOGGED);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    var facing = context.getHorizontalDirection().getOpposite();
    if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
      facing = facing.getOpposite();
    return defaultBlockState()
        .setValue(FACING, facing)
        .setValue(
            WATERLOGGED,
            context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
  }

  @Override
  protected FluidState getFluidState(BlockState state) {
    return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
  }

  @Override
  protected BlockState updateShape(
      BlockState state,
      LevelReader level,
      ScheduledTickAccess ticks,
      BlockPos pos,
      Direction side,
      BlockPos next,
      BlockState neighbor,
      RandomSource random) {
    if (state.getValue(WATERLOGGED))
      ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
    // Mounted controls use the genuine native open-body/cover/lever actor renderer.
    // Ordinary world shape updates close the physical controls, exactly as source world placement.
    return state.setValue(OPEN, false);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return OUTLINE.get(state.getValue(FACING));
  }

  @Override
  protected VoxelShape getCollisionShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return COLLISION.get(state.getValue(FACING));
  }

  @Override
  protected BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  }

  @Override
  protected BlockState mirror(BlockState state, Mirror mirror) {
    return state.rotate(mirror.getRotation(state.getValue(FACING)));
  }

  private static VoxelShape rotate(VoxelShape source, Direction facing) {
    if (facing == Direction.NORTH) return source;
    var shape = new VoxelShape[] {Shapes.empty()};
    source.forAllBoxes(
        (x1, y1, z1, x2, y2, z2) -> {
          var next =
              switch (facing) {
                case EAST -> Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2);
                case SOUTH -> Shapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
                case WEST -> Shapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1);
                default ->
                    throw new IllegalArgumentException("Horizontal source train controls required");
              };
          shape[0] = Shapes.or(shape[0], next);
        });
    return shape[0];
  }
}

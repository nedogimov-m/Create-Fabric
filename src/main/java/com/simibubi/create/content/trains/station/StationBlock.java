package com.simibubi.create.content.trains.station;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Actual source station body, waterlogging and current native train-presence comparator. */
public final class StationBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
  public static final BooleanProperty ASSEMBLING = BooleanProperty.create("assembling"),
      WATERLOGGED = BlockStateProperties.WATERLOGGED;
  private static final VoxelShape SHAPE =
      Shapes.or(box(0, 0, 0, 16, 2, 16), box(1, 0, 1, 15, 13, 15));

  public StationBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        defaultBlockState().setValue(ASSEMBLING, false).setValue(WATERLOGGED, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ASSEMBLING, WATERLOGGED);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState()
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
    return state;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new StationBlockEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return type == AllBlockEntityTypes.TRACK_STATION
        ? (world, pos, block, be) ->
            StationBlockEntity.tick(world, pos, block, (StationBlockEntity) be)
        : null;
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPE;
  }

  @Override
  protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

  @Override
  protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
    return LoadedLevelAccess.blockEntity(level,pos) instanceof StationBlockEntity station
        ? station.comparatorOutput() : 0;
  }

  @Override
  protected InteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (player.isShiftKeyDown() || stack.is(AllItems.WRENCH)) return InteractionResult.PASS;
    if(stack.is(net.minecraft.world.item.Items.FILLED_MAP)){
      if(level.isClientSide())return InteractionResult.SUCCESS;
      if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof StationBlockEntity station)||station.getStation()==null)return InteractionResult.FAIL;
      var map=net.minecraft.world.item.MapItem.getSavedData(stack,level);
      return map instanceof StationMapData stations&&stations.toggleStation(level,pos,station)?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
    var taken = takeAutoSchedule(level, pos, player);
    return taken == InteractionResult.PASS ? open(level, pos, player) : taken;
  }

  @Override
  public void stepOn(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.Entity entity) {
    super.stepOn(level, pos, state, entity);
    com.simibubi.create.content.logistics.depot.SharedDepotBlockMethods.onLanded(level, entity);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if(player.isShiftKeyDown())return InteractionResult.PASS;
    var taken=takeAutoSchedule(level,pos,player);
    return taken==InteractionResult.PASS?open(level,pos,player):taken;
  }

  private static InteractionResult takeAutoSchedule(Level level, BlockPos pos, Player player) {
    if(player.isSpectator() || !player.mayBuild() || !LoadedLevelAccess.isLoaded(level,pos)
        || !(LoadedLevelAccess.blockEntity(level,pos) instanceof StationBlockEntity station)
        || station.getAutoSchedule().isEmpty())return InteractionResult.PASS;
    if(level.isClientSide())return InteractionResult.SUCCESS;
    if(!level.mayInteract(player,pos))return InteractionResult.FAIL;
    var schedule=station.depotBehaviour.itemHandler.extractItem(0,Integer.MAX_VALUE,false);
    if(schedule.isEmpty())return InteractionResult.FAIL;
    player.getInventory().placeItemBackInInventory(schedule,net.minecraft.util.Prediction.SERVER_ONLY);
    station.setChanged();
    level.sendBlockUpdated(pos,station.getBlockState(),station.getBlockState(),Block.UPDATE_CLIENTS);
    level.playSound(null,pos,net.minecraft.sounds.SoundEvents.ITEM_PICKUP,net.minecraft.sounds.SoundSource.PLAYERS,.2f,1);
    return InteractionResult.SUCCESS;
  }

  private static InteractionResult open(Level level, BlockPos pos, Player player) {
    if (player.isSpectator() || !player.mayBuild() || !LoadedLevelAccess.isLoaded(level, pos))
      return InteractionResult.PASS;
    if (level.isClientSide()) return InteractionResult.SUCCESS;
    if (!(player instanceof ServerPlayer server)
        || !(LoadedLevelAccess.blockEntity(level, pos) instanceof StationBlockEntity station)
        || !StationPermissions.canEdit(player, station, true)
        || station.getStation() == null) return InteractionResult.FAIL;
    StationNetworking.open(server, station);
    return InteractionResult.SUCCESS;
  }
}

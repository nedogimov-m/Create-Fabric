package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.signal.SignalEdgePoints;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Block;

/**
 * Source track selection and real native station BlockItem placement; failed placement keeps
 * selection.
 */
public final class TrackTargetingBlockItem extends BlockItem {
  public static final double STRAIGHT_DISTANCE = 16, CURVE_DISTANCE = 48;

  public interface Target {
    TrackTargetingBehaviour<?> targeting();

    boolean setTarget(TrackTargetingSelection selection);
  }

  private final EdgePointType<?> type;

  public TrackTargetingBlockItem(Block block, Properties properties, EdgePointType<?> type) {
    super(block, properties);
    this.type = Objects.requireNonNull(type);
  }

  public EdgePointType<?> getType(ItemStack stack) {
    return type;
  }

  public boolean select(Player player, InteractionHand hand, TrackTargetingSelection selection) {
    var level = player.level();
    var stack = player.getItemInHand(hand);
    if (level.isClientSide()
        || player.isSpectator()
        || !player.mayBuild()
        || stack.getItem() != this
        || !selection.target().dimension().equals(level.dimension())
        || !level.mayInteract(player, selection.target().pos())) return false;
    try {
      var location = selection.resolve(level);
      if (location == null
          || TrackTargetingBehaviour.overlaps(location, type, selection.front(), null))
        return false;
    } catch (UnloadedTrackException missing) {
      return false;
    }
    stack.set(AllDataComponents.TRACK_TARGETING_SELECTION, selection);
    clearOriginalSelection(stack);
    player.sendOverlayMessage(Component.translatable("create.track_target.set"));
    return true;
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    var player = context.getPlayer();
    var level = context.getLevel();
    var stack = context.getItemInHand();
    var pos = context.getClickedPos();
    if (player == null
        || player.isSpectator()
        || !player.mayBuild()
        || !player.isWithinBlockInteractionRange(pos, 1)
        || !LoadedLevelAccess.isLoaded(level, pos)
        || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
    if (player.isShiftKeyDown() && (stack.has(AllDataComponents.TRACK_TARGETING_SELECTION)
        || stack.has(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS))) {
      if (!level.isClientSide()) {
        stack.remove(AllDataComponents.TRACK_TARGETING_SELECTION);
        clearOriginalSelection(stack);
        player.sendOverlayMessage(Component.translatable("create.track_target.clear"));
      }
      return InteractionResult.SUCCESS;
    }
    var state = LoadedLevelAccess.blockState(level, pos);
    if (state.getBlock() instanceof ITrackBlock track) {
      if (level.isClientSide()) return InteractionResult.SUCCESS;
      var axes = track.getTrackAxes(level, pos, state);
      if (axes.size() != 1 || axes.getFirst().lengthSqr() < 1e-12) return InteractionResult.FAIL;
      var nearest = track.getNearestTrackAxis(level, pos, state, player.getLookAngle());
      var selected =
          new TrackTargetingSelection(
              GlobalPos.of(level.dimension(), pos),
              nearest.getSecond() == Direction.AxisDirection.POSITIVE,
              nearest.getFirst(),
              Optional.empty());
      return select(player, context.getHand(), selected)
          ? InteractionResult.SUCCESS
          : InteractionResult.FAIL;
    }
    var selected = selection(stack, level);
    if (selected == null || !selected.target().dimension().equals(level.dimension()))
      return InteractionResult.FAIL;
    var placement = new BlockPlaceContext(context);
    var placedPos = placement.getClickedPos();
    if (!LoadedLevelAccess.isLoaded(level, placedPos)
        || !level.mayInteract(player, placedPos)
        || !selected
            .target()
            .pos()
            .closerThan(
                placedPos, selected.curve().isPresent() ? CURVE_DISTANCE : STRAIGHT_DISTANCE))
      return InteractionResult.FAIL;
    try {
      var location = selected.resolve(level);
      if (location == null
          || TrackTargetingBehaviour.overlaps(location, type, selected.front(), null))
        return InteractionResult.FAIL;
    } catch (UnloadedTrackException missing) {
      return InteractionResult.FAIL;
    }
    var result = super.useOn(context);
    if (level.isClientSide() || !result.consumesAction()) return result;
    if (!(LoadedLevelAccess.blockEntity(level, placedPos) instanceof Target station)
        || station.targeting().pointType() != type)
      throw new IllegalStateException(
          "Native targeting item placed no matching concrete point owner BE");
    if (!station.setTarget(selected))
      throw new IllegalStateException("Native station target changed during native placement");
    stack.remove(AllDataComponents.TRACK_TARGETING_SELECTION);
    clearOriginalSelection(stack);
    var remaining = player.getItemInHand(context.getHand());
    if (!remaining.isEmpty()) { remaining.remove(AllDataComponents.TRACK_TARGETING_SELECTION); clearOriginalSelection(remaining); }
    player.sendOverlayMessage(Component.translatable("create.track_target.success"));
    if(type==SignalEdgePoints.SIGNAL&&!(player instanceof FakePlayer))
      AllAdvancements.SIGNAL.awardTo(player);
    return result;
  }

  /** Original selection had no dimension or saved axis; it resolved in the actual use world. */
  public static TrackTargetingSelection selection(ItemStack stack, net.minecraft.world.level.Level level) {
    var current = stack.get(AllDataComponents.TRACK_TARGETING_SELECTION);
    if (current != null) return current;
    var pos = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS);
    if (pos == null || !LoadedLevelAccess.isLoaded(level, pos)) return null;
    var state = LoadedLevelAccess.blockState(level, pos);
    if (!(state.getBlock() instanceof ITrackBlock track)) return null;
    var axes = track.getTrackAxes(level, pos, state);
    var curve = Optional.ofNullable(stack.get(AllDataComponents.TRACK_TARGETING_ITEM_BEZIER));
    if (axes.isEmpty() || curve.isEmpty() && axes.size() != 1 || axes.getFirst().lengthSqr() < 1e-12) return null;
    return new TrackTargetingSelection(GlobalPos.of(level.dimension(), pos),
        stack.getOrDefault(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, false), axes.getFirst(), curve);
  }

  private static void clearOriginalSelection(ItemStack stack) {
    stack.remove(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS);
    stack.remove(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION);
    stack.remove(AllDataComponents.TRACK_TARGETING_ITEM_BEZIER);
  }
}

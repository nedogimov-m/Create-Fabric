package com.simibubi.create.api.behaviour.interaction;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.trains.TrainAdvancements;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** Genuine source schedule application/resume/return for actual adjacent block conductors. */
public abstract class ConductorBlockInteractionBehavior extends MovingInteractionBehaviour {
  public abstract boolean isValidConductor(BlockState state);

  protected void onScheduleUpdate(boolean present, BlockState state, Consumer<BlockState> setter) {}

  @Override
  public final boolean handlePlayerInteraction(
      Player player, InteractionHand hand, BlockPos local, AbstractContraptionEntity owner) {
    if (hand != InteractionHand.MAIN_HAND
        || !(owner instanceof CarriageContraptionEntity front)
        || !(front.getContraption() instanceof CarriageContraption body)) return false;
    var info = body.getBlocks().get(local);
    if (info == null || !isValidConductor(info.state()) || front.getCarriage() == null)
      return false;
    var train = front.getCarriage().train;
    if (train == null || train.isRestoring() || player.isSpectator()) return false;
    boolean controls = false;
    for (var direction : Direction.values())
      if (direction.getAxis() == body.getAssemblyDirection().getAxis()
          && body.inControl(local, direction)) controls = true;
    if (player.level().isClientSide()) return controls;
    if (!front.isGloballyManaged()) return false;
    if (!controls) {
      message(player, "schedule.non_controlling_seat");
      sound(player, AllSoundEvents.DENY);
      return true;
    }
    var held = player.getItemInHand(hand);
    if (train.runtime.getSchedule() != null) {
      if (train.runtime.paused && !train.runtime.completed) {
        train.runtime.paused = false;
        sound(player, AllSoundEvents.CONFIRM);
        message(player, "schedule.continued");
      } else if (!held.isEmpty()) {
        sound(player, AllSoundEvents.DENY);
        message(player, "schedule.remove_with_empty_hand");
      } else {
        String key =
            train.runtime.isAutoSchedule
                ? "schedule.auto_removed_from_train"
                : "schedule.removed_from_train";
        var returned = train.runtime.returnSchedule(player.registryAccess());
        player.setItemInHand(hand, returned);
        onScheduleUpdate(false, info.state(), state -> setBlock(front, local, state));
        sound(player, SoundEvents.ITEM_PICKUP);
        message(player, key);
      }
      train.manager().markDirty();
      return true;
    }
    if (!held.is(AllItems.SCHEDULE)) return true;
    var schedule = ScheduleItem.getSchedule(player.registryAccess(), held);
    if (schedule == null) return false;
    if (schedule.entries.isEmpty()) {
      sound(player, AllSoundEvents.DENY);
      message(player, "schedule.no_stops");
      return true;
    }
    train.runtime.setSchedule(schedule, false);
    onScheduleUpdate(true, info.state(), state -> setBlock(front, local, state));
    if (player instanceof net.minecraft.server.level.ServerPlayer server)
      TrainAdvancements.CONDUCTOR.awardTo(server);
    sound(player, AllSoundEvents.CONFIRM);
    player.sendOverlayMessage(
        Component.translatable("create.schedule.applied_to_train").withStyle(ChatFormatting.GREEN));
    held.shrink(1);
    player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
    train.manager().markDirty();
    return true;
  }

  private static void setBlock(CarriageContraptionEntity owner, BlockPos local, BlockState state) {
    var old = owner.getContraption().getBlocks().get(local);
    if (old != null) owner.setBlock(local, new StructureBlockInfo(local, state, old.nbt()));
  }

  private static void message(Player player, String key) {
    player.sendOverlayMessage(Component.translatable("create." + key));
  }

  private static void sound(Player player, SoundEvent event) {
    player.level().playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, 1, 1);
  }

  public static class BlazeBurner extends ConductorBlockInteractionBehavior {
    @Override
    public boolean isValidConductor(BlockState state) {
      return state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE;
    }
  }
}

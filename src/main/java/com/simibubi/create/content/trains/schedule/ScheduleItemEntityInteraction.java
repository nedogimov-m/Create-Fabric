package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Source conductor give/resume/return behavior on the real Fabric entity interaction event. */
public final class ScheduleItemEntityInteraction {
    private ScheduleItemEntityInteraction() {}

    public static InteractionResult interactWithConductor(Player player, Entity target, InteractionHand hand) {
        if (player.isSpectator() || !(target instanceof LivingEntity living)
                || !(target.getRootVehicle() instanceof CarriageContraptionEntity entity)) return InteractionResult.PASS;
        ItemStack scheduleItem = new ItemStack(AllItems.SCHEDULE);
        if (player.getCooldowns().isOnCooldown(scheduleItem)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof ScheduleItem schedule) {
            var result = schedule.handScheduleTo(held, player, living, hand);
            if (result.consumesAction()) {
                player.getCooldowns().addCooldown(scheduleItem, 5);
                return result;
            }
        }
        if (hand == InteractionHand.OFF_HAND || !(entity.getContraption() instanceof CarriageContraption body))
            return InteractionResult.PASS;
        var carriage = entity.getCarriage();
        var train = carriage == null ? null : carriage.train;
        if (train == null || train.isRestoring() || !entity.isGloballyManaged() || train.runtime.getSchedule() == null) return InteractionResult.PASS;
        var seat = body.getSeatOf(target.getUUID());
        if (seat == null || body.conductorSeats.get(seat) == null) return InteractionResult.PASS;
        boolean server = !player.level().isClientSide();
        if (train.runtime.paused && !train.runtime.completed) {
            if (server) {
                train.runtime.paused = false;
                if (train.trafficManager() != null) train.trafficManager().markDirty();
                ScheduleItem.play(player, AllSoundEvents.CONFIRM);
                player.sendOverlayMessage(ScheduleText.translate("schedule.continued"));
            }
            player.getCooldowns().addCooldown(scheduleItem, 5);
            return InteractionResult.SUCCESS;
        }
        if (!held.isEmpty()) {
            if (server) ScheduleItem.deny(player, "schedule.remove_with_empty_hand");
            return InteractionResult.SUCCESS;
        }
        if (server) {
            ScheduleItem.play(player, SoundEvents.ITEM_PICKUP);
            player.sendOverlayMessage(ScheduleText.translate(train.runtime.isAutoSchedule
                    ? "schedule.auto_removed_from_train" : "schedule.removed_from_train"));
            player.getInventory().placeItemBackInInventory(train.runtime.returnSchedule(player.registryAccess()), net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.getCooldowns().addCooldown(scheduleItem, 5);
        return InteractionResult.SUCCESS;
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> interactWithConductor(player, entity, hand));
    }
}

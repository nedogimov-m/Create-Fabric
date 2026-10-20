package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.TrainAdvancements;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.foundation.recipe.ItemCopyingRecipe.SupportsItemCopying;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Actual source schedule component/item, native editor opening and conductor hand-off. */
public final class ScheduleItem extends Item implements ExtendedMenuProvider<ScheduleMenu.Opening>, SupportsItemCopying {
    public ScheduleItem(Properties properties) { super(properties); }

    public static @Nullable Schedule getSchedule(HolderLookup.Provider registries, ItemStack stack) {
        var tag = stack.get(AllDataComponents.TRAIN_SCHEDULE);
        return tag == null ? null : Schedule.fromTag(registries, tag.copy());
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        return context.getPlayer() == null ? InteractionResult.PASS : use(context.getLevel(), context.getPlayer(), context.getHand());
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) server.openMenu(this);
        return InteractionResult.SUCCESS;
    }
    @Override public ScheduleMenu.Opening getScreenOpeningData(ServerPlayer player) {
        return new ScheduleMenu.Opening(player.getMainHandItem().copy(), player.getInventory().getSelectedSlot());
    }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ScheduleMenu(id, inventory, player.getMainHandItem(), inventory.getSelectedSlot());
    }
    @Override public Component getDisplayName() { return Component.translatable("item.create.schedule"); }
    @Override public DataComponentType<?> getComponentType() { return AllDataComponents.TRAIN_SCHEDULE; }
    @Override public ItemStack createCopy(ItemStack original, int count) {
        ItemStack copy = SupportsItemCopying.super.createCopy(original, count);
        var schedule = original.get(AllDataComponents.TRAIN_SCHEDULE);
        if (schedule != null) copy.set(AllDataComponents.TRAIN_SCHEDULE, schedule.copy());
        return copy;
    }

    public InteractionResult handScheduleTo(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Schedule schedule = getSchedule(player.registryAccess(), stack);
        if (schedule == null || target == null) return InteractionResult.PASS;
        if (!(target.getRootVehicle() instanceof CarriageContraptionEntity entity)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(entity.getContraption() instanceof CarriageContraption body)) return InteractionResult.SUCCESS;
        var carriage = entity.getCarriage();
        var train = carriage == null ? null : carriage.train;
        if (train == null || train.isRestoring() || !entity.isGloballyManaged()) return InteractionResult.SUCCESS;
        var seat = body.getSeatOf(target.getUUID());
        if (seat == null) return InteractionResult.SUCCESS;
        if (body.conductorSeats.get(seat) == null) {
            deny(player, "schedule.non_controlling_seat");
            return InteractionResult.SUCCESS;
        }
        if (train.runtime.getSchedule() != null) {
            deny(player, "schedule.remove_with_empty_hand");
            return InteractionResult.SUCCESS;
        }
        if (schedule.entries.isEmpty()) {
            deny(player, "schedule.no_stops");
            return InteractionResult.SUCCESS;
        }
        train.runtime.setSchedule(schedule, false);
        TrainAdvancements.CONDUCTOR.awardTo(player);
        play(player, AllSoundEvents.CONFIRM);
        player.sendOverlayMessage(ScheduleText.translate("schedule.applied_to_train").withStyle(ChatFormatting.GREEN));
        stack.shrink(1);
        player.setItemInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack);
        return InteractionResult.SUCCESS;
    }

    public static void play(Player player, SoundEvent sound) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1, 1);
    }
    public static void deny(Player player, String key) {
        play(player, AllSoundEvents.DENY);
        player.sendOverlayMessage(ScheduleText.translate(key));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                          Consumer<Component> lines, TooltipFlag flag) {
        Schedule schedule = getSchedule(context.registries(), stack);
        if (schedule == null || schedule.entries.isEmpty()) return;
        for (int i = 0; i < schedule.entries.size(); i++) {
            if (!(schedule.entries.get(i).instruction instanceof DestinationInstruction destination)) continue;
            boolean current = i == schedule.savedProgress && schedule.entries.size() > 1;
            lines.accept(Component.literal(current ? "-> " : "> ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(destination.getFilter()).withStyle(current ? ChatFormatting.YELLOW : ChatFormatting.GOLD)));
        }
    }
}

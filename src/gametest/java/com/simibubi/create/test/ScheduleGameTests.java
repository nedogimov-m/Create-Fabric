package com.simibubi.create.test;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.foundation.recipe.ItemCopyingRecipe;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;

/** Genuine native data/menu/recipe tests. No Train or cargo substitute is constructed. Runtime needs real assembled trains. */
public final class ScheduleGameTests {
    private static final String STRUCTURE = "create_test:reservoir";
    private static DestinationInstruction destination(String text) {
        var instruction = new DestinationInstruction();
        instruction.getData().putString("Text", text);
        return instruction;
    }
    private static Schedule schedule() {
        var instruction = destination("Foundry *");
        var delay = new ScheduledDelay();
        delay.getData().putInt("Value", 12);
        var idle = new IdleCargoCondition();
        idle.getData().putInt("Value", 3);
        var players = new PlayerPassengerCondition();
        players.getData().putInt("Count", 2);
        var conditions = new ArrayList<List<ScheduleWaitCondition>>();
        conditions.add(new ArrayList<>(List.of(delay, idle)));
        conditions.add(new ArrayList<>(List.of(players)));
        var rename = new ChangeTitleInstruction();
        rename.getData().putString("Text", "Morning freight");
        return new Schedule(new ArrayList<>(List.of(new ScheduleEntry(rename, new ArrayList<>()),
                new ScheduleEntry(instruction, conditions))), false, 1);
    }
    private static ItemStack held(GameTestHelper h, Schedule schedule) {
        var item = new ItemStack(AllItems.SCHEDULE);
        item.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(h.getLevel().registryAccess()));
        return item;
    }
    private static CompoundTag row(String id, CompoundTag data) {
        var row = new CompoundTag();
        row.putString("Id", "create:" + id);
        row.put("Data", data);
        return row;
    }

    @GameTest(structure=STRUCTURE) public void actualSourceTypeRegistryRetainsAllBuiltinFactories(GameTestHelper h) {
        h.assertValueEqual(Schedule.INSTRUCTION_TYPES.stream().map(pair -> pair.getFirst().getPath()).toList(),
                List.of("destination", "package_delivery", "package_retrieval", "rename", "throttle"), "All five actual source instructions");
        h.assertValueEqual(Schedule.CONDITION_TYPES.stream().map(pair -> pair.getFirst().getPath()).toList(),
                List.of("delay", "time_of_day", "fluid_threshold", "item_threshold", "redstone_link", "player_count", "idle", "unloaded", "powered"), "All nine concrete actual source conditions");
        for (var pair : Schedule.INSTRUCTION_TYPES) h.assertValueEqual(pair.getSecond().get().getId(), pair.getFirst(), "Actual factory identity");
        for (var pair : Schedule.CONDITION_TYPES) h.assertValueEqual(pair.getSecond().get().getId(), pair.getFirst(), "Actual factory identity");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void nativeScheduleNbtRetainsAndWithinOrColumnsAndSavedProgress(GameTestHelper h) {
        var original = schedule();
        var tag = original.write(h.getLevel().registryAccess());
        var restored = Schedule.fromTag(h.getLevel().registryAccess(), tag);
        h.assertTrue(!restored.cyclic && restored.savedProgress == 1 && restored.entries.size() == 2, "Real schedule flags/progress/entries");
        var columns = restored.entries.get(1).conditions;
        h.assertTrue(columns.size() == 2 && columns.get(0).get(0) instanceof ScheduledDelay
                && columns.get(0).get(1) instanceof IdleCargoCondition && columns.get(1).get(0) instanceof PlayerPassengerCondition, "Original ordered AND/OR semantics preserved in actual data");
        h.assertValueEqual(restored.write(h.getLevel().registryAccess()), tag, "Exact source NBT roundtrip");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void nativeRegistryFriendlyStreamRetainsNestedComponentsAndAllBytes(GameTestHelper h) {
        var schedule = schedule();
        var item = new ItemThresholdCondition();
        var named = new ItemStack(Items.APPLE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Expedited"));
        var filter = new ItemStack(AllItems.FILTER);
        filter.set(AllDataComponents.FILTER_ITEMS, ItemContainerContents.fromItems(List.of(named)));
        filter.set(AllDataComponents.FILTER_ITEMS_RESPECT_NBT, true);
        item.setItem(0, filter);
        schedule.entries.get(1).conditions.get(0).add(item);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            Schedule.STREAM_CODEC.encode(buffer, schedule);
            var restored = Schedule.STREAM_CODEC.decode(buffer);
            h.assertValueEqual(restored.write(h.getLevel().registryAccess()), schedule.write(h.getLevel().registryAccess()), "Native registryOps preserve filter item and nested components");
            h.assertValueEqual(buffer.readableBytes(), 0, "Entire actual native frame consumed");
        } finally { buffer.release(); }
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realEntryCloneDoesNotShareMutableRowsOrConfiguration(GameTestHelper h) {
        var entry = schedule().entries.get(1);
        var copy = entry.clone(h.getLevel().registryAccess());
        ((DestinationInstruction) copy.instruction).getData().putString("Text", "Changed");
        copy.conditions.get(0).get(0).getData().putInt("Value", 90);
        copy.conditions.get(1).clear();
        h.assertValueEqual(((DestinationInstruction) entry.instruction).getFilter(), "Foundry *", "Original actual destination unmodified");
        h.assertValueEqual(entry.conditions.get(0).get(0).getData().getIntOr("Value", -1), 12, "Original condition data unmodified");
        h.assertValueEqual(entry.conditions.get(1).size(), 1, "Original independent OR column unmodified");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void noWaitInstructionDoesNotReviveSerializedWaitingRows(GameTestHelper h) {
        var entry = schedule().entries.get(1);
        entry.instruction = new ChangeThrottleInstruction();
        var tag = entry.write(h.getLevel().registryAccess());
        h.assertFalse(tag.contains("Conditions"), "Source throttle omits conditions");
        var extra = new ListTag();
        extra.add(new ListTag());
        tag.put("Conditions", extra);
        h.assertTrue(ScheduleEntry.fromTag(h.getLevel().registryAccess(), tag).conditions.isEmpty(), "Actual decoder respects instruction supportsConditions");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void zeroProgressUsesOriginalAbsentFieldAndPositiveProgressSurvives(GameTestHelper h) {
        var schedule = schedule();
        schedule.savedProgress = 0;
        h.assertFalse(schedule.write(h.getLevel().registryAccess()).contains("Progress"), "Original absent zero representation");
        schedule.savedProgress = 1;
        h.assertValueEqual(Schedule.fromTag(h.getLevel().registryAccess(), schedule.write(h.getLevel().registryAccess())).savedProgress, 1, "Native positive progress retains original key");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void genuineLegacyRootFilterMigratesIntoActualNativeData(GameTestHelper h) {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Legacy iron"));
        var data = new CompoundTag();
        data.putString("Threshold", "64");
        var tag = row("item_threshold", data);
        tag.put("Item", ScheduleSerialization.writeItem(h.getLevel().registryAccess(), named));
        var restored = ScheduleWaitCondition.fromTag(h.getLevel().registryAccess(), tag);
        h.assertTrue(restored instanceof ItemThresholdCondition && ItemStack.isSameItemSameComponents(restored.getItem(0), named), "Real legacy root Item is decoded with actual registries");
        var written = restored.write(h.getLevel().registryAccess());
        h.assertTrue(!written.contains("Item") && written.getCompoundOrEmpty("Data").contains("Item"), "Actual modern data nesting after genuine migration");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void genuineNestedFilterTakesPrecedenceOverLegacyRoot(GameTestHelper h) {
        var data = new CompoundTag();
        data.put("Bucket", ScheduleSerialization.writeItem(h.getLevel().registryAccess(), new ItemStack(Items.LAVA_BUCKET)));
        var tag = row("fluid_threshold", data);
        tag.put("Bucket", ScheduleSerialization.writeItem(h.getLevel().registryAccess(), new ItemStack(Items.WATER_BUCKET)));
        var restored = ScheduleWaitCondition.fromTag(h.getLevel().registryAccess(), tag);
        h.assertTrue(restored.getItem(0).is(Items.LAVA_BUCKET), "Actual nested Bucket overrides genuine old root field");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void genuineLegacyFrequencyPairRetainsBothNativeComponents(GameTestHelper h) {
        var first = new ItemStack(Items.IRON_INGOT);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("Signal A"));
        var second = new ItemStack(Items.APPLE);
        var frequencies = new ListTag();
        frequencies.add(ScheduleSerialization.writeItem(h.getLevel().registryAccess(), first));
        frequencies.add(ScheduleSerialization.writeItem(h.getLevel().registryAccess(), second));
        var tag = row("redstone_link", new CompoundTag());
        tag.put("Frequency", frequencies);
        var restored = ScheduleWaitCondition.fromTag(h.getLevel().registryAccess(), tag);
        h.assertTrue(ItemStack.isSameItemSameComponents(restored.getItem(0), first) && restored.getItem(1).is(Items.APPLE), "Both actual legacy frequency slots survive migration");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void sourceUnknownInstructionFallbackRemainsRealDestination(GameTestHelper h) {
        var tag = row("missing_addon_instruction", new CompoundTag());
        h.assertTrue(ScheduleInstruction.fromTag(h.getLevel().registryAccess(), tag) instanceof DestinationInstruction, "Actual source fallback behavior");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualScheduleItemComponentRetainsNativeStackCountNameAndNbt(GameTestHelper h) {
        var item = held(h, schedule());
        item.setCount(16);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Dispatch master"));
        var nativeTag = ScheduleSerialization.writeItem(h.getLevel().registryAccess(), item);
        var restored = ScheduleSerialization.readItem(h.getLevel().registryAccess(), nativeTag);
        h.assertTrue(restored.is(AllItems.SCHEDULE) && restored.getCount() == 16
                && ItemStack.isSameItemSameComponents(item, restored), "Actual native item/component serialization");
        h.assertValueEqual(ScheduleItem.getSchedule(h.getLevel().registryAccess(), restored).write(h.getLevel().registryAccess()), schedule().write(h.getLevel().registryAccess()), "Actual CompoundTag schedule schema retained");
        h.assertTrue(ScheduleItem.getSchedule(h.getLevel().registryAccess(), new ItemStack(AllItems.SCHEDULE)) == null, "Actual absent component returns no schedule");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualNativeCopyRecipeProducesIndependentSchedulesWithoutEnchantments(GameTestHelper h) {
        var original = held(h, schedule());
        original.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
        var input = CraftingInput.of(3, 1, List.of(original, new ItemStack(AllItems.SCHEDULE), new ItemStack(AllItems.SCHEDULE)));
        var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value();
        h.assertTrue(recipe instanceof ItemCopyingRecipe, "Actual registered native copying recipe matched");
        var copy = recipe.assemble(input);
        h.assertTrue(copy.getCount() == 3 && copy.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()
                && !copy.has(DataComponents.STORED_ENCHANTMENTS), "Original source three copies and no copied enchantments");
        h.assertValueEqual(copy.get(AllDataComponents.TRAIN_SCHEDULE), original.get(AllDataComponents.TRAIN_SCHEDULE), "Real schedule component preserved");
        copy.get(AllDataComponents.TRAIN_SCHEDULE).putInt("Progress", 0);
        h.assertValueEqual(original.get(AllDataComponents.TRAIN_SCHEDULE).getIntOr("Progress", -1), 1, "Native copy must not share mutable schedule NBT");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realCopyRecipeRejectsSecondOriginalForeignItemAndNoTarget(GameTestHelper h) {
        var recipe = new ItemCopyingRecipe(CraftingBookCategory.MISC);
        var source = held(h, schedule());
        h.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(source, held(h, schedule()))), h.getLevel()), "Two filled source schedules are not blank targets");
        h.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(source, new ItemStack(Items.PAPER))), h.getLevel()), "Foreign item is not a schedule-copy target");
        h.assertFalse(recipe.matches(CraftingInput.of(1, 1, List.of(source)), h.getLevel()), "No physical blank target");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualScheduleCraftAndClearUseOriginalYieldAndBlankComponent(GameTestHelper h) {
        var input = CraftingInput.of(2, 1, List.of(new ItemStack(AllItems.STURDY_SHEET), new ItemStack(Items.PAPER)));
        var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value();
        var result = recipe.assemble(input);
        h.assertTrue(result.is(AllItems.SCHEDULE) && result.getCount() == 4 && !result.has(AllDataComponents.TRAIN_SCHEDULE), "Actual source craft produces four blank schedules");
        var clear = CraftingInput.of(1, 1, List.of(held(h, schedule())));
        var blank = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, clear, h.getLevel()).orElseThrow().value().assemble(clear);
        h.assertTrue(blank.is(AllItems.SCHEDULE) && blank.getCount() == 1 && !blank.has(AllDataComponents.TRAIN_SCHEDULE), "Actual native clearing recipe removes schedule component");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realNativeScheduleMenuGhostsNeverConsumeOrReleasePhysicalStock(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var holder = held(h, schedule());
        player.setItemInHand(InteractionHand.MAIN_HAND, holder);
        holder.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.containerMenu instanceof ScheduleMenu, "Real source item opened its native server menu");
        var menu = (ScheduleMenu) player.containerMenu;
        new ScheduleEditorPacket(menu.containerId, true, 2, ItemStack.EMPTY, ItemStack.EMPTY).handle(player);
        menu.setCarried(new ItemStack(Items.APPLE, 12));
        menu.clicked(37, 0, ContainerInput.PICKUP, player);
        h.assertTrue(menu.getCarried().getCount() == 12 && menu.ghostInventory.getItem(1).getCount() == 1, "Real cursor stock stays physical and ghost is one sample");
        h.assertFalse(menu.getSlot(37).mayPickup(player), "No ghost can be extracted as actual stock");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(37, 0, ContainerInput.PICKUP, player);
        h.assertTrue(menu.ghostInventory.getItem(1).isEmpty() && player.getMainHandItem() == holder, "Empty cursor clears sample; actual schedule owner remains exact");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualEditPacketRequiresNativeMenuAndExactSelectedOwner(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var original = held(h, schedule());
        player.setItemInHand(InteractionHand.MAIN_HAND, original);
        original.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        var menu = (ScheduleMenu) player.containerMenu;
        var changed = schedule(); changed.cyclic = true;
        new ScheduleEditPacket(menu.containerId + 1, changed).handle(player);
        h.assertFalse(ScheduleItem.getSchedule(player.registryAccess(), original).cyclic, "Foreign native menu id cannot write");
        var replacement = held(h, schedule());
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        new ScheduleEditPacket(menu.containerId, changed).handle(player);
        h.assertFalse(ScheduleItem.getSchedule(player.registryAccess(), replacement).cyclic, "Equal replacement item cannot inherit original native menu authority");
        h.assertFalse(ScheduleItem.getSchedule(player.registryAccess(), original).cyclic, "Retired actual owner unmodified");
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualBlankEditorCommitRemovesComponentWithoutChangingPhysicalCount(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var item = held(h, schedule()); item.setCount(7);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        item.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        new ScheduleEditPacket(player.containerMenu.containerId, new Schedule()).handle(player);
        h.assertTrue(!item.has(AllDataComponents.TRAIN_SCHEDULE) && item.getCount() == 7, "Actual menu clears data and preserves seven real held schedules");
        h.succeed();
    }
}

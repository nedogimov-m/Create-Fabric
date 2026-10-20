package com.simibubi.create.test;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.foundation.utility.NeoForgeFluidStackImport;
import com.simibubi.create.foundation.utility.NeoForgeItemComponentImport;
import com.simibubi.create.foundation.utility.NeoForgeSaveCodecs;
import com.simibubi.create.foundation.utility.NeoForgeTextComponentImport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Genuine3955 component shapes through the real DFU, registered codecs and native re-save. */
public final class NeoForgeItemComponentImportGameTests {
    private static CompoundTag stack(Item item, int count) {
        var result = new CompoundTag(); result.putString("id", BuiltInRegistries.ITEM.getKey(item).toString()); result.putInt("count", count); return result;
    }
    private static CompoundTag named(Item item, int count) {
        var result = stack(item, count); var components = new CompoundTag();
        components.putString("minecraft:custom_name", "{\"text\":\"Original3955\",\"bold\":true}"); result.put("components", components); return result;
    }
    private static ListTag rows(Tag... values) { var result = new ListTag(); for (var value : values) result.add(value); return result; }
    private static CompoundTag slot(int index, CompoundTag stack) { var result = new CompoundTag(); result.putInt("slot", index); result.put("item", stack); return result; }
    private static CompoundTag components(CompoundTag stack) {
        if (!stack.contains("components")) stack.put("components", new CompoundTag()); return stack.getCompoundOrEmpty("components");
    }
    private static CompoundTag filter() {
        var filter = stack(AllItems.FILTER, 1); components(filter).put("create:filter_items", rows(slot(17, named(Items.EMERALD, 3)))); return filter;
    }
    private static ItemStack migrate(GameTestHelper h, CompoundTag source) {
        var old = source.copy(); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var first = ItemStack.CODEC.parse(ops, NeoForgeSaveCodecs.item(source, 3955, h.getLevel().registryAccess())).getOrThrow();
        var second = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, first).getOrThrow()).getOrThrow();
        h.assertTrue(source.equals(old) && ItemStack.CODEC.encodeStart(ops, first).getOrThrow().equals(ItemStack.CODEC.encodeStart(ops, second).getOrThrow()), "Pure original input and exact native component payload re-save/reload"); return second;
    }
    private static ItemStack at(net.minecraft.world.item.component.ItemContainerContents contents, int slot) {
        var slots = net.minecraft.core.NonNullList.withSize(256, ItemStack.EMPTY); contents.copyInto(slots); return slots.get(slot);
    }
    private static void assertFilter(GameTestHelper h, ItemStack filter) {
        var sample = at(filter.get(AllDataComponents.FILTER_ITEMS), 17);
        h.assertTrue(sample.is(Items.EMERALD) && sample.getCount() == 3 && sample.getHoverName().getString().equals("Original3955")
            && Boolean.TRUE.equals(sample.get(DataComponents.CUSTOM_NAME).getStyle().isBold()), "Actual sparse sample index/count/name/style survive nested Create codec");
    }
    @GameTest public void nestedBundlePackageAndFilterUseEachActualContainingDfuOnce(GameTestHelper h) {
        var box = stack(PackageStyles.getDefaultBox().getItem(), 1); components(box).put("create:package_contents", rows(slot(8, filter())));
        var bundle = stack(Items.BUNDLE, 1); components(bundle).put("minecraft:bundle_contents", rows(box));
        var actual = migrate(h, bundle).get(DataComponents.BUNDLE_CONTENTS).items().iterator().next();
        assertFilter(h, at(actual.get(AllDataComponents.PACKAGE_CONTENTS), 8)); h.succeed();
    }
    @GameTest public void directMapAndRemovalPatchKeepTheirActualOwnersWithoutItemCarrier(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("minecraft:custom_name", "{\"text\":\"Map3955\"}"); source.put("create:filter_items", rows(slot(255, named(Items.DIAMOND, 2))));
        var regs = h.getLevel().registryAccess(); var ops = regs.createSerializationContext(NbtOps.INSTANCE); var old = source.copy();
        var map = DataComponentMap.CODEC.parse(ops, NeoForgeItemComponentImport.map(source, 3955, regs)).getOrThrow();
        h.assertTrue(map.get(DataComponents.CUSTOM_NAME).getString().equals("Map3955") && at(map.get(AllDataComponents.FILTER_ITEMS), 255).getCount() == 2, "Actual standalone map preserves high sparse slot");
        source.remove("minecraft:custom_name"); source.put("!minecraft:custom_name", new CompoundTag());
        var patch = DataComponentPatch.CODEC.parse(ops, NeoForgeItemComponentImport.patch(source, 3955, regs)).getOrThrow();
        var target = new ItemStack(Items.DIAMOND); target.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Before")); target.applyComponents(patch);
        h.assertTrue(!target.has(DataComponents.CUSTOM_NAME) && at(target.get(AllDataComponents.FILTER_ITEMS), 255).getCount() == 2
            && source.contains("!minecraft:custom_name") && old.contains("minecraft:custom_name"), "Actual removal marker remains removal, not an invented component value"); h.succeed();
    }
    @GameTest public void sandpaperRetainsRealWrapperAndUnitMarker(GameTestHelper h) {
        var source = stack(AllItems.SAND_PAPER, 1); var polishing = new CompoundTag(); polishing.put("item", named(AllItems.ROSE_QUARTZ, 1));
        components(source).put("create:sand_paper_polishing", polishing); components(source).put("create:sand_paper_jei", new CompoundTag());
        var actual = migrate(h, source);
        h.assertTrue(actual.get(AllDataComponents.SAND_PAPER_POLISHING).getHoverName().getString().equals("Original3955")
            && actual.has(AllDataComponents.SAND_PAPER_JEI), "Real source/native polishing wrapper and Unit component remain decodable"); h.succeed();
    }
    @GameTest public void toolboxRetainsSparseStockAndIndependentEightFilters(GameTestHelper h) {
        var inventory = new CompoundTag(); var slots = new CompoundTag(); var stock = new CompoundTag(); stock.put("31", named(Items.DIAMOND, 7));
        slots.put("items", stock); slots.putInt("size", 32); inventory.put("items", slots); var filters = new ListTag();
        for (int i = 0; i < 8; i++) filters.add(i == 7 ? named(Items.DIAMOND, 1) : new CompoundTag()); inventory.put("filters", filters);
        var source = stack(com.simibubi.create.AllBlocks.TOOLBOXES.get(net.minecraft.world.item.DyeColor.BROWN).asItem(), 1);
        components(source).put("create:toolbox_inventory", inventory); var actual = migrate(h, source).get(AllDataComponents.TOOLBOX_INVENTORY);
        h.assertTrue(actual.totalCount(7) == 7 && actual.getFilter(7).getCount() == 1
            && actual.getFilter(0).isEmpty() && actual.getStackInSlot(28).getHoverName().getString().equals("Original3955"), "Tail stock and retained filters are distinct source owners"); h.succeed();
    }
    @GameTest public void orderContextKeepsLargeRequestsAndCraftPatternsWithNestedFilters(GameTestHelper h) {
        var row = new CompoundTag(); row.put("item_stack", filter()); row.putInt("count", 1234567); var order = new CompoundTag(); order.put("entries", rows(row));
        var craft = new CompoundTag(); craft.put("pattern", order.copy()); craft.putInt("count", 23);
        var context = new CompoundTag(); context.put("ordered_stacks", order); context.put("ordered_crafts", rows(craft));
        var source = stack(PackageStyles.getDefaultBox().getItem(), 1); components(source).put("create:package_order_context", context);
        var actual = migrate(h, source).get(AllDataComponents.PACKAGE_ORDER_CONTEXT);
        h.assertTrue(actual.stacks().getFirst().count == 1234567 && actual.orderedCrafts().getFirst().count() == 23, "Request quantities remain independent from physical sample counts");
        assertFilter(h, actual.orderedCrafts().getFirst().pattern().stacks().getFirst().stack); h.succeed();
    }
    @GameTest public void clipboardKeepsStructuredStylesRichItemHoverIconsAndCopiedFilter(GameTestHelper h) {
        var text = new CompoundTag(); text.putString("text", "Checklist3955"); text.putBoolean("bold", true); var hover = new CompoundTag(); hover.putString("action", "show_item"); hover.put("contents", filter()); text.put("hoverEvent", hover);
        var entry = new CompoundTag(); entry.putBoolean("checked", true); entry.put("text", text); entry.put("icon", filter()); entry.putInt("item_amount", 777);
        var copied = new CompoundTag(); var filtering = new CompoundTag(); filtering.put("Filter", filter()); filtering.putInt("FilterAmount", 99); copied.put("Filtering", filtering);
        var clipboard = new CompoundTag(); clipboard.putString("type", "written"); clipboard.put("pages", rows(rows(entry))); clipboard.putBoolean("read_only", true); clipboard.putInt("previously_opened_page", 0); clipboard.put("copied_values", copied);
        var source = stack(com.simibubi.create.AllBlocks.CLIPBOARD.asItem(), 1); components(source).put("create:clipboard_content", clipboard); var actual = migrate(h, source).get(AllDataComponents.CLIPBOARD_CONTENT);
        var line = actual.pages().getFirst().getFirst(); h.assertTrue(line.checked && line.itemAmount == 777 && line.text.getStyle().isBold()
            && line.text.getStyle().getHoverEvent() instanceof HoverEvent.ShowItem && actual.readOnly(), "Structured component styles/rich hover are retained");
        assertFilter(h, line.icon);
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        assertFilter(h, ItemStack.CODEC.parse(ops, actual.copiedValues().orElseThrow().getCompoundOrEmpty("Filtering").get("Filter")).getOrThrow()); h.succeed();
    }
    @GameTest public void fluidPatchMigratesRealTextAndCreateSubtreesWithExactLongUnits(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("id", "minecraft:water"); source.putInt("amount", Integer.MAX_VALUE);
        var patch = new CompoundTag(); patch.putString("minecraft:custom_name", "{\"text\":\"OriginalFluid\"}"); patch.put("create:filter_items", rows(slot(9, filter()))); source.put("components", patch); var old = source.copy();
        var actual = NeoForgeFluidStackImport.convert(source, 3955, h.getLevel().registryAccess()); var actualMap = actual.variant().getComponents();
        h.assertTrue(actual.amount() == 173946175407L && source.equals(old)
            && actualMap.get(DataComponents.CUSTOM_NAME).getString().equals("OriginalFluid"), "Long source mB conversion and real standalone fluid patch");
        assertFilter(h, at(actualMap.get(AllDataComponents.FILTER_ITEMS), 9)); h.succeed();
    }
    @GameTest public void actualBlockItemFactoryPreparesChestNestedComponentsBeforeContainingDfu(GameTestHelper h) {
        var source = stack(Items.CHEST, 1); var be = new CompoundTag(); var stored = filter(); stored.putByte("Slot", (byte) 26); be.put("Items", rows(stored));
        components(source).put("minecraft:block_entity_data", be); var actual = migrate(h, source);
        var tag = actual.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId(); var item = ((CompoundTag) tag.getListOrEmpty("Items").getFirst()).copy(); item.remove("Slot");
        assertFilter(h, ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), item).getOrThrow()); h.succeed();
    }
    @GameTest public void originalTrackExtendCurveSpellingIsRetained(GameTestHelper h) {
        var source = stack(com.simibubi.create.AllBlocks.TRACK.asItem(), 1); components(source).putBoolean("create:track_extend_curve", true);
        var actual = migrate(h, source); var encoded = ItemStack.CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), actual).getOrThrow();
        h.assertTrue(((CompoundTag) encoded).getCompoundOrEmpty("components").getBooleanOr("create:track_extend_curve", false), "Pinned original component registry spelling is preserved"); h.succeed();
    }
    @GameTest public void originalTrackTargetSelectionResolvesOnlyActualLoadedUseWorldGeometry(GameTestHelper h) {
        var local = new net.minecraft.core.BlockPos(2, 2, 2); h.setBlock(local, com.simibubi.create.AllBlocks.TRACK);
        var actualPos = h.absolutePos(local); var source = stack(com.simibubi.create.AllBlocks.TRACK_STATION.asItem(), 1);
        components(source).put("create:track_targeting_item_selected_pos", net.minecraft.core.BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, actualPos).getOrThrow());
        components(source).putBoolean("create:track_targeting_item_selected_direction", true); var actual = migrate(h, source);
        var selected = com.simibubi.create.content.trains.track.TrackTargetingBlockItem.selection(actual, h.getLevel());
        h.assertTrue(selected != null && selected.target().pos().equals(actualPos) && selected.target().dimension().equals(h.getLevel().dimension())
            && selected.front() && !actual.has(AllDataComponents.TRACK_TARGETING_SELECTION), "Dimensionless source resolves real loaded geometry in use world without inventing a saved axis or dimension");
        h.setBlock(local, net.minecraft.world.level.block.Blocks.AIR);
        h.assertTrue(com.simibubi.create.content.trains.track.TrackTargetingBlockItem.selection(actual, h.getLevel()) == null
            && actual.has(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS), "Missing real track refuses and retains original selection for retry"); h.succeed();
    }
    @GameTest public void malformedOriginalComponentStockAndVersionsRefuseWithoutMutatingSource(GameTestHelper h) {
        var duplicate = filter(); components(duplicate).put("create:filter_items", rows(slot(1, named(Items.DIAMOND, 1)), slot(1, named(Items.EMERALD, 1))));
        var invalidCount = filter(); components(invalidCount).put("create:filter_items", rows(slot(1, stack(Items.DIAMOND, 100))));
        var future = stack(Items.DIAMOND, 1); components(future).put("minecraft:equippable", new CompoundTag());
        for (var source : java.util.List.of(duplicate, invalidCount, future)) {
            var old = source.copy(); boolean rejected = false; try { NeoForgeSaveCodecs.item(source, 3955, h.getLevel().registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected && source.equals(old), "Malformed original slot/count/future version refuses without defaulting away stock");
        }
        boolean rejected = false; try { NeoForgeItemComponentImport.patch(new CompoundTag(), 3956, h.getLevel().registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "A non-pinned version is never guessed as3955"); h.succeed();
    }
    @GameTest public void flattenedAndStructuredTextRemainDifferentRealSourceRepresentations(GameTestHelper h) {
        var structured = new CompoundTag(); structured.putString("text", "Original"); structured.putBoolean("italic", true); var before = structured.copy();
        var regs = h.getLevel().registryAccess(); var ops = regs.createSerializationContext(NbtOps.INSTANCE);
        var first = ComponentSerialization.CODEC.parse(ops, NeoForgeTextComponentImport.structured(structured, 3955, regs)).getOrThrow();
        var second = ComponentSerialization.CODEC.parse(ops, NeoForgeTextComponentImport.flattened(StringTag.valueOf("{\"text\":\"Original\",\"italic\":true}"), 3955, regs)).getOrThrow();
        h.assertTrue(first.equals(second) && first.getStyle().isItalic() && structured.equals(before), "Both genuine source representations preserve native text and original bytes"); h.succeed();
    }
}

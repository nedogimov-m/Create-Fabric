package com.simibubi.create.test;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.NeoForgeItemComponentImport;
import com.simibubi.create.foundation.utility.NeoForgeSaveCodecs;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Rich unknown Create branches inside actual vanilla3955 flattened text and typed nested owners. */
public final class NeoForgeItemTextMigrationGameTests {
    private static CompoundTag stack(Item item, int count) { var tag = new CompoundTag(); tag.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString()); tag.putInt("count", count); return tag; }
    private static ListTag list(Tag... values) { var tag = new ListTag(); for (var value : values) tag.add(value); return tag; }
    private static CompoundTag components(CompoundTag tag) { if (!tag.contains("components")) tag.put("components", new CompoundTag()); return tag.getCompoundOrEmpty("components"); }
    private static String rich() {
        var filter = stack(AllItems.FILTER, 1); var sample = stack(Items.EMERALD, 3); components(sample).putString("minecraft:custom_name", "{\"text\":\"OriginalNested3955\"}");
        var row = new CompoundTag(); row.putInt("slot", 17); row.put("item", sample); components(filter).put("create:filter_items", list(row));
        String json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, filter).toString();
        return "{\"text\":\"OriginalRich3955\",\"bold\":true,\"hoverEvent\":{\"action\":\"show_item\",\"contents\":" + json + "}}";
    }
    private static void richSurvived(GameTestHelper h, Component actual) {
        h.assertTrue(actual.getString().equals("OriginalRich3955") && actual.getStyle().isBold() && actual.getStyle().getHoverEvent() instanceof HoverEvent.ShowItem, "Original actual vanilla text/style/hover action retained");
        var hovered = ((HoverEvent.ShowItem) actual.getStyle().getHoverEvent()).item().create(); var slots = net.minecraft.core.NonNullList.withSize(256, ItemStack.EMPTY);
        hovered.get(AllDataComponents.FILTER_ITEMS).copyInto(slots); var sample = slots.get(17);
        h.assertTrue(sample.is(Items.EMERALD) && sample.getCount() == 3 && sample.getHoverName().getString().equals("OriginalNested3955"), "Unknown Create branch inside actual vanilla text hover is converted before native consumption");
    }
    private static ItemStack migrate(GameTestHelper h, CompoundTag source) {
        var before = source.copy(); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var actual = ItemStack.CODEC.parse(ops, NeoForgeSaveCodecs.item(source, 3955, h.getLevel().registryAccess())).getOrThrow();
        var result = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, actual).getOrThrow()).getOrThrow();
        h.assertTrue(source.equals(before), "Original caller data unchanged"); return result;
    }
    @GameTest public void vanillaNamesAndLoreRetainNestedCreateHoverThroughActualNativeResave(GameTestHelper h) {
        var source = stack(Items.DIAMOND, 7); components(source).putString("minecraft:custom_name", rich()); components(source).putString("minecraft:item_name", rich()); components(source).put("minecraft:lore", list(StringTag.valueOf(rich())));
        var actual = migrate(h, source); richSurvived(h, actual.get(DataComponents.CUSTOM_NAME)); richSurvived(h, actual.get(DataComponents.ITEM_NAME)); richSurvived(h, actual.get(DataComponents.LORE).lines().getFirst());
        h.assertTrue(actual.getCount() == 7, "Text completion never alters physical source quantity"); h.succeed();
    }
    @GameTest public void standaloneMapKeepsFilteredBookPagesAndRawTextWithoutItemCarrier(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("minecraft:custom_name", rich()); var book = new CompoundTag(); var title = new CompoundTag(); title.putString("raw", "Original title"); book.put("title", title); book.putString("author", "Original author");
        var page = new CompoundTag(); page.putString("raw", rich()); page.putString("filtered", rich()); book.put("pages", list(page)); source.put("minecraft:written_book_content", book); var before = source.copy();
        var regs = h.getLevel().registryAccess(); var actual = DataComponentMap.CODEC.parse(regs.createSerializationContext(NbtOps.INSTANCE), NeoForgeItemComponentImport.map(source, 3955, regs)).getOrThrow();
        richSurvived(h, actual.get(DataComponents.CUSTOM_NAME)); var pages = actual.get(DataComponents.WRITTEN_BOOK_CONTENT).pages(); richSurvived(h, pages.getFirst().raw()); richSurvived(h, pages.getFirst().filtered().orElseThrow());
        h.assertTrue(source.equals(before) && actual.get(DataComponents.WRITTEN_BOOK_CONTENT).author().equals("Original author"), "Actual standalone owner and independent filtered content preserved"); h.succeed();
    }
    @GameTest public void vanillaBundleChildTextUsesOriginalSourceAfterSingleContainingDfu(GameTestHelper h) {
        var child = stack(Items.DIAMOND, 5); components(child).putString("minecraft:custom_name", rich()); var source = stack(Items.BUNDLE, 1); components(source).put("minecraft:bundle_contents", list(child));
        var actual = migrate(h, source).get(DataComponents.BUNDLE_CONTENTS).items().iterator().next().create(); richSurvived(h, actual.get(DataComponents.CUSTOM_NAME)); h.assertTrue(actual.getCount() == 5, "Nested physical owner remains the same finite stack"); h.succeed();
    }
    @GameTest public void originalFoodRemainderMovesToRealNativeOwnerWithRichComponents(GameTestHelper h) {
        var child = stack(Items.BOWL, 1); components(child).putString("minecraft:custom_name", rich()); var food = new CompoundTag(); food.putInt("nutrition", 4); food.putFloat("saturation", .3f); food.put("using_converts_to", child);
        var source = stack(Items.APPLE, 1); components(source).put("minecraft:food", food); var actual = migrate(h, source);
        var remainder = actual.get(DataComponents.USE_REMAINDER).convertInto().create(); h.assertTrue(remainder.is(Items.BOWL) && remainder.getCount() == 1, "Actual old remainder is owned by native use_remainder, not duplicate food stock"); richSurvived(h, remainder.get(DataComponents.CUSTOM_NAME)); h.succeed();
    }
}

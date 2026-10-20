package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.NeoForgeLogisticsBlockEntityImport;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Original3955 fixture shapes are transcribed from pinned actual source serializers, not native saves.
 * Every successful stock case uses real registered native BE factory -> native save -> fresh BE factory.
 * UNRUN: no authored fixture pre-fills a native owner, reports success or replaces a core codec.
 */
public final class NeoForgeLogisticsBlockEntityImportGameTests {
    private static final int VERSION = NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("create", path); }
    private static CompoundTag item(Item item, int count) {
        var tag = new CompoundTag(); tag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString()); tag.putInt("count", count); return tag;
    }
    private static CompoundTag namedItem(Item item, int count) {
        var tag = item(item, count); var components = new CompoundTag();
        components.putString("minecraft:custom_name", "{\"text\":\"Original3955 cargo\"}"); tag.put("components", components); return tag;
    }
    private static CompoundTag mail() { return item(PackageStyles.getDefaultBox().getItem(), 1); }
    private static ListTag list(Tag... values) { var list = new ListTag(); for (Tag value : values) list.add(value); return list; }
    private static CompoundTag handler(int size, int slot, CompoundTag item) {
        var tag = new CompoundTag(); tag.putInt("Size", size); var items = new ListTag();
        if (item != null) { var row = item.copy(); row.putInt("Slot", slot); items.add(row); }
        tag.put("Items", items); return tag;
    }
    private static CompoundTag transported(CompoundTag item) {
        var tag = new CompoundTag(); tag.put("Item", item.copy()); tag.putFloat("Pos", .5f); tag.putFloat("PrevPos", .25f);
        tag.putFloat("Offset", .125f); tag.putFloat("PrevOffset", .0625f); tag.putInt("InSegment", 0); tag.putInt("Angle", 45); tag.putInt("InDirection", 1); return tag;
    }
    private static CompoundTag depot() {
        var tag = new CompoundTag(); tag.put("HeldItem", transported(item(Items.ENDER_PEARL, 32)));
        tag.put("OutputBuffer", handler(8, 7, namedItem(Items.DIAMOND, 3))); return tag;
    }
    private static CompoundTag ejector() {
        var tag = depot(); tag.put("Incoming", list(transported(item(Items.IRON_INGOT, 7)), transported(item(Items.IRON_INGOT, 7))));
        tag.putInt("HorizontalDistance", 4); tag.putInt("VerticalDistance", 1); tag.putBoolean("Powered", false); tag.putString("State", "RETRACTING");
        var lid = new CompoundTag(); lid.putFloat("Speed", .1f); lid.putFloat("Target", 0); lid.putFloat("Value", 1); tag.put("Lid", lid);
        var flight = new CompoundTag(); flight.putInt("Location", 2); flight.put("Item", item(Items.GOLD_INGOT, 5)); tag.put("LaunchedItems", list(flight)); return tag;
    }
    private static CompoundTag big(CompoundTag item, int count) { var tag = new CompoundTag(); tag.put("item_stack", item); tag.putInt("count", count); return tag; }
    private static CompoundTag order(CompoundTag item, int count) { var tag = new CompoundTag(); tag.put("entries", list(big(item, count))); return tag; }
    private static CompoundTag orderWithCrafts() {
        var tag = new CompoundTag(); tag.put("ordered_stacks", order(namedItem(Items.IRON_INGOT, 1), 12));
        var craft = new CompoundTag(); craft.put("pattern", order(namedItem(Items.GOLD_INGOT, 1), 2)); craft.putInt("count", 3);
        tag.put("ordered_crafts", list(craft)); return tag;
    }
    private static CompoundTag packager() {
        var tag = new CompoundTag(); tag.putBoolean("Active", false); tag.putBoolean("AnimationInward", false); tag.putInt("AnimationTicks", 5);
        tag.putString("SignAddress", "Original Market"); tag.putString("ComputerAddress", ""); tag.putBoolean("HasComputerAddress", false);
        tag.put("HeldBox", mail()); tag.put("InsertedBox", mail()); tag.put("QueuedExitingPackages", list(big(mail(), 4)));
        tag.put("LastSummary", list(big(namedItem(Items.DIAMOND, 1), 19))); return tag;
    }
    private static CompoundTag port() { var tag = new CompoundTag(); tag.put("Inventory", handler(18, 17, mail())); tag.putString("AddressFilter", "Original Market"); tag.putBoolean("AcceptsPackages", true); return tag; }
    private static NeoForgeMigrationScope scope(GameTestHelper h, BlockPos local) { return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(), h.absolutePos(local)); }
    private static CompoundTag convert(GameTestHelper h, String type, CompoundTag source, BlockPos local) {
        var before = source.copy(); var converted = NeoForgeLogisticsBlockEntityImport.convert(id(type), source, VERSION, h.getLevel().registryAccess(), scope(h, local)).orElseThrow();
        h.assertTrue(source.equals(before), "Pure original conversion must not alter source NBT"); return converted;
    }
    private static BlockEntity loadTwice(GameTestHelper h, String type, Block block, CompoundTag source, BlockPos local) {
        var original = source.copy(); original.putString("id", id(type).toString()); var at = h.absolutePos(local);
        original.putInt("x", at.getX()); original.putInt("y", at.getY()); original.putInt("z", at.getZ());
        var first = BlockEntity.loadStatic(at, block.defaultBlockState(), convert(h, type, original, local), h.getLevel().registryAccess());
        h.assertTrue(first != null && first.getLevel() == null, "Actual native registered BE factory must decode converted original payload before level assignment");
        var saved = first.saveWithFullMetadata(h.getLevel().registryAccess());
        var second = BlockEntity.loadStatic(at, block.defaultBlockState(), saved, h.getLevel().registryAccess());
        h.assertTrue(second != null && second != first, "Fresh actual native BE owner must decode its real re-save"); return second;
    }

    @GameTest public void originalDepotAndEjectorPreserveEveryIndependentStockOwner(GameTestHelper h) {
        var plain = (DepotBlockEntity) loadTwice(h, "depot", AllBlocks.DEPOT, depot(), new BlockPos(1, 2, 1));
        h.assertTrue(plain.getHeldItem().is(Items.ENDER_PEARL) && plain.getHeldItem().getCount() == 32, "Actual native Input0 retains source overstacked held cargo");
        var buffered = plain.inventory.outputs.get(7).stack();
        h.assertTrue(buffered.is(Items.DIAMOND) && buffered.getCount() == 3 && buffered.get(DataComponents.CUSTOM_NAME).getString().equals("Original3955 cargo"), "Last real output slot and typed vanilla custom-name DFU survive both native factories");
        var launch = (EjectorBlockEntity) loadTwice(h, "weighted_ejector", AllBlocks.WEIGHTED_EJECTOR, ejector(), new BlockPos(2, 2, 1));
        h.assertTrue(launch.depotBehaviour.getHeldItemStack().getCount() == 32 && launch.depotBehaviour.inventory.outputs.get(7).stack().getCount() == 3, "Ejector held/output are separate real owners");
        h.assertTrue(launch.depotBehaviour.getIncomingItems().size() == 2 && launch.depotBehaviour.getIncomingItems().stream().mapToInt(row -> row.stack.getCount()).sum() == 14, "Both real identical incoming rows remain distinct cargo");
        h.assertTrue(launch.getLaunchedItems().size() == 1 && launch.getLaunchedItems().getFirst().getValue().getCount() == 5, "Already launched five gold ingots retain their flight owner"); h.succeed();
    }

    @GameTest public void actualVaultRetainsTailSlotAndEveryLocalBlockOwner(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("StorageType", "CombinedInv"); source.putInt("Size", 1); source.putInt("Length", 1);
        source.put("Inventory", handler(41, 40, namedItem(Items.EMERALD, 13)));
        var first = (ItemVaultBlockEntity) loadTwice(h, "item_vault", AllBlocks.ITEM_VAULT, source, new BlockPos(1, 2, 1));
        var secondSource = source.copy(); secondSource.put("Inventory", handler(41, 40, item(Items.DIAMOND, 11)));
        var second = (ItemVaultBlockEntity) loadTwice(h, "item_vault", AllBlocks.ITEM_VAULT, secondSource, new BlockPos(2, 2, 1));
        h.assertTrue(first.custodySlots().get(40).stack().is(Items.EMERALD) && first.custodySlots().get(40).getAmount() == 13, "Original slot40 remains in actual native extension page");
        h.assertTrue(second.custodySlots().get(40).stack().is(Items.DIAMOND) && second.custodySlots().get(40).getAmount() == 11 && first.custodySlots().get(0).isResourceBlank(), "Distinct local vault blocks never collapse stock into a controller/default slot"); h.succeed();
    }

    @GameTest public void actualMailSlotsAndIncomingAnimationKeepDifferentParcels(GameTestHelper h) {
        var box = AllBlocks.POSTBOXES.values().iterator().next();
        var post = (PackagePortBlockEntity) loadTwice(h, "package_postbox", box, port(), new BlockPos(1, 2, 1));
        var source = port(); source.putFloat("PlacedYaw", 42); source.put("AnimatedPackage", mail()); source.putBoolean("Deposit", false);
        var frog = (FrogportBlockEntity) loadTwice(h, "package_frogport", AllBlocks.PACKAGE_FROGPORT, source, new BlockPos(2, 2, 1));
        h.assertTrue(post.inventory.getItem(17).getCount() == 1 && post.inventory.getItem(0).isEmpty(), "Actual eighteen-slot postbox retains original tail mail and empty slot0");
        h.assertTrue(frog.inventory.getItem(17).getCount() == 1 && frog.animatedPackage.getCount() == 1 && frog.displayPackage.getCount() == 1 && frog.isAnimationInProgress(), "Frog inventory plus pending incoming owner survive; visual display remains a mirror"); h.succeed();
    }

    @GameTest public void actualPackagerAndRepackagerKeepHeldAndQueuedMultiplicity(GameTestHelper h) {
        for (boolean repack : new boolean[]{false, true}) {
            var block = repack ? AllBlocks.REPACKAGER : AllBlocks.PACKAGER;
            var owner = (PackagerBlockEntity) loadTwice(h, repack ? "repackager" : "packager", block, packager(), new BlockPos(repack ? 2 : 1, 2, 1));
            h.assertTrue(owner.heldBox.getCount() == 1 && owner.queuedExitingPackages.size() == 1 && owner.queuedExitingPackages.getFirst().count == 4, "Actual native packager keeps one held and four queued packages");
            var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
            var summary = InventorySummary.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), saved.get("LastSummary")).getOrThrow();
            h.assertTrue(!owner.previouslyUnwrapped.isEmpty() && summary.getCountOf(new ItemStack(Items.DIAMOND)) == 0 && summary.getTotalCount() == 19, "InsertedBox and exact component-sensitive LastSummary are preserved without treating them as new stock");
        }
        h.succeed();
    }

    @GameTest public void packageFilterCategoryAndLastPaymentSlotSurviveActualTickerRead(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("PreviousAddress", "Original Market"); source.put("ReceivedPayments", handler(27, 26, namedItem(Items.DIAMOND, 23)));
        source.put("Categories", list(item(AllItems.PACKAGE_FILTER, 1), item(AllItems.FILTER, 1)));
        var hidden = new CompoundTag(); hidden.put("Id", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, UUID.randomUUID()).getOrThrow()); hidden.putIntArray("Indices", new int[]{-1, 0, 1}); source.put("HiddenCategories", list(hidden));
        var ticker = (StockTickerBlockEntity) loadTwice(h, "stock_ticker", AllBlocks.STOCK_TICKER, source, new BlockPos(1, 2, 1));
        h.assertTrue(ticker.categories.size() == 2 && ticker.categories.getFirst().is(AllItems.PACKAGE_FILTER) && ticker.categories.getFirst().getCount() == 1, "Actual native reader must retain real original package_filter category stock");
        h.assertTrue(ticker.receivedPayments.inputs.get(26).getAmount() == 23 && ticker.categories.get(1).is(AllItems.FILTER) && ticker.hiddenCategoriesByPlayer.values().iterator().next().size() == 3, "Last payment owner and all actual category indices survive re-save"); h.succeed();
    }

    @GameTest public void actualFactoryAndRequesterRetainNestedItemsCraftsAndPromises(GameTestHelper h) {
        var panel = new CompoundTag(); panel.put("Filter", item(Items.IRON_INGOT, 1)); panel.putInt("FilterAmount", 7); panel.putBoolean("UpTo", true);
        panel.put("Freq", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, UUID.randomUUID()).getOrThrow());
        for (String field : new String[]{"Timer", "LastLevel", "LastPromised", "LastUnloadedLinks"}) panel.putInt(field, 0);
        for (String field : new String[]{"Satisfied", "PromisedSatisfied", "Waiting", "RedstonePowered"}) panel.putBoolean(field, false);
        for (String field : new String[]{"Targeting", "TargetedBy", "TargetedByLinks"}) panel.put(field, new ListTag());
        panel.putString("RecipeAddress", "Original Market"); panel.putInt("RecipeOutput", 6); panel.putInt("PromiseClearingInterval", -1); panel.put("Craft", list(namedItem(Items.GOLD_INGOT, 1)));
        var promise = new CompoundTag(); promise.putInt("ticks_existed", 5); promise.put("promised_stack", big(item(Items.IRON_INGOT, 1), 12));
        var promises = new CompoundTag(); promises.put("List", list(promise)); panel.put("Promises", promises);
        var source = new CompoundTag(); source.putBoolean("Restocker", true); source.put("top_left", panel);
        var gauge = (FactoryPanelBlockEntity) loadTwice(h, "factory_panel", AllBlocks.FACTORY_GAUGE, source, new BlockPos(1, 2, 1));
        var actual = gauge.panels.get(FactoryPanelBlock.PanelSlot.TOP_LEFT);
        h.assertTrue(actual.active && actual.activeCraftingArrangement.getFirst().get(DataComponents.CUSTOM_NAME).getString().equals("Original3955 cargo") && actual.restockerPromises.getTotalPromisedAndRemoveExpired(new ItemStack(Items.IRON_INGOT), -1) == 12, "Actual native quadrant retains typed craft item and all twelve genuine promise quantities");
        var request = new CompoundTag(); request.putBoolean("Powered", false); request.putBoolean("Success", false); request.putBoolean("AllowPartial", true); request.putString("EncodedAddress", "Original Market"); request.put("EncodedRequest", orderWithCrafts());
        var requester = (RedstoneRequesterBlockEntity) loadTwice(h, "redstone_requester", AllBlocks.REDSTONE_REQUESTER, request, new BlockPos(2, 2, 1));
        h.assertTrue(requester.encodedRequest.stacks().getFirst().count == 12 && requester.encodedRequest.orderedCrafts().getFirst().count() == 3 && requester.encodedRequest.orderedCrafts().getFirst().pattern().stacks().getFirst().count == 2, "Actual typed nested request preserves order12, craft multiplicity3 and pattern2"); h.succeed();
    }

    @GameTest public void actualClothChutesAndTunnelReadOriginalTypedDomainFields(GameTestHelper h) {
        var request = new CompoundTag(); request.put("encoded_request", orderWithCrafts()); request.putString("encoded_target_address", "Original Market");
        request.put("target_offset", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, BlockPos.ZERO).getOrThrow()); request.putString("target_dim", "minecraft:overworld"); request.putBoolean("is_valid", true);
        var cloth = new CompoundTag(); cloth.put("Items", list(namedItem(Items.APPLE, 1))); cloth.put("RequestData", request); cloth.putInt("Facing", 0);
        var display = (TableClothBlockEntity) loadTwice(h, "table_cloth", AllBlocks.TABLE_CLOTHS.values().iterator().next(), cloth, new BlockPos(1, 2, 1));
        h.assertTrue(display.manuallyAddedItems.getFirst().getCount() == 1 && display.requestData.encodedRequest().stacks().getFirst().count == 12, "Actual cloth keeps removable display stock and typed order references");
        for (boolean smart : new boolean[]{false, true}) {
            var chute = new CompoundTag(); chute.put("Item", item(Items.IRON_INGOT, 17)); chute.putFloat("ItemPosition", .375f); chute.putFloat("Pull", 64); chute.putFloat("Push", 0); chute.putFloat("BottomAirFlowDistance", 2);
            var actual = (ChuteBlockEntity) loadTwice(h, smart ? "smart_chute" : "chute", smart ? AllBlocks.SMART_CHUTE : AllBlocks.CHUTE, chute, new BlockPos(smart ? 3 : 2, 2, 1));
            h.assertTrue(actual.getItem().getCount() == 17 && actual.getItemPosition(0) == .375f, "Actual source chute stack and motion survive each native type");
        }
        var tunnel = new CompoundTag(); for (String field : new String[]{"SyncedOutput", "ConnectedLeft", "ConnectedRight"}) tunnel.putBoolean(field, false);
        tunnel.put("StackToDistribute", namedItem(Items.GOLD_INGOT, 9)); tunnel.putFloat("DistributionProgress", 2); tunnel.putString("StackEnteredFrom", "NORTH");
        for (String field : new String[]{"PreviousIndex", "DistanceLeft", "DistanceRight"}) tunnel.putInt(field, 0);
        tunnel.put("Targets", new ListTag()); tunnel.put("FilteredTargets", new ListTag());
        var actual = loadTwice(h, "brass_tunnel", AllBlocks.BRASS_TUNNEL, tunnel, new BlockPos(4, 2, 1)); var saved = actual.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(ItemStack.OPTIONAL_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), saved.get("StackToDistribute")).getOrThrow().getCount() == 9 && saved.getIntOr("DistributionProgress", -5) == 2, "Actual native tunnel keeps queue and exact float-source integral countdown"); h.succeed();
    }

    @GameTest public void sourceScopeProducesStableDistinctNewTransportIdentities(GameTestHelper h) {
        var source = ejector(); var at = new BlockPos(1, 2, 1);
        var first = convert(h, "weighted_ejector", source, at); var again = convert(h, "weighted_ejector", source, at); var other = convert(h, "weighted_ejector", source, at.east());
        var a = first.getListOrEmpty("Incoming").getCompoundOrEmpty(0).read("Id", UUIDUtil.CODEC).orElseThrow();
        var b = first.getListOrEmpty("Incoming").getCompoundOrEmpty(1).read("Id", UUIDUtil.CODEC).orElseThrow();
        h.assertTrue(first.equals(again) && !a.equals(b) && !first.getCompoundOrEmpty("HeldItem").read("Id", UUIDUtil.CODEC).orElseThrow().equals(a), "Same actual owner/order yields stable NEW native identities while distinct source rows/roles remain distinct");
        h.assertTrue(!a.equals(other.getListOrEmpty("Incoming").getCompoundOrEmpty(0).read("Id", UUIDUtil.CODEC).orElseThrow()), "Different actual world block owner cannot collide on identical cargo fields"); h.succeed();
    }

    @GameTest public void malformedRecognizedPayloadFailsWithoutMutationOrEmptyFallback(GameTestHelper h) {
        var source = depot(); var original = source.copy();
        h.assertTrue(rejects(() -> NeoForgeLogisticsBlockEntityImport.convert(id("depot"), source, VERSION, h.getLevel().registryAccess())), "Original transport requires actual owner scope");
        h.assertTrue(rejects(() -> NeoForgeLogisticsBlockEntityImport.convert(id("depot"), source, VERSION - 1, h.getLevel().registryAccess(), scope(h, BlockPos.ZERO))), "Unknown source version cannot become a native empty inventory");
        var duplicate = source.copy(); var items = duplicate.getCompoundOrEmpty("OutputBuffer").getListOrEmpty("Items"); items.add(items.getCompoundOrEmpty(0).copy()); var before = duplicate.copy();
        h.assertTrue(rejects(() -> convert(h, "depot", duplicate, BlockPos.ZERO)) && duplicate.equals(before), "Duplicate actual source slots reject atomically");
        var malformed = source.copy(); malformed.getCompoundOrEmpty("HeldItem").getCompoundOrEmpty("Item").putInt("count", 0);
        h.assertTrue(rejects(() -> convert(h, "depot", malformed, BlockPos.ZERO)), "Nonempty original owner cannot silently decode empty");
        h.assertTrue(NeoForgeLogisticsBlockEntityImport.convert(Identifier.fromNamespaceAndPath("other", "stock"), null, -1, null).isEmpty() && source.equals(original), "Foreign ID alone returns empty and recognized failures never alter source"); h.succeed();
    }

    @GameTest public void nativeOverstackedOwnerUsesRealFabricRollbackAndExactExtraction(GameTestHelper h) {
        var owner = (DepotBlockEntity) loadTwice(h, "depot", AllBlocks.DEPOT, depot(), new BlockPos(1, 2, 1)); var slot = owner.inventory.inputs.getFirst();
        try (var transaction = Transaction.openOuter()) { h.assertTrue(slot.extract(slot.getResource(), 32, transaction) == 32, "Real native Fabric slot extracts exact source overstacked amount"); }
        h.assertTrue(slot.getAmount() == 32, "Real aborted native extraction restores all original cargo");
        try (var transaction = Transaction.openOuter()) { h.assertTrue(slot.extract(slot.getResource(), 32, transaction) == 32, "Real committed extraction transfers original32 once"); transaction.commit(); }
        h.assertTrue(slot.isResourceBlank() && owner.inventory.outputs.get(7).getAmount() == 3, "Held owner relinquishes its exact stock while independent output stays owned"); h.succeed();
    }
    private static boolean rejects(Runnable action) { try { action.run(); return false; } catch (IllegalArgumentException | IllegalStateException expected) { return true; } }
}

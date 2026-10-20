package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.table.SchematicTableBlockEntity;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeRemainingBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.math.Pointing;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Pinned original3955 fixture shapes; actual native factory -> save -> fresh factory. Runtime UNRUN. */
public final class NeoForgeRemainingBlockEntityImportGameTests {
    private static final int VERSION = NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static Identifier id(String value) { return Identifier.parse("create:" + value); }
    private static CompoundTag item(Item item, int count) {
        var result = new CompoundTag(); result.putString("id", BuiltInRegistries.ITEM.getKey(item).toString()); result.putInt("count", count); return result;
    }
    private static CompoundTag named(Item item, int count) {
        var result = item(item, count); var components = new CompoundTag(); components.putString("minecraft:custom_name", "{\"text\":\"Original3955\"}"); result.put("components", components); return result;
    }
    private static ListTag list(Tag... values) { var result = new ListTag(); for (var value : values) result.add(value); return result; }
    private static CompoundTag pos(BlockPos value) {
        var result = new CompoundTag(); result.putInt("X", value.getX()); result.putInt("Y", value.getY()); result.putInt("Z", value.getZ()); return result;
    }
    private static CompoundTag handler(int size, int slot, CompoundTag item) {
        var result = new CompoundTag(); result.putInt("Size", size); var rows = new ListTag();
        if (item != null) { var row = item.copy(); row.putInt("Slot", slot); rows.add(row); } result.put("Items", rows); return result;
    }
    private static CompoundTag transport(CompoundTag item, int segment) {
        var result = new CompoundTag(); result.put("Item", item); result.putFloat("Pos", segment + .5f); result.putFloat("PrevPos", segment + .25f);
        result.putFloat("Offset", .125f); result.putFloat("PrevOffset", .0625f); result.putInt("InSegment", segment); result.putInt("Angle", 45); result.putInt("InDirection", 2); return result;
    }
    private static NeoForgeMigrationScope scope(GameTestHelper h, BlockPos local) {
        return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(), h.absolutePos(local));
    }
    private static CompoundTag convert(GameTestHelper h, String type, CompoundTag source, BlockPos local) {
        var before = source.copy(); var result = NeoForgeRemainingBlockEntityImport.convert(id(type), source, VERSION, h.getLevel().registryAccess(), scope(h, local)).orElseThrow();
        h.assertTrue(source.equals(before), "Original migration must leave caller NBT immutable"); return result;
    }
    private static BlockEntity load(GameTestHelper h, String type, BlockState state, CompoundTag source, BlockPos local) {
        var original = source.copy(); var at = h.absolutePos(local); original.putString("id", id(type).toString()); original.putInt("x", at.getX()); original.putInt("y", at.getY()); original.putInt("z", at.getZ());
        var result = BlockEntity.loadStatic(at, state, convert(h, type, original, local), h.getLevel().registryAccess());
        h.assertTrue(result != null && result.getLevel() == null, "Actual registered native factory must decode original cargo before world assignment"); return result;
    }
    private static BlockEntity twice(GameTestHelper h, String type, Block block, CompoundTag source) {
        var local = new BlockPos(1, 2, 1); var first = load(h, type, block.defaultBlockState(), source, local);
        var second = BlockEntity.loadStatic(h.absolutePos(local), block.defaultBlockState(), first.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(second != null && second != first, "Fresh registered native factory must decode its actual re-save"); return second;
    }
    private static ItemStack savedStack(GameTestHelper h, BlockEntity owner, String field) {
        return owner.saveWithoutMetadata(h.getLevel().registryAccess()).read(field, ItemStack.OPTIONAL_CODEC, h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE)).orElseThrow();
    }
    private static void rejected(GameTestHelper h, Runnable operation) {
        boolean rejected = false; try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "Malformed original owner must fail explicitly before material publication");
    }

    @GameTest public void tableRetainsActualTailStockWithoutFabricatingUploadPayment(GameTestHelper h) {
        var source = new CompoundTag(); source.put("Inventory", handler(2, 1, named(Items.DIAMOND, 7)));
        var owner = (SchematicTableBlockEntity) twice(h, "schematic_table", AllBlocks.SCHEMATIC_TABLE, source);
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.inventory.getItem(0).isEmpty() && owner.inventory.getItem(1).getCount() == 7
            && owner.inventory.getItem(1).get(DataComponents.CUSTOM_NAME).getString().equals("Original3955"), "Both explicit slots and exact source components survive");
        h.assertTrue(!saved.contains("UploadId") && !saved.contains("UploadReceipt"), "Source inventory never proves a paid pending upload"); h.succeed();
    }

    @GameTest public void chainParcelsGetDistinctStableNativeIdentitiesWithoutSourceReceipts(GameTestHelper h) {
        var source = new CompoundTag(); source.put("Connections", list(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, new BlockPos(5, 0, 0)).getOrThrow()));
        var parcel = new CompoundTag(); parcel.putFloat("Position", 1); parcel.put("Item", item(PackageStyles.getDefaultBox().getItem(), 1));
        source.put("LoopingPackages", list(parcel.copy(), parcel.copy())); var travel = new CompoundTag(); travel.put("Target", pos(new BlockPos(5, 0, 0))); travel.put("Packages", list(parcel.copy())); source.put("TravellingPackages", list(travel));
        var first = convert(h, "chain_conveyor", source, new BlockPos(1, 2, 1)); var again = convert(h, "chain_conveyor", source, new BlockPos(1, 2, 1));
        h.assertTrue(first.equals(again), "Same real owner path gives deterministic NEW native row identities");
        var rows = first.getListOrEmpty("LoopingPackages");
        h.assertTrue(!((CompoundTag) rows.get(0)).get("Identity").equals(((CompoundTag) rows.get(1)).get("Identity")), "Identical original parcels remain separate physical rows");
        var owner = (ChainConveyorBlockEntity) twice(h, "chain_conveyor", AllBlocks.CHAIN_CONVEYOR, source);
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getListOrEmpty("LoopingPackages").size() == 2 && saved.getListOrEmpty("TravellingPackages").size() == 1, "Native re-save retains both loop owners and travelling owner"); h.succeed();
    }

    @GameTest public void drainKeepsActualFluidAndHeldCargoAtOriginalProcessingBoundary(GameTestHelper h) {
        var source = new CompoundTag(); source.putInt("ProcessingTicks", 5); source.put("HeldItem", transport(named(Items.BUCKET, 1), 0));
        var fluid = new CompoundTag(); fluid.putString("id", "minecraft:water"); fluid.putInt("amount", 750);
        var level = new CompoundTag(); level.putFloat("Speed", .1f); level.putFloat("Target", .5f); level.putFloat("Value", .4f);
        var wrapper = new CompoundTag(); wrapper.put("Fluid", fluid); var tank = new CompoundTag(); tank.put("TankContent", wrapper); tank.put("Level", level); source.put("Tanks", list(tank));
        var owner = (ItemDrainBlockEntity) twice(h, "item_drain", AllBlocks.ITEM_DRAIN, source);
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.tank.getAmount() == 750L * 81 && owner.getHeldItemStack().is(Items.BUCKET)
            && saved.getIntOr("ProcessingTicks", -1) == 5, "Real fluid units, original held item and pending actual emptying boundary survive");
        h.assertTrue(!saved.contains("ProcessingFluid"), "No physical fluid is invented from a preview"); h.succeed();
    }

    @GameTest public void savedLongBeltRetainsMoreThan1024ActualCargoOwners(GameTestHelper h) {
        var source = new CompoundTag(); source.putBoolean("IsController", true); source.putInt("Length", 1026); source.putInt("Index", 0); source.putString("Casing", "NONE"); source.putBoolean("Covered", false); source.putString("Dye", "LIGHT_BLUE");
        var rows = new ListTag(); for (int i = 0; i < 1025; i++) rows.add(transport(item(Items.IRON_INGOT, 1), i));
        var inventory = new CompoundTag(); inventory.put("Items", rows); inventory.putBoolean("PositiveOrder", true); source.put("Inventory", inventory);
        var owner = (BeltBlockEntity) twice(h, "belt", AllBlocks.BELT, source);
        h.assertTrue(owner.beltLength == 1026 && owner.index == 0 && owner.getInventory().getTransportedItems().size() == 1025,
            "Actual native save codec retains long source geometry and every independent row");
        h.assertTrue(owner.getInventory().getTransportedItems().stream().map(row -> row.id).distinct().count() == 1025
            && owner.getInventory().getTransportedItems().stream().mapToInt(row -> row.stack.getCount()).sum() == 1025, "No cargo identity or material count is merged/dropped at old arbitrary caps"); h.succeed();
    }

    @GameTest public void crusherAppliedRecipeTreatsSourceSlotZeroAndSlot31AsOutputs(GameTestHelper h) {
        var source = new CompoundTag(); source.putFloat("Speed", 1);
        var inventory = handler(32, 0, named(Items.DIAMOND, 3)); var tail = named(Items.EMERALD, 9); tail.putInt("Slot", 31); inventory.getListOrEmpty("Items").add(tail);
        inventory.putFloat("ProcessingTime", 7); inventory.putFloat("RecipeTime", 0); inventory.putBoolean("AppliedRecipe", true); source.put("Inventory", inventory);
        var owner = (CrushingWheelControllerBlockEntity) twice(h, "crushing_wheel_controller", AllBlocks.CRUSHING_WHEEL_CONTROLLER, source);
        h.assertTrue(owner.inventory.getInput().isEmpty() && owner.inventory.getOutputs().size() == 2
            && owner.inventory.getOutputs().getFirst().is(Items.DIAMOND) && owner.inventory.getOutputs().getFirst().getCount() == 3
            && owner.inventory.getOutputs().getLast().is(Items.EMERALD) && owner.inventory.getOutputs().getLast().getCount() == 9,
            "Already consumed original input is never recreated from output slot0; tail stock remains ordered"); h.succeed();
    }

    private static CompoundTag crafter(String phase, CompoundTag stack, boolean controller, BlockPos... offsets) {
        var source = new CompoundTag(); source.put("Inventory", handler(1, 0, null)); source.putString("Phase", phase); source.putInt("CountDown", 1); source.putBoolean("Cover", false);
        var input = new CompoundTag(); input.putBoolean("Controller", controller); var members = new ListTag(); for (var offset : offsets) members.add(pos(offset)); input.put("Data", members); source.put("ConnectedInput", input);
        var grouped = new CompoundTag(); var cell = new CompoundTag(); cell.putInt("x", 0); cell.putInt("y", 0); cell.put("item", stack); grouped.put("Grid", list(cell)); source.put("GroupedItems", grouped); return source;
    }
    @GameTest public void originalActiveCrafterJoinsOnlyActualLoadedDirectionalOwners(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = a.east();
        var stateA = AllBlocks.MECHANICAL_CRAFTER.defaultBlockState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.SOUTH).setValue(MechanicalCrafterBlock.POINTING, Pointing.RIGHT);
        var stateB = stateA.setValue(MechanicalCrafterBlock.POINTING, Pointing.UP);
        h.setBlock(a, stateA); h.setBlock(b, stateB);
        var first = (MechanicalCrafterBlockEntity) load(h, "mechanical_crafter", stateA, crafter("EXPORTING", named(Items.DIAMOND, 1), true, BlockPos.ZERO, new BlockPos(1, 0, 0)), a);
        var second = (MechanicalCrafterBlockEntity) load(h, "mechanical_crafter", stateB, crafter("ACCEPTING", item(Items.EMERALD, 1), false, new BlockPos(-1, 0, 0)), b);
        h.getLevel().setBlockEntity(first); h.getLevel().setBlockEntity(second);
        MechanicalCrafterBlockEntity.tick(h.getLevel(), h.absolutePos(a), stateA, first);
        var savedA = first.saveWithoutMetadata(h.getLevel().registryAccess()); var savedB = second.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(first.phase == MechanicalCrafterBlockEntity.Phase.EXPORTING && second.phase == MechanicalCrafterBlockEntity.Phase.ACCEPTING
            && first.groupedItems.grid.values().stream().mapToInt(ItemStack::getCount).sum() == 1 && second.groupedItems.grid.values().stream().mapToInt(ItemStack::getCount).sum() == 1,
            "Migration does not eject/recraft original active stock");
        h.assertTrue(savedA.contains("Session") && savedA.get("Session").equals(savedB.get("Session"))
            && savedA.getListOrEmpty("Participants").size() == 2 && !savedA.contains("OriginalConnectedInput"), "Only actual reciprocal loaded owners form the new native session"); h.succeed();
    }

    @GameTest public void toolboxRetainsLastCompartmentStockIdentityAndTypedName(GameTestHelper h) {
        var source = new CompoundTag(); var inventory = handler(32, 31, named(Items.DIAMOND, 11)); var filters = new ListTag(); for (int i = 0; i < 8; i++) filters.add(i == 7 ? named(Items.DIAMOND, 1) : new CompoundTag()); inventory.put("Compartments", filters); source.put("Inventory", inventory);
        var uuid = UUID.fromString("f0813631-e06e-4ce3-947d-557180c58599"); source.put("UniqueId", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, uuid).getOrThrow()); var nested = new CompoundTag(); nested.putInt("slot", 0); nested.put("item", named(Items.EMERALD, 1));
        var filter = item(AllItems.FILTER, 1); var filterComponents = new CompoundTag(); filterComponents.put("create:filter_items", list(nested)); filter.put("components", filterComponents);
        var hover = new CompoundTag(); hover.putString("action", "show_item"); hover.put("contents", filter);
        var text = new CompoundTag(); text.putString("text", "Original toolbox"); text.put("hoverEvent", hover);
        source.putString("CustomName", NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, text).toString());
        var owner = (ToolboxBlockEntity) twice(h, "toolbox", AllBlocks.TOOLBOXES.values().iterator().next(), source);
        long diamonds = 0; for (var view : owner.inventory) if (!view.isResourceBlank() && view.getResource().toStack().is(Items.DIAMOND)) diamonds += view.getAmount();
        h.assertTrue(diamonds == 11 && owner.getUniqueId().equals(uuid) && owner.getCustomName().getString().equals("Original toolbox"), "Physical stock is not counted again as compartment templates and typed names/UUID survive");
        h.assertTrue(owner.getCustomName().getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowItem, "Actual native text retains its typed original hover item");
        var show = (net.minecraft.network.chat.HoverEvent.ShowItem) owner.getCustomName().getStyle().getHoverEvent();
        var displayed = show.item().create(); var nestedItems = displayed.get(AllDataComponents.FILTER_ITEMS).itemCopies().toList();
        h.assertTrue(displayed.is(AllItems.FILTER) && nestedItems.getFirst().is(Items.EMERALD)
            && nestedItems.getFirst().get(DataComponents.CUSTOM_NAME).getString().equals("Original3955"), "Nested genuine Create filter contents in original flattened text use the typed item/component migration"); h.succeed();
    }

    @GameTest public void copycatMaterialAndRecoverableOriginalReceiptSurviveTypedStateDfu(GameTestHelper h) {
        var source = new CompoundTag(); source.put("Item", item(Items.STONE, 1)); var material = new CompoundTag(); material.putString("Name", "minecraft:stone"); source.put("Material", material);
        var owner = (CopycatBlockEntity) twice(h, "copycat", AllBlocks.COPYCAT_STEP, source);
        h.assertTrue(owner.getMaterial().is(Blocks.STONE) && owner.getConsumedItem().is(Items.STONE) && owner.getConsumedItem().getCount() == 1,
            "Actual material identity and original refundable count1 receipt remain paired");
        var invalid = source.copy(); invalid.getCompound("Material").orElseThrow().putString("Name", "missing_original:material");
        rejected(h, () -> convert(h, "copycat", invalid, new BlockPos(1, 2, 1))); h.succeed();
    }

    @GameTest public void armPlacardBacktankAndClipboardPreserveTypedEquipmentData(GameTestHelper h) {
        var arm = new CompoundTag(); arm.put("HeldItem", named(Items.DIAMOND, 5)); arm.put("InteractionPoints", new ListTag()); arm.putString("Phase", "SEARCH_OUTPUTS"); arm.putBoolean("Powered", false); arm.putBoolean("Goggles", true); arm.putInt("TargetPointIndex", -1); arm.putFloat("MovementProgress", .25f);
        h.assertTrue(savedStack(h, twice(h, "mechanical_arm", AllBlocks.MECHANICAL_ARM, arm), "HeldItem").getCount() == 5, "Actual arm held stock survives");
        var placard = new CompoundTag(); placard.put("Item", named(Items.EMERALD, 3)); placard.putInt("PoweredTicks", 12);
        h.assertTrue(savedStack(h, twice(h, "placard", AllBlocks.PLACARD, placard), "Item").getCount() == 3, "Recoverable placard stock survives");
        var tank = new CompoundTag(); tank.putInt("Air", 500); tank.putInt("Timer", 7); tank.putInt("CapacityEnchantment", 0); tank.putString("CustomName", "{\"text\":\"Original backtank\"}"); tank.put("Components", new CompoundTag());
        var backtank = (BacktankBlockEntity) twice(h, "backtank", AllBlocks.COPPER_BACKTANK, tank);
        h.assertTrue(backtank.airLevel == 500 && backtank.airLevelTimer == 7 && backtank.getName().getString().equals("Original backtank"), "Actual air and typed original text survive");
        var clipboard = new CompoundTag(); var map = new CompoundTag(); map.putString("minecraft:custom_name", "{\"text\":\"Original clipboard\"}"); clipboard.put("components", map);
        var owner = twice(h, "clipboard", AllBlocks.CLIPBOARD, clipboard);
        h.assertTrue(owner.components().get(DataComponents.CUSTOM_NAME).getString().equals("Original clipboard"), "Original genuine component map uses typed DFU without a fabricated carrier item"); h.succeed();
    }

    @GameTest public void lecternPreservesSparseOriginalControllerComponentSlots(GameTestHelper h) {
        var source = new CompoundTag(); var row = new CompoundTag(); row.putInt("slot", 5); row.put("item", named(Items.EMERALD, 1)); source.put("ControllerData", list(row));
        var owner = twice(h, "lectern_controller", AllBlocks.LECTERN_CONTROLLER, source);
        var contents = owner.saveWithoutMetadata(h.getLevel().registryAccess()).read("ControllerData", net.minecraft.world.item.component.ItemContainerContents.CODEC,
            h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE)).orElseThrow().itemCopies().toList();
        h.assertTrue(contents.size() == 6 && contents.get(0).isEmpty() && contents.get(5).is(Items.EMERALD)
            && contents.get(5).get(DataComponents.CUSTOM_NAME).getString().equals("Original3955"), "Sparse actual slot5 is not compacted into another controller input"); h.succeed();
    }

    @GameTest public void pendingCannonRetainsOriginalTargetsWithoutInventingRefundableIcons(GameTestHelper h) {
        var source = new CompoundTag(); source.put("Inventory", handler(5, 4, item(Items.GUNPOWDER, 3))); source.putFloat("Progress", .25f); source.putFloat("PaperProgress", 0); source.putInt("RemainingFuel", 20); source.putString("Status", "ready"); source.putString("State", "PAUSED"); source.putInt("AmountPlaced", 2); source.putInt("AmountToPlace", 8); source.putFloat("DefaultYaw", 45);
        var options = new CompoundTag(); options.putInt("replace_mode", 2); options.putBoolean("skip_missing", false); options.putBoolean("replace_block_entities", true); source.put("Options", options);
        var printer = new CompoundTag(); printer.putInt("EntityProgress", -1); printer.putString("PrintStage", "BLOCKS"); printer.put("DeferredBlocks", new ListTag()); source.put("Printer", printer);
        var launch = new CompoundTag(); launch.putInt("TotalTicks", 20); launch.putInt("TicksLeft", 8); launch.put("Stack", item(AllItems.BELT_CONNECTOR, 1)); launch.put("Target", pos(h.absolutePos(new BlockPos(3, 2, 1))));
        var belt = new CompoundTag(); belt.putString("Name", "create:belt"); var properties = new CompoundTag(); properties.putString("part", "start"); properties.putString("slope", "horizontal"); properties.putString("facing", "east"); properties.putString("casing", "false"); properties.putString("waterlogged", "false"); belt.put("Properties", properties); launch.put("BlockState", belt); launch.putInt("Length", 33); launch.putIntArray("Casing", new int[33]); source.put("FlyingBlocks", list(launch));
        var owner = (SchematicannonBlockEntity) twice(h, "schematicannon", AllBlocks.SCHEMATICANNON, source);
        h.assertTrue(owner.flyingBlocks.size() == 1 && owner.flyingBlocks.getFirst().paidOriginals().isEmpty()
            && owner.flyingBlocks.getFirst().target.equals(h.absolutePos(new BlockPos(3, 2, 1))) && owner.flyingBlocks.getFirst().ticksRemaining == 8,
            "Already consumed original long-belt flight keeps actual target/time and no invented refundable connector receipt"); h.succeed();
    }

    @GameTest public void safeCrafterPayloadNeedsNoInventedInventoryAndMalformedSourceFailsClosed(GameTestHelper h) {
        var source = new CompoundTag(); var input = new CompoundTag(); input.putBoolean("Controller", true); input.put("Data", list(pos(BlockPos.ZERO))); source.put("ConnectedInput", input);
        var converted = NeoForgeRemainingBlockEntityImport.convertSafe(id("mechanical_crafter"), source, VERSION, h.getLevel().registryAccess(), scope(h, new BlockPos(1, 2, 1))).orElseThrow();
        h.assertTrue(converted.contains("OriginalConnectedInput") && !converted.contains("Inventory") && !converted.contains("Session") && !converted.contains("Participants"), "Actual safe placement input preserves source evidence without fabricated full material owners/session");
        var table = new CompoundTag(); table.put("Inventory", handler(2, 0, item(Items.DIAMOND, 1))); table.putBoolean("Uploading", true);
        var before = table.copy(); rejected(h, () -> convert(h, "schematic_table", table, new BlockPos(1, 2, 1)));
        h.assertTrue(table.equals(before), "Refusal never alters caller original save");
        h.assertTrue(NeoForgeRemainingBlockEntityImport.convert(Identifier.parse("unowned:table"), table, VERSION, h.getLevel().registryAccess()).isEmpty(), "Foreign schemas are left to their actual owning converter"); h.succeed();
    }

    @GameTest public void originalArmMoreThan1024TargetsSurviveNativeSaveAndActualPacket(GameTestHelper h) {
        var source = new CompoundTag(); source.put("HeldItem", named(Items.DIAMOND, 5)); source.putString("Phase", "MOVE_TO_OUTPUT"); source.putBoolean("Powered", false); source.putBoolean("Goggles", false); source.putInt("TargetPointIndex", 1024); source.putFloat("MovementProgress", .25f);
        var points = new ListTag();
        for (int i = 0; i < 1025; i++) {
            var point = new CompoundTag(); point.putString("Type", "create:depot"); point.putString("Mode", "DEPOSIT"); point.put("Pos", pos(new BlockPos(i % 41 + 1, i / 41, 0))); points.add(point);
        }
        source.put("InteractionPoints", points);
        var owner = (ArmBlockEntity) twice(h, "mechanical_arm", AllBlocks.MECHANICAL_ARM, source);
        h.assertTrue(owner.interactionPoints().size() == 1025 && owner.getTargetPointIndex() == 1024 && owner.getHeldItem().getCount() == 5,
            "Every original definition and actual target index survive under a reduced current interaction range");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var packet = new ArmPlacementPacket(owner.interactionPoints(), owner.getBlockPos(), 17);
            ArmPlacementPacket.STREAM_CODEC.encode(buffer, packet);
            h.assertTrue(ArmPlacementPacket.STREAM_CODEC.decode(buffer).equals(packet) && buffer.readableBytes() == 0,
                "Actual native packet carries all valid rows with bounds from its real bytes");
        } finally { buffer.release(); }
        rejected(h, () -> new ArmPlacementPacket(List.of(owner.interactionPoints().getFirst(), owner.interactionPoints().getFirst()), owner.getBlockPos(), 17)); h.succeed();
    }

    @GameTest public void backtankNativeReloadAndSourceSettersRetainSavedAirAfterConfigReduction(GameTestHelper h) {
        var setting = AllConfigs.server().equipment.airInBacktank; int previous = setting.getServer();
        try {
            setting.set(100); var source = new CompoundTag(); source.putInt("Air", 500); source.putInt("Timer", 7); source.putInt("CapacityEnchantment", 0); source.put("Components", new CompoundTag());
            var owner = (BacktankBlockEntity) twice(h, "backtank", AllBlocks.COPPER_BACKTANK, source);
            h.assertTrue(owner.getAirLevel() == 500, "Read and implicit-component loading do not clamp previously saved air to the lowered fill limit");
            owner.setCapacityEnchantLevel(0); owner.setAirLevel(500);
            var restored = (BacktankBlockEntity) BlockEntity.loadStatic(owner.getBlockPos(), owner.getBlockState(), owner.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(restored != null && restored.getAirLevel() == 500, "Source-compatible setters and actual native re-save keep the original stored air");
        } finally { setting.set(previous); }
        h.succeed();
    }

    @GameTest(structure="create_test:reservoir",padding = 32) public void actualLongBeltSurvivesLoweredConstructionLimitAndDismantlesEveryLoadedOwner(GameTestHelper h) {
        var setting = AllConfigs.server().kinetics.maxBeltLength; int previous = setting.getServer();
        var first = h.absolutePos(new BlockPos(1, 2, 1)); var last = first.east(24);
        var shaft = AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS, Direction.Axis.Z);
        try {
            for (int i = 0; i <= 24; i++) h.assertTrue(h.getLevel().isLoaded(first.east(i)), "Actual saved chain footprint must already be loaded");
            setting.set(40); h.getLevel().setBlockAndUpdate(first, shaft); h.getLevel().setBlockAndUpdate(last, shaft);
            h.assertTrue(BeltConnectorItem.createBelts(h.getLevel(), first, last), "Current configured length admits one actual long native chain");
            var controller = (BeltBlockEntity) h.getLevel().getBlockEntity(first);
            h.assertTrue(controller != null && controller.chainLoaded() && controller.beltLength == 25, "All25 actual native segment owners configure before transport");
            setting.set(5); BeltBlock.initBelt(h.getLevel(), first.east(12));
            h.assertTrue(controller.chainLoaded() && controller.beltLength == 25 && BeltBlock.getBeltChain(h.getLevel(), first).size() == 25,
                "Reduced construction setting never truncates already saved geometry");
            h.getLevel().setBlockAndUpdate(first.south(2), shaft); h.getLevel().setBlockAndUpdate(last.south(2), shaft);
            h.assertTrue(!BeltConnectorItem.canConnect(h.getLevel(), first.south(2), last.south(2)), "New creation still obeys the lowered current limit");
            h.getLevel().destroyBlock(first.east(12), false);
            for (int i = 0; i <= 24; i++) h.assertTrue(!(h.getLevel().getBlockState(first.east(i)).getBlock() instanceof BeltBlock), "Dismantling follows every real surviving segment beyond old20 cap");
        } finally {
            setting.set(previous);
            for (int i = 0; i <= 24; i++) h.getLevel().setBlockAndUpdate(first.east(i), Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(first.south(2), Blocks.AIR.defaultBlockState()); h.getLevel().setBlockAndUpdate(last.south(2), Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }
}

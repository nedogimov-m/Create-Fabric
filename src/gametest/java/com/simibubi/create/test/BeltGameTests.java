package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public class BeltGameTests {
    private static final BlockPos START = new BlockPos(2, 2, 3);
    private static final BlockPos END = START.east(4);
    private static void shaft(GameTestHelper helper, BlockPos pos, Direction.Axis axis) {
        helper.setBlock(pos, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, axis));
    }
    private static BeltBlockEntity connect(GameTestHelper helper, BlockPos start, BlockPos end, Direction.Axis axis) {
        shaft(helper, start, axis); shaft(helper, end, axis);
        helper.assertTrue(BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(start), helper.absolutePos(end)), "Connector creates belt");
        return helper.getBlockEntity(start, BeltBlockEntity.class);
    }
    private static BeltBlockEntity horizontal(GameTestHelper helper, boolean powered) {
        var belt = connect(helper, START, END, Direction.Axis.Z);
        if (powered) {
            helper.setBlock(START.north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
            helper.getBlockEntity(START.north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        }
        return belt;
    }
    private static long insert(BeltBlockEntity belt, int segment, ItemStack stack, boolean commit) {
        try (Transaction tx = Transaction.openOuter()) {
            long amount = belt.getInventory().segment(segment, Direction.UP).insert(ItemVariant.of(stack), stack.getCount(), tx);
            if (commit) tx.commit();
            return amount;
        }
    }
    private static long count(BeltBlockEntity belt, Item item) {
        return belt.getInventory().getTransportedItems().stream().filter(s -> s.stack.is(item)).mapToLong(s -> s.stack.getCount()).sum();
    }
    private static long count(DepotBlockEntity depot, Item item) {
        try (Transaction tx = Transaction.openOuter()) { return depot.inventory.automation.extract(ItemVariant.of(item), 10000, tx); }
    }
    private static long count(net.fabricmc.fabric.api.transfer.v1.storage.Storage<ItemVariant> storage, Item item) {
        try (Transaction tx = Transaction.openOuter()) { return storage.extract(ItemVariant.of(item), 10000, tx); }
    }
    @GameTest
    public void connectorConfiguresControllerAndIntermediatePulleys(GameTestHelper helper) {
        shaft(helper, START.east(2), Direction.Axis.Z);
        var belt = horizontal(helper, false);
        helper.assertTrue(belt.isController() && belt.chainLoaded(), "Whole chain configured immediately");
        helper.assertValueEqual(belt.beltLength, 5, "Five segments");
        helper.assertValueEqual(helper.getBlockState(START.east(2)).getValue(BeltBlock.PART), BeltPart.PULLEY, "Existing intermediate shaft kept");
        for (int i = 0; i < 5; i++) {
            var segment = helper.getBlockEntity(START.east(i), BeltBlockEntity.class);
            helper.assertValueEqual(segment.index, i, "Segment index");
            helper.assertValueEqual(segment.getController(), helper.absolutePos(START), "Shared controller");
        }
        helper.succeed();
    }
    @GameTest
    public void connectorRejectsAxisMismatchBlockedPathAndUnequalSlope(GameTestHelper helper) {
        shaft(helper, START, Direction.Axis.Z); shaft(helper, END, Direction.Axis.X);
        helper.assertFalse(BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(END)), "Mismatched shaft axes");
        shaft(helper, END, Direction.Axis.Z); helper.setBlock(START.east(2), Blocks.STONE);
        helper.assertFalse(BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(END)), "Solid obstacle");
        helper.setBlock(START.east(2), Blocks.AIR); shaft(helper, END.above(2), Direction.Axis.Z);
        helper.assertFalse(BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(END.above(2))), "Only 45 degree slopes");
        helper.succeed();
    }
    @GameTest(padding = 24)
    public void connectorEnforcesEuclideanRangeAndShaftPlane(GameTestHelper helper) {
        for (var pos : BeltConnectorItem.chainBetween(START, START.east(15).above(15))) {
            helper.getLevel().getChunkAt(helper.absolutePos(pos));
            helper.setBlock(pos, Blocks.AIR);
        }
        shaft(helper, START, Direction.Axis.Z); shaft(helper, START.east(20), Direction.Axis.Z);
        helper.assertFalse(BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(START.east(20))), "20 blocks is exclusive range");
        shaft(helper, START.east(14).above(14), Direction.Axis.Z);
        helper.assertTrue(BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(START.east(14).above(14))), "Diagonal length below 20");
        shaft(helper, START.east(15).above(15), Direction.Axis.Z);
        helper.assertFalse(BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(START.east(15).above(15))), "Diagonal length above 20");
        shaft(helper, START.south(3), Direction.Axis.Z);
        helper.assertFalse(BeltConnectorItem.canConnect(helper.getLevel(), helper.absolutePos(START), helper.absolutePos(START.south(3))), "Cannot connect along shaft axis");
        helper.succeed();
    }
    @GameTest(maxTicks = 30)
    public void diagonalBeltTransmitsPowerToFarPulley(GameTestHelper helper) {
        var end = START.east(3).above(3);
        var belt = connect(helper, START, end, Direction.Axis.Z);
        helper.setBlock(START.north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
        shaft(helper, end.south(), Direction.Axis.Z);
        helper.runAfterDelay(8, () -> {
            helper.assertValueEqual(belt.getBlockState().getValue(BeltBlock.SLOPE), BeltSlope.UPWARD, "Ascending slope");
            helper.assertValueEqual(helper.getBlockEntity(end.south(), KineticBlockEntity.class).getSpeed(), 16f, "Diagonal transmission ratio one");
            helper.succeed();
        });
    }
    @GameTest(maxTicks = 30)
    public void verticalAndSidewaysBeltsTransmitButRejectItems(GameTestHelper helper) {
        var vertical = connect(helper, START, START.above(3), Direction.Axis.Z);
        helper.setBlock(START.north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
        var second = START.south(4);
        var sideways = connect(helper, second, second.east(3), Direction.Axis.Y);
        helper.setBlock(second.below(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        helper.runAfterDelay(8, () -> {
            helper.assertValueEqual(helper.getBlockEntity(START.above(3), BeltBlockEntity.class).getSpeed(), 16f, "Vertical transmission");
            helper.assertValueEqual(helper.getBlockEntity(second.east(3), BeltBlockEntity.class).getSpeed(), 16f, "Sideways transmission");
            helper.assertTrue(vertical.storage(Direction.UP) == null && sideways.storage(Direction.UP) == null, "Only transporting slopes expose storage");
            helper.succeed();
        });
    }
    @GameTest
    public void beltTransactionsRollbackAndPreserveComponents(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var named = new ItemStack(Items.DIAMOND, 7); named.set(DataComponents.CUSTOM_NAME, Component.literal("Belt cargo"));
        helper.assertValueEqual(insert(belt, 1, named, false), 7L, "Aborted insertion accepted");
        helper.assertValueEqual(count(belt, Items.DIAMOND), 0L, "Aborted insertion reverted");
        helper.assertValueEqual(insert(belt, 1, named, true), 7L, "Committed insertion");
        var storage = belt.getInventory().segment(1, Direction.UP);
        var view = storage.iterator().next();
        try (Transaction tx = Transaction.openOuter()) { helper.assertValueEqual(view.extract(ItemVariant.of(named), 7, tx), 7L, "Aborted full extraction"); }
        helper.assertValueEqual(view.getAmount(), 7L, "Stable view restored after rollback");
        helper.assertTrue(view.getResource().toStack().get(DataComponents.CUSTOM_NAME).getString().equals("Belt cargo"), "Components retained");
        try (Transaction tx = Transaction.openOuter()) { view.extract(ItemVariant.of(named), 3, tx); tx.commit(); }
        helper.assertValueEqual(count(belt, Items.DIAMOND), 4L, "Exact committed extraction");
        helper.succeed();
    }
    @GameTest(maxTicks = 70)
    public void stoppedBeltRetainsCargoAndEnforcesSpacing(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        insert(belt, 1, new ItemStack(Items.DIAMOND, 3), true);
        helper.assertValueEqual(insert(belt, 1, new ItemStack(Items.IRON_INGOT), true), 0L, "Occupied segment rejects insertion");
        helper.assertValueEqual(insert(belt, 3, new ItemStack(Items.IRON_INGOT), true), 1L, "Distant segment accepts insertion");
        float at = belt.getInventory().getTransportedItems().getFirst().beltPosition;
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(belt.getInventory().getTransportedItems().getFirst().beltPosition, at, "Unpowered belt does not move");
            helper.assertValueEqual(count(belt, Items.DIAMOND), 3L, "Cargo retained"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 80)
    public void reversingDriveReversesCargoMovement(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        insert(belt, 2, new ItemStack(Items.DIAMOND), true);
        float initial = belt.getInventory().getTransportedItems().getFirst().beltPosition;
        var position = new float[1];
        helper.runAfterDelay(20, () -> {
            position[0] = belt.getInventory().getTransportedItems().getFirst().beltPosition;
            helper.assertTrue(position[0] > initial, "Negative RPM moves east");
            helper.getBlockEntity(START.north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(belt.getInventory().getTransportedItems().getFirst().beltPosition < position[0], "Reversal moves west"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 180)
    public void beltTransfersCargoToEndpointStorage(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        helper.setBlock(END.east(), AllBlocks.DEPOT);
        var depot = helper.getBlockEntity(END.east(), DepotBlockEntity.class);
        insert(belt, 0, new ItemStack(Items.IRON_INGOT, 13), true);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(depot, Items.IRON_INGOT), 13L, "Whole stack arrives at endpoint");
            helper.assertValueEqual(count(belt, Items.IRON_INGOT), 0L, "No duplicate on belt");
        });
    }
    @GameTest(maxTicks = 100)
    public void blockedEndRetainsCargoUntilObstacleIsRemoved(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        helper.setBlock(END.east(), Blocks.STONE);
        insert(belt, 4, new ItemStack(Items.DIAMOND, 5), true);
        helper.runAfterDelay(35, () -> {
            helper.assertValueEqual(count(belt, Items.DIAMOND), 5L, "Solid end retains cargo");
            helper.assertTrue(belt.getInventory().getTransportedItems().getFirst().beltPosition < 5, "Stopped before outside block");
            helper.setBlock(END.east(), AllBlocks.DEPOT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 35, "Wait for end to open");
            helper.assertValueEqual(count(helper.getBlockEntity(END.east(), DepotBlockEntity.class), Items.DIAMOND), 5L, "Cargo exits after unblocking");
        });
    }
    @GameTest(maxTicks = 100)
    public void runningBeltPicksUpDroppedStack(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        var pos = helper.absolutePos(START.east(2));
        var entity = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 0.83, pos.getZ() + 0.5, new ItemStack(Items.DIAMOND, 9), 0, 0, 0);
        helper.getLevel().addFreshEntity(entity);
        helper.succeedWhen(() -> {
            helper.assertFalse(entity.isAlive(), "Entity picked up");
            helper.assertValueEqual(count(belt, Items.DIAMOND), 9L, "Cargo enters shared belt inventory");
        });
    }
    @GameTest
    public void beltSaveRestoresControllerInventoryAndComponents(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var named = new ItemStack(Items.DIAMOND, 4); named.set(DataComponents.CUSTOM_NAME, Component.literal("Saved cargo"));
        insert(belt, 2, named, true);
        var item = belt.getInventory().getTransportedItems().getFirst();
        item.heldBy = helper.absolutePos(START.east(2).above(2)); item.processedBy = com.simibubi.create.content.kinetics.fan.processing.FanProcessing.Type.SPLASHING; item.processingTime = 81;
        var saved = belt.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (BeltBlockEntity) BlockEntity.loadStatic(helper.absolutePos(START), belt.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored != null && restored.isController(), "Controller restored");
        var cargo = restored.getInventory().getTransportedItems().getFirst();
        helper.assertTrue(ItemStack.isSameItemSameComponents(cargo.stack, named) && cargo.stack.getCount() == 4, "Saved components and amount");
        helper.assertValueEqual(cargo.id, item.id, "Cargo identity retained");
        helper.assertValueEqual(cargo.heldBy, item.heldBy, "Press hold retained");
        helper.assertValueEqual(cargo.processingTime, 81, "Fan progress retained"); helper.succeed();
    }
    @GameTest(maxTicks = 30)
    public void breakingMiddleRestoresPulleysAndEjectsOnce(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        insert(belt, 1, new ItemStack(Items.DIAMOND, 11), true);
        helper.getLevel().destroyBlock(helper.absolutePos(START.east(2)), true);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(AllBlocks.SHAFT, START); helper.assertBlockPresent(AllBlocks.SHAFT, END);
            helper.assertBlockPresent(Blocks.AIR, START.east()); helper.assertBlockPresent(Blocks.AIR, START.east(3));
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(START)).inflate(7));
            helper.assertValueEqual(entities.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum(), 11, "Cargo ejected exactly once");
            helper.assertValueEqual(entities.stream().filter(e -> e.getItem().is(AllItems.BELT_CONNECTOR)).mapToInt(e -> e.getItem().getCount()).sum(), 1, "One connector recovered");
            helper.assertValueEqual(entities.stream().filter(e -> e.getItem().is(AllBlocks.SHAFT.asItem())).mapToInt(e -> e.getItem().getCount()).sum(), 0, "Other pulleys restored in place"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 260)
    public void pressProcessesStackOnMovingBeltAndReleasesResults(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        var press = START.east(2).above(2);
        helper.setBlock(press, AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING, Direction.EAST));
        helper.setBlock(press.west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        var motor = helper.getBlockEntity(press.west(), KineticBlockEntity.class);
        for (int i = 0; i < 4; i++) motor.adjustGeneratedSpeed(false);
        // Source ordinary depots reject another batch while occupied; a real barrel receives every cycle.
        helper.setBlock(END.east(), Blocks.BARREL);
        var destination = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(END.east()), Direction.WEST);
        helper.assertTrue(destination != null, "Actual barrel insertion provider exists");
        helper.assertValueEqual(insert(belt, 1, new ItemStack(Items.IRON_INGOT, 3), true), 3L, "Exactly three original ingots enter the real belt");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(destination, AllItems.IRON_SHEET), 3L, "All three ingots pressed and released");
            helper.assertValueEqual(count(belt, Items.IRON_INGOT), 0L, "No unprocessed ingots remain");
            helper.assertValueEqual(count(belt, AllItems.IRON_SHEET), 0L, "Every processed result has left the belt");
            helper.assertValueEqual(count(destination, Items.IRON_INGOT), 0L, "The real destination receives no unprocessed material");
            helper.assertFalse(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(START)).expandTowards(5, 2, 0).inflate(.25)).stream().anyMatch(entity -> entity.getItem().is(Items.IRON_INGOT) || entity.getItem().is(AllItems.IRON_SHEET)), "All original material stays in the physical belt and barrel owners");
        });
    }
    @GameTest(maxTicks = 240)
    public void fanWashesCargoOnStationaryBelt(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var fan = START.east(2).south(2).above();
        helper.setBlock(fan, AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING, Direction.NORTH));
        helper.setBlock(fan.south(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        helper.setBlock(fan.north(), Blocks.WATER);
        insert(belt, 2, new ItemStack(AllItems.WHEAT_FLOUR, 3), true);
        float position = belt.getInventory().getTransportedItems().getFirst().beltPosition;
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(belt, AllItems.DOUGH), 3L, "Air current washes entire belt stack");
            helper.assertValueEqual(count(belt, AllItems.WHEAT_FLOUR), 0L, "Flour consumed once");
            helper.assertValueEqual(belt.getInventory().getTransportedItems().getFirst().beltPosition, position, "Processing does not move cargo on stopped belt");
        });
    }
    @GameTest(maxTicks = 30)
    public void multipleFansCannotDecrementBeltProgressTwicePerTick(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var input = new ItemStack(AllItems.WHEAT_FLOUR);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("Shared fan input"));
        insert(belt, 2, input, true);
        var segment = helper.getBlockEntity(START.east(2), BeltBlockEntity.class);
        var cargo = belt.getInventory().getTransportedItems().getFirst();
        // Source transported-stack processing reserves the initialization call and stores duration + 1.
        int initialTime = com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fanProcessingTime.get() + 1;
        FanProcessing.process(segment, FanProcessing.Type.SPLASHING);
        helper.assertValueEqual(cargo.processingTime, initialTime, "Source initialization reserves its own call");
        FanProcessing.process(segment, FanProcessing.Type.SPLASHING);
        helper.assertValueEqual(cargo.processingTime, initialTime, "Second fan cannot decrement on initialization tick");
        helper.assertTrue(ItemStack.isSameItemSameComponents(cargo.stack, input) && count(belt, AllItems.WHEAT_FLOUR) == 1L, "Initialization retains exactly one original input with components");
        helper.runAfterDelay(2, () -> {
            FanProcessing.process(segment, FanProcessing.Type.SPLASHING);
            helper.assertValueEqual(cargo.processingTime, initialTime - 1, "First fan advances one processing tick");
            FanProcessing.process(segment, FanProcessing.Type.SPLASHING);
            helper.assertValueEqual(cargo.processingTime, initialTime - 1, "Two fans provide one processing tick");
            helper.assertTrue(belt.getInventory().getTransportedItems().size() == 1 && belt.getInventory().getTransportedItems().getFirst() == cargo && ItemStack.isSameItemSameComponents(cargo.stack, input) && count(belt, AllItems.WHEAT_FLOUR) == 1L, "Repeated fan calls retain the original cargo owner and exact components");
            helper.succeed();
        });
    }
    @GameTest(maxTicks = 70)
    public void basinExportsOutputToLowerBelt(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var basinPos = START.east(2).south().above();
        helper.setBlock(basinPos, AllBlocks.BASIN);
        var basin = helper.getBlockEntity(basinPos, BasinBlockEntity.class);
        try (Transaction tx = Transaction.openOuter()) {
            basin.inventory.automation.insert(ItemVariant.of(Items.DIAMOND), 2, tx);
            basin.inventory.insertOutput(new ItemStack(AllItems.BRASS_INGOT, 3), tx); tx.commit();
        }
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(belt, AllItems.BRASS_INGOT), 3L, "Basin output reaches lower belt");
            helper.assertValueEqual(count(belt, Items.DIAMOND), 0L, "Basin input stays in basin");
        });
    }
    @GameTest
    public void dyeColorsEntireBeltAndWaterClearsIt(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var middle = helper.getBlockEntity(START.east(2), BeltBlockEntity.class);
        helper.assertTrue(middle.applyColor(DyeColor.BLUE), "Color applied from any segment");
        helper.assertFalse(belt.applyColor(DyeColor.BLUE), "Already matching color makes no change");
        for (int i = 0; i < 5; i++) helper.assertValueEqual(helper.getBlockEntity(START.east(i), BeltBlockEntity.class).getColor(), DyeColor.BLUE, "All segments recolored");
        var saved = belt.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (BeltBlockEntity) BlockEntity.loadStatic(helper.absolutePos(START), belt.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.getColor(), DyeColor.BLUE, "Color survives save");
        helper.assertTrue(middle.applyColor(null), "Water clears dye");
        for (int i = 0; i < 5; i++) helper.assertTrue(helper.getBlockEntity(START.east(i), BeltBlockEntity.class).getColor() == null, "Dye cleared across chain");
        helper.succeed();
    }
    private static net.minecraft.world.entity.player.Player player(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }
    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, boolean positive) {
        var absolute = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(absolute).add(positive ? 0.3 : -0.3, 0.4, 0), Direction.UP, absolute, false);
    }
    private static long playerCount(net.minecraft.world.entity.player.Player player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum();
    }
    @GameTest(maxTicks = 30)
    public void extendingControllerRetainsWorldCargoPositionAndColor(GameTestHelper helper) {
        var belt = horizontal(helper, false); belt.applyColor(DyeColor.RED);
        insert(belt, 2, new ItemStack(Items.DIAMOND, 7), true);
        var cargo = belt.getInventory().getTransportedItems().getFirst();
        var before = BeltHelper.getVectorForOffset(belt, cargo.beltPosition);
        var player = player(helper); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.BELT_CONNECTOR));
        var result = AllItems.BELT_CONNECTOR.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, START, false)));
        helper.assertValueEqual(result, InteractionResult.SUCCESS, "Connector extends start");
        var controller = helper.getBlockEntity(START.west(), BeltBlockEntity.class);
        helper.assertTrue(controller.chainLoaded() && controller.beltLength == 6, "New controller initialized");
        var restored = controller.getInventory().getTransportedItems().getFirst();
        helper.assertValueEqual(restored.id, cargo.id, "Cargo identity kept");
        helper.assertTrue(BeltHelper.getVectorForOffset(controller, restored.beltPosition).distanceTo(before) < 1e-6, "Cargo stays in same world position");
        helper.assertValueEqual(controller.getColor(), DyeColor.RED, "Extended segment inherits dye");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(controller.chainLoaded(), "Scheduled updates retain edited chain");
            helper.assertValueEqual(count(controller, Items.DIAMOND), 7L, "No cargo duplicated or ejected"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 30)
    public void shorteningStartMovesControllerAndOnlyEjectsRemovedCargo(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        insert(belt, 0, new ItemStack(Items.IRON_INGOT, 3), true);
        insert(belt, 2, new ItemStack(Items.DIAMOND, 7), true);
        var retained = belt.getInventory().getTransportedItems().stream().filter(s -> s.stack.is(Items.DIAMOND)).findFirst().orElseThrow();
        var before = BeltHelper.getVectorForOffset(belt, retained.beltPosition);
        var player = player(helper); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH));
        AllItems.WRENCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, START, false)));
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(Blocks.AIR, START);
            var controller = helper.getBlockEntity(START.east(), BeltBlockEntity.class);
            helper.assertTrue(controller.chainLoaded() && controller.beltLength == 4, "Shortened chain initialized");
            var cargo = controller.getInventory().getTransportedItems().getFirst();
            helper.assertTrue(BeltHelper.getVectorForOffset(controller, cargo.beltPosition).distanceTo(before) < 1e-6, "Retained cargo stays put");
            helper.assertValueEqual(count(controller, Items.DIAMOND), 7L, "Retained stack unchanged");
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(START)).inflate(5));
            helper.assertValueEqual(entities.stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).mapToInt(e -> e.getItem().getCount()).sum(), 3, "Only cargo on removed segment ejected"); helper.succeed();
        });
    }
    @GameTest
    public void splitRequiresResourcesWithoutPartialConsumption(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var player = player(helper); player.getInventory().setItem(1, new ItemStack(AllItems.BELT_CONNECTOR));
        player.getInventory().setItem(2, new ItemStack(AllBlocks.SHAFT));
        var result = BeltSlicer.useWrench(helper.getLevel(), helper.absolutePos(START.east(2)), player, hit(helper, START.east(2), true));
        helper.assertValueEqual(result, InteractionResult.FAIL, "Missing second shaft prevents split");
        helper.assertValueEqual(playerCount(player, AllItems.BELT_CONNECTOR), 1L, "Connector not consumed");
        helper.assertValueEqual(playerCount(player, AllBlocks.SHAFT.asItem()), 1L, "First shaft not consumed");
        helper.assertTrue(belt.chainLoaded() && belt.beltLength == 5, "Original belt intact"); helper.succeed();
    }
    @GameTest(maxTicks = 30)
    public void splitThenMergeTransfersCargoAndRefundsExtraHardware(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        insert(belt, 1, new ItemStack(Items.IRON_INGOT, 4), true);
        insert(belt, 4, new ItemStack(Items.DIAMOND, 7), true);
        var player = player(helper); player.getInventory().setItem(1, new ItemStack(AllItems.BELT_CONNECTOR));
        player.getInventory().setItem(2, new ItemStack(AllBlocks.SHAFT, 2));
        var cut = START.east(2);
        helper.assertValueEqual(BeltSlicer.useWrench(helper.getLevel(), helper.absolutePos(cut), player, hit(helper, cut, true)), InteractionResult.SUCCESS, "Split succeeds");
        var right = helper.getBlockEntity(START.east(3), BeltBlockEntity.class);
        helper.assertTrue(belt.chainLoaded() && belt.beltLength == 3 && right.chainLoaded() && right.beltLength == 2, "Two valid independent chains");
        helper.assertValueEqual(count(belt, Items.IRON_INGOT), 4L, "Left cargo retained");
        helper.assertValueEqual(count(right, Items.DIAMOND), 7L, "Right cargo transferred");
        helper.assertValueEqual(playerCount(player, AllItems.BELT_CONNECTOR), 0L, "Extra connector used");
        helper.assertValueEqual(playerCount(player, AllBlocks.SHAFT.asItem()), 0L, "Two new pulleys used");
        helper.assertValueEqual(BeltSlicer.useConnector(helper.getLevel(), helper.absolutePos(cut), player, hit(helper, cut, true)), InteractionResult.SUCCESS, "Merge succeeds");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(belt.chainLoaded() && belt.beltLength == 5, "Original chain restored");
            helper.assertValueEqual(count(belt, Items.IRON_INGOT), 4L, "Left cargo once");
            helper.assertValueEqual(count(belt, Items.DIAMOND), 7L, "Right cargo once");
            helper.assertValueEqual(playerCount(player, AllItems.BELT_CONNECTOR), 1L, "Extra connector refunded");
            helper.assertValueEqual(playerCount(player, AllBlocks.SHAFT.asItem()), 2L, "Two redundant pulleys refunded"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 30)
    public void mergingOppositeBeltsReversesInventoryCoordinates(GameTestHelper helper) {
        var belt = connect(helper, START, START.east(2), Direction.Axis.Z);
        var other = connect(helper, START.east(5), START.east(3), Direction.Axis.Z);
        insert(other, 1, new ItemStack(Items.DIAMOND, 8), true);
        var before = BeltHelper.getVectorForOffset(other, other.getInventory().getTransportedItems().getFirst().beltPosition);
        var player = player(helper);
        BeltSlicer.useConnector(helper.getLevel(), helper.absolutePos(START.east(2)), player, hit(helper, START.east(2), true));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(belt.chainLoaded() && belt.beltLength == 6, "Opposite chain flipped and joined");
            var cargo = belt.getInventory().getTransportedItems().getFirst();
            helper.assertValueEqual(count(belt, Items.DIAMOND), 8L, "Cargo retained once");
            helper.assertTrue(BeltHelper.getVectorForOffset(belt, cargo.beltPosition).distanceTo(before) < 1e-6, "Reverse coordinate keeps world position"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 60)
    public void movingCargoInvalidatesOldSegmentStorageView(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        insert(belt, 1, new ItemStack(Items.DIAMOND, 6), true);
        var view = belt.getInventory().segment(1, Direction.UP).iterator().next();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(view.isResourceBlank(), "View no longer exposes cargo after it leaves segment");
            try (Transaction tx = Transaction.openOuter()) {
                helper.assertValueEqual(view.extract(ItemVariant.of(Items.DIAMOND), 6, tx), 0L, "Cannot extract cargo remotely through old segment view");
            }
            helper.assertValueEqual(count(belt, Items.DIAMOND), 6L, "Cargo remains on actual segment"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 180)
    public void stoppingBeltDoesNotInterruptIndependentlyPoweredPress(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        var press = START.east(2).above(2);
        helper.setBlock(press, AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING, Direction.EAST));
        helper.setBlock(press.west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        insert(belt, 2, new ItemStack(Items.IRON_INGOT), true);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(helper.getBlockEntity(press, com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity.class).isRunning(), "Arrival immediately starts press");
            helper.setBlock(START.north(), Blocks.AIR);
        });
        helper.runAfterDelay(150, () -> {
            helper.assertValueEqual(belt.getSpeed(), 0f, "Belt stopped");
            helper.assertValueEqual(count(belt, AllItems.IRON_SHEET), 1L, "Independent press completes even on stopped belt");
            helper.assertValueEqual(count(belt, Items.IRON_INGOT), 0L, "Input consumed once"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 40)
    public void conveyorMovesLivingEntities(GameTestHelper helper) {
        horizontal(helper, true);
        var pos = START.east(2);
        var pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, Vec3.atLowerCornerOf(pos).add(0.5, 0.9, 0.5));
        pig.setNoAi(true);
        double initial = pig.getX();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(pig.getX() > initial + 0.05, "Conveyor moves grounded living entity east"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 60)
    public void crossingSegmentBoundaryMovesPassengerOnlyOncePerTick(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        var entity = helper.spawn(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, Vec3.atLowerCornerOf(START.east()).add(0.5, 0.82, 0.5));
        entity.setNoGravity(true);
        double[] previous = {entity.getX()};
        helper.onEachTick(() -> {
            double moved = entity.getX() - previous[0]; previous[0] = entity.getX();
            helper.assertTrue(moved <= 16.0 / 480 + 1e-5, "One movement step per tick even across segment boundary: " + moved);
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(belt.getSpeed() != 0 && entity.getX() > helper.absolutePos(START.east(2)).getX(), "Passenger crossed into next segment"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 80)
    public void inclinedBeltCarriesLivingEntityUphill(GameTestHelper helper) {
        var belt = connect(helper, START, START.east(3).above(3), Direction.Axis.Z);
        helper.setBlock(START.north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
        helper.getBlockEntity(START.north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        var pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, Vec3.atLowerCornerOf(START).add(0.5, 0.84, 0.5)); pig.setNoAi(true);
        double initialY = pig.getY(), initialX = pig.getX();
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(belt.getSpeed() != 0, "Inclined belt powered");
            helper.assertTrue(pig.getX() > initialX + 0.5 && pig.getY() > initialY + 0.5,
                    "Entity climbs inclined belt: displacement " + (pig.getX() - initialX) + ", " + (pig.getY() - initialY)); helper.succeed();
        });
    }
    @GameTest(maxTicks = 80)
    public void beltRetainsRemainderWhenEndpointOnlyAcceptsPartialStack(GameTestHelper helper) {
        var belt = horizontal(helper, true);
        // A vanilla barrel supplies exactly four free matching slots; ordinary source depots do not merge.
        helper.setBlock(END.east(), Blocks.BARREL);
        var barrel = helper.getBlockEntity(END.east(), net.minecraft.world.level.block.entity.BarrelBlockEntity.class);
        var destination = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(END.east()), Direction.WEST);
        helper.setBlock(END.east().south(2), Blocks.BARREL);
        var receipt = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(END.east().south(2)), Direction.UP);
        helper.assertTrue(destination != null && receipt != null, "Both physical barrel providers exist");
        long filler = (barrel.getContainerSize() - 1) * 64L;
        try (Transaction tx = Transaction.openOuter()) {
            helper.assertValueEqual(destination.insert(ItemVariant.of(Items.IRON_INGOT), 60, tx), 60L, "Original destination stock seeded");
            helper.assertValueEqual(destination.insert(ItemVariant.of(Items.DIAMOND), filler, tx), filler, "Every other real slot occupied");
            tx.commit();
        }
        try (Transaction tx = Transaction.openOuter()) {
            helper.assertValueEqual(destination.insert(ItemVariant.of(Items.IRON_INGOT), 10, tx), 4L, "Real destination has exactly four free matching item spaces");
        }
        helper.assertValueEqual(count(destination, Items.IRON_INGOT), 60L, "Capacity probe abort restores all original destination stock");
        helper.assertValueEqual(insert(belt, 4, new ItemStack(Items.IRON_INGOT, 10), true), 10L, "Exactly ten original cargo ingots enter the belt");
        var cargoId = belt.getInventory().getTransportedItems().getFirst().id;
        helper.runAfterDelay(30, () -> {
            helper.assertValueEqual(count(belt, Items.IRON_INGOT), 6L, "Only four items fit at destination");
            helper.assertValueEqual(count(destination, Items.IRON_INGOT), 64L, "No overfilled stack");
            helper.assertValueEqual(belt.getInventory().getTransportedItems().getFirst().id, cargoId, "Partial insertion retains the original cargo owner");
            helper.assertValueEqual(count(destination, Items.DIAMOND), filler, "Unrelated real stock remains unchanged");
            try (Transaction tx = Transaction.openOuter()) {
                helper.assertValueEqual(destination.extract(ItemVariant.of(Items.IRON_INGOT), 64, tx), 64L, "Real filled stack leaves destination");
                helper.assertValueEqual(receipt.insert(ItemVariant.of(Items.IRON_INGOT), 64, tx), 64L, "The entire removed stock changes to a second physical owner");
                tx.commit();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 30, "Wait for destination space");
            helper.assertValueEqual(count(belt, Items.IRON_INGOT), 0L, "Remainder transferred after space available");
            helper.assertValueEqual(count(destination, Items.IRON_INGOT), 6L, "Exactly remaining cargo transferred");
            helper.assertValueEqual(count(receipt, Items.IRON_INGOT), 64L, "Every withdrawn item remains in its actual receipt owner");
            helper.assertValueEqual(count(destination, Items.DIAMOND), filler, "All unrelated stock survives the complete transfer");
        });
    }
    @GameTest(maxTicks = 80)
    public void inclinedBeltOutputsAtEndPulleyHeight(GameTestHelper helper) {
        var end = START.east(3).above(3);
        var belt = connect(helper, START, end, Direction.Axis.Z);
        helper.setBlock(START.north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
        helper.getBlockEntity(START.north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        helper.setBlock(end.east(), AllBlocks.DEPOT);
        var depot = helper.getBlockEntity(end.east(), DepotBlockEntity.class);
        insert(belt, 3, new ItemStack(Items.DIAMOND, 3), true);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(depot, Items.DIAMOND), 3L, "Slope output uses pulley level without extra vertical step");
            helper.assertValueEqual(count(belt, Items.DIAMOND), 0L, "Slope cargo consumed once");
        });
    }
    @GameTest
    public void casingMaterialSurvivesSaveAndWrenchRemovesOnlyCasing(GameTestHelper helper) {
        var belt = horizontal(helper, false);
        var pos = START.east(2); var absolute = helper.absolutePos(pos);
        var player = player(helper); var casing = new ItemStack(AllBlocks.BRASS_CASING);
        player.setItemInHand(InteractionHand.MAIN_HAND, casing);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0, -0.45), Direction.NORTH, absolute, false);
        helper.getBlockState(pos).useItemOn(casing, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        var segment = helper.getBlockEntity(pos, BeltBlockEntity.class);
        helper.assertTrue(helper.getBlockState(pos).getValue(BeltBlock.CASING), "Casing property set");
        helper.assertValueEqual(segment.getCasingType(), BeltBlockEntity.CasingType.BRASS, "Brass material applied");
        helper.assertValueEqual(casing.getCount(), 1, "Applying casing does not consume block");
        var saved = segment.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (BeltBlockEntity) BlockEntity.loadStatic(absolute, segment.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.getCasingType(), BeltBlockEntity.CasingType.BRASS, "Material persisted");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH));
        AllItems.WRENCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertFalse(helper.getBlockState(pos).getValue(BeltBlock.CASING), "Wrench removes casing");
        helper.assertTrue(belt.chainLoaded() && belt.beltLength == 5, "Wrench leaves chain intact"); helper.succeed();
    }
}

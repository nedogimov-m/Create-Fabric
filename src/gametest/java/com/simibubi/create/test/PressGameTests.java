package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class PressGameTests {
    private static final BlockPos PRESS = new BlockPos(3, 4, 3);

    private static DepotBlockEntity setup(GameTestHelper helper, int inputCount) {
        helper.setBlock(PRESS.west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.setBlock(PRESS, AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING, Direction.EAST));
        helper.setBlock(PRESS.below(2), AllBlocks.DEPOT);
        var depot = helper.getBlockEntity(PRESS.below(2), DepotBlockEntity.class);
        try (Transaction transaction = Transaction.openOuter()) {
            depot.inventory.automation.insert(ItemVariant.of(Items.IRON_INGOT), inputCount, transaction);
            transaction.commit();
        }
        return depot;
    }

    private static long extract(DepotBlockEntity depot, ItemVariant variant, boolean commit) {
        try (Transaction transaction = Transaction.openOuter()) {
            long amount = depot.inventory.automation.extract(variant, 64, transaction);
            if (commit) transaction.commit();
            return amount;
        }
    }

    @GameTest(maxTicks = 130)
    public void pressProcessesOneItemAndKeepsRemainingInput(GameTestHelper helper) {
        var depot = setup(helper, 3);
        helper.runAfterDelay(85, () -> {
            helper.assertValueEqual(extract(depot, ItemVariant.of(AllItems.IRON_SHEET), false), 1L, "One plate per cycle");
            helper.assertValueEqual(extract(depot, ItemVariant.of(Items.IRON_INGOT), false), 2L, "Unprocessed ingots retained");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void removingPowerPausesPressWithoutConsumingInput(GameTestHelper helper) {
        var depot = setup(helper, 1);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(helper.getBlockEntity(PRESS, MechanicalPressBlockEntity.class).isRunning(), "Press started");
            helper.setBlock(PRESS.west(), Blocks.AIR);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertValueEqual(extract(depot, ItemVariant.of(Items.IRON_INGOT), false), 1L, "Paused press retains input");
            helper.assertValueEqual(extract(depot, ItemVariant.of(AllItems.IRON_SHEET), false), 0L, "Paused press makes no plates");
            helper.setBlock(PRESS.west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 90, "Waiting for power restoration");
            helper.assertValueEqual(extract(depot, ItemVariant.of(AllItems.IRON_SHEET), false), 1L, "Press resumes after restoring power");
        });
    }

    @GameTest(maxTicks = 100)
    public void depotTransactionAbortRetainsItemComponents(GameTestHelper helper) {
        var depot = setup(helper, 1);
        helper.assertValueEqual(extract(depot, ItemVariant.of(Items.IRON_INGOT), false), 1L, "Simulated extraction");
        helper.assertValueEqual(extract(depot, ItemVariant.of(Items.IRON_INGOT), true), 1L, "Actual extraction");
        helper.assertValueEqual(extract(depot, ItemVariant.of(Items.IRON_INGOT), false), 0L, "No duplicated item");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void invalidDepotInputDoesNotStartPress(GameTestHelper helper) {
        var depot = setup(helper, 0);
        try (Transaction transaction = Transaction.openOuter()) {
            depot.inventory.automation.insert(ItemVariant.of(Items.DIAMOND), 1, transaction);
            transaction.commit();
        }
        helper.runAfterDelay(30, () -> {
            helper.assertFalse(helper.getBlockEntity(PRESS, MechanicalPressBlockEntity.class).isRunning(), "Diamond is not pressable");
            helper.assertValueEqual(extract(depot, ItemVariant.of(Items.DIAMOND), false), 1L, "Invalid input preserved");
            helper.succeed();
        });
    }
}

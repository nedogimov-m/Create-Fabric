package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MillstoneGameTests {
    private static final BlockPos MILL = new BlockPos(2, 3, 2);

    private static MillstoneBlockEntity placeMill(GameTestHelper helper, boolean powered) {
        if (powered) {
            helper.setBlock(MILL.below(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        }
        helper.setBlock(MILL, AllBlocks.MILLSTONE);
        return helper.getBlockEntity(MILL, MillstoneBlockEntity.class);
    }

    private static void insert(MillstoneBlockEntity mill, ItemVariant item, int count) {
        try (Transaction transaction = Transaction.openOuter()) {
            if (mill.inventory.automation.insert(item, count, transaction) != count) throw new AssertionError("Insert failed");
            transaction.commit();
        }
    }

    @GameTest
    public void millingRecipesLoad(GameTestHelper helper) {
        long recipes = helper.getLevel().getServer().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.value().getType() == AllRecipeTypes.MILLING).count();
        helper.assertValueEqual(recipes, 47L, "All base milling recipes");
        helper.succeed();
    }

    @GameTest
    public void automationRejectsInvalidInputAndCannotExtractUnprocessedItems(GameTestHelper helper) {
        var mill = placeMill(helper, false);
        var storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(MILL), Direction.UP);
        helper.assertTrue(storage != null, "Fabric sided storage lookup");
        try (Transaction transaction = Transaction.openOuter()) {
            helper.assertValueEqual(storage.insert(ItemVariant.of(Items.DIAMOND), 1, transaction), 0L, "Invalid input");
            helper.assertValueEqual(storage.insert(ItemVariant.of(Items.GRAVEL), 1, transaction), 1L, "Valid input");
            helper.assertValueEqual(storage.extract(ItemVariant.of(Items.GRAVEL), 1, transaction), 0L, "Input is not an output");
            transaction.commit();
        }
        helper.assertValueEqual(mill.inventory.inputs.getFirst().getAmount(), 1L, "Committed input");
        helper.succeed();
    }

    @GameTest
    public void abortedInsertDoesNotChangeInventory(GameTestHelper helper) {
        var mill = placeMill(helper, false);
        try (Transaction transaction = Transaction.openOuter()) {
            mill.inventory.automation.insert(ItemVariant.of(Items.GRAVEL), 16, transaction);
        }
        helper.assertTrue(mill.inventory.inputs.getFirst().isResourceBlank(), "Aborted transaction restores empty slot");
        helper.succeed();
    }

    @GameTest(maxTicks = 300)
    public void poweredMillProcessesGravel(GameTestHelper helper) {
        var mill = placeMill(helper, true);
        insert(mill, ItemVariant.of(Items.GRAVEL), 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(mill.inventory.inputs.getFirst().isResourceBlank(), "Input consumed");
            helper.assertValueEqual(mill.inventory.outputs.getFirst().getAmount(), 1L, "Flint count");
            helper.assertTrue(mill.inventory.outputs.getFirst().getResource().matches(new net.minecraft.world.item.ItemStack(Items.FLINT)), "Flint output");
        });
    }

    @GameTest(maxTicks = 200)
    public void wheatAlwaysProducesAtLeastOneFlour(GameTestHelper helper) {
        var mill = placeMill(helper, true);
        insert(mill, ItemVariant.of(Items.WHEAT), 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(mill.inventory.inputs.getFirst().isResourceBlank(), "Wheat consumed");
            helper.assertTrue(mill.inventory.outputs.stream().anyMatch(slot -> slot.getResource().equals(ItemVariant.of(AllItems.WHEAT_FLOUR))
                    && slot.getAmount() >= 1), "Guaranteed flour output");
        });
    }

    @GameTest(maxTicks = 300)
    public void fullOutputDoesNotLoseInputOrDuplicateResults(GameTestHelper helper) {
        var mill = placeMill(helper, true);
        try (Transaction transaction = Transaction.openOuter()) {
            for (var slot : mill.inventory.outputs) slot.insert(ItemVariant.of(Items.DIAMOND), 64, transaction);
            transaction.commit();
        }
        insert(mill, ItemVariant.of(Items.GRAVEL), 1);
        helper.runAfterDelay(260, () -> {
            helper.assertValueEqual(mill.inventory.inputs.getFirst().getAmount(), 1L, "Blocked output preserves input");
            try (Transaction transaction = Transaction.openOuter()) {
                var slot = mill.inventory.outputs.getFirst();
                slot.extract(slot.getResource(), 64, transaction);
                transaction.commit();
            }
        });
        helper.runAfterDelay(270, () -> {
            helper.assertTrue(mill.inventory.inputs.getFirst().isResourceBlank(), "Processing resumes after output clears");
            helper.assertValueEqual(mill.inventory.outputs.getFirst().getAmount(), 1L, "Exactly one flint");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void inventoryAndProcessingProgressSurviveSerialization(GameTestHelper helper) {
        var mill = placeMill(helper, true);
        insert(mill, ItemVariant.of(Items.GRAVEL), 2);
        helper.runAfterDelay(8, () -> {
            var tag = mill.saveWithFullMetadata(helper.getLevel().registryAccess());
            var restored = (MillstoneBlockEntity) BlockEntity.loadStatic(helper.absolutePos(MILL), mill.getBlockState(),
                    tag, helper.getLevel().registryAccess());
            helper.assertTrue(restored != null, "Millstone block entity deserializes");
            helper.assertValueEqual(restored.inventory.inputs.getFirst().getAmount(), 2L, "Input count persists");
            helper.assertValueEqual(restored.getProcessingTimer(), mill.getProcessingTimer(), "Progress persists");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void removedMillDropsStoredInput(GameTestHelper helper) {
        var mill = placeMill(helper, false);
        insert(mill, ItemVariant.of(Items.GRAVEL), 3);
        helper.runAfterDelay(5, () -> helper.setBlock(MILL, Blocks.AIR));
        helper.runAfterDelay(8, () -> {
            helper.assertItemEntityCountIs(Items.GRAVEL, MILL, 2, 3);
            helper.succeed();
        });
    }
}

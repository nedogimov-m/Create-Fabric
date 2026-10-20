package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;

public class BasinGameTests {
    private static final BlockPos BASIN = new BlockPos(3, 2, 3);
    private static BasinBlockEntity basin(GameTestHelper helper) {
        helper.setBlock(BASIN, AllBlocks.BASIN);
        return helper.getBlockEntity(BASIN, BasinBlockEntity.class);
    }
    private static void insert(BasinBlockEntity basin, Item item, int count) {
        try (Transaction transaction = Transaction.openOuter()) {
            if (basin.inventory.automation.insert(ItemVariant.of(item), count, transaction) != count) throw new AssertionError("Could not insert input");
            transaction.commit();
        }
    }
    private static long count(BasinBlockEntity basin, Item item) {
        try (Transaction transaction = Transaction.openOuter()) { return basin.inventory.automation.extract(ItemVariant.of(item), 10000, transaction); }
    }
    private static void water(BasinBlockEntity basin, long amount) {
        try (Transaction transaction = Transaction.openOuter()) {
            if (basin.fluids.automation.insert(FluidVariant.of(Fluids.WATER), amount, transaction) != amount) throw new AssertionError("Could not insert fluid");
            transaction.commit();
        }
    }
    private static void mixer(GameTestHelper helper, boolean fast) {
        helper.setBlock(BASIN.above(2), AllBlocks.MECHANICAL_MIXER);
        helper.setBlock(BASIN.above(2).west(), AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Y));
        helper.setBlock(BASIN.above().west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        if (fast) helper.getBlockEntity(BASIN.above().west(), KineticBlockEntity.class).adjustGeneratedSpeed(false);
    }

    @GameTest(maxTicks = 300)
    public void mixerProducesAndesiteAlloyFromTwoIngredients(GameTestHelper helper) {
        var basin = basin(helper); mixer(helper, true);
        insert(basin, Items.ANDESITE, 2); insert(basin, Items.IRON_NUGGET, 2);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(basin, AllItems.ANDESITE_ALLOY), 2L, "Two mixing batches produce two alloy");
            helper.assertValueEqual(count(basin, Items.ANDESITE), 0L, "Andesite consumed once per batch");
            helper.assertValueEqual(count(basin, Items.IRON_NUGGET), 0L, "Nuggets consumed once per batch");
        });
    }
    @GameTest(maxTicks = 80)
    public void mixerRequiresMediumSpeed(GameTestHelper helper) {
        var basin = basin(helper); mixer(helper, false);
        insert(basin, Items.ANDESITE, 1); insert(basin, Items.IRON_NUGGET, 1);
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(helper.getBlockEntity(BASIN.above(2), MechanicalMixerBlockEntity.class).isRunning(), "16 RPM does not satisfy mixer speed requirement");
            helper.assertValueEqual(count(basin, Items.ANDESITE), 1L, "Slow mixer retains inputs"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 300)
    public void mixerConsumesOneFabricBucketPerDough(GameTestHelper helper) {
        var basin = basin(helper); mixer(helper, true);
        insert(basin, AllItems.WHEAT_FLOUR, 2); water(basin, FluidConstants.BUCKET);
        helper.runAfterDelay(210, () -> {
            helper.assertValueEqual(count(basin, AllItems.DOUGH), 1L, "One bucket makes one dough");
            helper.assertValueEqual(count(basin, AllItems.WHEAT_FLOUR), 1L, "Second flour waits for water");
            helper.assertTrue(basin.fluids.inputs.stream().allMatch(t -> t.isResourceBlank()), "81000 fluid units consumed"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 180)
    public void pressCompactsNineSnowBlocksInBasin(GameTestHelper helper) {
        var basin = basin(helper);
        helper.setBlock(BASIN.above(2), AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING, Direction.EAST));
        helper.setBlock(BASIN.above(2).west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        insert(basin, Items.SNOW_BLOCK, 9);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(basin, Items.ICE), 1L, "Press compacts 9 snow blocks");
            helper.assertValueEqual(count(basin, Items.SNOW_BLOCK), 0L, "All nine ingredients consumed");
        });
    }
    @GameTest
    public void fullOutputRollsBackItemsAndFluidTogether(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, AllItems.WHEAT_FLOUR, 1); water(basin, FluidConstants.BUCKET);
        try (Transaction transaction = Transaction.openOuter()) {
            basin.inventory.outputs.forEach(slot -> slot.insert(ItemVariant.of(Items.DIAMOND), 64, transaction)); transaction.commit();
        }
        var recipe = basin.findRecipe(AllRecipeTypes.MIXING);
        helper.assertTrue(recipe != null, "Dough recipe loaded");
        helper.assertFalse(basin.process(recipe.value(), true), "Output-full simulation fails");
        helper.assertFalse(basin.process(recipe.value(), false), "Output-full execution fails");
        helper.assertValueEqual(count(basin, AllItems.WHEAT_FLOUR), 1L, "Input rolls back");
        helper.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(), FluidConstants.BUCKET, "Fluid rolls back");
        helper.assertValueEqual(count(basin, Items.DIAMOND), 576L, "Existing output preserved"); helper.succeed();
    }
    @GameTest
    public void simulationAndExternalAbortDoNotChangeBasin(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, AllItems.WHEAT_FLOUR, 1); water(basin, FluidConstants.BUCKET);
        var recipe = basin.findRecipe(AllRecipeTypes.MIXING);
        helper.assertTrue(recipe != null && basin.process(recipe.value(), true), "Recipe is executable");
        try (Transaction transaction = Transaction.openOuter()) {
            basin.fluids.automation.extract(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, transaction);
            basin.inventory.automation.extract(ItemVariant.of(AllItems.WHEAT_FLOUR), 1, transaction);
        }
        helper.assertValueEqual(count(basin, AllItems.WHEAT_FLOUR), 1L, "Aborted extraction retains item");
        helper.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(), FluidConstants.BUCKET, "Aborted extraction retains fluid");
        helper.assertValueEqual(count(basin, AllItems.DOUGH), 0L, "Simulation creates no output"); helper.succeed();
    }
    @GameTest
    public void basinSavesBothFluidSegmentsAndItems(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, AllItems.WHEAT_FLOUR, 3); water(basin, 20250);
        try (Transaction transaction = Transaction.openOuter()) {
            basin.fluids.automation.insert(FluidVariant.of(Fluids.LAVA), 4050, transaction); transaction.commit();
        }
        var saved = basin.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (BasinBlockEntity) BlockEntity.loadStatic(helper.absolutePos(BASIN), basin.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(count(restored, AllItems.WHEAT_FLOUR), 3L, "Items persisted");
        helper.assertValueEqual(restored.fluids.inputs.getFirst().getAmount(), 20250L, "Water persisted");
        helper.assertValueEqual(restored.fluids.inputs.get(1).getResource(), FluidVariant.of(Fluids.LAVA), "Second fluid persisted");
        helper.assertValueEqual(restored.fluids.inputs.get(1).getAmount(), 4050L, "Second amount persisted"); helper.succeed();
    }
    @GameTest
    public void basinKeepsOneStackPerItemVariant(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, Items.ANDESITE, 64);
        try (Transaction transaction = Transaction.openOuter()) {
            helper.assertValueEqual(basin.inventory.automation.insert(ItemVariant.of(Items.ANDESITE), 1, transaction), 0L, "Unique ingredient stack limit");
            helper.assertValueEqual(basin.inventory.automation.insert(ItemVariant.of(Items.IRON_NUGGET), 64, transaction), 64L, "Other ingredients still fit");
            transaction.commit();
        }
        helper.assertValueEqual(count(basin, Items.ANDESITE), 64L, "No duplicate stack inserted"); helper.succeed();
    }
    @GameTest
    public void compactingConsumesOriginalMillibucketAmount(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, Items.FLINT, 2); insert(basin, Items.GRAVEL, 1);
        try (Transaction transaction = Transaction.openOuter()) {
            basin.fluids.automation.insert(FluidVariant.of(Fluids.LAVA), 81000, transaction); transaction.commit();
        }
        var recipe = basin.findRecipe(AllRecipeTypes.COMPACTING);
        helper.assertTrue(recipe != null && basin.process(recipe.value(), false), "Andesite compacting recipe loaded");
        helper.assertValueEqual(count(basin, Items.ANDESITE), 1L, "Andesite output");
        helper.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(), 72900L, "100 mB converts to 8100 Fabric units"); helper.succeed();
    }

    @GameTest
    public void basinAdaptsVanillaCompressionRecipe(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, Items.IRON_INGOT, 9);
        var recipe = basin.findRecipe(AllRecipeTypes.COMPACTING);
        helper.assertTrue(recipe != null && basin.process(recipe.value(), false), "Vanilla iron block compression available in press");
        helper.assertValueEqual(count(basin, Items.IRON_BLOCK), 1L, "Nine ingots compress into one block"); helper.succeed();
    }
    @GameTest
    public void basinAdaptsVanillaShapelessMixingRecipe(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, Items.DYE.red(), 1); insert(basin, Items.DYE.white(), 1);
        var recipe = basin.findRecipe(AllRecipeTypes.MIXING);
        helper.assertTrue(recipe != null && basin.process(recipe.value(), false), "Vanilla shapeless dye recipe is mixable");
        helper.assertValueEqual(count(basin, Items.DYE.pink()), 2L, "Mixer uses vanilla crafting output"); helper.succeed();
    }
    @GameTest(maxTicks = 60)
    public void basinExportsOutputsToLowerDepotWithoutExportingInputs(GameTestHelper helper) {
        var basin = basin(helper);
        helper.setBlock(BASIN.east().below(), AllBlocks.DEPOT);
        insert(basin, Items.DIAMOND, 1);
        try (Transaction transaction = Transaction.openOuter()) {
            basin.inventory.insertOutput(new net.minecraft.world.item.ItemStack(AllItems.IRON_SHEET, 3), transaction); transaction.commit();
        }
        helper.succeedWhen(() -> {
            helper.assertValueEqual(helper.getBlockState(BASIN).getValue(com.simibubi.create.content.processing.basin.BasinBlock.FACING), Direction.EAST, "Spout aims at depot");
            var depot = helper.getBlockEntity(BASIN.east().below(), com.simibubi.create.content.logistics.depot.DepotBlockEntity.class);
            try (Transaction transaction = Transaction.openOuter()) {
                helper.assertValueEqual(depot.inventory.automation.extract(ItemVariant.of(AllItems.IRON_SHEET), 64, transaction), 3L, "All outputs exported");
            }
            helper.assertValueEqual(count(basin, Items.DIAMOND), 1L, "Input remains in basin");
        });
    }
    @GameTest(maxTicks = 60)
    public void basinCanDisableSpoutAndRetainBlockedOutputs(GameTestHelper helper) {
        var basin = basin(helper); helper.setBlock(BASIN.east().below(), AllBlocks.DEPOT);
        helper.runAfterDelay(10, () -> {
            basin.onWrenched(Direction.EAST);
            try (Transaction transaction = Transaction.openOuter()) {
                basin.inventory.insertOutput(new net.minecraft.world.item.ItemStack(AllItems.IRON_SHEET, 3), transaction); transaction.commit();
            }
        });
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(count(basin, AllItems.IRON_SHEET), 3L, "Disabled spout retains output");
            helper.assertValueEqual(helper.getBlockState(BASIN).getValue(com.simibubi.create.content.processing.basin.BasinBlock.FACING), Direction.DOWN, "Spout disabled");
            var saved = basin.saveWithFullMetadata(helper.getLevel().registryAccess());
            var restored = (BasinBlockEntity) BlockEntity.loadStatic(helper.absolutePos(BASIN), basin.getBlockState(), saved, helper.getLevel().registryAccess());
            helper.assertValueEqual(restored.saveWithFullMetadata(helper.getLevel().registryAccess()).getListOrEmpty("DisabledSpoutput").size(), 1, "Disabled outlet persisted");
            helper.succeed();
        });
    }

    @GameTest
    public void bucketInteractionTransfersFluidWithoutDuplicatingContainers(GameTestHelper helper) {
        var basin = basin(helper);
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.WATER_BUCKET));
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(BASIN)), Direction.UP, helper.absolutePos(BASIN), false);
        helper.getBlockState(BASIN).useItemOn(player.getMainHandItem(), helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(), FluidConstants.BUCKET, "Water bucket empties into basin");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "One empty bucket returned");
        helper.getBlockState(BASIN).useItemOn(player.getMainHandItem(), helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(basin.fluids.inputs.stream().allMatch(t -> t.isResourceBlank()), "Bucket extracts the stored water");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET) && player.getMainHandItem().getCount() == 1, "Only one full bucket returned");
        helper.succeed();
    }

    @GameTest
    public void fullFluidOutputRollsBackConsumedItems(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, Items.DIRT, 1);
        try (Transaction transaction = Transaction.openOuter()) {
            helper.assertTrue(basin.fluids.insertOutput(FluidVariant.of(Fluids.LAVA), 2 * FluidConstants.BUCKET, transaction), "Output tanks filled"); transaction.commit();
        }
        var recipe = new com.simibubi.create.content.processing.basin.BasinRecipe(true,
                java.util.List.of(com.mojang.datafixers.util.Either.left(net.minecraft.world.item.crafting.Ingredient.of(Items.DIRT))),
                java.util.List.of(com.mojang.datafixers.util.Either.right(new com.simibubi.create.content.processing.recipe.FluidOutput(Fluids.LAVA, 81000, net.minecraft.core.component.DataComponentPatch.EMPTY))),
                com.simibubi.create.content.processing.recipe.HeatCondition.NONE, 100);
        helper.assertFalse(basin.process(recipe, false), "Full fluid output prevents processing");
        helper.assertValueEqual(count(basin, Items.DIRT), 1L, "Item consumption rolled back");
        helper.assertValueEqual(basin.fluids.outputs.stream().mapToLong(t -> t.getAmount()).sum(), 162000L, "Existing output fluid preserved"); helper.succeed();
    }

    @GameTest
    public void recipeRemainderIsReturnedInSameTransaction(GameTestHelper helper) {
        var basin = basin(helper); insert(basin, Items.MILK_BUCKET, 1);
        var recipe = new com.simibubi.create.content.processing.basin.BasinRecipe(true,
                java.util.List.of(com.mojang.datafixers.util.Either.left(net.minecraft.world.item.crafting.Ingredient.of(Items.MILK_BUCKET))),
                java.util.List.of(com.mojang.datafixers.util.Either.left(new com.simibubi.create.content.processing.recipe.ProcessingOutput(Items.SUGAR, 1, net.minecraft.core.component.DataComponentPatch.EMPTY, 1))),
                com.simibubi.create.content.processing.recipe.HeatCondition.NONE, 100);
        helper.assertTrue(basin.process(recipe, false), "Item recipe processed");
        helper.assertValueEqual(count(basin, Items.MILK_BUCKET), 0L, "Input consumed");
        helper.assertValueEqual(count(basin, Items.SUGAR), 1L, "Output created");
        helper.assertValueEqual(count(basin, Items.BUCKET), 1L, "Crafting remainder returned once"); helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void removingMixerDriveDuringProcessingPreservesInputs(GameTestHelper helper) {
        var basin = basin(helper); mixer(helper, true);
        insert(basin, AllItems.WHEAT_FLOUR, 1); water(basin, FluidConstants.BUCKET);
        helper.runAfterDelay(30, () -> helper.setBlock(BASIN.above().west(), Blocks.AIR));
        helper.runAfterDelay(90, () -> {
            helper.assertValueEqual(count(basin, AllItems.WHEAT_FLOUR), 1L, "Unpowered mixer retains flour");
            helper.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(), FluidConstants.BUCKET, "Unpowered mixer retains water");
            helper.assertValueEqual(count(basin, AllItems.DOUGH), 0L, "No processing after power loss"); helper.succeed();
        });
    }
}

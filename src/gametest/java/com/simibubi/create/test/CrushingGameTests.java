package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crusher.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import java.util.*;

public class CrushingGameTests {
    private static final BlockPos CENTER = new BlockPos(4, 4, 4);
    private static void motor(GameTestHelper helper, BlockPos pos, Direction facing, boolean reverse, int speed) {
        helper.setBlock(pos, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, facing));
        var motor = helper.getBlockEntity(pos, KineticBlockEntity.class);
        for (int value = 16; value < speed; value *= 2) motor.adjustGeneratedSpeed(false);
        if (reverse) motor.adjustGeneratedSpeed(true);
    }
    private static CrushingWheelControllerBlockEntity setup(GameTestHelper helper, boolean powered) {
        var state = AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(CrushingWheelBlock.AXIS, Direction.Axis.Z);
        helper.setBlock(CENTER.west(), state); helper.setBlock(CENTER.east(), state);
        CrushingWheelBlock.updateControllers(helper.getLevel(), helper.absolutePos(CENTER.west()), state);
        if (powered) {
            motor(helper, CENTER.west().north(), Direction.SOUTH, true, 256);
            motor(helper, CENTER.east().north(), Direction.SOUTH, false, 256);
        }
        return helper.getBlockEntity(CENTER, CrushingWheelControllerBlockEntity.class);
    }
    private static DepotBlockEntity output(GameTestHelper helper) {
        helper.setBlock(CENTER.below(), AllBlocks.DEPOT);
        return helper.getBlockEntity(CENTER.below(), DepotBlockEntity.class);
    }
    private static long insert(CrushingWheelControllerBlockEntity crusher, ItemStack stack, boolean commit) {
        try (Transaction tx = Transaction.openOuter()) { long result = crusher.inventory.insert(ItemVariant.of(stack), stack.getCount(), tx); if (commit) tx.commit(); return result; }
    }
    private static long count(DepotBlockEntity depot, Item item) {
        try (Transaction tx = Transaction.openOuter()) { return depot.inventory.automation.extract(ItemVariant.of(item), 100000, tx); }
    }
    @GameTest(maxTicks = 40)
    public void opposedWheelsCreatePoweredControllerAndConsumeStress(GameTestHelper helper) {
        var crusher = setup(helper, true);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.VALID), "Opposed wheels active");
            helper.assertValueEqual(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.FACING), Direction.DOWN, "Cross product directs output down");
            helper.assertValueEqual(crusher.getCrushingSpeed(), 256f / 50, "Original crushing speed scale");
            helper.assertValueEqual(helper.getBlockEntity(CENTER.west(), CrushingWheelBlockEntity.class).getStressImpact(), 2048.0, "Eight stress per RPM per wheel"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 40)
    public void sameDirectionInvalidatesControllerAndReversalRestoresIt(GameTestHelper helper) {
        setup(helper, true);
        helper.runAfterDelay(8, () -> helper.getBlockEntity(CENTER.west().north(), KineticBlockEntity.class).adjustGeneratedSpeed(true));
        helper.runAfterDelay(13, () -> {
            helper.assertFalse(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.VALID), "Same direction cannot crush");
            helper.assertValueEqual(helper.getBlockEntity(CENTER, CrushingWheelControllerBlockEntity.class).getCrushingSpeed(), 0f, "Invalid pair stopped");
            helper.getBlockEntity(CENTER.east().north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        });
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.VALID), "Opposite direction restores processing");
            helper.assertValueEqual(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.FACING), Direction.UP, "Reversing both wheels reverses output"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 30)
    public void mismatchedWheelAxesDoNotCreateController(GameTestHelper helper) {
        helper.setBlock(CENTER.west(), AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(CrushingWheelBlock.AXIS, Direction.Axis.Z));
        helper.setBlock(CENTER.east(), AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(CrushingWheelBlock.AXIS, Direction.Axis.Y));
        helper.runAfterDelay(5, () -> { helper.assertBlockPresent(Blocks.AIR, CENTER); helper.succeed(); });
    }
    @GameTest(maxTicks = 30)
    public void controllerNeverOverwritesSolidGap(GameTestHelper helper) {
        helper.setBlock(CENTER, Blocks.STONE);
        var wheel = AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(CrushingWheelBlock.AXIS, Direction.Axis.Z);
        helper.setBlock(CENTER.west(), wheel); helper.setBlock(CENTER.east(), wheel);
        helper.runAfterDelay(5, () -> { helper.assertBlockPresent(Blocks.STONE, CENTER); helper.succeed(); });
    }
    @GameTest
    public void abortedInsertionRestoresBatchAndTimerWithItemComponents(GameTestHelper helper) {
        var crusher = setup(helper, false);
        var named = new ItemStack(Items.RAW_IRON, 8); named.set(DataComponents.CUSTOM_NAME, Component.literal("Ore batch"));
        helper.assertValueEqual(insert(crusher, named, false), 8L, "Simulated input accepted");
        helper.assertTrue(crusher.inventory.isEmpty() && crusher.inventory.remainingTime == 0, "Aborted insertion resets timer and cargo");
        helper.assertValueEqual(insert(crusher, named, true), 8L, "Real insertion accepted");
        helper.assertTrue(ItemStack.isSameItemSameComponents(crusher.inventory.getInput(), named), "Components preserved");
        helper.assertValueEqual(insert(crusher, named, true), 0L, "Occupied input rejects a second batch");
        try (Transaction tx = Transaction.openOuter()) { helper.assertValueEqual(crusher.inventory.extract(ItemVariant.of(named), 8, tx), 0L, "Automated extraction cannot steal a processing batch"); }
        helper.succeed();
    }
    @GameTest(maxTicks = 230)
    public void crusherProcessesEntireRawOreStack(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        insert(crusher, new ItemStack(Items.RAW_IRON, 32), true);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(depot, AllItems.CRUSHED_IRON), 32L, "Whole stack crushed");
            helper.assertTrue(crusher.inventory.getInput().isEmpty(), "Processed input consumed");
        });
    }
    @GameTest(maxTicks = 100)
    public void crushingRecipeTakesPriorityOverMilling(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        insert(crusher, new ItemStack(Items.GRAVEL), true);
        helper.succeedWhen(() -> helper.assertValueEqual(count(depot, Items.SAND), 1L, "Crushing gravel yields sand rather than milling flint"));
    }
    @GameTest(maxTicks = 100)
    public void crusherFallsBackToMillingRecipe(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        insert(crusher, new ItemStack(Items.COBBLESTONE, 4), true);
        helper.succeedWhen(() -> helper.assertValueEqual(count(depot, Items.GRAVEL), 4L, "Cobblestone uses milling recipe in crusher"));
    }
    @GameTest(maxTicks = 80)
    public void unmatchedInputIsDestroyedWithoutOutput(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        insert(crusher, new ItemStack(Items.DIAMOND, 2), true);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(crusher.inventory.isEmpty(), "Unmatched items destroyed after default duration");
            helper.assertValueEqual(count(depot, Items.DIAMOND), 0L, "No unchanged item emitted"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 240)
    public void stoppedWheelsPauseProcessingAndRestoreWithoutLoss(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        insert(crusher, new ItemStack(Items.RAW_GOLD, 32), true);
        helper.runAfterDelay(15, () -> helper.setBlock(CENTER.west().north(), Blocks.AIR));
        var paused = new float[1];
        helper.runAfterDelay(22, () -> paused[0] = crusher.inventory.remainingTime);
        helper.runAfterDelay(65, () -> {
            helper.assertValueEqual(crusher.inventory.remainingTime, paused[0], "No progress without opposed drive");
            helper.assertValueEqual(crusher.inventory.getInput().getCount(), 32, "Input stack retained");
            motor(helper, CENTER.west().north(), Direction.SOUTH, true, 256);
        });
        helper.succeedWhen(() -> helper.assertValueEqual(count(depot, AllItems.CRUSHED_GOLD), 32L, "Batch resumes after restoring wheel power"));
    }
    @GameTest(maxTicks = 230)
    public void blockedOutputKeepsRandomResultsAcrossSave(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        try (Transaction tx = Transaction.openOuter()) {
            depot.inventory.automation.insert(ItemVariant.of(Items.DIAMOND), 64, tx);
            for (var slot : depot.inventory.outputs) slot.insert(ItemVariant.of(Items.DIAMOND), 64, tx); tx.commit();
        }
        insert(crusher, new ItemStack(Items.RAW_GOLD, 16), true);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(crusher.inventory.appliedRecipe && !crusher.inventory.isEmpty(), "Blocked batch retains rolled output");
            var saved = crusher.saveWithFullMetadata(helper.getLevel().registryAccess());
            var restored = (CrushingWheelControllerBlockEntity) BlockEntity.loadStatic(helper.absolutePos(CENTER), crusher.getBlockState(), saved, helper.getLevel().registryAccess());
            var original = crusher.inventory.getOutputs(); var loaded = restored.inventory.getOutputs();
            helper.assertValueEqual(loaded.size(), original.size(), "Pending results survive save");
            for (int i = 0; i < original.size(); i++) helper.assertTrue(ItemStack.matches(original.get(i), loaded.get(i)), "Randomized count survives save");
            try (Transaction tx = Transaction.openOuter()) { depot.inventory.automation.extract(ItemVariant.of(Items.DIAMOND), 10000, tx); tx.commit(); }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 120, "Wait for output to open");
            helper.assertValueEqual(count(depot, AllItems.CRUSHED_GOLD), 16L, "Exactly one blocked batch emitted");
        });
    }
    @GameTest(maxTicks = 100)
    public void droppedItemsAreCapturedAndProcessed(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        var pos = Vec3.atCenterOf(helper.absolutePos(CENTER)).add(0, 0.4, 0);
        var input = new ItemStack(Items.COBBLESTONE, 2);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("Physical crusher input"));
        var item = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, input.copy(), 0, 0, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "Original physical item enters the actual native world");
        var captured = new boolean[2];
        helper.onEachTick(() -> {
            if (crusher.processingEntity == item) {
                captured[0] = true;
                helper.assertTrue(item.isAlive() && ItemStack.matches(item.getItem(), input), "Actual crusher owns the original live entity with both named input items");
            }
            if (!crusher.inventory.getInput().isEmpty()) {
                captured[1] = true;
                helper.assertTrue(ItemStack.matches(crusher.inventory.getInput(), input), "Physical intake transfers the complete original stack and components");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(captured[0] && captured[1], "Real entity capture and physical intake precede processing");
            helper.assertFalse(item.isAlive(), "Dropped entity consumed once");
            helper.assertValueEqual(count(depot, Items.GRAVEL), 2L, "Captured item stack processed");
            helper.assertValueEqual(count(depot, Items.COBBLESTONE), 0L, "Unprocessed input cannot fall through into the output owner");
            helper.assertTrue(crusher.inventory.isEmpty() && !crusher.hasEntity(), "Crusher releases the completed original batch without a duplicate owner");
        });
    }
    @GameTest(maxTicks = 60)
    public void removingWheelEjectsStoredInputExactlyOnce(GameTestHelper helper) {
        var crusher = setup(helper, false);
        insert(crusher, new ItemStack(Items.RAW_IRON, 9), true);
        helper.setBlock(CENTER.west(), Blocks.AIR);
        helper.runAfterDelay(4, () -> {
            helper.assertBlockPresent(Blocks.AIR, CENTER);
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CENTER)).inflate(3));
            helper.assertValueEqual(entities.stream().filter(e -> e.getItem().is(Items.RAW_IRON)).mapToInt(e -> e.getItem().getCount()).sum(), 9, "Controller drops inventory exactly once"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 80)
    public void outputEntitiesCarryPersistentBypassMarker(GameTestHelper helper) {
        var crusher = setup(helper, true);
        insert(crusher, new ItemStack(Items.WHEAT, 2), true);
        helper.succeedWhen(() -> {
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CENTER)).inflate(3));
            var flour = entities.stream().filter(e -> e.getItem().is(AllItems.WHEAT_FLOUR)).findFirst().orElse(null);
            helper.assertTrue(flour != null, "Processed stack ejected into world");
            helper.assertValueEqual(flour.getAttached(CrushingWheelControllerBlockEntity.BYPASS), helper.absolutePos(CENTER), "Output bypasses same crusher");
            helper.assertFalse(crusher.tryStartCrushing(flour), "Output cannot be captured again by same controller");
        });
    }
    @GameTest(maxTicks = 80)
    public void crusherDamagesLivingEntities(GameTestHelper helper) {
        setup(helper, true);
        var pig = helper.spawn(EntityTypes.PIG, Vec3.atCenterOf(CENTER)); pig.setNoAi(true);
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "Active wheels deal crush damage"));
    }
    @GameTest
    public void experienceNuggetsYieldThreeExperienceEach(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(AllItems.EXP_NUGGET, 7); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(CENTER)));
        AllItems.EXP_NUGGET.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(stack.isEmpty(), "Whole stack used normally");
        var orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(player.position(), player.position()).inflate(4));
        helper.assertValueEqual(orbs.stream().mapToInt(ExperienceOrb::getValue).sum(), 21, "Three experience per nugget"); helper.succeed();
    }
    @GameTest(maxTicks = 40)
    public void verticalWheelAxesProduceNorthAndSouthOutput(GameTestHelper helper) {
        var state = AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(CrushingWheelBlock.AXIS, Direction.Axis.Y);
        helper.setBlock(CENTER.west(), state); helper.setBlock(CENTER.east(), state);
        motor(helper, CENTER.west().below(), Direction.UP, false, 16);
        motor(helper, CENTER.east().below(), Direction.UP, true, 16);
        helper.runAfterDelay(8, () -> {
            helper.assertValueEqual(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.FACING), Direction.NORTH, "Y axis wheels pull north");
            helper.getBlockEntity(CENTER.west().below(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
            helper.getBlockEntity(CENTER.east().below(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        });
        helper.runAfterDelay(14, () -> {
            helper.assertValueEqual(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.FACING), Direction.SOUTH, "Reverse wheels pull south"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 40)
    public void stackedWheelsProduceEastAndWestOutput(GameTestHelper helper) {
        var state = AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(CrushingWheelBlock.AXIS, Direction.Axis.Z);
        helper.setBlock(CENTER.below(), state); helper.setBlock(CENTER.above(), state);
        motor(helper, CENTER.below().north(), Direction.SOUTH, true, 16);
        motor(helper, CENTER.above().north(), Direction.SOUTH, false, 16);
        helper.runAfterDelay(8, () -> {
            helper.assertValueEqual(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.FACING), Direction.EAST, "Stacked Z wheels pull east");
            helper.getBlockEntity(CENTER.below().north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
            helper.getBlockEntity(CENTER.above().north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        });
        helper.runAfterDelay(14, () -> {
            helper.assertValueEqual(helper.getBlockState(CENTER).getValue(CrushingWheelControllerBlock.FACING), Direction.WEST, "Reverse stacked wheels pull west"); helper.succeed();
        });
    }
    private static BeltBlockEntity belt(GameTestHelper helper, BlockPos start, BlockPos end, Direction.Axis axis, Direction motorSide, boolean reverse) {
        var state = AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, axis);
        helper.setBlock(start, state); helper.setBlock(end, state);
        helper.assertTrue(BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(start), helper.absolutePos(end)), "Test conveyor built");
        motor(helper, start.relative(motorSide), motorSide.getOpposite(), reverse, 256);
        return helper.getBlockEntity(start, BeltBlockEntity.class);
    }
    @GameTest(maxTicks = 120)
    public void movingBeltFeedsWholeBatchIntoCrusher(GameTestHelper helper) {
        setup(helper, true); var depot = output(helper);
        var belt = belt(helper, CENTER.south(3), CENTER.south(), Direction.Axis.X, Direction.EAST, false);
        try (Transaction tx = Transaction.openOuter()) { belt.getInventory().segment(1, Direction.UP).insert(ItemVariant.of(Items.COBBLESTONE), 5, tx); tx.commit(); }
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(depot, Items.GRAVEL), 5L, "Conveyor cargo crushed and delivered");
            helper.assertTrue(belt.getInventory().getTransportedItems().isEmpty(), "Batch removed from input conveyor once");
        });
    }
    @GameTest(maxTicks = 120)
    public void crusherOutputsOntoConveyorBelowController(GameTestHelper helper) {
        var crusher = setup(helper, true);
        var belt = belt(helper, CENTER.west(2).below(), CENTER.east(2).below(), Direction.Axis.Z, Direction.SOUTH, false);
        helper.setBlock(CENTER.east(3).below(), AllBlocks.DEPOT);
        var depot = helper.getBlockEntity(CENTER.east(3).below(), DepotBlockEntity.class);
        insert(crusher, new ItemStack(Items.COBBLESTONE, 6), true);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(depot, Items.GRAVEL), 6L, "Crusher output travels along lower conveyor");
            helper.assertTrue(crusher.inventory.isEmpty(), "Crusher empties after belt accepts deterministic batch");
        });
    }
    @GameTest(maxTicks = 60)
    public void crushingOutputBypassSurvivesEntitySave(GameTestHelper helper) {
        var crusher = setup(helper, true); insert(crusher, new ItemStack(Items.COBBLESTONE), true);
        helper.succeedWhen(() -> {
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CENTER)).inflate(3));
            var item = entities.stream().filter(e -> e.getItem().is(Items.GRAVEL)).findFirst().orElse(null);
            helper.assertTrue(item != null, "Output entity spawned");
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            item.saveWithoutId(output);
            var restored = new ItemEntity(helper.getLevel(), item.getX(), item.getY(), item.getZ(), new ItemStack(Items.GRAVEL));
            restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
            helper.assertValueEqual(restored.getAttached(CrushingWheelControllerBlockEntity.BYPASS), helper.absolutePos(CENTER), "Persistent attachment survives entity reload");
        });
    }
    @GameTest
    public void crushingAndOreRefiningRecipeSetLoads(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        helper.assertValueEqual(manager.getRecipes().stream().filter(holder -> holder.value().getType() == AllRecipeTypes.CRUSHING).count(), 55L, "All base crushing recipes loaded");
        helper.assertTrue(manager.getRecipeFor(AllRecipeTypes.CRUSHING, new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.NETHERRACK)), helper.getLevel()).isPresent(), "Converted Fabric resource condition keeps base netherrack recipe");
        helper.assertTrue(manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(AllItems.CRUSHED_ZINC)), helper.getLevel()).isPresent(), "Crushed zinc can be smelted");
        helper.assertTrue(manager.getRecipeFor(AllRecipeTypes.SPLASHING, new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(AllItems.CRUSHED_IRON)), helper.getLevel()).isPresent(), "Crushed iron can be washed");
        var damage = new net.minecraft.world.damagesource.DamageSource(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(CrushingWheelControllerBlockEntity.CRUSH));
        helper.assertTrue(damage.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR), "Original crushing damage bypasses armor"); helper.succeed();
    }
    @GameTest(maxTicks = 250)
    public void largeRawBlockBatchPreservesAllGuaranteedOutput(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        insert(crusher, new ItemStack(Items.RAW_GOLD_BLOCK, 64), true);
        helper.runAfterDelay(180, () -> {
            helper.assertTrue(crusher.inventory.getInput().isEmpty(), "Full raw block stack processed");
            long pending = crusher.inventory.getOutputs().stream().filter(s -> s.is(AllItems.CRUSHED_GOLD)).mapToLong(ItemStack::getCount).sum();
            helper.assertValueEqual(pending + count(depot, AllItems.CRUSHED_GOLD), 576L, "All nine outputs per raw block retained despite limited destination");
            helper.assertTrue(crusher.inventory.getOutputs().stream().allMatch(s -> s.getCount() <= s.getMaxStackSize()), "Output stacks respect max stack sizes"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 120)
    public void reloadingControllerMidBatchResumesWithoutDuplicatingInput(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        insert(crusher, new ItemStack(Items.COBBLESTONE, 16), true);
        helper.runAfterDelay(12, () -> {
            var saved = crusher.saveWithFullMetadata(helper.getLevel().registryAccess());
            var restored = (CrushingWheelControllerBlockEntity) BlockEntity.loadStatic(helper.absolutePos(CENTER), crusher.getBlockState(), saved, helper.getLevel().registryAccess());
            helper.assertValueEqual(restored.inventory.remainingTime, crusher.inventory.remainingTime, "Processing timer retained");
            helper.getLevel().removeBlockEntity(helper.absolutePos(CENTER)); helper.getLevel().setBlockEntity(restored);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 12, "Wait for reload");
            helper.assertValueEqual(count(depot, Items.GRAVEL), 16L, "One batch finishes after controller reload");
            helper.assertTrue(helper.getBlockEntity(CENTER, CrushingWheelControllerBlockEntity.class).inventory.isEmpty(), "Restored inventory emptied");
        });
    }
    @GameTest(maxTicks = 100)
    public void killingEntityMovesOnlyItsNewLootToOutput(GameTestHelper helper) {
        setup(helper, true);
        var center = Vec3.atCenterOf(helper.absolutePos(CENTER));
        var pig = helper.spawn(EntityTypes.PIG, Vec3.atCenterOf(CENTER)); pig.setNoAi(true); pig.setHealth(4);
        var unrelated = new ItemEntity(helper.getLevel(), center.x + 0.4, center.y - 1, center.z, new ItemStack(Items.DIAMOND), 0.2, 0, 0);
        unrelated.setNoGravity(true); helper.getLevel().addFreshEntity(unrelated);
        helper.succeedWhen(() -> {
            helper.assertFalse(pig.isAlive(), "Crushing damage kills vulnerable mob");
            var loot = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(3));
            var pork = loot.stream().filter(e -> e.getItem().is(Items.PORKCHOP)).findFirst().orElse(null);
            helper.assertTrue(pork != null && pork.getY() < center.y - 0.5, "Mob loot appears at crusher exit");
            helper.assertTrue(Math.abs(pork.getDeltaMovement().x) < 1e-5 && Math.abs(pork.getDeltaMovement().z) < 1e-5, "Crushed mob drops have no sideways launch");
            helper.assertTrue(unrelated.getAttached(CrushingWheelControllerBlockEntity.BYPASS) == null && unrelated.getDeltaMovement().x > 0.01, "Existing nearby item is left untouched");
        });
    }
    @GameTest(maxTicks = 120)
    public void upwardCrushingEjectsItemsAboveController(GameTestHelper helper) {
        var crusher = setup(helper, true);
        helper.getBlockEntity(CENTER.west().north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        helper.getBlockEntity(CENTER.east().north(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
        insert(crusher, new ItemStack(Items.COBBLESTONE), true);
        helper.succeedWhen(() -> {
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CENTER)).inflate(4));
            var output = entities.stream().filter(e -> e.getItem().is(Items.GRAVEL)).findFirst().orElse(null);
            helper.assertTrue(output != null && output.getY() > helper.absolutePos(CENTER).getY() + 1, "Upward output shoots above crusher");
            helper.assertTrue(output.getDeltaMovement().y > 0, "Upward item receives positive launch velocity");
        });
    }
    @GameTest(maxTicks = 300)
    public void crusherAndWashingFanRefineOreIntoNuggets(GameTestHelper helper) {
        var crusher = setup(helper, true); var depot = output(helper);
        var fanPos = CENTER.south(2);
        helper.setBlock(fanPos, AllBlocks.ENCASED_FAN.defaultBlockState().setValue(com.simibubi.create.content.kinetics.fan.EncasedFanBlock.FACING, Direction.NORTH));
        motor(helper, fanPos.south(), Direction.NORTH, false, 16); helper.setBlock(CENTER.south(), Blocks.WATER);
        insert(crusher, new ItemStack(Items.RAW_IRON), true);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(depot, Items.IRON_NUGGET), 9L, "Crush and wash chain produces nine iron nuggets");
            helper.assertValueEqual(count(depot, AllItems.CRUSHED_IRON), 0L, "Crushed intermediate consumed");
        });
    }
}

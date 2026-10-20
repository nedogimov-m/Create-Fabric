package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.drill.*;
import com.simibubi.create.content.kinetics.saw.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import java.util.*;

public class CuttingGameTests {
    private static final BlockPos P = new BlockPos(4, 3, 4);
    private static KineticBlockEntity motor(GameTestHelper h, BlockPos pos, Direction facing, int rpm) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, facing));
        var motor = h.getBlockEntity(pos, KineticBlockEntity.class);
        for (int value = 16; value < Math.abs(rpm); value *= 2) motor.adjustGeneratedSpeed(false);
        if (rpm < 0) motor.adjustGeneratedSpeed(true);
        return motor;
    }
    private static DrillBlockEntity drill(GameTestHelper h, Direction facing, int rpm) {
        h.setBlock(P, AllBlocks.MECHANICAL_DRILL.defaultBlockState().setValue(DrillBlock.FACING, facing));
        if (rpm != 0) motor(h, P.relative(facing.getOpposite()), facing, rpm);
        return h.getBlockEntity(P, DrillBlockEntity.class);
    }
    private static SawBlockEntity saw(GameTestHelper h, Direction facing, boolean axis, int rpm) {
        h.setBlock(P, AllBlocks.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING, facing).setValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE, axis));
        Direction input = facing.getAxis().isHorizontal() ? facing.getOpposite() : axis ? Direction.WEST : Direction.NORTH;
        if (rpm != 0) motor(h, P.relative(input), input.getOpposite(), rpm);
        return h.getBlockEntity(P, SawBlockEntity.class);
    }
    private static long insert(SawBlockEntity saw, ItemStack stack, boolean commit) {
        try (var tx = Transaction.openOuter()) { long moved = saw.inventory.insert(ItemVariant.of(stack), stack.getCount(), tx); if (commit) tx.commit(); return moved; }
    }
    private static DepotBlockEntity depot(GameTestHelper h, Direction direction) {
        h.setBlock(P.relative(direction), AllBlocks.DEPOT); return h.getBlockEntity(P.relative(direction), DepotBlockEntity.class);
    }
    private static long count(DepotBlockEntity depot, Item item) {
        try (var tx = Transaction.openOuter()) { return depot.inventory.automation.extract(ItemVariant.of(item), 10000, tx); }
    }
    private static ChestBlockEntity singleSlotOutput(GameTestHelper h) {
        h.setBlock(P.east(), Blocks.CHEST);
        var chest = h.getBlockEntity(P.east(), ChestBlockEntity.class);
        for (int slot = 1; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.STONE, 64));
        return chest;
    }
    private static long count(ChestBlockEntity chest, Item item) {
        long count = 0; for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount(); return count;
    }
    private static void shaftInput(GameTestHelper h, SawBlockEntity saw) {
        // Original source saws cycle cutting and stonecutting recipes unless the output is filtered.
        saw.setFilter(new ItemStack(AllBlocks.SHAFT));
        h.assertValueEqual(insert(saw, new ItemStack(AllItems.ANDESITE_ALLOY), true), 1L, "One real alloy pays for the cutting batch");
        h.assertValueEqual(saw.inventory.selectedRecipe(), Create.asResource("cutting/andesite_alloy"), "Actual output filter selects the six-shaft recipe");
        h.assertValueEqual(saw.inventory.recipeDuration, 200f, "Original cutting duration remains two hundred");
    }
    private static int dropped(GameTestHelper h, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(3)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }
    @GameTest(maxTicks = 80)
    public void drillBreaksStoneAndDropsCobblestone(GameTestHelper h) {
        var d = drill(h, Direction.EAST, 256); h.setBlock(P.east(), Blocks.STONE);
        h.succeedWhen(() -> { h.assertBlockPresent(Blocks.AIR, P.east()); h.assertValueEqual(dropped(h, Items.COBBLESTONE), 1, "Stone loot"); h.assertValueEqual(d.getStressImpact(), 1024.0, "Drill stress four per RPM"); });
    }
    @GameTest(maxTicks = 80)
    public void drillExtractsDiamondWithoutPlayerTool(GameTestHelper h) {
        drill(h, Direction.UP, 256); h.setBlock(P.above(), Blocks.DIAMOND_ORE);
        h.succeedWhen(() -> { h.assertBlockPresent(Blocks.AIR, P.above()); h.assertValueEqual(dropped(h, Items.DIAMOND), 1, "Ore uses normal drops without silk touch"); });
    }
    @GameTest(maxTicks = 45)
    public void bedrockAndWaterAreNotBreakable(GameTestHelper h) {
        var d = drill(h, Direction.EAST, 256); h.setBlock(P.east(), Blocks.BEDROCK);
        h.runAfterDelay(20, () -> { h.assertBlockPresent(Blocks.BEDROCK, P.east()); h.assertValueEqual(d.getDestroyProgress(), 0, "Bedrock cracks absent"); h.setBlock(P.east(), Blocks.WATER); });
        h.runAfterDelay(35, () -> { h.assertTrue(h.getBlockState(P.east()).is(Blocks.WATER), "Fluid is not mined"); h.assertValueEqual(d.getDestroyProgress(), 0, "Fluid cracks absent"); h.succeed(); });
    }
    @GameTest(maxTicks = 90)
    public void lossOfPowerPausesAndRestorationFinishes(GameTestHelper h) {
        var d = drill(h, Direction.EAST, 256); h.setBlock(P.east(), Blocks.IRON_BLOCK); int[] progress = {0};
        h.runAfterDelay(8, () -> { progress[0] = d.getDestroyProgress(); h.assertTrue(progress[0] > 0, "Mining started"); h.setBlock(P.west(), Blocks.AIR); });
        h.runAfterDelay(18, () -> { h.assertValueEqual(d.getSpeed(), 0f, "Drive stopped"); h.assertValueEqual(d.getDestroyProgress(), progress[0], "No progress without power"); motor(h, P.west(), Direction.EAST, 256); });
        h.succeedWhen(() -> { h.assertTrue(h.getTick() > 18, "Wait for restoration"); h.assertBlockPresent(Blocks.AIR, P.east()); h.assertValueEqual(dropped(h, Items.IRON_BLOCK), 1, "No duplicate loot"); });
    }
    @GameTest(maxTicks = 110)
    public void drillProgressSurvivesBlockEntityReload(GameTestHelper h) {
        var d = drill(h, Direction.EAST, 128); h.setBlock(P.east(), Blocks.IRON_BLOCK);
        h.runAfterDelay(12, () -> {
            int progress = d.getDestroyProgress(); h.assertTrue(progress > 0, "Partial mining before reload");
            var saved = d.saveWithFullMetadata(h.getLevel().registryAccess());
            var loaded = (DrillBlockEntity) BlockEntity.loadStatic(h.absolutePos(P), d.getBlockState(), saved, h.getLevel().registryAccess());
            h.assertValueEqual(loaded.getDestroyProgress(), progress, "Cracks persisted"); h.getLevel().removeBlockEntity(h.absolutePos(P)); h.getLevel().setBlockEntity(loaded);
        });
        h.succeedWhen(() -> { h.assertTrue(h.getTick() > 12, "Wait for reload"); h.assertBlockPresent(Blocks.AIR, P.east()); h.assertValueEqual(dropped(h, Items.IRON_BLOCK), 1, "One loot after reload"); });
    }
    @GameTest(maxTicks = 40)
    public void replacingTargetResetsItsMiningProgress(GameTestHelper h) {
        var d = drill(h, Direction.EAST, 256); h.setBlock(P.east(), Blocks.STONE);
        h.runAfterDelay(8, () -> { h.assertTrue(d.getDestroyProgress() >= 3, "Stone almost broken"); h.setBlock(P.east(), Blocks.OBSIDIAN); });
        h.runAfterDelay(12, () -> { h.assertBlockPresent(Blocks.OBSIDIAN, P.east()); h.assertTrue(d.getDestroyProgress() <= 1, "New hardness starts from zero"); h.succeed(); });
    }
    @GameTest(maxTicks = 80)
    public void waterloggedTargetLeavesWater(GameTestHelper h) {
        drill(h, Direction.EAST, 256);
        h.setBlock(P.east(), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        h.runAfterDelay(40, () -> { h.assertBlockPresent(Blocks.WATER, P.east()); h.assertValueEqual(dropped(h, Items.OAK_SLAB), 1, "Waterlogged slab loot"); h.succeed(); });
    }
    @GameTest(maxTicks = 100)
    public void drillBreaksChestWithItsStoredItems(GameTestHelper h) {
        drill(h, Direction.EAST, 256); h.setBlock(P.east(), Blocks.CHEST);
        h.getBlockEntity(P.east(), net.minecraft.world.level.block.entity.ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 7));
        h.succeedWhen(() -> { h.assertBlockPresent(Blocks.AIR, P.east()); h.assertValueEqual(dropped(h, Items.CHEST), 1, "Chest item"); h.assertValueEqual(dropped(h, Items.DIAMOND), 7, "Container inventory dropped once"); });
    }
    @GameTest
    public void singleItemInsertionRollbackRestoresRecipeAndComponents(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 0); var named = new ItemStack(AllItems.ANDESITE_ALLOY, 12); named.set(DataComponents.CUSTOM_NAME, Component.literal("Batch"));
        h.assertValueEqual(insert(saw, named, false), 1L, "Single item default"); h.assertTrue(saw.inventory.isEmpty(), "Rollback restores input");
        h.assertValueEqual(saw.inventory.remainingTime, -1f, "Rollback restores timer"); h.assertValueEqual(saw.inventory.recipeIndex, 0, "Rollback restores cycle");
        insert(saw, named, true); h.assertTrue(ItemStack.isSameItemSameComponents(saw.inventory.input(), named.copyWithCount(1)), "Components retained");
        h.assertValueEqual(insert(saw, named, true), 0L, "Occupied saw rejects more items"); h.succeed();
    }
    @GameTest(maxTicks = 180)
    public void sawProducesSixShaftsAndTransfersToDepot(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST); shaftInput(h, saw);
        h.succeedWhen(() -> { h.assertValueEqual(count(out, AllBlocks.SHAFT.asItem()), 6L, "Six shafts per alloy"); h.assertTrue(saw.inventory.isEmpty(), "Batch delivered"); h.assertValueEqual(saw.getStressImpact(), 1024.0, "Saw stress four per RPM"); });
    }
    @GameTest(maxTicks = 100)
    public void generatedRecipeStripsOakLog(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST); saw.setFilter(new ItemStack(Items.STRIPPED_OAK_LOG)); insert(saw, new ItemStack(Items.OAK_LOG), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.STRIPPED_OAK_LOG), 1L, "Registry generated stripping"));
    }
    @GameTest(maxTicks = 100)
    public void generatedRecipeTurnsStrippedLogIntoSixPlanks(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST); insert(saw, new ItemStack(Items.STRIPPED_OAK_LOG), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.OAK_PLANKS), 6L, "Registry generated plank yield"));
    }
    @GameTest(maxTicks = 100)
    public void bambooBlockYieldsThreePlanks(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST); insert(saw, new ItemStack(Items.STRIPPED_BAMBOO_BLOCK), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.BAMBOO_PLANKS), 3L, "Bamboo block multiplier"));
    }
    @GameTest(maxTicks = 100)
    public void sawFilterSelectsStonecuttingOutput(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST); saw.setFilter(new ItemStack(Items.STONE_SLAB)); insert(saw, new ItemStack(Items.STONE), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.STONE_SLAB), 2L, "Vanilla stonecutting obeys output filter"));
    }
    @GameTest(maxTicks = 100)
    public void generatedPlankRecipeSelectsTwoSlabs(GameTestHelper h) {
        var saw = saw(h, Direction.UP, true, 256); var out = depot(h, Direction.NORTH); saw.setFilter(new ItemStack(Items.OAK_SLAB)); insert(saw, new ItemStack(Items.OAK_PLANKS), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.OAK_SLAB), 2L, "Generated slab recipe and perpendicular output axis"));
    }
    @GameTest(maxTicks = 100)
    public void reverseSawOutputsWestAndPassesUnknownInputComponents(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, -256); var out = depot(h, Direction.WEST); var stack = new ItemStack(Items.DIAMOND); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Retained")); insert(saw, stack, true);
        h.succeedWhen(() -> { h.assertTrue(ItemStack.isSameItemSameComponents(out.getHeldItem(), stack), "Pass-through preserves components"); h.assertTrue(saw.inventory.isEmpty(), "Reverse output exported"); });
    }
    @GameTest(maxTicks = 160)
    public void fullOutputPausesBatchUntilSpaceIsAvailable(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST);
        try (var tx = Transaction.openOuter()) { out.inventory.automation.insert(ItemVariant.of(Items.DIAMOND), 64, tx); tx.commit(); }
        shaftInput(h, saw);
        h.runAfterDelay(60, () -> { h.assertTrue(saw.inventory.appliedRecipe, "Recipe applied"); h.assertValueEqual(saw.inventory.outputs().getFirst().getCount(), 6, "Full output batch retained"); h.assertValueEqual(count(out, Items.DIAMOND), 64L, "Existing buffer intact"); try (var tx = Transaction.openOuter()) { h.assertValueEqual(out.inventory.automation.extract(ItemVariant.of(Items.DIAMOND), 64, tx), 64L, "Actual extraction empties the non-merging depot"); tx.commit(); } });
        h.succeedWhen(() -> { h.assertTrue(h.getTick() > 60, "Wait for space"); h.assertValueEqual(count(out, AllBlocks.SHAFT.asItem()), 6L, "Batch delivered when unblocked"); h.assertTrue(saw.inventory.isEmpty(), "No retained duplicate"); });
    }
    @GameTest(maxTicks = 150)
    public void sawProcessingAndFilterSurviveReload(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 64); var out = depot(h, Direction.EAST); saw.setFilter(new ItemStack(AllBlocks.SHAFT)); insert(saw, new ItemStack(AllItems.ANDESITE_ALLOY), true);
        h.runAfterDelay(15, () -> {
            var loaded = (SawBlockEntity) BlockEntity.loadStatic(h.absolutePos(P), saw.getBlockState(), saw.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertValueEqual(loaded.inventory.remainingTime, saw.inventory.remainingTime, "Timer preserved"); h.assertValueEqual(loaded.inventory.selectedRecipe(), saw.inventory.selectedRecipe(), "Recipe preserved"); h.assertTrue(loaded.getFilter().is(AllBlocks.SHAFT.asItem()), "Filter preserved");
            h.getLevel().removeBlockEntity(h.absolutePos(P)); h.getLevel().setBlockEntity(loaded);
        });
        h.succeedWhen(() -> { h.assertTrue(h.getTick() > 15, "Wait for reload"); h.assertValueEqual(count(out, AllBlocks.SHAFT.asItem()), 6L, "One recipe after reload"); });
    }
    @GameTest(maxTicks = 100)
    public void droppingItemOntoSawProcessesOneFromStack(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = singleSlotOutput(h); saw.setFilter(new ItemStack(Items.BIRCH_PLANKS));
        var pos = Vec3.atCenterOf(h.absolutePos(P)).add(0, 0.5, 0); var item = new ItemEntity(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(Items.STRIPPED_BIRCH_LOG, 2), 0, 0, 0); h.getLevel().addFreshEntity(item);
        var firstBatch = new boolean[1];
        h.onEachTick(() -> {
            if (!firstBatch[0] && !saw.inventory.input().isEmpty()) {
                h.assertValueEqual(saw.inventory.input().getCount(), 1, "Single-item mode claims exactly one of the two actual logs");
                h.assertTrue(item.isAlive() && item.getItem().is(Items.STRIPPED_BIRCH_LOG) && item.getItem().getCount() == 1, "Original physical entity retains the other paid input"); firstBatch[0] = true;
            }
        });
        h.succeedWhen(() -> { h.assertValueEqual(count(out, Items.BIRCH_PLANKS), 12L, "Two item entities processed sequentially"); h.assertTrue(firstBatch[0] && item.isRemoved() && saw.inventory.isEmpty(), "Two real single-log batches relinquish both physical and processing owners"); h.assertValueEqual(count(out, Items.STONE), (out.getContainerSize() - 1) * 64L, "Output uses only the one actual destination slot"); h.assertValueEqual(dropped(h, Items.STRIPPED_BIRCH_LOG), 0, "No input log remains loose"); h.assertValueEqual(dropped(h, Items.BIRCH_PLANKS), 0, "No processed planks bypass destination custody"); });
    }
    @GameTest(maxTicks = 30)
    public void onlyUpwardSawAcceptsItemsAndHorizontalCannotMineStone(GameTestHelper h) {
        var saw = saw(h, Direction.EAST, false, 256); h.setBlock(P.east(), Blocks.STONE); h.assertValueEqual(insert(saw, new ItemStack(Items.STONE), true), 0L, "Horizontal saw rejects items");
        h.runAfterDelay(20, () -> { h.assertBlockPresent(Blocks.STONE, P.east()); h.assertValueEqual(saw.getDestroyProgress(), 0, "Saw does not mine stone"); h.succeed(); });
    }
    @GameTest(maxTicks = 120)
    public void horizontalSawFellsTrunkAndItsLeaves(GameTestHelper h) {
        saw(h, Direction.EAST, false, 256); var trunk = P.east(); for (int i = 0; i < 4; i++) h.setBlock(trunk.above(i), Blocks.OAK_LOG);
        h.setBlock(trunk.above(3).east(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, true));
        h.succeedWhen(() -> { for (int i = 0; i < 4; i++) h.assertBlockPresent(Blocks.AIR, trunk.above(i)); h.assertBlockPresent(Blocks.AIR, trunk.above(3).east()); h.assertValueEqual(dropped(h, Items.OAK_LOG), 4, "Trunk cut once"); });
    }
    @GameTest
    public void twoByTwoTreeRemainsUntilCutLayerIsUnsupported(GameTestHelper h) {
        var cut = P.east(); var trunks = List.of(cut, cut.east(), cut.south(), cut.east().south());
        for (var pos : trunks) for (int y = 0; y < 3; y++) h.setBlock(pos.above(y), Blocks.SPRUCE_LOG);
        h.setBlock(cut, Blocks.AIR); var state = Blocks.SPRUCE_LOG.defaultBlockState();
        h.assertTrue(TreeCutter.findTree(h.getLevel(), h.absolutePos(cut), state).logs.isEmpty(), "Other trunks still support crown");
        for (var pos : trunks) h.setBlock(pos, Blocks.AIR);
        h.assertValueEqual(TreeCutter.findTree(h.getLevel(), h.absolutePos(cut), state).logs.size(), 8, "Remaining crown found after full cut"); h.succeed();
    }
    @GameTest
    public void leafDistanceDoesNotCrossIntoNeighbouringTree(GameTestHelper h) {
        var cut = P; h.setBlock(cut.above(), Blocks.OAK_LOG);
        h.setBlock(cut.above().east(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        h.setBlock(cut.above().east(2), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 2));
        h.setBlock(cut.above().east(3), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        var tree = TreeCutter.findTree(h.getLevel(), h.absolutePos(cut), Blocks.OAK_LOG.defaultBlockState());
        h.assertTrue(tree.leaves.contains(h.absolutePos(cut.above().east(2))), "Outward distance included"); h.assertFalse(tree.leaves.contains(h.absolutePos(cut.above().east(3))), "Neighbour tree leaves excluded"); h.succeed();
    }
    @GameTest
    public void netherFungusAndAttachmentsAreIncluded(GameTestHelper h) {
        var cut = P; h.setBlock(cut.above(), Blocks.CRIMSON_STEM); h.setBlock(cut.above(2), Blocks.NETHER_WART_BLOCK); h.setBlock(cut.above(2).east(), Blocks.SHROOMLIGHT);
        var tree = TreeCutter.findTree(h.getLevel(), h.absolutePos(cut), Blocks.CRIMSON_STEM.defaultBlockState());
        h.assertTrue(tree.logs.contains(h.absolutePos(cut.above())), "Fungus trunk"); h.assertTrue(tree.leaves.contains(h.absolutePos(cut.above(2))), "Wart canopy"); h.assertTrue(tree.attachments.contains(h.absolutePos(cut.above(2).east())), "Shroomlight attachment"); h.succeed();
    }
    @GameTest
    public void verticalBambooIsDeduplicatedAndChorusBranchesAreFound(GameTestHelper h) {
        for (int i = 1; i <= 4; i++) h.setBlock(P.above(i), Blocks.BAMBOO);
        h.assertValueEqual(TreeCutter.findTree(h.getLevel(), h.absolutePos(P), Blocks.BAMBOO.defaultBlockState()).logs.size(), 4, "Vertical plant not duplicated");
        for (int i = 1; i <= 4; i++) h.setBlock(P.above(i), Blocks.AIR);
        h.setBlock(P.above(), Blocks.CHORUS_PLANT); h.setBlock(P.above().east(), Blocks.CHORUS_PLANT); h.setBlock(P.above().east().above(), Blocks.CHORUS_FLOWER);
        h.assertValueEqual(TreeCutter.findTree(h.getLevel(), h.absolutePos(P), Blocks.CHORUS_PLANT.defaultBlockState()).logs.size(), 3, "Chorus branch topology"); h.succeed();
    }
    @GameTest
    public void runtimeConcreteRecipeIsLoadedIntoModernRecipeRegistry(GameTestHelper h) {
        var id = ResourceKey.create(Registries.RECIPE, Create.asResource("splashing/runtime_generated/compat/minecraft/red_concrete_powder_to_red_concrete"));
        h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(id).isPresent(), "Runtime washing recipe loaded alongside cutting recipes"); h.succeed();
    }
    @GameTest(maxTicks = 110)
    public void partialOutputRetainsExactlyTheUndeliveredRemainder(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = singleSlotOutput(h); out.setItem(0, new ItemStack(AllBlocks.SHAFT, 60));
        var output = ItemStorage.SIDED.find(h.getLevel(), out.getBlockPos(), Direction.WEST); h.assertTrue(output != null, "Real native chest storage exposes the constrained destination"); var received = new long[1];
        h.assertValueEqual(insert(saw, new ItemStack(Items.ECHO_SHARD), true), 1L, "One actual echo shard pays for the ninety-nine-shaft fixture recipe");
        h.runAfterDelay(30, () -> {
            h.assertValueEqual(count(out, AllBlocks.SHAFT.asItem()), 64L, "Only four fit");
            h.assertValueEqual(saw.inventory.outputs().stream().mapToInt(ItemStack::getCount).sum(), 95, "All 95 undelivered items retained");
            h.assertTrue(saw.inventory.outputs().stream().allMatch(stack -> stack.getCount() <= stack.getMaxStackSize()), "Outputs split to legal stack sizes");
            try (var tx = Transaction.openOuter()) { long moved = output.extract(ItemVariant.of(AllBlocks.SHAFT.asItem()), 64, tx); h.assertValueEqual(moved, 64L, "First sixty-four shafts leave through a real transfer receipt"); tx.commit(); received[0] += moved; }
        });
        h.runAfterDelay(40, () -> {
            h.assertValueEqual(count(out, AllBlocks.SHAFT.asItem()), 64L, "Next full stack delivered");
            h.assertValueEqual(saw.inventory.outputs().stream().mapToInt(ItemStack::getCount).sum(), 31, "Final 31 remain");
            try (var tx = Transaction.openOuter()) { long moved = output.extract(ItemVariant.of(AllBlocks.SHAFT.asItem()), 64, tx); h.assertValueEqual(moved, 64L, "Next sixty-four shafts leave through a real transfer receipt"); tx.commit(); received[0] += moved; }
        });
        h.runAfterDelay(50, () -> { h.assertValueEqual(count(out, AllBlocks.SHAFT.asItem()), 31L, "Final remainder delivered once"); h.assertTrue(saw.inventory.isEmpty(), "All 159 accounted for"); h.assertValueEqual(received[0] + count(out, AllBlocks.SHAFT.asItem()), 159L, "Initial sixty and ninety-nine crafted shafts conserved across actual receipts and remaining destination"); h.assertValueEqual(count(out, Items.STONE), (out.getContainerSize() - 1) * 64L, "Every blocked destination slot retains its original stock"); h.assertValueEqual(dropped(h, AllBlocks.SHAFT.asItem()), 0, "No undelivered shaft escaped to a loose entity"); h.succeed(); });
    }
    @GameTest(maxTicks = 60)
    public void stoppedSawRetainsInputAndBreakingItDropsOnlyThatInput(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 0); shaftInput(h, saw);
        h.runAfterDelay(25, () -> { h.assertValueEqual(saw.getSpeed(), 0f, "Actual saw has no native power source"); h.assertValueEqual(saw.inventory.remainingTime, 200f, "No processing without power"); h.assertTrue(!saw.inventory.appliedRecipe && saw.inventory.input().is(AllItems.ANDESITE_ALLOY) && saw.inventory.input().getCount() == 1 && saw.inventory.outputs().isEmpty(), "Unpowered saw still owns exactly its one original alloy"); h.setBlock(P, Blocks.AIR); });
        h.runAfterDelay(30, () -> { h.assertValueEqual(dropped(h, AllItems.ANDESITE_ALLOY), 1, "Pending input returned once"); h.assertValueEqual(dropped(h, AllBlocks.SHAFT.asItem()), 0, "No unprocessed result"); h.succeed(); });
    }
    @GameTest(maxTicks = 65)
    public void breakingSawWithCompletedBatchDropsOutputOnce(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST);
        try (var tx = Transaction.openOuter()) { out.inventory.automation.insert(ItemVariant.of(Items.DIAMOND), 64, tx); tx.commit(); }
        shaftInput(h, saw);
        h.runAfterDelay(40, () -> { h.assertTrue(saw.inventory.appliedRecipe, "Completed batch waiting"); h.assertValueEqual(saw.inventory.outputs().stream().mapToInt(ItemStack::getCount).sum(), 6, "Exactly six completed shafts remain saw-owned before break"); h.setBlock(P, Blocks.AIR); });
        h.runAfterDelay(45, () -> { h.assertValueEqual(dropped(h, AllBlocks.SHAFT.asItem()), 6, "Completed result dropped once"); h.assertValueEqual(dropped(h, AllItems.ANDESITE_ALLOY), 0, "Consumed input absent"); h.assertValueEqual(count(out, Items.DIAMOND), 64L, "Breaking the saw leaves unrelated output stock untouched"); h.succeed(); });
    }
    @GameTest
    public void wrenchRotatesVerticalSawAxisAndKeepsInventory(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 0); insert(saw, new ItemStack(AllItems.ANDESITE_ALLOY), true);
        var player = h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH)); var absolute = h.absolutePos(P);
        AllItems.WRENCH.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        h.assertTrue(h.getBlockState(P).getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE), "Top wrench rotates drive axis");
        h.assertValueEqual(AllBlocks.MECHANICAL_SAW.getRotationAxis(h.getBlockState(P)), Direction.Axis.X, "New X shaft axis");
        h.assertTrue(h.getBlockEntity(P, SawBlockEntity.class).inventory.input().is(AllItems.ANDESITE_ALLOY), "Input survives wrench"); h.succeed();
    }
    @GameTest
    public void sawRowHelperPreservesOrientationAndConsumesOneItem(GameTestHelper h) {
        saw(h, Direction.UP, true, 0); var state = h.getBlockState(P).setValue(SawBlock.FLIPPED, true); h.setBlock(P, state);
        var player = h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var stack = new ItemStack(AllBlocks.MECHANICAL_SAW, 3); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var absolute = h.absolutePos(P); var hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0.49, 0, 0), Direction.EAST, absolute, false);
        state.useItemOn(stack, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertValueEqual(h.getBlockState(P.east()), state, "Row helper copies facing, axis and flip"); h.assertValueEqual(stack.getCount(), 2, "One item consumed in survival"); h.succeed();
    }
    @GameTest(maxTicks = 50)
    public void sawCyclesBetweenUnfilteredStonecuttingRecipes(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); insert(saw, new ItemStack(Items.STONE), true); var first = saw.inventory.selectedRecipe();
        h.runAfterDelay(25, () -> { saw.inventory.collect(); insert(saw, new ItemStack(Items.STONE), true); h.assertFalse(java.util.Objects.equals(first, saw.inventory.selectedRecipe()), "Next input advances recipe selection"); h.succeed(); });
    }
    @GameTest(maxTicks = 35)
    public void drillAndSawDamageUseCustomTypesAndIgnoreItems(GameTestHelper h) {
        var d = drill(h, Direction.EAST, 256); var zombie = h.spawn(EntityTypes.ZOMBIE, P); zombie.setNoAi(true); zombie.setNoGravity(true); zombie.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        h.runAfterDelay(8, () -> {
            float health = zombie.getHealth(); DrillBlock.hurtInside(h.getLevel(), h.absolutePos(P), zombie, d.getSpeed(), DrillBlock.DRILL_DAMAGE);
            h.assertTrue(zombie.getHealth() < health || health < zombie.getMaxHealth(), "Powered drill hurts living entity");
            var pos = Vec3.atCenterOf(h.absolutePos(P)); var item = new ItemEntity(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(Items.DIAMOND)); h.getLevel().addFreshEntity(item);
            DrillBlock.hurtInside(h.getLevel(), h.absolutePos(P), item, d.getSpeed(), DrillBlock.DRILL_DAMAGE); h.assertTrue(item.isAlive(), "Items are not damaged");
            h.assertValueEqual(DrillBlock.getDamage(256), 10.0, "Original damage ceiling"); h.assertValueEqual(DrillBlock.getDamage(-32), 3.5, "Reverse damage scale");
            h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get( SawBlock.SAW_DAMAGE).isPresent(), "Saw damage registered"); h.succeed();
        });
    }
    @GameTest(maxTicks = 100)
    public void runtimeGeneratorSupportsAddonWoodByRegistryName(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST);
        var log = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("create_test:stripped_fir_log"));
        var planks = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("create_test:fir_planks"));
        insert(saw, new ItemStack(log), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, planks), 6L, "Addon stripped log gains plank recipe"));
    }
    @GameTest(maxTicks = 100)
    public void runtimeGeneratorCombinesUnstrippableAddonLogsInTag(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST);
        var log = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("create_test:silverwood_wood"));
        var planks = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("create_test:silverwood_planks"));
        insert(saw, new ItemStack(log), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, planks), 6L, "Generated tag accepts wood with no stripped counterpart"));
    }
    @GameTest(maxTicks = 100)
    public void higherPriorityDatapackOverridesGeneratedRecipe(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256); var out = depot(h, Direction.EAST); insert(saw, new ItemStack(Items.STRIPPED_PALE_OAK_LOG), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.PALE_OAK_PLANKS), 13L, "Datapack override replaces generated six-plank yield"));
    }
    @GameTest(maxTicks = 150)
    public void beltsFeedSawAndCarryItsResultToDepot(GameTestHelper h) {
        var saw = saw(h, Direction.UP, false, 256);
        var before = P.west(4); var after = P.east(); var axis = AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS, Direction.Axis.Z);
        for (var pos : List.of(before, P.west(), after, P.east(4))) h.setBlock(pos, axis);
        h.assertTrue(com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(h.getLevel(), h.absolutePos(before), h.absolutePos(P.west())), "Input belt installed");
        h.assertTrue(com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(h.getLevel(), h.absolutePos(after), h.absolutePos(P.east(4))), "Output belt installed");
        motor(h, before.north(), Direction.SOUTH, -256); motor(h, after.north(), Direction.SOUTH, -256);
        h.setBlock(P.east(5), AllBlocks.DEPOT); var out = h.getBlockEntity(P.east(5), DepotBlockEntity.class);
        var belt = h.getBlockEntity(before, com.simibubi.create.content.kinetics.belt.BeltBlockEntity.class);
        try (var tx = Transaction.openOuter()) { h.assertValueEqual(belt.storage(Direction.UP).insert(ItemVariant.of(Items.STRIPPED_OAK_LOG), 1, tx), 1L, "Input loaded"); tx.commit(); }
        h.succeedWhen(() -> { h.assertValueEqual(count(out, Items.OAK_PLANKS), 6L, "Entire belt-saw-belt pipeline"); h.assertTrue(saw.inventory.isEmpty(), "Saw export consumed once"); });
    }
}

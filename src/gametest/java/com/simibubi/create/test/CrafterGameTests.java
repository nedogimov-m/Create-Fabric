package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crafter.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.createmod.catnip.math.Pointing;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import java.util.*;

public class CrafterGameTests {
    private static final BlockPos ORIGIN = new BlockPos(2, 2, 4);
    private static MechanicalCrafterBlockEntity crafter(GameTestHelper h, BlockPos pos, Pointing pointing) {
        h.setBlock(pos, AllBlocks.MECHANICAL_CRAFTER.defaultBlockState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.SOUTH).setValue(MechanicalCrafterBlock.POINTING, pointing));
        return h.getBlockEntity(pos, MechanicalCrafterBlockEntity.class);
    }
    private static void power(GameTestHelper h, BlockPos first, int rpm) {
        h.setBlock(first.west(), AllBlocks.COGWHEEL.defaultBlockState().setValue(CogWheelBlock.AXIS, Direction.Axis.Z));
        var motorPos = first.west().north();
        h.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
        for (int s = 16; s < rpm; s *= 2) h.getBlockEntity(motorPos, KineticBlockEntity.class).adjustGeneratedSpeed(false);
    }
    private static long insert(MechanicalCrafterBlockEntity crafter, ItemStack stack, boolean commit) {
        try (Transaction tx = Transaction.openOuter()) { long inserted = crafter.storage().insert(ItemVariant.of(stack), stack.getCount(), tx); if (commit) tx.commit(); return inserted; }
    }
    private static long count(DepotBlockEntity depot, Item item) { try (Transaction tx = Transaction.openOuter()) { return depot.inventory.automation.extract(ItemVariant.of(item), 10000, tx); } }
    private static long count(net.minecraft.world.Container container, Item item) { long amount=0;for(int slot=0;slot<container.getContainerSize();slot++)if(container.getItem(slot).is(item))amount+=container.getItem(slot).getCount();return amount; }
    private static DepotBlockEntity depot(GameTestHelper h, BlockPos pos) { h.setBlock(pos, AllBlocks.DEPOT); return h.getBlockEntity(pos, DepotBlockEntity.class); }
    @GameTest(maxTicks = 60)
    public void isolatedCrafterUsesVanillaRecipe(GameTestHelper h) {
        var c = crafter(h, ORIGIN, Pointing.RIGHT); power(h, ORIGIN, 256); var out = depot(h, ORIGIN.east());
        insert(c, new ItemStack(Items.OAK_LOG), true);
        h.succeedWhen(() -> { h.assertValueEqual(count(out, Items.OAK_PLANKS), 4L, "Vanilla recipe crafted"); h.assertValueEqual(c.phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Crafter reset"); });
    }
    @GameTest
    public void singleItemSlotRollsBackTransactions(GameTestHelper h) {
        var c = crafter(h, ORIGIN, Pointing.RIGHT);
        h.assertValueEqual(insert(c, new ItemStack(Items.DIAMOND, 8), false), 1L, "One item per slot");
        h.assertTrue(c.inventory.isResourceBlank(), "Aborted insertion restored empty slot");
        h.assertValueEqual(insert(c, new ItemStack(Items.DIAMOND, 8), true), 1L, "Committed one item");
        try (Transaction tx = Transaction.openOuter()) { h.assertValueEqual(c.storage().extract(ItemVariant.of(Items.DIAMOND), 1, tx), 0L, "Automation cannot extract crafting input"); }
        h.succeed();
    }
    @GameTest(maxTicks = 60)
    public void pairedCraftersAssembleShapedVanillaRecipe(GameTestHelper h) {
        var bottom = crafter(h, ORIGIN, Pointing.RIGHT); var top = crafter(h, ORIGIN.above(), Pointing.DOWN);
        power(h, ORIGIN, 256); var out = depot(h, ORIGIN.east());
        insert(bottom, new ItemStack(Items.OAK_PLANKS), true); insert(top, new ItemStack(Items.OAK_PLANKS), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.STICK), 4L, "Two vertical ingredients produce sticks"));
    }
    @GameTest(maxTicks = 70)
    public void unfilledSlotPreventsAutomaticStart(GameTestHelper h) {
        var bottom = crafter(h, ORIGIN, Pointing.RIGHT); crafter(h, ORIGIN.above(), Pointing.DOWN);
        power(h, ORIGIN, 256); insert(bottom, new ItemStack(Items.OAK_LOG), true);
        h.runAfterDelay(30, () -> { h.assertValueEqual(bottom.phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Unfilled slot holds automatic start"); h.assertFalse(bottom.inventory.isResourceBlank(), "Ingredient retained"); h.succeed(); });
    }
    @GameTest(maxTicks = 80)
    public void coverRepresentsEmptyRecipeSlot(GameTestHelper h) {
        var bottom = crafter(h, ORIGIN, Pointing.RIGHT); var top = crafter(h, ORIGIN.above(), Pointing.DOWN); top.covered = true;
        power(h, ORIGIN, 256); var out = depot(h, ORIGIN.east()); insert(bottom, new ItemStack(Items.OAK_LOG), true);
        h.succeedWhen(() -> { h.assertValueEqual(count(out, Items.OAK_PLANKS), 4L, "Covered slot treated as empty"); h.assertTrue(top.covered, "Cover remains for next cycle"); });
    }
    @GameTest(maxTicks = 100)
    public void redstoneEdgeStartsPartiallyFilledGrid(GameTestHelper h) {
        var bottom = crafter(h, ORIGIN, Pointing.RIGHT); crafter(h, ORIGIN.above(), Pointing.DOWN);
        power(h, ORIGIN, 256); var out = depot(h, ORIGIN.east()); insert(bottom, new ItemStack(Items.OAK_LOG), true);
        h.runAfterDelay(12, () -> { h.assertValueEqual(bottom.phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Missing slot initially waits"); h.setBlock(ORIGIN.south(), Blocks.REDSTONE_BLOCK); });
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.OAK_PLANKS), 4L, "Rising signal permits empty slot"));
    }
    @GameTest(maxTicks = 50)
    public void cyclicArrowsNeverConsumeInput(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.RIGHT); var b = crafter(h, ORIGIN.east(), Pointing.LEFT);
        power(h, ORIGIN, 256); insert(a, new ItemStack(Items.DIAMOND), true); insert(b, new ItemStack(Items.DIAMOND), true);
        h.runAfterDelay(20, () -> { h.assertValueEqual(a.phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Cycle rejected"); h.assertFalse(a.inventory.isResourceBlank() || b.inventory.isResourceBlank(), "Cycle retains both inputs"); h.succeed(); });
    }
    @GameTest
    public void connectedInputsDistributeTopRowFirstAndRollback(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.RIGHT); var b = crafter(h, ORIGIN.above(), Pointing.DOWN); var c = crafter(h, ORIGIN.east(), Pointing.UP);
        ConnectedInputHandler.toggleConnection(h.getLevel(), h.absolutePos(ORIGIN), h.absolutePos(ORIGIN.above()));
        ConnectedInputHandler.toggleConnection(h.getLevel(), h.absolutePos(ORIGIN), h.absolutePos(ORIGIN.east()));
        h.assertValueEqual(insert(a, new ItemStack(Items.OAK_PLANKS, 3), false), 3L, "Simulation distributes over group");
        h.assertTrue(a.inventory.isResourceBlank() && b.inventory.isResourceBlank() && c.inventory.isResourceBlank(), "All simulated slots restored");
        h.assertValueEqual(insert(c, new ItemStack(Items.DIAMOND), true), 1L, "Input from any member accepts one item");
        h.assertTrue(b.inventory.stack().is(Items.DIAMOND) && a.inventory.isResourceBlank(), "Top slot filled first"); h.succeed();
    }
    @GameTest(maxTicks = 160)
    public void originalFiveByFiveRecipeProducesTwoCrushingWheels(GameTestHelper h) {
        String[] pattern = {" AAA ", "AAPAA", "APSPA", "AAPAA", " AAA "};
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            var c = crafter(h, ORIGIN.offset(x, y, 0), x == 4 && y > 0 ? Pointing.DOWN : Pointing.RIGHT);
            char symbol = pattern[4 - y].charAt(x);
            if (symbol == ' ') c.covered = true;
            else insert(c, new ItemStack(symbol == 'A' ? AllItems.ANDESITE_ALLOY : symbol == 'P' ? Items.OAK_PLANKS : Items.STONE), true);
        }
        power(h, ORIGIN, 256); var out = depot(h, ORIGIN.east(5));
        h.succeedWhen(() -> {
            h.assertValueEqual(count(out, AllBlocks.CRUSHING_WHEEL.asItem()), 2L, "Original 5x5 pattern produces two wheels");
            for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) h.assertValueEqual(h.getBlockEntity(ORIGIN.offset(x, y, 0), MechanicalCrafterBlockEntity.class).phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Whole grid resets");
        });
    }
    @GameTest(maxTicks = 70)
    public void invalidRecipeReturnsAllIngredients(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.RIGHT); var b = crafter(h, ORIGIN.east(), Pointing.RIGHT); power(h, ORIGIN, 256);
        insert(a, new ItemStack(Items.DIAMOND), true); insert(b, new ItemStack(Items.OAK_LOG), true);
        h.runAfterDelay(40, () -> {
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(ORIGIN)).inflate(4));
            h.assertValueEqual(items.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum(), 1, "Diamond ejected once");
            h.assertValueEqual(items.stream().filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum(), 1, "Log ejected once"); h.succeed();
        });
    }
    @GameTest(maxTicks = 170)
    public void blockedOutputRetainsFinishedProductUntilDestinationClears(GameTestHelper h) {
        var c = crafter(h, ORIGIN, Pointing.RIGHT); power(h, ORIGIN, 256); var out = depot(h, ORIGIN.east());
        try (Transaction tx = Transaction.openOuter()) { out.inventory.automation.insert(ItemVariant.of(Items.DIAMOND), 64, tx); tx.commit(); }
        insert(c, new ItemStack(Items.OAK_LOG), true);
        h.runAfterDelay(45, () -> {
            h.assertValueEqual(c.phase, MechanicalCrafterBlockEntity.Phase.INSERTING, "Finished output retained at blocked destination");
            h.assertTrue(c.groupedItems.grid.values().stream().anyMatch(s -> s.is(Items.OAK_PLANKS) && s.getCount() == 4), "Four planks retained");
            try (Transaction tx = Transaction.openOuter()) { out.inventory.automation.extract(ItemVariant.of(Items.DIAMOND), 64, tx); tx.commit(); }
        });
        h.succeedWhen(() -> { h.assertTrue(h.getTick() > 45, "Wait for destination"); h.assertValueEqual(count(out, Items.OAK_PLANKS), 4L, "Exactly one output inserted"); });
    }
    @GameTest(maxTicks = 200)
    public void lossOfDrivePausesBatchWithoutConsumingItTwice(GameTestHelper h) {
        var bottom = crafter(h, ORIGIN, Pointing.RIGHT); var top = crafter(h, ORIGIN.above(), Pointing.DOWN);
        power(h, ORIGIN, 64); var out = depot(h, ORIGIN.east());
        insert(bottom, new ItemStack(Items.OAK_PLANKS), true); insert(top, new ItemStack(Items.OAK_PLANKS), true);
        h.runAfterDelay(8, () -> h.setBlock(ORIGIN.west().north(), Blocks.AIR));
        var timer = new int[1]; h.runAfterDelay(13, () -> timer[0] = top.countDown);
        h.runAfterDelay(45, () -> {
            h.assertValueEqual(top.countDown, timer[0], "Assembly countdown frozen without drive"); h.assertValueEqual(count(out, Items.STICK), 0L, "No early output"); power(h, ORIGIN, 256);
        });
        h.succeedWhen(() -> { h.assertTrue(h.getTick() > 45, "Wait for restored drive"); h.assertValueEqual(count(out, Items.STICK), 4L, "One resumed craft"); });
    }
    @GameTest(maxTicks = 100)
    public void breakingMemberDuringExportReturnsEveryIngredientOnce(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.RIGHT); var b = crafter(h, ORIGIN.above(), Pointing.DOWN);
        power(h, ORIGIN, 16); insert(a, new ItemStack(Items.OAK_PLANKS), true); insert(b, new ItemStack(Items.OAK_PLANKS), true);
        h.runAfterDelay(10, () -> h.getLevel().destroyBlock(h.absolutePos(ORIGIN.above()), false));
        h.runAfterDelay(15, () -> {
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(ORIGIN)).inflate(4));
            h.assertValueEqual(items.stream().filter(e -> e.getItem().is(Items.OAK_PLANKS)).mapToInt(e -> e.getItem().getCount()).sum(), 2, "Both ingredients returned exactly once");
            h.assertValueEqual(a.phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Remaining member reset"); h.succeed();
        });
    }
    @GameTest(maxTicks = 120)
    public void reloadingAllMembersDuringExportResumesSameBatch(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.RIGHT); var b = crafter(h, ORIGIN.above(), Pointing.DOWN);
        power(h, ORIGIN, 64); var out = depot(h, ORIGIN.east());
        insert(a, new ItemStack(Items.OAK_PLANKS), true); insert(b, new ItemStack(Items.OAK_PLANKS), true);
        h.runAfterDelay(8, () -> {
            for (var position : List.of(ORIGIN, ORIGIN.above())) {
                var old = h.getBlockEntity(position, MechanicalCrafterBlockEntity.class);
                var saved = old.saveWithFullMetadata(h.getLevel().registryAccess());
                var restored = (MechanicalCrafterBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(position), old.getBlockState(), saved, h.getLevel().registryAccess());
                h.assertValueEqual(restored.phase, old.phase, "Phase retained"); h.assertValueEqual(restored.countDown, old.countDown, "Countdown retained");
                h.getLevel().removeBlockEntity(h.absolutePos(position)); h.getLevel().setBlockEntity(restored);
            }
        });
        h.succeedWhen(() -> { h.assertTrue(h.getTick() > 8, "Wait for restored crafters"); h.assertValueEqual(count(out, Items.STICK), 4L, "Restored batch outputs four sticks"); });
    }
    @GameTest
    public void splittingInputGroupUpdatesPreviouslyObtainedStorage(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.UP); var b = crafter(h, ORIGIN.east(), Pointing.UP);
        ConnectedInputHandler.toggleConnection(h.getLevel(), h.absolutePos(ORIGIN), h.absolutePos(ORIGIN.east()));
        var storage = a.storage();
        ConnectedInputHandler.toggleConnection(h.getLevel(), h.absolutePos(ORIGIN), h.absolutePos(ORIGIN.east()));
        try (Transaction tx = Transaction.openOuter()) { h.assertValueEqual(storage.insert(ItemVariant.of(Items.DIAMOND), 2, tx), 1L, "Old storage resolves current group"); tx.commit(); }
        h.assertTrue(b.inventory.isResourceBlank(), "Split member no longer receives input"); h.succeed();
    }
    @GameTest
    public void directionalRecipeRejectsMirroredPatternUnlessEnabled(GameTestHelper h) {
        var recipe = new MechanicalCraftingRecipe(Map.of("A", Ingredient.of(Items.DIAMOND), "B", Ingredient.of(Items.EMERALD)), List.of("AB"), new ItemStackTemplate(Items.STICK), false);
        var normal = CraftingInput.of(2, 1, List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD)));
        var mirrored = CraftingInput.of(2, 1, List.of(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND)));
        h.assertTrue(recipe.matches(normal, h.getLevel()), "Unmirrored pattern matches"); h.assertFalse(recipe.matches(mirrored, h.getLevel()), "Mirror rejected by default");
        h.assertTrue(new MechanicalCraftingRecipe(recipe.key(), recipe.pattern(), recipe.result(), true).matches(mirrored, h.getLevel()), "Explicit mirrored recipe matches"); h.succeed();
    }
    @GameTest
    public void nineByNinePatternLoadsWithoutChangingVanillaCraftingLimit(GameTestHelper h) {
        var key = Map.of("A", Ingredient.of(Items.DIAMOND));
        var nine = new MechanicalCraftingRecipe(key, Collections.nCopies(9, "AAAAAAAAA"), new ItemStackTemplate(Items.STICK), false);
        var ops = h.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = MechanicalCraftingRecipe.CODEC.codec().encodeStart(ops, nine).getOrThrow();
        var decoded = MechanicalCraftingRecipe.CODEC.codec().parse(ops, encoded).getOrThrow();
        h.assertTrue(decoded.matches(CraftingInput.of(9, 9, Collections.nCopies(81, new ItemStack(Items.DIAMOND))), h.getLevel()), "9x9 pattern round trips and matches"); h.succeed();
    }
    @GameTest(maxTicks = 120)
    public void vanillaCakeReturnsThreeEmptyBuckets(GameTestHelper h) {
        Item[][] ingredients = {{Items.WHEAT, Items.WHEAT, Items.WHEAT}, {Items.SUGAR, Items.EGG, Items.SUGAR}, {Items.MILK_BUCKET, Items.MILK_BUCKET, Items.MILK_BUCKET}};
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            var c = crafter(h, ORIGIN.offset(x, y, 0), x == 2 && y > 0 ? Pointing.DOWN : Pointing.RIGHT);
            insert(c, new ItemStack(ingredients[y][x]), true);
        }
        power(h, ORIGIN, 256);
        h.runAfterDelay(65, () -> {
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(ORIGIN)).inflate(6));
            h.assertValueEqual(items.stream().filter(e -> e.getItem().is(Items.CAKE)).mapToInt(e -> e.getItem().getCount()).sum(), 1, "One cake crafted");
            h.assertValueEqual(items.stream().filter(e -> e.getItem().is(Items.BUCKET)).mapToInt(e -> e.getItem().getCount()).sum(), 3, "Three crafting containers returned once"); h.succeed();
        });
    }
    @GameTest
    public void wrenchRotatesArrowAndPreservesIdleIngredient(GameTestHelper h) {
        var c = crafter(h, ORIGIN, Pointing.UP);
        insert(c, new ItemStack(Items.DIAMOND), true);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH));
        var absolute = h.absolutePos(ORIGIN);
        AllItems.WRENCH.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute).add(0, 0, 0.5), Direction.SOUTH, absolute, false)));
        h.assertValueEqual(h.getBlockState(ORIGIN).getValue(MechanicalCrafterBlock.POINTING), Pointing.LEFT, "Front wrench cycles pointing");
        h.assertTrue(c.inventory.stack().is(Items.DIAMOND), "Idle ingredient remains in slot"); h.succeed();
    }
    @GameTest
    public void coverInteractionConsumesAndReturnsCoverAndBlocksInput(GameTestHelper h) {
        var c = crafter(h, ORIGIN, Pointing.UP); var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var covers = new ItemStack(AllItems.CRAFTER_SLOT_COVER, 2); var absolute = h.absolutePos(ORIGIN);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute).add(0, 0, 0.5), Direction.SOUTH, absolute, false);
        h.getBlockState(ORIGIN).useItemOn(covers, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(c.covered, "Cover attached to front"); h.assertValueEqual(covers.getCount(), 1, "One cover consumed");
        h.assertValueEqual(insert(c, new ItemStack(Items.DIAMOND), true), 0L, "Covered input rejects automation");
        h.getBlockState(ORIGIN).useWithoutItem(h.getLevel(), player, hit);
        h.assertFalse(c.covered, "Empty hand removes cover"); h.assertTrue(player.getInventory().contains(new ItemStack(AllItems.CRAFTER_SLOT_COVER)), "Cover returned"); h.succeed();
    }
    @GameTest(maxTicks = 80)
    public void inputGroupsPersistAndSplitWhenConnectingMemberIsRemoved(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.UP); var b = crafter(h, ORIGIN.east(), Pointing.UP); var c = crafter(h, ORIGIN.east(2), Pointing.UP);
        ConnectedInputHandler.toggleConnection(h.getLevel(), h.absolutePos(ORIGIN), h.absolutePos(ORIGIN.east()));
        ConnectedInputHandler.toggleConnection(h.getLevel(), h.absolutePos(ORIGIN.east()), h.absolutePos(ORIGIN.east(2)));
        var saved = a.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = (MechanicalCrafterBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(ORIGIN), a.getBlockState(), saved, h.getLevel().registryAccess());
        h.assertValueEqual(((MechanicalCrafterBlockEntity.InputRenderData) restored.getRenderData()).members().size(), 3, "Connected inputs survive save");
        h.setBlock(ORIGIN.east(), Blocks.AIR);
        h.runAfterDelay(4, () -> {
            h.assertValueEqual(((MechanicalCrafterBlockEntity.InputRenderData) a.getRenderData()).members().size(), 1, "Left component detached");
            h.assertValueEqual(((MechanicalCrafterBlockEntity.InputRenderData) c.getRenderData()).members().size(), 1, "Right component detached");
            h.assertValueEqual(insert(a, new ItemStack(Items.DIAMOND, 2), true), 1L, "Only current left input available"); h.succeed();
        });
    }
    @GameTest(maxTicks = 150)
    public void northFacingConnectedInputUsesFrontLeftOrdering(GameTestHelper h) {
        var left = ORIGIN.east(); var right = ORIGIN;
        h.setBlock(left, AllBlocks.MECHANICAL_CRAFTER.defaultBlockState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.NORTH).setValue(MechanicalCrafterBlock.POINTING, Pointing.DOWN));
        h.setBlock(right, AllBlocks.MECHANICAL_CRAFTER.defaultBlockState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.NORTH).setValue(MechanicalCrafterBlock.POINTING, Pointing.DOWN));
        var a = h.getBlockEntity(left, MechanicalCrafterBlockEntity.class); var b = h.getBlockEntity(right, MechanicalCrafterBlockEntity.class);
        h.assertValueEqual(MechanicalCrafterBlock.getTargetDirection(AllBlocks.MECHANICAL_CRAFTER.defaultBlockState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.NORTH).setValue(MechanicalCrafterBlock.POINTING, Pointing.RIGHT)), Direction.WEST, "Right is relative to north-facing front");
        h.assertTrue(ConnectedInputHandler.toggleConnection(h.getLevel(), h.absolutePos(right), h.absolutePos(left)), "North-facing inputs join");
        insert(a, new ItemStack(Items.DIAMOND), true);
        h.assertTrue(a.inventory.stack().is(Items.DIAMOND) && b.inventory.isResourceBlank(), "Front-left ordering reverses with facing"); h.succeed();
    }
    @GameTest(maxTicks = 160)
    public void signalRaisedDuringCraftDoesNotRestartNextPartialBatch(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.RIGHT); var b = crafter(h, ORIGIN.above(), Pointing.DOWN); power(h, ORIGIN, 64); var out = depot(h, ORIGIN.east());
        insert(a, new ItemStack(Items.OAK_PLANKS), true); insert(b, new ItemStack(Items.OAK_PLANKS), true);
        h.runAfterDelay(25, () -> h.setBlock(ORIGIN.south(), Blocks.REDSTONE_BLOCK));
        long[] insertedAt = {-1};
        h.onEachTick(() -> {
            if (insertedAt[0] < 0 && a.phase == MechanicalCrafterBlockEntity.Phase.IDLE && count(out, Items.STICK) == 4) {
                insert(a, new ItemStack(Items.OAK_LOG), true); insertedAt[0] = h.getTick();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(insertedAt[0] >= 0 && h.getTick() > insertedAt[0] + 20, "Wait for held signal and second input");
            h.assertValueEqual(a.phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Held signal is not a new rising edge");
            h.assertTrue(a.inventory.stack().is(Items.OAK_LOG), "Partially filled second recipe remains unconsumed");
        });
    }
    @GameTest(maxTicks = 30)
    public void removingIdleMemberEjectsInputsFromWholeArrowChain(GameTestHelper h) {
        var a = crafter(h, ORIGIN, Pointing.RIGHT); var b = crafter(h, ORIGIN.above(), Pointing.DOWN);
        insert(a, new ItemStack(Items.DIAMOND), true); insert(b, new ItemStack(Items.EMERALD), true);
        h.setBlock(ORIGIN.above(), Blocks.AIR);
        h.runAfterDelay(2, () -> {
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(ORIGIN)).inflate(4));
            h.assertTrue(a.inventory.isResourceBlank(), "Other idle input removed from chain");
            h.assertValueEqual(items.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum(), 1, "Other ingredient ejected once");
            h.assertValueEqual(items.stream().filter(e -> e.getItem().is(Items.EMERALD)).mapToInt(e -> e.getItem().getCount()).sum(), 1, "Removed ingredient ejected once"); h.succeed();
        });
    }
    @GameTest(maxTicks = 90)
    public void craftedOutputTravelsAlongAdjacentBelt(GameTestHelper h) {
        var c = crafter(h, ORIGIN, Pointing.RIGHT); power(h, ORIGIN, 256);
        var shaft = AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS, Direction.Axis.Z);
        h.setBlock(ORIGIN.east(), shaft); h.setBlock(ORIGIN.east(4), shaft);
        h.assertTrue(com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(h.getLevel(), h.absolutePos(ORIGIN.east()), h.absolutePos(ORIGIN.east(4))), "Output conveyor created");
        h.setBlock(ORIGIN.east().north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
        var motor = h.getBlockEntity(ORIGIN.east().north(), KineticBlockEntity.class); motor.adjustGeneratedSpeed(true);
        for (int i = 0; i < 4; i++) motor.adjustGeneratedSpeed(false);
        var out = depot(h, ORIGIN.east(5)); insert(c, new ItemStack(Items.OAK_LOG), true);
        h.succeedWhen(() -> h.assertValueEqual(count(out, Items.OAK_PLANKS), 4L, "Crafted stack reaches final depot through moving belt"));
    }
    @GameTest(maxTicks = 140)
    public void partiallyFilledDestinationRetainsOnlyUntransferredResult(GameTestHelper h) {
        var c = crafter(h, ORIGIN, Pointing.RIGHT); power(h, ORIGIN, 256);
        h.setBlock(ORIGIN.east(),Blocks.BARREL);h.setBlock(ORIGIN.east(3),Blocks.BARREL);
        var out=h.getBlockEntity(ORIGIN.east(),net.minecraft.world.level.block.entity.BarrelBlockEntity.class);var receipt=h.getBlockEntity(ORIGIN.east(3),net.minecraft.world.level.block.entity.BarrelBlockEntity.class);
        for(int slot=0;slot<out.getContainerSize();slot++)out.setItem(slot,new ItemStack(Items.STONE,64));out.setItem(0,new ItemStack(Items.OAK_PLANKS,62));
        var target=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(h.getLevel(),h.absolutePos(ORIGIN.east()),Direction.WEST);var destination=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(h.getLevel(),h.absolutePos(ORIGIN.east(3)),Direction.WEST);
        insert(c, new ItemStack(Items.OAK_LOG), true);
        h.runAfterDelay(40, () -> {
            h.assertValueEqual(count(out, Items.OAK_PLANKS), 64L, "Two result items inserted");
            h.assertValueEqual(c.groupedItems.grid.values().stream().filter(s -> s.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum(), 2, "Only two pending result items retained");
            try (Transaction tx = Transaction.openOuter()) { h.assertValueEqual(target.extract(ItemVariant.of(Items.OAK_PLANKS),64,tx),64L,"Withdraw exactly the completed destination stack");h.assertValueEqual(destination.insert(ItemVariant.of(Items.OAK_PLANKS),64,tx),64L,"A second real barrel receives every withdrawn plank");tx.commit(); }
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 40, "Wait for free space"); h.assertValueEqual(count(out, Items.OAK_PLANKS), 2L, "Exactly pending half delivered");
            h.assertValueEqual(c.phase, MechanicalCrafterBlockEntity.Phase.IDLE, "Batch completed");
            h.assertValueEqual(count(receipt,Items.OAK_PLANKS),64L,"Withdrawn destination stock stays physically owned");h.assertValueEqual(count(out,Items.OAK_PLANKS)+count(receipt,Items.OAK_PLANKS),66L,"Initial62 plus the four crafted planks are all conserved");h.assertValueEqual(count(out,Items.STONE),(out.getContainerSize()-1)*64L,"Unrelated full slots remain intact");
        });
    }
}

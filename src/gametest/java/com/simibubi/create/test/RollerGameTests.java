package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.roller.*;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity.RollingMode;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public class RollerGameTests {
    private static final BlockPos P = new BlockPos(2, 6, 4), TOOL = P.east(2), B = P.below(), WORK = TOOL.north().below(2), R = new BlockPos(3, 4, 3);
    private static final class Exposed extends RollerMovementBehaviour {
        PaveTask profile;
        @Override protected PaveTask createHeightProfileForTracks(MovementContext context) { return profile; }
        List<BlockPos> targets(MovementContext ctx, BlockPos pos) { return getPositionsToBreak(ctx, pos); }
        void pave(MovementContext ctx, BlockPos pos) { triggerPaver(ctx, pos); }
        boolean fill(MovementContext ctx, BlockPos pos, BlockState state) { return tryFill(ctx, pos, state) == PaveResult.SUCCESS; }
        BlockState material(MovementContext ctx) { return getStateToPaveWith(ctx); }
        BlockState slab(MovementContext ctx) { return getStateToPaveWithAsSlab(ctx); }
        void destroy(MovementContext ctx, BlockPos pos) { destroyBlock(ctx, pos); }
        float speed(MovementContext ctx) { return getBlockBreakingSpeed(ctx); }
    }
    private static BlockState roller() { return AllBlocks.MECHANICAL_ROLLER.defaultBlockState().setValue(RollerBlock.FACING, Direction.NORTH); }
    private static RollerBlockEntity placed(GameTestHelper h) { h.setBlock(R, roller()); return h.getBlockEntity(R, RollerBlockEntity.class); }
    private static ControlledContraptionEntity moving(GameTestHelper h, RollingMode mode, ItemStack material, ItemStack stock) throws Exception {
        h.setBlock(P, Blocks.STONE); h.setBlock(P.east(), Blocks.BARREL); h.setBlock(TOOL, roller());
        var blockEntity = h.getBlockEntity(TOOL, RollerBlockEntity.class); blockEntity.filtering.setFilter(material); blockEntity.setMode(mode);
        h.getBlockEntity(P.east(), BarrelBlockEntity.class).setItem(0, stock);
        h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(P), h.absolutePos(TOOL))));
        var structure = new BearingContraption(false, Direction.UP); structure.assemble(h.getLevel(), h.absolutePos(B));
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO), "Real mounted ownership");
        var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel()); owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P))); owner.tickActors();
        var ctx = context(owner); ctx.motion = ctx.relativeMotion = new Vec3(0, 0, -.2); return owner;
    }
    private static ControlledContraptionEntity moving(GameTestHelper h, RollingMode mode, int stock) throws Exception { return moving(h, mode, new ItemStack(Items.STONE), stock == 0 ? ItemStack.EMPTY : new ItemStack(Items.STONE, stock)); }
    private static MovementContext context(ControlledContraptionEntity owner) { return owner.getContraption().getActorAt(new BlockPos(2, 0, 0)).right; }
    private static long count(ControlledContraptionEntity owner, Item item) { long n = 0; for (var view : owner.getContraption().getStorage().getAllItems()) if (view.getResource().isOf(item)) n += view.getAmount(); return n; }
    private static RollerMovementBehaviour movement() { return (RollerMovementBehaviour) MovementBehaviour.REGISTRY.get(AllBlocks.MECHANICAL_ROLLER); }
    private static void finish(ControlledContraptionEntity owner) { for (int i = 0; i < 150 && context(owner).stall; i++) movement().tick(context(owner)); }
    private static Player lookingAt(GameTestHelper h, Vec3 target) {
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setPos(target.x, target.y + 2.5 - player.getEyeHeight(), target.z); player.setXRot(90); player.setYRot(0); return player;
    }
    @GameTest public void validMaterialAcceptsFullBlocksAndDoubleSlabsButRejectsOtherSamples(GameTestHelper h) {
        var roller = placed(h);
        for (var item : List.of(Items.STONE, Items.GLASS, Items.OAK_PLANKS, Items.OAK_SLAB)) h.assertTrue(roller.filtering.setFilter(new ItemStack(item)), "Valid paving material " + item);
        var old = roller.filtering.getFilter();
        for (var item : List.of(Items.STONE_STAIRS, Items.CHEST, Items.GLASS_PANE, Items.WHEAT, AllItems.FILTER)) h.assertFalse(roller.filtering.setFilter(new ItemStack(item)), "Invalid paving sample " + item);
        h.assertTrue(ItemStack.isSameItemSameComponents(roller.filtering.getFilter(), old), "Rejected samples preserve previous material"); h.succeed();
    }
    @GameTest public void materialAndModePropagateAcrossMatchingAdjacentRollers(GameTestHelper h) {
        var middle = placed(h); h.setBlock(R.west(), roller()); h.setBlock(R.west(2), roller()); h.setBlock(R.east(), roller());
        middle.filtering.setFilter(new ItemStack(Items.STONE)); middle.setMode(RollingMode.WIDE_FILL);
        for (var pos : List.of(R.west(), R.west(2), R.east())) { var other = h.getBlockEntity(pos, RollerBlockEntity.class); h.assertTrue(other.getMode() == RollingMode.WIDE_FILL && other.filtering.getFilter().is(Items.STONE), "Shared row values"); }
        h.succeed();
    }
    @GameTest public void sharingStopsAtAStateBoundaryOrGap(GameTestHelper h) {
        var middle = placed(h); h.setBlock(R.east(), roller().setValue(RollerBlock.WATERLOGGED, true)); h.setBlock(R.east(2), roller()); h.setBlock(R.west(2), roller());
        middle.setMode(RollingMode.WIDE_FILL);
        for (var pos : List.of(R.east(), R.east(2), R.west(2))) h.assertValueEqual(h.getBlockEntity(pos, RollerBlockEntity.class).getMode(), RollingMode.TUNNEL_PAVE, "No crossing state/gap boundary"); h.succeed();
    }
    @GameTest public void aPlacedRowMemberCopiesAnExistingNeighborsSettings(GameTestHelper h) {
        var first = placed(h); first.filtering.setFilter(new ItemStack(Items.BRICKS)); first.setMode(RollingMode.STRAIGHT_FILL); h.setBlock(R.east(), roller());
        var next = h.getBlockEntity(R.east(), RollerBlockEntity.class); next.searchForSharedValues();
        h.assertTrue(next.getMode() == RollingMode.STRAIGHT_FILL && next.filtering.getFilter().is(Items.BRICKS), "Native placement settings inheritance"); h.succeed();
    }
    @GameTest public void nativeBlockEntitySaveKeepsModeAndComponentsButNotSceneAnimation(GameTestHelper h) {
        var be = placed(h); var material = new ItemStack(Items.STONE); material.set(DataComponents.CUSTOM_NAME, Component.literal("road material")); be.filtering.setFilter(material); be.setMode(RollingMode.WIDE_FILL); be.setAnimatedSpeed(600);
        var saved = be.saveWithFullMetadata(h.getLevel().registryAccess()); var restored = new RollerBlockEntity(h.absolutePos(R), roller());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved));
        h.assertTrue(restored.getMode() == RollingMode.WIDE_FILL && ItemStack.isSameItemSameComponents(restored.filtering.getFilter(), material) && restored.getAnimatedSpeed() == 0, "Saved source configuration, transient scene animation"); h.succeed();
    }
    @GameTest public void blockItemLeavesTheOriginalOneBlockWheelClearance(GameTestHelper h) {
        var support = new BlockPos(2, 2, 2); h.setBlock(support, Blocks.STONE); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setYRot(180); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_ROLLER));
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(support)).add(0, .5, 0), Direction.UP, h.absolutePos(support), false);
        var item = (RollerBlockItem) AllBlocks.MECHANICAL_ROLLER.asItem(); item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertBlockPresent(Blocks.AIR, support.above()); h.assertBlockPresent(AllBlocks.MECHANICAL_ROLLER, support.above(2));
        h.assertTrue(player.getMainHandItem().isEmpty() && h.getBlockState(support.above(2)).getValue(RollerBlock.FACING) == Direction.SOUTH, "Placement consumes one and opposes view"); h.succeed();
    }
    @GameTest public void placementHelperExtendsTheLateralRowAndKeepsItsFacing(GameTestHelper h) {
        var be = placed(h); be.setMode(RollingMode.WIDE_FILL); be.filtering.setFilter(new ItemStack(Items.BRICKS));
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setYRot(270); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_ROLLER, 2));
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(R)).add(.4, 0, -.5), Direction.NORTH, h.absolutePos(R), false);
        h.getBlockState(R).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertBlockPresent(AllBlocks.MECHANICAL_ROLLER, R.east()); var next = h.getBlockEntity(R.east(), RollerBlockEntity.class);
        h.assertTrue(next.getBlockState().getValue(RollerBlock.FACING) == Direction.NORTH && next.getMode() == RollingMode.WIDE_FILL && next.filtering.getFilter().is(Items.BRICKS) && player.getMainHandItem().getCount() == 1, "Original lateral placement and row inheritance"); h.succeed();
    }
    @GameTest public void rowPlacementAlsoRaisesForSolidGroundAndRetainsFacing(GameTestHelper h) {
        placed(h); h.setBlock(R.east().below(), Blocks.STONE); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setYRot(270); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_ROLLER));
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(R)).add(.4, 0, -.5), Direction.NORTH, h.absolutePos(R), false);
        h.getBlockState(R).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertBlockPresent(Blocks.AIR, R.east()); h.assertBlockPresent(AllBlocks.MECHANICAL_ROLLER, R.east().above()); h.assertValueEqual(h.getBlockState(R.east().above()).getValue(RollerBlock.FACING), Direction.NORTH, "Facing survives raised context"); h.succeed();
    }
    @GameTest public void fullShapeWaterloggingRearAttachmentAndWrenchMatchTheSource(GameTestHelper h) {
        placed(h); var state = roller().setValue(RollerBlock.WATERLOGGED, true);
        h.assertTrue(state.getFluidState().isSource() && state.getShape(h.getLevel(), h.absolutePos(R)).bounds().getSize() == 1, "Full roller shape and water");
        h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(state, h.getLevel(), h.absolutePos(R), Direction.SOUTH) && BlockMovementChecks.isNotSupportive(state, Direction.NORTH), "Attached actor movement rules");
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH));
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(h.absolutePos(R)), Direction.UP, h.absolutePos(R), false));
        h.assertValueEqual(AllItems.WRENCH.useOn(use), InteractionResult.FAIL, "Ordinary wrench does not rotate attached actor"); player.setShiftKeyDown(true); AllItems.WRENCH.useOn(use); h.assertBlockPresent(Blocks.AIR, R);
        int returned = 0; for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(AllBlocks.MECHANICAL_ROLLER.asItem())) returned += player.getInventory().getItem(i).getCount(); h.assertValueEqual(returned, 1, "One returned roller"); h.succeed();
    }
    @GameTest public void upperMaterialPanelSamplesWithoutConsumingTheBlockStack(GameTestHelper h) {
        var roller = placed(h); var pos = h.absolutePos(R); var point = Vec3.atLowerCornerOf(pos).add(RollerBlock.panelOffset(roller.getBlockState(), true)); var player = lookingAt(h, point); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 5));
        var hit = new BlockHitResult(point.add(0, 1 / 32d, 0), Direction.UP, pos, false);
        h.assertValueEqual(FilteringSettings.interact(h.getLevel(), player, InteractionHand.MAIN_HAND, hit), InteractionResult.SUCCESS, "Real shared sample interaction"); h.assertTrue(roller.filtering.getFilter().is(Items.STONE) && player.getMainHandItem().getCount() == 5, "Sample is not stock consumption"); h.succeed();
    }
    @GameTest public void modePacketRequiresTheActualUpperModeRayAndValidChoice(GameTestHelper h) {
        var roller = placed(h); var pos = h.absolutePos(R); var point = Vec3.atLowerCornerOf(pos).add(RollerBlock.panelOffset(roller.getBlockState(), false)); var player = lookingAt(h, point);
        h.assertTrue(RollerSettings.apply(h.getLevel(), player, new RollerSettings.SetMode(pos, 2)) && roller.getMode() == RollingMode.WIDE_FILL, "Valid mode ray");
        h.assertFalse(RollerSettings.apply(h.getLevel(), player, new RollerSettings.SetMode(pos, 3)), "Out-of-range mode denied");
        h.setBlock(R.above(), Blocks.STONE); h.assertFalse(RollerSettings.apply(h.getLevel(), player, new RollerSettings.SetMode(pos, 0)), "Wall blocks forged settings"); h.succeed();
    }
    @GameTest public void modePacketCannotUseTheMaterialPanelOrADistantPosition(GameTestHelper h) {
        var roller = placed(h); var pos = h.absolutePos(R); var point = Vec3.atLowerCornerOf(pos).add(RollerBlock.panelOffset(roller.getBlockState(), true)); var player = lookingAt(h, point);
        h.assertFalse(RollerSettings.apply(h.getLevel(), player, new RollerSettings.SetMode(pos, 1)), "Material panel is not mode panel"); player.setPos(player.position().add(20, 0, 0)); h.assertFalse(RollerSettings.apply(h.getLevel(), player, new RollerSettings.SetMode(pos, 1)), "Distance checked"); h.succeed();
    }
    @GameTest public void materialPacketsCannotUseTheModeBoxOrCrossAnObstruction(GameTestHelper h) {
        var roller=placed(h);var pos=h.absolutePos(R);var mode=Vec3.atLowerCornerOf(pos).add(RollerBlock.panelOffset(roller.getBlockState(),false));var player=lookingAt(h,mode);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,5));
        var packet=new FilteringSettings.SetFilter(pos,Direction.UP,InteractionHand.MAIN_HAND,0,true);
        h.assertFalse(FilteringSettings.apply(h.getLevel(),player,packet),"Wrong value box denied");
        var material=Vec3.atLowerCornerOf(pos).add(RollerBlock.panelOffset(roller.getBlockState(),true));player.setPos(material.x,material.y+2.5-player.getEyeHeight(),material.z);
        h.assertTrue(FilteringSettings.apply(h.getLevel(),player,packet)&&roller.filtering.getFilter().is(Items.STONE),"Actual material ray accepted");
        h.setBlock(R.above(),Blocks.STONE);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT));h.assertFalse(FilteringSettings.apply(h.getLevel(),player,packet),"Wall denied");h.assertTrue(roller.filtering.getFilter().is(Items.STONE),"Rejected packet preserves configuration");h.succeed();
    }
    @GameTest public void modeAndMaterialPanelsStayDistinctInAllFourOrientations(GameTestHelper h) {
        for(var facing:Direction.Plane.HORIZONTAL){h.setBlock(R,roller().setValue(RollerBlock.FACING,facing));var state=h.getBlockState(R);var pos=h.absolutePos(R);var mode=Vec3.atLowerCornerOf(pos).add(RollerBlock.panelOffset(state,false));var player=lookingAt(h,mode);h.assertTrue(RollerSettings.apply(h.getLevel(),player,new RollerSettings.SetMode(pos,1)),"Mode orientation: "+facing);var material=Vec3.atLowerCornerOf(pos).add(RollerBlock.panelOffset(state,true));player.setPos(material.x,material.y+2.5-player.getEyeHeight(),material.z);h.assertFalse(RollerSettings.apply(h.getLevel(),player,new RollerSettings.SetMode(pos,2)),"Distinct material orientation: "+facing);}h.succeed();
    }
    @GameTest public void onlyForwardMotionActivatesTheTwoBlockLowerWorkArea(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 4); var ctx = context(owner); var movement = movement();
        h.assertTrue(movement.isActive(ctx) && movement.getActiveAreaOffset(ctx).distanceTo(new Vec3(0, -2, -.45)) < 1e-7, "Source work point"); ctx.relativeMotion = new Vec3(0, 0, 1); h.assertFalse(movement.isActive(ctx), "Reverse disabled"); ctx.relativeMotion = new Vec3(1, 0, 0); h.assertFalse(movement.isActive(ctx), "Sideways disabled"); ctx.relativeMotion = new Vec3(0, 0, -1); ctx.disabled = true; h.assertFalse(movement.isActive(ctx), "Controls disable"); h.succeed();
    }
    @GameTest public void tunnelChoosesTheHardestBlockAcrossItsWholeWorkingColumn(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 8); h.setBlock(WORK, Blocks.DIRT); h.setBlock(WORK.above(), Blocks.OBSIDIAN); h.setBlock(WORK.above(2), Blocks.STONE);
        movement().visitNewPosition(context(owner), h.absolutePos(WORK)); h.assertTrue(context(owner).stall, "Column holds construct");
        h.assertValueEqual(context(owner).data.read("BreakingPos", BlockPos.CODEC).orElseThrow(), h.absolutePos(WORK.above()), "Hardest reference"); h.assertValueEqual(context(owner).data.read("ReferencePos", BlockPos.CODEC).orElseThrow(), h.absolutePos(WORK), "Original column reference"); h.succeed();
    }
    @GameTest public void tunnelClearsTheColumnPavesAndSpendsExactlyOneItem(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 4); for (int y = 0; y < 3; y++) h.setBlock(WORK.above(y), Blocks.DIRT);
        movement().visitNewPosition(context(owner), h.absolutePos(WORK)); finish(owner);
        h.assertBlockPresent(Blocks.STONE, WORK); h.assertBlockPresent(Blocks.AIR, WORK.above()); h.assertBlockPresent(Blocks.AIR, WORK.above(2)); h.assertValueEqual(count(owner, Items.STONE), 3L, "One paving item"); h.assertFalse(context(owner).data.contains("ReferencePos"), "Reference cleared after work"); h.succeed();
    }
    @GameTest public void missingMaterialLeavesTheFloorWhileClearingAboveIt(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 5)); h.setBlock(WORK, Blocks.STONE); h.setBlock(WORK.above(), Blocks.DIRT);
        movement().visitNewPosition(context(owner), h.absolutePos(WORK)); finish(owner); h.assertBlockPresent(Blocks.STONE, WORK); h.assertBlockPresent(Blocks.AIR, WORK.above()); h.assertValueEqual(count(owner, Items.DIAMOND), 5L, "No floor replacement without material"); h.succeed();
    }
    @GameTest public void absentStockDoesNotRemoveTheLowerLayer(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 0); h.setBlock(WORK, Blocks.DIRT); h.setBlock(WORK.above(), Blocks.DIRT);
        movement().visitNewPosition(context(owner), h.absolutePos(WORK)); finish(owner); h.assertBlockPresent(Blocks.DIRT, WORK); h.assertBlockPresent(Blocks.AIR, WORK.above()); h.succeed();
    }
    @GameTest public void sourceAlreadyPavedSurfaceIsNotBrokenOrChargedAgain(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 4); h.setBlock(WORK, Blocks.STONE); h.setBlock(WORK.above(), Blocks.STONE_SLAB);
        var exposed = new Exposed(); h.assertTrue(exposed.targets(context(owner), h.absolutePos(WORK)).isEmpty(), "Original material and slab excluded"); exposed.pave(context(owner), h.absolutePos(WORK)); h.assertValueEqual(count(owner, Items.STONE), 4L, "No unnecessary consumption"); h.succeed();
    }
    @GameTest public void fillModesNeverExcavateTheColumn(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, 4); h.setBlock(WORK, Blocks.DIRT); h.setBlock(WORK.above(), Blocks.OBSIDIAN);
        movement().visitNewPosition(context(owner), h.absolutePos(WORK)); h.assertBlockPresent(Blocks.DIRT, WORK); h.assertBlockPresent(Blocks.OBSIDIAN, WORK.above()); h.assertBlockPresent(Blocks.STONE, WORK.below()); h.assertValueEqual(count(owner, Items.STONE), 3L, "Fill supports the preserved solid surface from below"); h.succeed();
    }
    @GameTest public void straightFillContinuesBelowMatchingPavedLayers(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, 4); h.setBlock(WORK, Blocks.STONE); h.setBlock(WORK.below(), Blocks.STONE);
        new Exposed().pave(context(owner), h.absolutePos(WORK)); h.assertBlockPresent(Blocks.STONE, WORK.below(2)); h.assertBlockPresent(Blocks.AIR, WORK.below(2).east()); h.assertValueEqual(count(owner, Items.STONE), 3L, "One column item"); h.succeed();
    }
    @GameTest public void wideFillUsesADiamondLayerWithoutFillingItsCorners(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.WIDE_FILL, 16); h.setBlock(WORK, Blocks.STONE); h.setBlock(WORK.below(), Blocks.STONE);
        new Exposed().pave(context(owner), h.absolutePos(WORK));
        for (var side : Direction.Plane.HORIZONTAL) h.assertBlockPresent(Blocks.STONE, WORK.below().relative(side)); h.assertBlockPresent(Blocks.AIR, WORK.below().north().east()); h.assertValueEqual(count(owner, Items.STONE), 12L, "Four diamond neighbors"); h.succeed();
    }
    @GameTest public void fillDepthLimitStopsBeforeTheNextEmptyLayer(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, 4); for (int i = 0; i <= 2; i++) h.setBlock(WORK.below(i), Blocks.STONE);
        int old = ContraptionSettings.rollerFillDepth; try { ContraptionSettings.rollerFillDepth = 2; new Exposed().pave(context(owner), h.absolutePos(WORK)); } finally { ContraptionSettings.rollerFillDepth = old; }
        h.assertBlockPresent(Blocks.AIR, WORK.below(3)); h.assertValueEqual(count(owner, Items.STONE), 4L, "No placement beyond configured depth"); h.succeed();
    }
    @GameTest public void exhaustedStockAndBlockedTargetsDoNotConsumeOtherItems(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, new ItemStack(Items.STONE), new ItemStack(Items.DIAMOND, 5)); var exposed = new Exposed();
        h.assertFalse(exposed.fill(context(owner), h.absolutePos(WORK), Blocks.STONE.defaultBlockState()), "Missing matching stock"); h.setBlock(WORK, Blocks.BEDROCK); h.assertFalse(exposed.fill(context(owner), h.absolutePos(WORK), Blocks.STONE.defaultBlockState()), "Blocked target");
        h.assertValueEqual(count(owner, Items.DIAMOND), 5L, "Other stock untouched"); h.succeed();
    }
    @GameTest public void pavingPreservesTheComponentsOfUnspentStock(GameTestHelper h) throws Exception {
        var stock = new ItemStack(Items.STONE, 5); stock.set(DataComponents.CUSTOM_NAME, Component.literal("named road")); var owner = moving(h, RollingMode.STRAIGHT_FILL, new ItemStack(Items.STONE), stock);
        new Exposed().pave(context(owner), h.absolutePos(WORK));
        var slot = owner.getContraption().getStorage().getAllItemStorages().get(new BlockPos(1, 0, 0)).getStackInSlot(0);
        h.assertTrue(slot.getCount() == 4 && ItemStack.isSameItemSameComponents(slot, stock), "Remaining components survive transaction"); h.succeed();
    }
    @GameTest public void leavesAndReplaceableVegetationCanBePavedButPortalsCannot(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, 5); var exposed = new Exposed(); h.setBlock(WORK, Blocks.OAK_LEAVES);
        h.assertTrue(exposed.fill(context(owner), h.absolutePos(WORK), Blocks.STONE.defaultBlockState()), "Leaf replacement"); h.setBlock(WORK, Blocks.DANDELION); h.assertTrue(exposed.fill(context(owner), h.absolutePos(WORK), Blocks.STONE.defaultBlockState()), "Vegetation replacement");
        h.setBlock(WORK, Blocks.NETHER_PORTAL); h.assertFalse(exposed.fill(context(owner), h.absolutePos(WORK), Blocks.STONE.defaultBlockState()), "Portal protection"); h.assertValueEqual(count(owner, Items.STONE), 3L, "Only successful placements charged"); h.succeed();
    }
    @GameTest public void miningRefusesTracksPortalsAndTheirAdjacentBlocks(GameTestHelper h) throws Exception {
        moving(h, RollingMode.TUNNEL_PAVE, 1); h.setBlock(WORK, Blocks.STONE); h.setBlock(WORK.east(), Blocks.NETHER_PORTAL);
        h.assertFalse(movement().canBreak(h.getLevel(), h.absolutePos(WORK), h.getBlockState(WORK)), "Portal adjacency protected"); h.setBlock(WORK.east(), Blocks.AIR); h.assertTrue(movement().canBreak(h.getLevel(), h.absolutePos(WORK), h.getBlockState(WORK)), "Ordinary terrain breakable");
        h.setBlock(WORK, Blocks.POLISHED_BASALT); h.assertFalse(movement().canBreak(h.getLevel(), h.absolutePos(WORK), h.getBlockState(WORK)), "Test-only track tag vetoes solid terrain"); h.succeed();
    }
    @GameTest public void slabsUseDoublePavingAndAProfileCanPlaceABottomSlabAbove(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, new ItemStack(Items.OAK_SLAB), new ItemStack(Items.OAK_SLAB, 5)); var exposed = new Exposed(); var target = h.absolutePos(WORK);
        exposed.profile = new PaveTask(0, 0); exposed.profile.put(target.getX(), target.getZ(), target.getY() + .5f); exposed.pave(context(owner), target);
        h.assertValueEqual(h.getBlockState(WORK).getValue(SlabBlock.TYPE), SlabType.DOUBLE, "Full paving slab"); h.assertValueEqual(h.getBlockState(WORK.above()).getValue(SlabBlock.TYPE), SlabType.BOTTOM, "Profile half height"); h.assertValueEqual(count(owner, Items.OAK_SLAB), 3L, "Original one-item-per-placement policy"); h.succeed();
    }
    @GameTest public void associatedSlabLookupUsesTheOriginalPluralAndPlanksRules(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS, 4));
        h.assertTrue(new Exposed().slab(context(owner)).is(Blocks.OAK_SLAB), "Planks slab lookup"); h.succeed();
    }
    @GameTest public void fallingMaterialFillsDownToAnExistingSupport(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, new ItemStack(Items.SAND), new ItemStack(Items.SAND, 8)); h.setBlock(WORK.below(3), Blocks.STONE);
        new Exposed().pave(context(owner), h.absolutePos(WORK)); for (int y = 0; y < 3; y++) h.assertBlockPresent(Blocks.SAND, WORK.below(y)); h.assertValueEqual(count(owner, Items.SAND), 5L, "Three falling-material layers charged"); h.succeed();
    }
    @GameTest public void pavingDelayPersistsAndDoesNotDuplicateThePlacedBlock(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 3); new Exposed().pave(context(owner), h.absolutePos(WORK)); var ctx = context(owner);
        h.assertTrue(ctx.stall && ctx.data.getIntOr("WaitingTicks", 0) == 2, "Source two-tick delay");
        var loaded = Contraption.fromNBT(h.getLevel(), owner.getContraption().writeNBT(h.getLevel().registryAccess(), false), false); var restored = loaded.getActorAt(new BlockPos(2, 0, 0)).right;
        for (int i = 0; i < 4; i++) movement().tick(restored); h.assertFalse(restored.stall, "Saved delay releases"); long remaining = 0; for (var view : loaded.getStorage().getAllItems()) if (view.getResource().isOf(Items.STONE)) remaining += view.getAmount(); h.assertValueEqual(remaining, 2L, "No duplicate paving on retry"); h.succeed();
    }
    @GameTest public void unfinishedColumnSaveRetainsProgressReferenceAndMaterial(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 5); h.setBlock(WORK.above(), Blocks.OBSIDIAN); movement().visitNewPosition(context(owner), h.absolutePos(WORK)); movement().tick(context(owner));
        var loaded = Contraption.fromNBT(h.getLevel(), owner.getContraption().writeNBT(h.getLevel().registryAccess(), false), false); var restored = loaded.getActorAt(new BlockPos(2, 0, 0)).right;
        h.assertTrue(restored.stall && restored.data.getIntOr("Progress", 0) > 0 && restored.data.read("ReferencePos", BlockPos.CODEC).orElseThrow().equals(h.absolutePos(WORK)), "Source saved column lifecycle"); h.assertTrue(new Exposed().material(restored).is(Blocks.STONE), "Material still configured"); h.succeed();
    }
    @GameTest public void disableAndStopClearColumnReferenceAndBreakingProgress(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 3); h.setBlock(WORK.above(), Blocks.OBSIDIAN); movement().visitNewPosition(context(owner), h.absolutePos(WORK)); movement().tick(context(owner));
        owner.getContraption().setActorsActive(ItemStack.EMPTY, false); h.assertTrue(!context(owner).stall && !context(owner).data.contains("ReferencePos") && !context(owner).data.contains("BreakingPos"), "Controls cancel unfinished work"); owner.getContraption().stop(h.getLevel()); h.assertValueEqual(count(owner, Items.STONE), 3L, "Cancel does not charge material"); h.succeed();
    }
    @GameTest public void miningToolTierBlocksDoNotYieldLootAndOrdinaryLootIsNeverDuplicated(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 1); var exposed = new Exposed(); h.setBlock(WORK, Blocks.IRON_BLOCK); exposed.destroy(context(owner), h.absolutePos(WORK)); h.assertValueEqual(count(owner, Items.IRON_BLOCK), 0L, "Tool-tier loot discarded");
        h.setBlock(WORK, Blocks.DIRT); exposed.destroy(context(owner), h.absolutePos(WORK)); h.assertTrue(count(owner, Items.DIRT) <= 1, "Random kept loot is collected once"); h.assertBlockPresent(Blocks.AIR, WORK); h.succeed();
    }
    @GameTest public void entityDamageUsesTheRollerTypeAndLeavesItemsAlone(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.TUNNEL_PAVE, 2); var pig = h.spawnWithNoFreeWill(EntityTypes.PIG, WORK); var item = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), pig.getX(), pig.getY(), pig.getZ(), new ItemStack(Items.DIAMOND)); h.getLevel().addFreshEntity(item); var velocity = item.getDeltaMovement();
        movement().damageEntities(context(owner), h.absolutePos(WORK), h.getLevel()); h.assertTrue(pig.getHealth() < pig.getMaxHealth() && item.getDeltaMovement().equals(velocity) && item.getItem().getCount() == 1, "Roller damage excludes items"); h.succeed();
    }
    @GameTest public void unloadedAndOutOfHeightQueriesDoNotChargeOrCreateChunks(GameTestHelper h) throws Exception {
        var owner = moving(h, RollingMode.STRAIGHT_FILL, 3); var far = new BlockPos(2000000, 40, 2000000); var exposed = new Exposed();
        movement().visitNewPosition(context(owner), far); h.assertFalse(exposed.fill(context(owner), far, Blocks.STONE.defaultBlockState()), "Unknown paving target denied");
        h.assertFalse(exposed.fill(context(owner), new BlockPos(h.absolutePos(WORK).getX(), h.getLevel().getMinY() - 1, h.absolutePos(WORK).getZ()), Blocks.STONE.defaultBlockState()), "World height denied");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null && count(owner, Items.STONE) == 3, "No load or resource charge"); h.succeed();
    }
    @GameTest public void liveSettingsAreCapturedAfterAnEarlierAssemblyPreview(GameTestHelper h) throws Exception {
        h.setBlock(P, Blocks.STONE); h.setBlock(TOOL, roller()); h.setBlock(P.east(), Blocks.BARREL); h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(P), h.absolutePos(TOOL))));
        var structure = new BearingContraption(false, Direction.UP); structure.assemble(h.getLevel(), h.absolutePos(B)); var be = h.getBlockEntity(TOOL, RollerBlockEntity.class); be.setMode(RollingMode.WIDE_FILL); be.filtering.setFilter(new ItemStack(Items.BRICKS)); structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO);
        var ctx = structure.getActorAt(new BlockPos(2, 0, 0)).right; h.assertTrue(ctx.blockEntityData.getIntOr("ScrollValue", 0) == 2 && new Exposed().material(ctx).is(Blocks.BRICKS), "Last world configuration captured"); h.succeed();
    }
    private static void machine(GameTestHelper h, RollingMode mode) {
        h.setBlock(P, Blocks.STONE); h.setBlock(P.east(), Blocks.BARREL); h.setBlock(TOOL, roller()); var be = h.getBlockEntity(TOOL, RollerBlockEntity.class); be.filtering.setFilter(new ItemStack(Items.STONE)); be.setMode(mode); h.getBlockEntity(P.east(), BarrelBlockEntity.class).setItem(0, new ItemStack(Items.STONE, 5));
        h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(P), h.absolutePos(TOOL)))); h.setBlock(B, AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP)); h.setBlock(B.below(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.UP));
    }
    @GameTest(maxTicks = 90) public void actualKineticBearingCutsAndPavesTheWorldFromMountedStock(GameTestHelper h) {
        h.setBlock(WORK, Blocks.DIRT); h.setBlock(WORK.above(), Blocks.DIRT); machine(h, RollingMode.TUNNEL_PAVE);
        h.runAfterDelay(50, () -> { var owner = h.getBlockEntity(B, MechanicalBearingBlockEntity.class).getMovedContraption(); h.assertTrue(owner != null, "Actual roller construct assembled"); h.assertBlockPresent(Blocks.STONE, WORK); h.assertBlockPresent(Blocks.AIR, WORK.above()); h.assertTrue(count(owner, Items.STONE) < 5, "Real mounted material spent"); h.succeed(); });
    }
    @GameTest(maxTicks = 55) public void worldRestorationKeepsUnspentMaterialAndRollerConfiguration(GameTestHelper h) {
        machine(h, RollingMode.STRAIGHT_FILL);
        h.runAfterDelay(12, () -> { var bearing = h.getBlockEntity(B, MechanicalBearingBlockEntity.class); var owner = bearing.getMovedContraption(); h.assertTrue(owner != null, "Real capture"); long remaining = count(owner, Items.STONE); var transform = new StructureTransform(h.absolutePos(P), 0, owner.getAngle(1), 0); var tool = transform.apply(new BlockPos(2, 0, 0)); var barrel = transform.apply(new BlockPos(1, 0, 0)); h.assertTrue(bearing.disassemble(), "Native disassembly"); var restored = (RollerBlockEntity) h.getLevel().getBlockEntity(tool); var inventory = (BarrelBlockEntity) h.getLevel().getBlockEntity(barrel); long n = 0; for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(Items.STONE)) n += inventory.getItem(i).getCount(); h.assertTrue(n == remaining && restored.getMode() == RollingMode.STRAIGHT_FILL && restored.filtering.getFilter().is(Items.STONE), "World settings and exact material restored"); h.succeed(); });
    }
}

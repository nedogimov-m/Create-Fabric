package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.redstone.contact.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public class ContactGameTests {
    private static final BlockPos P = new BlockPos(4, 5, 4), TARGET = P.north();
    private static BlockState contact(Direction facing) { return AllBlocks.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, facing); }
    private static boolean powered(GameTestHelper h, BlockPos pos) { return h.getLevel().getBlockState(h.absolutePos(pos)).getValue(RedstoneContactBlock.POWERED); }
    private static void pair(GameTestHelper h, Direction facing) {
        h.setBlock(P, contact(facing)); h.setBlock(P.relative(facing), contact(facing.getOpposite()));
        h.runAfterDelay(3, () -> { h.assertTrue(powered(h, P) && powered(h, P.relative(facing)), "Opposing contacts power each other"); h.setBlock(P.relative(facing), Blocks.AIR); h.runAfterDelay(3, () -> { h.assertFalse(powered(h, P), "Breaking mate clears signal"); h.succeed(); }); });
    }
    private static ControlledContraptionEntity moving(GameTestHelper h) throws Exception {
        h.setBlock(P, contact(Direction.NORTH)); var structure = new BearingContraption(false, Direction.UP);
        h.assertTrue(structure.assemble(h.getLevel(), h.absolutePos(P.below())), "Capture"); h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO), "Take ownership");
        var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel()); owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P))); h.getLevel().addFreshEntity(owner); owner.tickActors(); return owner;
    }
    private static MovementContext ctx(ControlledContraptionEntity owner) { return owner.getContraption().getActorAt(BlockPos.ZERO).right; }
    private static ContactMovementBehaviour actor() { return (ContactMovementBehaviour) MovementBehaviour.REGISTRY.get(AllBlocks.REDSTONE_CONTACT); }
    private static void activate(GameTestHelper h, ControlledContraptionEntity owner) { ctx(owner).firstMovement = false; actor().visitNewPosition(ctx(owner), h.absolutePos(TARGET)); }
    @GameTest public void northPair(GameTestHelper h) { pair(h, Direction.NORTH); }
    @GameTest public void southPair(GameTestHelper h) { pair(h, Direction.SOUTH); }
    @GameTest public void eastPair(GameTestHelper h) { pair(h, Direction.EAST); }
    @GameTest public void westPair(GameTestHelper h) { pair(h, Direction.WEST); }
    @GameTest public void upwardPair(GameTestHelper h) { pair(h, Direction.UP); }
    @GameTest public void downwardPair(GameTestHelper h) { pair(h, Direction.DOWN); }
    @GameTest public void parallelContactsDoNotActivate(GameTestHelper h) {
        h.setBlock(P, contact(Direction.NORTH)); h.setBlock(TARGET, contact(Direction.NORTH)); h.runAfterDelay(3, () -> { h.assertTrue(!powered(h, P) && !powered(h, TARGET), "Facing must oppose"); h.succeed(); });
    }
    @GameTest public void sourceOutputsAndWireConnectionsExcludeTheFrontSideArgument(GameTestHelper h) {
        for (var facing : Direction.values()) { var state = contact(facing).setValue(RedstoneContactBlock.POWERED, true); for (var side : Direction.values()) {
            h.assertValueEqual(state.getSignal(h.getLevel(), h.absolutePos(P), side), side == facing.getOpposite() ? 0 : 15, "Directional source signal");
            h.assertTrue(state.shouldRedstoneWireConnectTo(h.getLevel(), h.absolutePos(P), side) == (side != facing.getOpposite()), "Native wire connectivity");
        } } h.succeed();
    }
    @GameTest public void unpoweredContactDoesNotRelayNeighborStrongPower(GameTestHelper h) {
        h.setBlock(P, contact(Direction.NORTH)); h.setBlock(P.east(), Blocks.REDSTONE_BLOCK);
        for (var side : Direction.values()) h.assertValueEqual(h.getLevel().getSignal(h.absolutePos(P), side), 0, "No NeoForge weak-power recheck/conduction"); h.succeed();
    }
    @GameTest public void actualWireTurnsOffWhenStaticPairIsBroken(GameTestHelper h) {
        h.setBlock(P.east().below(), Blocks.STONE); h.setBlock(P.east(2).below(), Blocks.STONE); h.setBlock(P.east(), Blocks.REDSTONE_WIRE); h.setBlock(P.east(2), Blocks.REDSTONE_WIRE);
        h.setBlock(P, contact(Direction.NORTH)); h.setBlock(TARGET, contact(Direction.SOUTH));
        h.runAfterDelay(4, () -> { h.assertValueEqual(h.getLevel().getBlockState(h.absolutePos(P.east())).getValue(RedstoneWireBlock.POWER), 15, "Real wire receives signal (contact="+h.getLevel().getBlockState(h.absolutePos(P))+", wire="+h.getLevel().getBlockState(h.absolutePos(P.east()))+", neighbor="+h.getLevel().getBestNeighborSignal(h.absolutePos(P.east()))+")"); h.setBlock(TARGET, Blocks.AIR); h.runAfterDelay(4, () -> { h.assertValueEqual(h.getLevel().getBlockState(h.absolutePos(P.east(2))).getValue(RedstoneWireBlock.POWER), 0, "Real wire clears"); h.succeed(); }); });
    }
    @GameTest public void ordinaryPlacementUsesOppositeLookWhileShiftUsesClickedFace(GameTestHelper h) {
        h.setBlock(P.below(), Blocks.STONE); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0, 0, 3)); player.setXRot(0); player.setYRot(180); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.REDSTONE_CONTACT));
        var support = h.absolutePos(P.below()); var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false);
        var input = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertValueEqual(AllBlocks.REDSTONE_CONTACT.getStateForPlacement(input).getValue(RedstoneContactBlock.FACING), input.getNearestLookingDirection().getOpposite(), "Source opposite look");
        player.setShiftKeyDown(true); h.assertValueEqual(AllBlocks.REDSTONE_CONTACT.getStateForPlacement(input).getValue(RedstoneContactBlock.FACING), Direction.DOWN, "Source sneak placement"); h.succeed();
    }
    @GameTest public void placementPrefersMatchingContactUnderClickedFace(GameTestHelper h) {
        h.setBlock(P.below(), contact(Direction.UP)); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0, 0, 3)); player.setXRot(0); player.setYRot(180); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.REDSTONE_CONTACT));
        var support = h.absolutePos(P.below()); var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false); var state = AllBlocks.REDSTONE_CONTACT.getStateForPlacement(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)));
        h.assertTrue(state.getValue(RedstoneContactBlock.FACING) == Direction.DOWN && state.getValue(RedstoneContactBlock.POWERED), "Matching clicked mate wins over look"); h.succeed();
    }
    @GameTest public void wrenchRotatesAroundClickedAxisAndUpdatesMate(GameTestHelper h) {
        h.setBlock(P, contact(Direction.NORTH)); h.setBlock(TARGET, contact(Direction.SOUTH)); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH));
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)), Direction.UP, h.absolutePos(P), false); var result = AllItems.WRENCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(result == InteractionResult.SUCCESS && h.getLevel().getBlockState(h.absolutePos(P)).getValue(RedstoneContactBlock.FACING) == Direction.EAST, "Native wrench route"); h.runAfterDelay(3, () -> { h.assertFalse(powered(h, TARGET), "Old mate loses power"); h.succeed(); });
    }
    @GameTest public void shiftWrenchRefundsOneContact(GameTestHelper h) {
        h.setBlock(P, contact(Direction.NORTH)); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(true); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH));
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)), Direction.UP, h.absolutePos(P), false); AllItems.WRENCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        int count = 0; for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(AllBlocks.REDSTONE_CONTACT.asItem())) count += player.getInventory().getItem(i).getCount(); h.assertTrue(count == 1 && h.getLevel().getBlockState(h.absolutePos(P)).isAir(), "One source loot refund"); h.succeed();
    }
    @GameTest public void wrenchRecomputesActualWireConnectionWithNewFacing(GameTestHelper h) {
        h.setBlock(P.east().below(), Blocks.STONE); h.setBlock(P.east().east().below(), Blocks.STONE); h.setBlock(P, contact(Direction.NORTH)); h.setBlock(P.east(), Blocks.REDSTONE_WIRE); h.setBlock(P.east(2), Blocks.REDSTONE_WIRE);
        h.setBlock(P.east().north().below(), Blocks.STONE); h.setBlock(P.east().north(), Blocks.REDSTONE_WIRE);
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH));
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)), Direction.UP, h.absolutePos(P), false);
        AllItems.WRENCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(P.east())).getValue(RedstoneWireBlock.WEST) == net.minecraft.world.level.block.state.properties.RedstoneSide.NONE, "New front side no longer connects wire"); h.succeed();
    }
    @GameTest public void loadedShapeReaderDoesNotCreateMissingChunks(GameTestHelper h) {
        var reader = new com.simibubi.create.foundation.utility.LoadedLevelReader(h.getLevel()); var far = new BlockPos(2000000, 60, 2000000);
        h.setBlock(P.above(4), Blocks.STONE); var column = h.absolutePos(P);
        h.assertValueEqual(reader.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()), h.absolutePos(P.above(5)).getY(), "Height is first free block above the column");
        h.assertValueEqual(reader.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 30000000, 30000000), h.getLevel().getSeaLevel() + 1, "Native out-of-bounds height");
        h.assertTrue(reader.getChunk(far.getX() >> 4, far.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true) == null && reader.getBlockState(far).isAir() && reader.getBlockEntity(far) == null && reader.getChunkForCollisions(far.getX() >> 4, far.getZ() >> 4) == null, "Even create=true remains read-only");
        reader.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ()); reader.getBiome(far);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Indirect height/biome reads leave chunk absent"); h.succeed();
    }
    @GameTest public void firstMovingVisitDoesNotActivateOrClearPreviousData(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.SOUTH)); ctx(owner).data.store(ContactMovementBehaviour.LAST_CONTACT, BlockPos.CODEC, h.absolutePos(P.east(3))); actor().visitNewPosition(ctx(owner), h.absolutePos(TARGET));
        h.assertTrue(!powered(h, TARGET) && ctx(owner).data.contains(ContactMovementBehaviour.LAST_CONTACT), "First movement is ignored entirely"); owner.discard(); h.succeed();
    }
    @GameTest(maxTicks=60) public void stationaryMovingContactMaintainsPowerAndDisableReleasesIt(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.SOUTH)); activate(h, owner); h.assertTrue(powered(h, TARGET), "Immediate source activation");
        h.runAfterDelay(20, () -> { h.assertTrue(powered(h, TARGET), "Standing contact remains active"); owner.getContraption().setActorsActive(new ItemStack(AllBlocks.REDSTONE_CONTACT), false); h.runAfterDelay(3, () -> { h.assertTrue(!powered(h, TARGET) && !ctx(owner).data.contains(ContactMovementBehaviour.LAST_CONTACT), "Disable clears key and real signal"); owner.discard(); h.succeed(); }); });
    }
    @GameTest public void movingWrongFacingDoesNotPowerTarget(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.NORTH)); activate(h, owner); h.assertFalse(powered(h, TARGET), "Opposing nearest direction required"); h.assertFalse(ctx(owner).data.contains(ContactMovementBehaviour.LAST_CONTACT), "No active target recorded"); owner.discard(); h.succeed();
    }
    @GameTest public void rotatedContactUsesTransformedFacingAndKeepsSourceNearestRule(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.SOUTH)); owner.setAngle(30); owner.tickActors(); ctx(owner).firstMovement = false; actor().visitNewPosition(ctx(owner), h.absolutePos(TARGET));
        h.assertTrue(powered(h, TARGET), "Source nearest cardinal accepts a thirty-degree normal"); owner.discard(); h.succeed();
    }
    @GameTest public void departureSchedulesOldContactOffAndDoesNotStrandPower(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.SOUTH)); activate(h, owner); owner.setPos(owner.position().add(2, 0, 0)); owner.tickActors();
        h.runAfterDelay(3, () -> { h.assertFalse(powered(h, TARGET), "Previous world contact turns off"); owner.discard(); h.succeed(); });
    }
    @GameTest public void stopMovingClearsLastContactAndReleasesWorldSignal(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.SOUTH)); activate(h, owner); owner.getContraption().stop(h.getLevel());
        h.runAfterDelay(3, () -> { h.assertFalse(powered(h, TARGET), "Stop releases signal"); owner.discard(); h.succeed(); });
    }
    @GameTest public void disablingMovingContactKeepsARealStaticPairPowered(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.SOUTH)); activate(h, owner); h.setBlock(P, contact(Direction.NORTH)); owner.getContraption().setActorsActive(ItemStack.EMPTY, false);
        h.runAfterDelay(3, () -> { h.assertTrue(powered(h, TARGET) && powered(h, P), "A genuine static pair remains active"); owner.discard(); h.succeed(); });
    }
    @GameTest public void unloadedVisitAndCleanupNeverLoadTargetChunk(GameTestHelper h) throws Exception {
        var owner = moving(h); var far = new BlockPos(2000000, 60, 2000000); ctx(owner).firstMovement = false; actor().visitNewPosition(ctx(owner), far); ctx(owner).data.store(ContactMovementBehaviour.LAST_CONTACT, BlockPos.CODEC, far); ctx(owner).stall = true; actor().cancelStall(ctx(owner));
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null && !ctx(owner).data.contains(ContactMovementBehaviour.LAST_CONTACT) && !ctx(owner).stall, "Loaded-only visit/stop/cancel"); owner.discard(); h.succeed();
    }
    @GameTest public void actorDataPersistsLastWorldContactAndDisabledPolicy(GameTestHelper h) throws Exception {
        var owner = moving(h); h.setBlock(TARGET, contact(Direction.SOUTH)); activate(h, owner); var saved = owner.getContraption().writeNBT(h.getLevel().registryAccess(), false); var restored = Contraption.fromNBT(h.getLevel(), saved, false).getActorAt(BlockPos.ZERO).right;
        h.assertTrue(restored.data.read(ContactMovementBehaviour.LAST_CONTACT, BlockPos.CODEC).orElseThrow().equals(h.absolutePos(TARGET)) && actor().canBeDisabledVia(restored).is(AllBlocks.REDSTONE_CONTACT.asItem()), "Native persistent actor state/control reference"); owner.discard(); h.succeed();
    }
    @GameTest public void orphanPoweredContactRevalidatesWithoutAWorldPair(GameTestHelper h) {
        h.setBlock(P, contact(Direction.NORTH).setValue(RedstoneContactBlock.POWERED, true)); h.runAfterDelay(3, () -> { h.assertFalse(powered(h, P), "Native scheduled tick recovers orphan signal"); h.succeed(); });
    }
}

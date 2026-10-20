package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.psi.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

public class PortableInterfaceGameTests {
    private static final BlockPos P = new BlockPos(3, 4, 4), B = P.below(), T = P.north(2);
    private static final long BUCKET = FluidConstants.BUCKET;
    private static final PortableStorageInterfaceMovement MOVEMENT = new PortableStorageInterfaceMovement();
    private static BlockState state(boolean fluids, Direction direction) {
        return (fluids ? AllBlocks.PORTABLE_FLUID_INTERFACE : AllBlocks.PORTABLE_STORAGE_INTERFACE)
            .defaultBlockState().setValue(PortableStorageInterfaceBlock.FACING, direction);
    }
    private static void worldBlocks(GameTestHelper h, boolean fluids) {
        h.setBlock(P, state(fluids, Direction.NORTH));
        h.setBlock(P.east(), Blocks.BARREL);
        h.getBlockEntity(P.east(), BarrelBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 9));
        if (fluids) {
            h.setBlock(P.south(), AllBlocks.FLUID_TANK);
            try (var tx = Transaction.openOuter()) {
                h.getBlockEntity(P.south(), FluidTankBlockEntity.class).getTankInventory().insert(FluidVariant.of(Fluids.WATER), 3 * BUCKET + 1, tx);
                tx.commit();
            }
        }
        h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(P), h.absolutePos(P.east().south()))));
    }
    private static ControlledContraptionEntity moving(GameTestHelper h, boolean fluids) throws Exception {
        worldBlocks(h, fluids);
        var structure = new BearingContraption(false, Direction.UP);
        h.assertTrue(structure.assemble(h.getLevel(), h.absolutePos(B)), "Real capture");
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO), "Mounted ownership");
        var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel());
        owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P))); owner.tickActors();
        return owner;
    }
    private static MovementContext context(ControlledContraptionEntity owner) { return owner.getContraption().getActorAt(BlockPos.ZERO).right; }
    private static PortableStorageInterfaceBlockEntity endpoint(GameTestHelper h, boolean fluids) {
        h.setBlock(T, state(fluids, Direction.SOUTH));
        return fluids ? h.getBlockEntity(T, PortableFluidInterfaceBlockEntity.class) : h.getBlockEntity(T, PortableItemInterfaceBlockEntity.class);
    }
    private static PortableItemInterfaceBlockEntity connectItems(GameTestHelper h, ControlledContraptionEntity owner) {
        var endpoint = (PortableItemInterfaceBlockEntity) endpoint(h, false);
        MOVEMENT.visitNewPosition(context(owner), h.absolutePos(T));
        h.assertTrue(endpoint.canTransfer(), "Real item connection"); return endpoint;
    }
    private static PortableFluidInterfaceBlockEntity connectFluids(GameTestHelper h, ControlledContraptionEntity owner) {
        var endpoint = (PortableFluidInterfaceBlockEntity) endpoint(h, true);
        MOVEMENT.visitNewPosition(context(owner), h.absolutePos(T));
        h.assertTrue(endpoint.canTransfer(), "Real fluid connection"); return endpoint;
    }
    private static long items(ControlledContraptionEntity owner, Item item) {
        long n = 0; for (var view : owner.getContraption().getStorage().getAllItems()) if (view.getResource().isOf(item)) n += view.getAmount(); return n;
    }
    private static long fluid(ControlledContraptionEntity owner) {
        long n = 0; for (var view : owner.getContraption().getStorage().getFluids()) n += view.getAmount(); return n;
    }
    private static StorageView<ItemVariant> diamondView(Storage<ItemVariant> storage) {
        for (var view : storage) if (view.getResource().isOf(Items.DIAMOND)) return view; throw new AssertionError("Diamond slot missing");
    }
    private static void expire(PortableStorageInterfaceBlockEntity endpoint) { endpoint.keepAlive = 1; endpoint.tick(); }

    @GameTest public void itemLookupExposesAllSidesOfTheRealMountedInventory(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        for (var direction : Direction.values()) {
            var storage = ItemStorage.SIDED.find(h.getLevel(), h.absolutePos(T), direction);
            h.assertTrue(storage != null && diamondView(storage).getAmount() == 9, "Connected side: " + direction);
        }
        h.assertValueEqual(endpoint.getTransferTimer(), 14, "Original initial timer"); h.succeed();
    }
    @GameTest public void committedItemExtractionExtendsTimeoutOnlyAfterCommit(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(endpoint.storage().extract(ItemVariant.of(Items.DIAMOND), 2, tx), 2L, "Extraction");
            h.assertValueEqual(endpoint.getTransferTimer(), 14, "No premature notification"); tx.commit();
        }
        h.assertValueEqual(items(owner, Items.DIAMOND), 7L, "Actual stock changed");
        h.assertValueEqual(endpoint.getTransferTimer(), 64, "Committed source timeout"); h.succeed();
    }
    @GameTest public void abortedItemTransferRestoresStockWithoutExtendingTimeout(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        try (var tx = Transaction.openOuter()) { endpoint.storage().extract(ItemVariant.of(Items.DIAMOND), 2, tx); }
        h.assertTrue(items(owner, Items.DIAMOND) == 9 && endpoint.getTransferTimer() == 14, "Abort preserves stock/timer"); h.succeed();
    }
    @GameTest public void nestedRollbackAndCommittedOuterDoNotEmitFalseTransfer(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        try (var outer = Transaction.openOuter()) {
            try (var nested = outer.openNested()) { endpoint.storage().extract(ItemVariant.of(Items.DIAMOND), 2, nested); }
            outer.commit();
        }
        h.assertTrue(items(owner, Items.DIAMOND) == 9 && endpoint.getTransferTimer() == 14, "Aborted nested transfer stays silent"); h.succeed();
    }
    @GameTest public void committedNestedTransferStillRollsBackWithItsOuter(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        try (var outer = Transaction.openOuter()) {
            try (var nested = outer.openNested()) { endpoint.storage().extract(ItemVariant.of(Items.DIAMOND), 2, nested); nested.commit(); }
        }
        h.assertTrue(items(owner, Items.DIAMOND) == 9 && endpoint.getTransferTimer() == 14, "Outer abort restores committed child"); h.succeed();
    }
    @GameTest public void insertedComponentsAndMultipleOperationsSurviveOneCommit(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal("portable components"));
        var variant = ItemVariant.of(stack);
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(endpoint.storage().insert(variant, 3, tx), 3L, "Component insert");
            h.assertValueEqual(endpoint.storage().extract(variant, 1, tx), 1L, "Same component extraction"); tx.commit();
        }
        long amount = 0; for (var view : owner.getContraption().getStorage().getAllItems()) if (view.getResource().equals(variant)) amount += view.getAmount();
        h.assertValueEqual(amount, 2L, "Exact component variant"); h.assertValueEqual(endpoint.getTransferTimer(), 64, "Committed timeout"); h.succeed();
    }
    @GameTest public void noOpExtractionAndFullInsertDoNotExtendConnection(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        var inventory = owner.getContraption().getStorage().getAllItemStorages().get(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < inventory.getSlotCount(); slot++) inventory.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(endpoint.storage().insert(ItemVariant.of(Items.IRON_INGOT), 1, tx), 0L, "Full insertion");
            h.assertValueEqual(endpoint.storage().extract(ItemVariant.of(Items.DIAMOND), 1, tx), 0L, "Missing resource"); tx.commit();
        }
        h.assertValueEqual(endpoint.getTransferTimer(), 14, "No transfer notification"); h.succeed();
    }
    @GameTest public void cachedStorageAndViewExpireWithKeepAliveAndDoNotExposeStock(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); var storage = endpoint.storage(); var view = diamondView(storage);
        expire(endpoint);
        h.assertTrue(view.isResourceBlank() && view.getAmount() == 0 && view.getCapacity() == 0 && !storage.supportsExtraction(), "Expired views are empty");
        try (var tx = Transaction.openOuter()) {
            h.assertTrue(view.extract(ItemVariant.of(Items.DIAMOND), 1, tx) == 0 && storage.extract(ItemVariant.of(Items.DIAMOND), 1, tx) == 0, "No stale extraction"); tx.commit();
        }
        h.assertValueEqual(items(owner, Items.DIAMOND), 9L, "Stock remains owned"); h.succeed();
    }
    @GameTest public void aNewConnectionDoesNotRetargetAnOldCachedHandle(GameTestHelper h) throws Exception {
        var first = moving(h, false); var endpoint = connectItems(h, first); var old = endpoint.storage(); var view = diamondView(old);
        var second = moving(h, false); endpoint.startTransferringTo(second.getContraption(), 1);
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(old.extract(ItemVariant.of(Items.DIAMOND), 3, tx), 0L, "Old storage expired");
            h.assertValueEqual(view.extract(ItemVariant.of(Items.DIAMOND), 3, tx), 0L, "Old view expired");
            h.assertValueEqual(endpoint.storage().extract(ItemVariant.of(Items.DIAMOND), 2, tx), 2L, "New connection works"); tx.commit();
        }
        h.assertTrue(items(first, Items.DIAMOND) == 9 && items(second, Items.DIAMOND) == 7, "Independent owners"); h.succeed();
    }
    @GameTest public void underlyingViewIdentityMatchesTheMountedSlot(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        h.assertTrue(diamondView(endpoint.storage()).getUnderlyingView() == diamondView(owner.getContraption().getStorage().getAllItems()).getUnderlyingView(), "Fabric identity contract"); h.succeed();
    }
    @GameTest public void keepAliveExpiryDisconnectsAndRetractsTheSourceTimer(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); endpoint.keepAlive = 2; endpoint.tick();
        h.assertTrue(endpoint.getConnectedEntity() == owner && endpoint.keepAlive == 1, "First keepAlive tick"); endpoint.tick();
        h.assertTrue(endpoint.getConnectedEntity() == null && endpoint.getTransferTimer() == 3 && !endpoint.canTransfer(), "Expires at source ANIMATION-1"); h.succeed();
    }
    @GameTest public void redstoneDisconnectsAndUnpowerRefreshesTheStationaryActor(GameTestHelper h) throws Exception {
        var owner = moving(h, false); h.getLevel().addFreshEntity(owner); var endpoint = connectItems(h, owner); var old = endpoint.storage();
        h.setBlock(T.east(), Blocks.REDSTONE_BLOCK); endpoint.neighbourChanged();
        h.assertTrue(endpoint.isPowered() && !endpoint.canTransfer(), "Power refuses transfer");
        try (var tx = Transaction.openOuter()) { h.assertValueEqual(old.extract(ItemVariant.of(Items.DIAMOND), 1, tx), 0L, "Powered stale handle denied"); }
        h.setBlock(T.east(), Blocks.AIR); endpoint.neighbourChanged();
        h.assertTrue(!endpoint.isPowered() && endpoint.getConnectedEntity() == owner && endpoint.canTransfer(), "Unpower rechecks actor without movement"); h.succeed();
    }
    @GameTest public void poweredTargetIsNotChosen(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = endpoint(h, false); h.setBlock(T.east(), Blocks.REDSTONE_BLOCK); endpoint.neighbourChanged();
        MOVEMENT.visitNewPosition(context(owner), h.absolutePos(T));
        h.assertTrue(!context(owner).data.contains(PortableStorageInterfaceMovement.WORKING_POS) && endpoint.getConnectedEntity() == null, "Powered endpoint excluded"); h.succeed();
    }
    @GameTest public void wrongTypeAndSameFacingAreExcluded(GameTestHelper h) throws Exception {
        var owner = moving(h, false);
        for (var state : List.of(state(true, Direction.SOUTH), state(false, Direction.NORTH))) {
            h.setBlock(T, state); MOVEMENT.visitNewPosition(context(owner), h.absolutePos(T));
            h.assertFalse(context(owner).data.contains(PortableStorageInterfaceMovement.WORKING_POS), "Wrong endpoint excluded");
        }
        h.succeed();
    }
    @GameTest public void twoSearchCellsProduceTheOriginalOneAndTwoBlockDistances(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var first = connectItems(h, owner);
        h.assertTrue(Math.abs(first.getConnectionDistance() - 1) < 1e-5, "Near connection");
        h.setBlock(T, Blocks.AIR); h.setBlock(T.north(), state(false, Direction.SOUTH));
        MOVEMENT.visitNewPosition(context(owner), h.absolutePos(T));
        var next = h.getBlockEntity(T.north(), PortableItemInterfaceBlockEntity.class);
        h.assertTrue(next.getConnectedEntity() == owner && Math.abs(next.getConnectionDistance() - 2) < 1e-5, "Far connection"); h.succeed();
    }
    @GameTest public void allSixDirectionsUseTheTransformedActivePoint(GameTestHelper h) throws Exception {
        var owner = moving(h, false);
        for (var direction : Direction.values()) {
            var ctx = new MovementContext(h.getLevel(), new StructureBlockInfo(BlockPos.ZERO, state(false, direction), null), owner.getContraption());
            ctx.position = Vec3.atCenterOf(h.absolutePos(P)).add(MOVEMENT.getActiveAreaOffset(ctx));
            var target = P.relative(direction, 2); h.setBlock(target, state(false, direction.getOpposite()));
            MOVEMENT.visitNewPosition(ctx, h.absolutePos(target));
            h.assertTrue(ctx.data.read(PortableStorageInterfaceMovement.WORKING_POS, BlockPos.CODEC).orElseThrow().equals(h.absolutePos(target)), "Direction: " + direction);
        }
        h.succeed();
    }
    @GameTest public void cardinalToleranceUsesRotatedFacingRatherThanTheOriginalBlock(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var ctx = context(owner);
        ctx.rotation = vector -> StructureTransform.rotate(vector, 28, Direction.Axis.Y);
        h.assertTrue(MOVEMENT.getCurrentFacingIfValid(ctx).isPresent(), "Within source half-unit tolerance");
        ctx.rotation = vector -> StructureTransform.rotate(vector, 30, Direction.Axis.Y);
        h.assertTrue(MOVEMENT.getCurrentFacingIfValid(ctx).isEmpty(), "Diagonal rejected");
        ctx.rotation = vector -> StructureTransform.rotate(vector, 90, Direction.Axis.Y);
        h.assertValueEqual(MOVEMENT.getCurrentFacingIfValid(ctx).orElseThrow(), Direction.WEST, "Actual transformed direction"); h.succeed();
    }
    @GameTest public void movingAwayStallsAndTimeoutReleasesTheActor(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); var ctx = context(owner);
        ctx.position = Vec3.atCenterOf(h.absolutePos(T)).add(0, 0, -.1); ctx.motion = new Vec3(0, 0, -.1); MOVEMENT.tick(ctx);
        h.assertTrue(ctx.stall && endpoint.keepAlive == 2, "Moving away stalls");
        for (int i = 0; i < 12; i++) { endpoint.tick(); MOVEMENT.tick(ctx); }
        h.assertFalse(ctx.stall, "Original timer releases stall"); h.succeed();
    }
    @GameTest public void invalidAlignmentMissingEndpointAndDisableClearTheStall(GameTestHelper h) throws Exception {
        var owner = moving(h, false); connectItems(h, owner); var ctx = context(owner); ctx.stall = true;
        ctx.rotation = vector -> StructureTransform.rotate(vector, 45, Direction.Axis.Y); MOVEMENT.tick(ctx);
        h.assertTrue(!ctx.stall && !ctx.data.contains(PortableStorageInterfaceMovement.WORKING_POS), "Invalid alignment reset");
        ctx.rotation = vector -> vector; MOVEMENT.visitNewPosition(ctx, h.absolutePos(T)); ctx.stall = true; h.setBlock(T, Blocks.AIR); MOVEMENT.tick(ctx);
        h.assertFalse(ctx.stall, "Missing endpoint reset"); ctx.stall = true; MOVEMENT.onDisabledByControls(ctx); h.assertFalse(ctx.stall, "Disable releases"); h.succeed();
    }
    @GameTest public void busyEndpointDoesNotTrapAnotherConstruct(GameTestHelper h) throws Exception {
        var first = moving(h, false); var endpoint = connectItems(h, first); var second = moving(h, false);
        MOVEMENT.visitNewPosition(context(second), h.absolutePos(T)); MOVEMENT.tick(context(second));
        h.assertTrue(endpoint.getConnectedEntity() == first && !context(second).stall && !context(second).data.contains(PortableStorageInterfaceMovement.WORKING_POS), "Exclusive live connection"); h.succeed();
    }
    @GameTest public void repeatedDiscoveryOfTheSameOwnerDoesNotResetACommittedTimeout(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); endpoint.onContentTransferred();
        var handle = endpoint.storage(); MOVEMENT.visitNewPosition(context(owner), h.absolutePos(T));
        h.assertTrue(endpoint.getTransferTimer() == 64 && endpoint.storage() == handle, "No timeout/handle reset for same owner"); h.succeed();
    }
    @GameTest public void fluidLookupTransfersExactNativeUnitsAndExtendsOnCommit(GameTestHelper h) throws Exception {
        var owner = moving(h, true); var endpoint = connectFluids(h, owner);
        var lookup = FluidStorage.SIDED.find(h.getLevel(), h.absolutePos(T), Direction.DOWN);
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(lookup.extract(FluidVariant.of(Fluids.WATER), BUCKET + 1, tx), BUCKET + 1, "Exact fluid units");
            h.assertValueEqual(endpoint.getTransferTimer(), 14, "No simulated timeout"); tx.commit();
        }
        h.assertValueEqual(fluid(owner), 2 * BUCKET, "No unit truncation"); h.assertValueEqual(endpoint.getTransferTimer(), 64, "Fluid commit timeout"); h.succeed();
    }
    @GameTest public void fluidRollbackAndForeignFluidDoNotChangeContentsOrTimer(GameTestHelper h) throws Exception {
        var owner = moving(h, true); var endpoint = connectFluids(h, owner);
        try (var outer = Transaction.openOuter()) {
            try (var child = outer.openNested()) { endpoint.storage().extract(FluidVariant.of(Fluids.WATER), BUCKET + 1, child); child.commit(); }
        }
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(endpoint.storage().insert(FluidVariant.of(Fluids.LAVA), BUCKET, tx), 0L, "Tank refuses mixing"); tx.commit();
        }
        h.assertTrue(fluid(owner) == 3 * BUCKET + 1 && endpoint.getTransferTimer() == 14, "Fluid rollback and no-op stay silent"); h.succeed();
    }
    @GameTest public void fluidInsertionAndCachedFluidViewObeyConnectionLifetime(GameTestHelper h) throws Exception {
        var owner = moving(h, true); var endpoint = connectFluids(h, owner); var handle = endpoint.storage(); var view = handle.iterator().next();
        try (var tx = Transaction.openOuter()) { h.assertValueEqual(handle.insert(FluidVariant.of(Fluids.WATER), 7, tx), 7L, "Microunit insertion"); tx.commit(); }
        expire(endpoint);
        try (var tx = Transaction.openOuter()) { h.assertValueEqual(view.extract(FluidVariant.of(Fluids.WATER), 1, tx), 0L, "Old fluid view denied"); tx.commit(); }
        h.assertTrue(view.getAmount() == 0 && fluid(owner) == 3 * BUCKET + 8, "Exact amount survives expiry"); h.succeed();
    }
    @GameTest public void removedEndpointAndDeadOwnerImmediatelyRefuseCachedAccess(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); var handle = endpoint.storage();
        owner.discard();
        try (var tx = Transaction.openOuter()) { h.assertValueEqual(handle.extract(ItemVariant.of(Items.DIAMOND), 1, tx), 0L, "Removed owner denied"); }
        endpoint.tick(); h.assertTrue(endpoint.getConnectedEntity() == null, "Dead link cleared");
        var next = moving(h, false); endpoint.startTransferringTo(next.getContraption(), 1); handle = endpoint.storage(); h.setBlock(T, Blocks.AIR);
        try (var tx = Transaction.openOuter()) { h.assertValueEqual(handle.extract(ItemVariant.of(Items.DIAMOND), 1, tx), 0L, "Removed endpoint denied"); }
        h.succeed();
    }
    @GameTest public void rotationImmediatelyInvalidatesAnOldHandle(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); var handle = endpoint.storage();
        h.setBlock(T, state(false, Direction.EAST));
        try (var tx = Transaction.openOuter()) { h.assertValueEqual(handle.extract(ItemVariant.of(Items.DIAMOND), 1, tx), 0L, "Wrong facing invalidates access"); }
        endpoint.tick(); h.assertTrue(endpoint.getConnectedEntity() == null, "Rotated link cleared"); h.succeed();
    }
    @GameTest public void endpointSaveContainsTimerDistancePowerButNoPrivateStock(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); endpoint.onContentTransferred();
        var tag = endpoint.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = new PortableItemInterfaceBlockEntity(h.absolutePos(T), state(false, Direction.SOUTH));
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), tag));
        h.assertTrue(restored.getTransferTimer() == 64 && restored.getConnectionDistance() == endpoint.getConnectionDistance() && restored.getConnectedEntity() == null, "Saved scalars, transient live link");
        h.assertTrue(restored.storage().iterator().hasNext() == false && !tag.contains("Items") && !tag.contains("Storage"), "No duplicate private mounted inventory"); h.succeed();
    }
    @GameTest public void workingPositionSurvivesNativeContraptionSaveAndCanReconnect(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner); var ctx = context(owner); ctx.stall = true;
        var loaded = Contraption.fromNBT(h.getLevel(), owner.getContraption().writeNBT(h.getLevel().registryAccess(), false), false);
        var restored = loaded.getActorAt(BlockPos.ZERO).right;
        h.assertTrue(restored.stall && restored.data.read(PortableStorageInterfaceMovement.WORKING_POS, BlockPos.CODEC).orElseThrow().equals(h.absolutePos(T)), "Working position and stall saved");
        expire(endpoint); var replacement = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel()); replacement.setContraption(loaded); replacement.setPos(owner.position());
        MOVEMENT.tick(restored); h.assertTrue(endpoint.getConnectedEntity() == replacement && endpoint.canTransfer(), "Fresh owner reattaches saved context"); h.succeed();
    }
    @GameTest public void unloadedEndpointQueriesDoNotLoadChunks(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var ctx = context(owner); var far = new BlockPos(2000000, 40, 2000000); ctx.position = Vec3.atCenterOf(far);
        MOVEMENT.visitNewPosition(ctx, far); MOVEMENT.tick(ctx);
        h.assertTrue(!ctx.data.contains(PortableStorageInterfaceMovement.WORKING_POS) && h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Loaded-only discovery"); h.succeed();
    }
    @GameTest public void comparatorReflectsTheSourceConnectedTimer(GameTestHelper h) throws Exception {
        var owner = moving(h, false); var endpoint = connectItems(h, owner);
        h.assertValueEqual(h.getBlockState(T).getAnalogOutputSignal(h.getLevel(), h.absolutePos(T), Direction.NORTH), 15, "Connected comparator");
        expire(endpoint); h.assertValueEqual(h.getBlockState(T).getAnalogOutputSignal(h.getLevel(), h.absolutePos(T), Direction.NORTH), 0, "Disconnected comparator"); h.succeed();
    }
    @GameTest(maxTicks = 55) public void vanillaHopperActuallyExtractsThroughTheFabricEndpoint(GameTestHelper h) throws Exception {
        var owner = moving(h, false); connectItems(h, owner); h.setBlock(T.below(), Blocks.HOPPER);
        h.runAfterDelay(25, () -> {
            var hopper = h.getBlockEntity(T.below(), HopperBlockEntity.class); int total = 0;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) if (hopper.getItem(slot).is(Items.DIAMOND)) total += hopper.getItem(slot).getCount();
            h.assertTrue(total > 0 && total + items(owner, Items.DIAMOND) == 9, "Real hopper conserves mounted stock"); h.succeed();
        });
    }
    private static void machine(GameTestHelper h, boolean fluids) {
        worldBlocks(h, fluids); endpoint(h, fluids);
        h.setBlock(B, AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
        h.setBlock(B.below(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.UP));
    }
    @GameTest(maxTicks = 110) public void realKineticBearingStallsDuringTransferAndThenResumes(GameTestHelper h) {
        machine(h, false);
        h.runAfterDelay(7, () -> {
            var bearing = h.getBlockEntity(B, MechanicalBearingBlockEntity.class); var owner = bearing.getMovedContraption();
            h.assertTrue(owner != null && owner.isStalled(), "Actual controller stalled");
            var endpoint = h.getBlockEntity(T, PortableItemInterfaceBlockEntity.class); var handle = endpoint.storage();
            try (var tx = Transaction.openOuter()) { h.assertValueEqual(handle.extract(ItemVariant.of(Items.DIAMOND), 2, tx), 2L, "Native moving stock"); tx.commit(); }
            float angle = owner.getAngle(1);
            h.runAfterDelay(25, () -> h.assertTrue(owner.isStalled() && owner.getAngle(1) == angle && endpoint.canTransfer(), "Transfer holds real angle"));
            h.runAfterDelay(75, () -> {
                h.assertTrue(!owner.isStalled() && Math.abs(owner.getAngle(1) - angle) > 1, "Actual rotation resumed after timeout");
                h.assertValueEqual(items(owner, Items.DIAMOND), 7L, "Exact remaining stock"); h.succeed();
            });
        });
    }
    @GameTest(maxTicks = 50) public void realFluidBearingTransfersAndRestoresTheActualTank(GameTestHelper h) {
        machine(h, true);
        h.runAfterDelay(7, () -> {
            var bearing = h.getBlockEntity(B, MechanicalBearingBlockEntity.class); var owner = bearing.getMovedContraption();
            h.assertTrue(owner != null && owner.isStalled(), "Actual fluid controller stalled");
            var endpoint = h.getBlockEntity(T, PortableFluidInterfaceBlockEntity.class);
            try (var tx = Transaction.openOuter()) { h.assertValueEqual(endpoint.storage().extract(FluidVariant.of(Fluids.WATER), BUCKET + 1, tx), BUCKET + 1, "Native fluid transfer"); tx.commit(); }
            h.assertTrue(bearing.disassemble(), "Disassemble real controller");
            h.assertValueEqual(h.getBlockEntity(P.south(), FluidTankBlockEntity.class).getTankInventory().getFluidAmount(), 2 * BUCKET, "World tank exact remainder");
            h.succeed();
        });
    }
}

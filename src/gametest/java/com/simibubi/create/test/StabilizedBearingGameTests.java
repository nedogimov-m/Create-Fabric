package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Native topology, passenger identity and sole cargo ownership regression fixtures. */
public class StabilizedBearingGameTests {
    private static final BlockPos B = new BlockPos(3, 2, 3), P = B.above(), R = P.east(), Q = R.above();
    private static MechanicalBearingBlockEntity setup(GameTestHelper h) {
        h.setBlock(B, AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP)); h.setBlock(P, Blocks.STONE);
        h.setBlock(R, AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP)); h.setBlock(Q, Blocks.BARREL);
        h.getBlockEntity(Q, BarrelBlockEntity.class).setItem(0, cargo());
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(P), h.absolutePos(R)))), "Native narrow glue connects only parent cells");
        return h.getBlockEntity(B, MechanicalBearingBlockEntity.class);
    }
    private static ItemStack cargo() { var stack = new ItemStack(Items.DIAMOND, 7); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Real stabilized child cargo")); return stack; }
    private static boolean exact(ItemStack stack) { return stack.getCount() == 7 && ItemStack.isSameItemSameComponents(stack, cargo()); }
    private static OrientedContraptionEntity child(GameTestHelper h, ControlledContraptionEntity parent) {
        var ids = parent.getContraption().getStabilizedSubContraptions().children(); h.assertValueEqual(ids.size(), 1, "Exactly one native child identity");
        var entity = h.getLevel().getEntity(ids.keySet().iterator().next()); h.assertTrue(entity instanceof OrientedContraptionEntity, "Child is a real oriented native entity type"); return (OrientedContraptionEntity) entity;
    }
    private static void close(Vec3 actual, Vec3 expected, String message) { if (actual.distanceToSqr(expected) > 1e-10) throw new AssertionError(message + ": " + actual + " / " + expected); }

    @GameTest public void orientedYawPitchQuaternionMatchesSourceOrderAndHasAnActualInverse(GameTestHelper h) {
        var owner = new OrientedContraptionEntity(AllEntityTypes.ORIENTED_CONTRAPTION, h.getLevel()); owner.setInitialOrientation(Direction.NORTH); owner.setAngles(215, 31);
        var vector = new Vec3(.2, .7, -.4); var expected = StructureTransform.rotate(StructureTransform.rotate(StructureTransform.rotate(vector, 180, Direction.Axis.Y), 31, Direction.Axis.Z), -215, Direction.Axis.Y);
        close(owner.applyRotation(vector, 1), expected, "Native quaternion preserves source initial-Y / pitch-Z / view-Y order"); close(owner.reverseRotation(owner.applyRotation(vector, 1), 1), vector, "Independent oriented inverse");
        close(owner.getRotationState().asMatrix().transform(owner.applyRotation(vector, 1)), vector, "Collision receives the same inverse orientation, including simultaneous yaw/pitch"); h.succeed();
    }
    @GameTest public void verticalInitialOrientationUsesTheSourceSouthYawFallback(GameTestHelper h) {
        for (var direction : Direction.values()) { var owner = new OrientedContraptionEntity(AllEntityTypes.ORIENTED_CONTRAPTION, h.getLevel()); owner.setInitialOrientation(direction); close(owner.applyRotation(new Vec3(1, .25, -.5), 1), new Vec3(1, .25, -.5), "Initial captured world geometry remains unchanged for " + direction); }
        h.succeed();
    }
    @GameTest public void mechanicalAssemblyCreatesDistinctOwnedChildAndNativePassenger(GameTestHelper h) {
        var bearing = setup(h); bearing.assemble(); var parent = bearing.getMovedContraption(); h.assertTrue(parent != null, "Parent actual owner exists"); var child = child(h, parent);
        h.assertTrue(child.getVehicle() == parent && child.getType() == AllEntityTypes.ORIENTED_CONTRAPTION && child.getContraption() instanceof StabilizedContraption, "Native riding uses an oriented stabilized owner");
        h.assertValueEqual(parent.getContraption().getBlocks().size(), 2, "Parent never duplicates child structure"); h.assertValueEqual(child.getContraption().getBlocks().size(), 1, "Child owns the front barrel independently");
        h.assertTrue(exact(child.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0)), "Exact named cargo has one native moving owner"); h.assertBlockPresent(Blocks.AIR, Q); h.succeed();
    }
    @GameTest public void quarterTurnMovesTheRealRiderAnchorWithoutRotatingItsWorldFrame(GameTestHelper h) {
        var bearing = setup(h); bearing.assemble(); var parent = bearing.getMovedContraption(); var child = child(h, parent); var local = parent.getContraption().getStabilizedSubContraptions().location(child.getUUID()).connected();
        for (int angle : new int[]{90, 180, 270}) { bearing.setAngle(angle); close(child.position(), parent.toGlobalVector(Vec3.atCenterOf(local), 1).add(0, -.5, 0), "Actual native rider follows the transformed bearing anchor"); close(child.applyRotation(new Vec3(1, 0, 0), 1), new Vec3(1, 0, 0), "Controlled parent cannot rotate its stabilized child's independent world frame"); }
        h.succeed();
    }
    @GameTest public void perpendicularBearingCapturesItsFrontIntoTheParentInsteadOfInventingAChild(GameTestHelper h) {
        var bearing = setup(h); h.setBlock(R, AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.EAST)); h.setBlock(Q, Blocks.AIR); h.setBlock(R.east(), Blocks.GOLD_BLOCK);
        bearing.assemble(); var parent = bearing.getMovedContraption(); h.assertTrue(parent != null && parent.getContraption().getBlocks().size() == 3 && parent.getContraption().getStabilizedSubContraptions().children().isEmpty(), "Source same-axis stabilization eligibility and ordinary front capture"); h.succeed();
    }
    @GameTest public void aStabilizedChildKeepsInternalBearingFrontsInItsOwnStructure(GameTestHelper h) {
        var bearing = setup(h); h.setBlock(Q.east(), AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP)); h.setBlock(Q.east().above(), Blocks.GOLD_BLOCK);
        h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(Q), h.absolutePos(Q.east())))); bearing.assemble(); var child = child(h, bearing.getMovedContraption());
        h.assertTrue(child.getContraption().getBlocks().size() == 3 && child.getContraption().getStabilizedSubContraptions().children().isEmpty(), "Source StabilizedContraption.canBeStabilized=false prevents invented recursive stabilized grandchildren"); h.succeed();
    }
    @GameTest public void nativeChildReservationRejectionCannotTakeParentOrBarrelCargo(GameTestHelper h) throws Exception {
        setup(h); var structure = new BearingContraption(false, Direction.UP); h.assertTrue(structure.assemble(h.getLevel(), h.absolutePos(B)), "Real graph capture");
        var parent = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel()); parent.setControllerPos(h.absolutePos(B)); parent.setPos(Vec3.atLowerCornerOf(h.absolutePos(P))); h.assertTrue(h.getLevel().addFreshEntity(parent), "Native parent reservation accepted"); var bound = new AtomicBoolean();
        try {
            var result = ContraptionAssemblyGraph.assemble(h.getLevel(), List.of(new ContraptionAssemblyGraph.Root(parent, structure)), complete -> bound.set(true), (level, child) -> { var refused = new OrientedContraptionEntity(AllEntityTypes.ORIENTED_CONTRAPTION, level); refused.setUUID(parent.getUUID()); return refused; });
            h.assertTrue(!result.complete() && result.failure() != null && !bound.get() && parent.getContraption() == null, "Actual duplicate UUID refusal occurs before all source mutation"); h.assertBlockPresent(Blocks.STONE, P); h.assertBlockPresent(AllBlocks.MECHANICAL_BEARING, R); h.assertTrue(exact(h.getBlockEntity(Q, BarrelBlockEntity.class).getItem(0)), "Refused native child leaves all original cargo in the world");
        } finally { if (parent.getContraption() == null) parent.discard(); } h.succeed();
    }
    @GameTest public void nativePassengerSaveContainsChildStructureOnceAndParentOnlyReferencesItsUuid(GameTestHelper h) {
        var bearing = setup(h); bearing.assemble(); var parent = bearing.getMovedContraption(); var child = child(h, parent); var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); h.assertTrue(parent.saveAsPassenger(out), "Native recursive entity serialization");
        var data = out.buildResult(); var children = data.getCompoundOrEmpty("Contraption").getListOrEmpty("SubContraptions"); var metadata = (net.minecraft.nbt.CompoundTag) children.getFirst();
        h.assertTrue(metadata.read("Id", UUIDUtil.CODEC).orElseThrow().equals(child.getUUID()) && !metadata.contains("Contraption"), "Parent serializes UUID/location only, never child cargo");
        var passengers = data.getListOrEmpty("Passengers"); h.assertValueEqual(passengers.size(), 1, "Exactly one actual native passenger structure"); var passenger = (net.minecraft.nbt.CompoundTag) passengers.getFirst(); h.assertTrue(passenger.getStringOr("id", "").equals("create:oriented_contraption") && passenger.contains("Contraption"), "Child structure lives in the native oriented passenger entry"); h.succeed();
    }
    @GameTest public void graphRestorationPlacesParentAndChildCargoExactlyOnce(GameTestHelper h) {
        var bearing = setup(h); bearing.assemble(); var parent = bearing.getMovedContraption(); var child = child(h, parent); h.assertTrue(bearing.disassemble(), "Actual graph restoration completes");
        h.assertTrue(parent.isRemoved() && child.isRemoved(), "Both actual native owners retire only after world ownership completes"); h.assertBlockPresent(Blocks.STONE, P); h.assertBlockPresent(AllBlocks.MECHANICAL_BEARING, R); h.assertTrue(exact(h.getBlockEntity(Q, BarrelBlockEntity.class).getItem(0)), "Exactly seven component-bearing diamonds restored"); h.succeed();
    }
    @GameTest(maxTicks=80) public void actualChildDrillWorkStallsItsRealMovingParent(GameTestHelper h) {
        setup(h); h.getBlockEntity(Q, BarrelBlockEntity.class).clearContent(); h.setBlock(Q, AllBlocks.MECHANICAL_DRILL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.drill.DrillBlock.FACING, Direction.NORTH)); h.setBlock(Q.north(), Blocks.IRON_BLOCK);
        h.setBlock(B.below(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.UP));
        h.succeedWhen(() -> {
            var bearing = h.getBlockEntity(B, MechanicalBearingBlockEntity.class); var parent = bearing.getMovedContraption(); h.assertTrue(parent != null, "Real motor assembles parent"); var child = child(h, parent);
            h.assertTrue(child.isStalled() && parent.isStalled(), "Actual child breaker stall propagates into the registered parent");
            h.assertTrue(child.getContraption().getActors().getFirst().right.data.getIntOr("Progress", 0) > 0, "Actual native child drill is doing work, not a fabricated stall flag"); h.assertBlockPresent(Blocks.IRON_BLOCK, Q.north());
        });
    }
    private static void withBarrelStop(Consumer<com.simibubi.create.content.contraptions.behaviour.MovementContext> hook, Runnable body) {
        var active = new AtomicBoolean(true); var actor = new MovementBehaviour() { @Override public void stopMoving(com.simibubi.create.content.contraptions.behaviour.MovementContext context) { if (active.get()) hook.accept(context); } };
        MovementBehaviour.REGISTRY.registerProvider(block -> active.get() && block == Blocks.BARREL ? actor : null);
        try { body.run(); } finally { active.set(false); MovementBehaviour.REGISTRY.invalidate(); }
    }
    @GameTest public void actualChildStopCannotReenterParentOrChildOwnershipTransfer(GameTestHelper h) {
        var parent = new AtomicReference<ControlledContraptionEntity>(); var child = new AtomicReference<OrientedContraptionEntity>(); var observed = new AtomicBoolean();
        withBarrelStop(context -> { observed.set(true); h.assertFalse(ContraptionAssemblyGraph.restore(parent.get()), "Nested parent restore rejected"); h.assertFalse(ContraptionAssemblyGraph.restore(child.get()), "Nested child restore rejected while ancestor owns the transfer"); h.assertTrue(context.contraption.getStorage().ownsContents(), "Actual callback cannot release owned cargo early"); }, () -> {
            var bearing = setup(h); bearing.assemble(); parent.set(bearing.getMovedContraption()); child.set(child(h, parent.get())); h.assertTrue(bearing.disassemble(), "Outer actual graph restoration completes"); h.assertTrue(observed.get() && exact(h.getBlockEntity(Q, BarrelBlockEntity.class).getItem(0)), "Real actor stop ran once with exact restored cargo");
        }); h.succeed();
    }
    @SuppressWarnings("unchecked") @GameTest public void actualChildStopCanRefuseLaterParentPlacementWithoutLosingItsDurableCargoOrPendingCells(GameTestHelper h) throws Exception {
        var manager = ((ServerLevelEntityManagerAccessor) h.getLevel()).create$getEntityManager(); var field = PersistentEntitySectionManager.class.getDeclaredField("chunkLoadStatuses"); field.setAccessible(true); var statuses = (Long2ObjectMap<Object>) field.get(manager); long key = ChunkPos.pack(h.absolutePos(Q)); var saved = statuses.get(key); var changed = new AtomicBoolean();
        try {
            withBarrelStop(context -> { if (changed.compareAndSet(false, true)) statuses.remove(key); }, () -> {
                var bearing = setup(h); var torch = Q.above(); h.setBlock(torch, Blocks.TORCH); glue(h,Q,torch); bearing.assemble(); var parent = bearing.getMovedContraption(); var child = child(h, parent);
                h.assertValueEqual(child.getContraption().getBlocks().size(),2,"Actual child owns the original barrel cargo and its attached torch");
                h.assertFalse(bearing.disassemble(), "Real callback invalidates parent readiness after child placement"); h.assertTrue(changed.get() && child.isRemoved() && parent.isAlive() && parent.getContraption().phase() == Contraption.Phase.REMOVED, "Only remaining native owner stays durable");
                parent.discard();h.assertTrue(parent.isAlive()&&parent.hasOwnedStructure(),"Native destructive discard cannot bypass recovery after the last child UUID completed");
                h.assertTrue(exact(h.getBlockEntity(Q, BarrelBlockEntity.class).getItem(0)), "Already restored child cargo is world-owned exactly once"); h.assertBlockPresent(Blocks.TORCH,torch);
                h.assertTrue(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.BARREL.defaultBlockState()).isPresent()&&net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.TORCH.defaultBlockState()).isEmpty(),"Native barrel POI uses the source notification exclusion while its attached torch requires finalization");
                h.assertTrue(Set.copyOf(parent.getContraption().getStabilizedSubContraptions().pendingRestoration()).equals(Set.of(h.absolutePos(torch))), "Root persists exactly the restored absolute non-POI cell instead of a duplicate cargo copy");
                statuses.put(key, saved); h.assertTrue(bearing.disassemble(), "Native readiness retry completes graph restoration"); h.assertTrue(exact(h.getBlockEntity(Q, BarrelBlockEntity.class).getItem(0)), "Retry cannot duplicate the previously restored child cargo"); h.assertBlockPresent(Blocks.TORCH,torch);
            });
        } finally { statuses.put(key, saved); } h.succeed();
    }
    @GameTest public void worldOwnedFinalizationUsesPositionsOnlyNativeSavedDataWithoutLoadingItsChunk(GameTestHelper h) {
        var far = new BlockPos(2_000_000, 60, 2_000_000); h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Actual finalization target begins unloaded");
        DeferredContraptionRestoration.finishOrQueue(h.getLevel(), List.of(far)); DeferredContraptionRestoration.tick(h.getLevel()); h.assertTrue(DeferredContraptionRestoration.isPending(h.getLevel(), far), "Unknown native cells retain durable finalization metadata");
        var data = h.getLevel().getDataStorage().computeIfAbsent(DeferredContraptionRestoration.TYPE); var encoded = DeferredContraptionRestoration.CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), data).getOrThrow();
        var text = encoded.toString(); h.assertTrue(text.contains("Positions") && !text.contains("Contraption") && !text.contains("Items") && !text.contains("storage") && !text.contains("Stack"), "Native SavedData contains positions/work identity, never a second cargo or structure snapshot");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "SavedData retries never fabricate a world chunk"); h.succeed();
    }
    @GameTest public void nativeSavedDataCodecRetainsOnlyTheActualPendingFinalizationIdentityAndCells(GameTestHelper h) {
        var far = new BlockPos(2_100_000, 62, 2_100_000); DeferredContraptionRestoration.finishOrQueue(h.getLevel(), List.of(far)); var data = h.getLevel().getDataStorage().computeIfAbsent(DeferredContraptionRestoration.TYPE);
        var ops = h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE); var encoded = DeferredContraptionRestoration.CODEC.encodeStart(ops, data).getOrThrow(); var decoded = DeferredContraptionRestoration.CODEC.parse(ops, encoded).getOrThrow();
        h.assertValueEqual(DeferredContraptionRestoration.CODEC.encodeStart(ops, decoded).getOrThrow(), encoded, "Native SavedData reload roundtrip retains metadata identity/cells without reconstructing an owner or inventory"); h.succeed();
    }
    @GameTest public void runningInnerRotorRestoresBeforeParentCaptureAndRestartsFromTheMovedBearingReceipt(GameTestHelper h) {
        var outer=setup(h);var inner=h.getBlockEntity(R,MechanicalBearingBlockEntity.class);inner.assemble();var original=inner.getMovedContraption();
        h.assertTrue(original!=null&&exact(original.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0)),"Original running rotor owns the exact cargo");
        outer.assemble();var parent=outer.getMovedContraption();var movedChild=child(h,parent);
        h.assertTrue(original.isRemoved()&&original.getContraption().disassembled&&!original.getUUID().equals(movedChild.getUUID()),"Old rotor retires before a distinct actual stabilized child captures the world body");
        var movedBearing=parent.getContraption().getBlocks().get(R.subtract(P)).nbt();
        h.assertTrue(movedBearing!=null&&movedBearing.getBooleanOr("QueuedMechanicalReassembly",false)&&!movedBearing.contains("MovedContraption")&&!movedBearing.getBooleanOr("Running",true),"Moved BE persists only the restart receipt, never a retired owner UUID");
        h.assertTrue(outer.disassemble(),"Parent and actual child restore");var restored=h.getBlockEntity(R,MechanicalBearingBlockEntity.class);
        var saved=restored.saveWithFullMetadata(h.getLevel().registryAccess());var loaded=(MechanicalBearingBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(R),h.getBlockState(R),saved,h.getLevel().registryAccess());loaded.setLevel(h.getLevel());h.getLevel().setBlockEntity(loaded);
        loaded.setNetworkState(16,16,false,new com.simibubi.create.content.kinetics.KineticGraph.Stress(512,64,false),Map.of(Direction.DOWN,16f));MechanicalBearingBlockEntity.tick(h.getLevel(),h.absolutePos(R),h.getBlockState(R),loaded);
        var restarted=loaded.getMovedContraption();h.assertTrue(restarted!=null&&restarted!=original&&!restarted.getUUID().equals(movedChild.getUUID())&&exact(restarted.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0)),"Reloaded relocated bearing restarts from world-owned exact cargo into one fresh real owner");h.assertBlockPresent(Blocks.AIR,Q);h.assertTrue(loaded.disassemble(),"Cleanup restores the restarted original cargo");h.succeed();
    }
    @GameTest public void refusedParentCaptureRetainsTheRestoredInnerRotorCargoAndDurableRestartReceipt(GameTestHelper h) {
        var outer=setup(h);var inner=h.getBlockEntity(R,MechanicalBearingBlockEntity.class);inner.assemble();var original=inner.getMovedContraption();
        h.setBlock(R.east(),AllBlocks.STICKER.defaultBlockState().setValue(com.simibubi.create.content.contraptions.chassis.StickerBlock.FACING,Direction.EAST).setValue(com.simibubi.create.content.contraptions.chassis.StickerBlock.EXTENDED,true));h.setBlock(R.east(2),Blocks.OBSIDIAN);glue(h,R,R.east());outer.assemble();
        h.assertTrue(!outer.isRunning()&&outer.getMovedContraption()==null&&original.isRemoved(),"Real unmovable parent branch refuses after the inner rotor's guarded world restoration");
        h.assertTrue(exact(h.getBlockEntity(Q,BarrelBlockEntity.class).getItem(0))&&inner.saveWithFullMetadata(h.getLevel().registryAccess()).getBooleanOr("QueuedMechanicalReassembly",false),"Failed parent capture leaves exact world cargo and an original restart receipt");
        inner.setNetworkState(16,16,false,new com.simibubi.create.content.kinetics.KineticGraph.Stress(512,64,false),Map.of(Direction.DOWN,16f));MechanicalBearingBlockEntity.tick(h.getLevel(),h.absolutePos(R),h.getBlockState(R),inner);
        var restarted=inner.getMovedContraption();h.assertTrue(restarted!=null&&restarted!=original&&exact(restarted.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0)),"Failed outer assembly cannot strand or duplicate a previously running nested rotor");h.assertTrue(inner.disassemble(),"Cleanup restores cargo");h.succeed();
    }
    @GameTest public void unresolvedSavedInnerRotorIdentityRefusesParentCaptureEvenWhenRunningFlagIsFalse(GameTestHelper h) {
        var outer=setup(h);var inner=h.getBlockEntity(R,MechanicalBearingBlockEntity.class);var missing=UUID.randomUUID();var data=inner.saveWithFullMetadata(h.getLevel().registryAccess());data.putBoolean("Running",false);data.store("MovedContraption",UUIDUtil.CODEC,missing);
        var unresolved=(MechanicalBearingBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(R),h.getBlockState(R),data,h.getLevel().registryAccess());unresolved.setLevel(h.getLevel());h.getLevel().setBlockEntity(unresolved);outer.assemble();
        h.assertTrue(!outer.isRunning()&&outer.getMovedContraption()==null&&unresolved.saveWithFullMetadata(h.getLevel().registryAccess()).read("MovedContraption",UUIDUtil.CODEC).orElseThrow().equals(missing),"Missing actual native owner retains its exact saved identity and refuses the enclosing capture");h.assertTrue(exact(h.getBlockEntity(Q,BarrelBlockEntity.class).getItem(0)),"Unresolved identity cannot take original world cargo");h.succeed();
    }
    @GameTest public void runningClockworkInsideParentRetiresBothHandsBeforeCaptureAndRestartsAfterNativeBearingReload(GameTestHelper h) {
        var outer=setup(h);h.setBlock(R,AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));h.setBlock(Q.above(),Blocks.GOLD_BLOCK);var clock=h.getBlockEntity(R,ClockworkBearingBlockEntity.class);clock.assemble();var hour=clock.getHourHand();var minute=clock.getMinuteHand();h.assertTrue(hour!=null&&minute!=null,"Actual nested clock owns both hands");
        outer.assemble();var parent=outer.getMovedContraption();h.assertTrue(parent!=null&&hour.isRemoved()&&minute.isRemoved()&&parent.getContraption().getStabilizedSubContraptions().children().isEmpty(),"Clock's actual old hand owners restore before the enclosing bearing captures its controller");
        var moved=parent.getContraption().getBlocks().get(R.subtract(P)).nbt();h.assertTrue(moved.getBooleanOr("QueuedClockworkReassembly",false)&&!moved.contains("HourOwner")&&!moved.contains("MinuteOwner"),"Moved clock persists a durable restart receipt without stale native hand identities");h.assertTrue(exact(h.getBlockEntity(Q,BarrelBlockEntity.class).getItem(0)),"Unglued restored hand body remains world-owned during controller movement");
        h.assertTrue(outer.disassemble(),"Outer actual owner returns the nested controller");var restored=h.getBlockEntity(R,ClockworkBearingBlockEntity.class);var data=restored.saveWithFullMetadata(h.getLevel().registryAccess());var loaded=(ClockworkBearingBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(R),h.getBlockState(R),data,h.getLevel().registryAccess());loaded.setLevel(h.getLevel());h.getLevel().setBlockEntity(loaded);
        loaded.setNetworkState(16,16,false,new com.simibubi.create.content.kinetics.KineticGraph.Stress(512,64,false),Map.of(Direction.DOWN,16f));ClockworkBearingBlockEntity.tick(h.getLevel(),h.absolutePos(R),h.getBlockState(R),loaded);
        h.assertTrue(loaded.getHourHand()!=null&&loaded.getMinuteHand()!=null&&loaded.getHourHand()!=hour&&loaded.getMinuteHand()!=minute,"Reloaded nested clock restarts both real hand bodies without reviving retired owners");h.assertTrue(loaded.disassemble()&&exact(h.getBlockEntity(Q,BarrelBlockEntity.class).getItem(0)),"Fresh hands restore the original exact cargo once");h.succeed();
    }
    private static final BlockPos CB=new BlockPos(4,3,4),CH=CB.above(),CM=CB.above(2),CR1=CH.east(2).north(2),CQ1=CR1.above(),CT=CQ1.above(),CR2=CM.east(3).north(2),CQ2=CR2.above();
    private static void glue(GameTestHelper h,BlockPos from,BlockPos to){h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(from),h.absolutePos(to)))),"Native graph fixture glue is accepted");}
    private static ClockworkBearingBlockEntity forest(GameTestHelper h){
        h.setBlock(CB,AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));
        for(var pos:List.of(CH,CH.north(),CH.north(2),CH.north(2).east(),CM,CM.east(),CM.east(2),CM.east(3),CM.east(3).north()))h.setBlock(pos,Blocks.STONE);
        for(var pos:List.of(CR1,CR2))h.setBlock(pos,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));
        for(var pos:List.of(CQ1,CQ2)){h.setBlock(pos,Blocks.BARREL);h.getBlockEntity(pos,BarrelBlockEntity.class).setItem(0,cargo());}
        h.setBlock(CT,Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING,Direction.WEST));
        glue(h,CH,CR1);glue(h,CM,CM.east(3));glue(h,CM.east(3),CR2);glue(h,CQ1,CT);
        var bearing=h.getBlockEntity(CB,ClockworkBearingBlockEntity.class);bearing.assemble();h.assertTrue(bearing.getHourHand()!=null&&bearing.getMinuteHand()!=null,"Both actual clock roots exist");var a=child(h,bearing.getHourHand());var b=child(h,bearing.getMinuteHand());h.assertValueEqual(a.getContraption().getBlocks().size(),2,"Hour child owns barrel plus attached wall torch");h.assertValueEqual(b.getContraption().getBlocks().size(),1,"Minute child independently owns the torch support barrel");return bearing;
    }
    private static void exactForest(GameTestHelper h){h.assertBlockPresent(Blocks.WALL_TORCH,CT);h.assertTrue(exact(h.getBlockEntity(CQ1,BarrelBlockEntity.class).getItem(0))&&exact(h.getBlockEntity(CQ2,BarrelBlockEntity.class).getItem(0)),"Both independent native child cargo stacks restore exactly once");}
    @GameTest public void clockworkForestDefersAnActualChildAttachmentUntilTheOtherTreesChildSupportExists(GameTestHelper h){
        var bearing=forest(h);var a=bearing.getHourHand();var b=bearing.getMinuteHand();var ca=child(h,a);var cb=child(h,b);h.assertTrue(bearing.disassemble(),"Native forest restores both rooted child trees as one neighbor finalization");h.assertTrue(a.isRemoved()&&b.isRemoved()&&ca.isRemoved()&&cb.isRemoved(),"All four real owners retire after their own world transfer");exactForest(h);h.runAfterDelay(2,()->{exactForest(h);h.succeed();});
    }
    @SuppressWarnings("unchecked") @GameTest public void clockworkForestPartialChildPlacementPersistsWorldCellsAndOriginalRemainingOwnerUuidsAcrossNativeReload(GameTestHelper h)throws Exception{
        var manager=((ServerLevelEntityManagerAccessor)h.getLevel()).create$getEntityManager();var field=PersistentEntitySectionManager.class.getDeclaredField("chunkLoadStatuses");field.setAccessible(true);var statuses=(Long2ObjectMap<Object>)field.get(manager);long key=ChunkPos.pack(h.absolutePos(CQ1));var saved=statuses.get(key);var changed=new AtomicBoolean();
        try{withBarrelStop(context->{if(context.localPos.equals(BlockPos.ZERO)&&changed.compareAndSet(false,true))statuses.remove(key);},()->{
            var bearing=forest(h);var a=bearing.getHourHand();var b=bearing.getMinuteHand();var ca=child(h,a);var cb=child(h,b);h.assertFalse(bearing.disassemble(),"Real child callback removes native readiness before its parent transfer");h.assertTrue(changed.get()&&ca.isRemoved()&&a.hasOwnedStructure()&&b.hasOwnedStructure()&&cb.hasOwnedStructure(),"Completed child is world-owned; exactly three original owners remain durable");
            h.assertTrue(exact(h.getBlockEntity(CQ1,BarrelBlockEntity.class).getItem(0)),"Completed child's original cargo is already world-owned once");h.assertTrue(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.BARREL.defaultBlockState()).isPresent()&&net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(h.getBlockState(CT)).isEmpty(),"Native barrel POI is excluded from source neighbor updates while its actual attached torch is not");
            var data=bearing.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(Set.copyOf(data.read("PendingRestorationUpdates",BlockPos.CODEC.listOf()).orElseThrow()).equals(Set.of(h.absolutePos(CT))),"Controller journal stores exactly the already world-owned child cell requiring finalization, never duplicate cargo");var loaded=(ClockworkBearingBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(CB),h.getBlockState(CB),data,h.getLevel().registryAccess());loaded.setLevel(h.getLevel());h.getLevel().setBlockEntity(loaded);h.assertTrue(loaded.getHourHand()==a&&loaded.getMinuteHand()==b&&child(h,b)==cb,"Native reload resolves original surviving root and child UUIDs");
            statuses.put(key,saved);h.assertTrue(loaded.disassemble(),"Remaining original owners resume after native entity data returns");exactForest(h);h.assertTrue(a.isRemoved()&&b.isRemoved()&&cb.isRemoved(),"No replacement native cargo owners are invented for recovery");
        });}finally{statuses.put(key,saved);}h.succeed();
    }
    @GameTest public void actualChildStopCannotDirectlyTransferAnotherProtectedTreeOrItsRoot(GameTestHelper h){
        var roots=new AtomicReference<ClockworkBearingBlockEntity>();var seen=new AtomicBoolean();withBarrelStop(context->{if(!seen.compareAndSet(false,true))return;var bearing=roots.get();for(var owner:List.of(bearing.getHourHand(),bearing.getMinuteHand(),child(h,bearing.getMinuteHand()))){try{h.assertFalse(owner.getContraption().tryAddBlocksToWorld(h.getLevel(),owner.getRestorationTransform()),"Direct protected sibling/root placement refused inside another real actor transfer");}catch(AssemblyException expected){}h.assertTrue(owner.hasOwnedStructure(),"Reentrant direct call cannot release another native cargo owner");}},()->{var bearing=forest(h);roots.set(bearing);h.assertTrue(bearing.disassemble(),"Outer actual forest restoration completes after refusing direct sibling transfer");h.assertTrue(seen.get(),"Actual child stop callback ran");exactForest(h);});h.succeed();
    }
}

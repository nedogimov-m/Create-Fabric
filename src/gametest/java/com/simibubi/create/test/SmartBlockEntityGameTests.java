package com.simibubi.create.test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.test.SmartBlockEntityTestFixtures.Counting;
import com.simibubi.create.test.SmartBlockEntityTestFixtures.Probe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** Real registered owners, native persistence and world lifecycle; proposed runtime cases are UNRUN. */
public final class SmartBlockEntityGameTests {
    private static final String STRUCTURE = "create_test:reservoir";
    private static final BlockPos P = new BlockPos(6, 5, 6);
    private static Probe probe(GameTestHelper h) {
        h.setBlock(P, SmartBlockEntityTestFixtures.FALLBACK_BLOCK);
        return h.getBlockEntity(P, Probe.class);
    }
    private static void read(GameTestHelper h, BlockEntity owner, CompoundTag tag) {
        owner.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), tag));
    }
    private static CompoundTag payload(int seed, int value) {
        var tag = new CompoundTag(); tag.putInt("Seed", seed); tag.putInt("AddonValue", value); return tag;
    }
    private static void tick(GameTestHelper h, SmartBlockEntity owner) {
        new SmartBlockEntityTicker<BlockEntity>().tick(h.getLevel(), owner.getBlockPos(), owner.getBlockState(), owner);
    }

    @GameTest(structure=STRUCTURE) public void firstNativeReadPublishesConcreteOwnerAndAllActualDeferredBehaviours(GameTestHelper h) {
        var owner = probe(h); var calls = new AtomicInteger();
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> event.forType(SmartBlockEntityTestFixtures.TYPE, actual -> {
            h.assertTrue(actual == owner && event.getBlockEntity() == owner, "Public event receives the exact native concrete owner");
            h.assertValueEqual(actual.constructorHooks, 1, "Addon constructor hook executes once");
            h.assertValueEqual(actual.deferredHooks, 1, "Deferred hook executes before event");
            h.assertValueEqual(actual.deferredSeed, 7, "Deferred behaviour sees the owner's first actual native data");
            h.assertTrue(actual.getBehaviour(SmartBlockEntityTestFixtures.DEFERRED) == actual.deferred, "Actual deferred object is registered");
            h.assertValueEqual(actual.base.reads, 0, "Event runs before any behaviour read");
            calls.incrementAndGet();
            event.attach(new Counting(actual, SmartBlockEntityTestFixtures.INJECTED, "Injected", false));
        }))) { read(h, owner, payload(7, 31)); }
        var injected = owner.getBehaviour(SmartBlockEntityTestFixtures.INJECTED);
        h.assertValueEqual(calls.get(), 1, "One first-read event");
        h.assertTrue(injected.reads == 1 && injected.value == 31 && !injected.clientRead, "Injected object receives the genuine first server payload");
        read(h, owner, payload(8, 47)); tick(h, owner);
        h.assertTrue(calls.get() == 1 && owner.deferredHooks == 1 && injected.reads == 2 && injected.initializations == 1,
            "Later native reads and initialization preserve one deferred/event/initialize phase");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void recursiveFirstReadCannotRepeatEventOrReadBeforePublication(GameTestHelper h) {
        var owner = probe(h); var calls = new AtomicInteger();
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> {
            if (event.getBlockEntity() != owner) return;
            calls.incrementAndGet();
            var injected = new Counting(owner, SmartBlockEntityTestFixtures.INJECTED, "Injected", false);
            event.attach(injected); read(h, owner, payload(99, 47));
            h.assertValueEqual(injected.reads, 0, "Recursive real input waits until publication finishes");
        })) { read(h, owner, payload(7, 31)); }
        h.assertTrue(calls.get() == 1 && owner.deferredHooks == 1 && owner.constructorHooks == 1, "Reentry keeps one source first-NBT claim");
        h.assertValueEqual(owner.getBehaviour(SmartBlockEntityTestFixtures.INJECTED).payloads, List.of(47, 31),
            "Actual nested then original inputs are both delivered without losing the original first payload");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void placedPathPublishesBeforeInitializeAndSkipsLoadedOnlyDeferredHook(GameTestHelper h) {
        var owner = probe(h); var calls = new AtomicInteger();
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> {
            if (event.getBlockEntity() != owner) return;
            calls.incrementAndGet(); h.assertTrue(owner.base.initializations == 0 && owner.deferred == null, "Placed event precedes actual initialization");
            event.attach(new Counting(owner, SmartBlockEntityTestFixtures.INJECTED, "Injected", false));
        })) { tick(h, owner); }
        h.assertTrue(calls.get() == 1 && owner.deferredHooks == 0 && owner.ownerInitializations == 1,
            "Placed path does not construct loaded-only behaviours");
        h.assertTrue(owner.base.initializations == 1 && owner.base.ticks == 1 && owner.productTicks == 1 && owner.ownerLazyTicks == 1,
            "Actual owner, behaviour and first lazy tick run once");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=12) public void nativeChunkSuppliesTickerWhenAddonBlockReturnsNull(GameTestHelper h) {
        var owner = probe(h);
        h.assertTrue(owner.getBlockState().getTicker(h.getLevel(), owner.getType()) == null, "Actual addon block supplies no ticker");
        h.runAfterDelay(3, () -> {
            h.assertTrue(owner.productTicks > 0 && owner.productTicks == owner.base.ticks && owner.ownerInitializations == 1,
                "Actual LevelChunk fallback drives the concrete owner's native ticks once"); h.succeed();
        });
    }

    @GameTest(structure=STRUCTURE,maxTicks=12) public void existingNativeTickerDoesNotExecuteConcreteProductTickTwice(GameTestHelper h) {
        h.setBlock(P, SmartBlockEntityTestFixtures.TICKER_BLOCK); var owner = h.getBlockEntity(P, Probe.class);
        h.runAfterDelay(3, () -> {
            h.assertTrue(owner.productTicks > 0 && owner.productTicks == owner.base.ticks,
                "Native wrapper's behaviour phase and original virtual product tick each run once"); h.succeed();
        });
    }

    @GameTest(structure=STRUCTURE) public void actualKineticAndNonkineticNativeTickEntrypointsShareOneRegistryPhase(GameTestHelper h) {
        h.setBlock(P, AllBlocks.SHAFT); var kinetic = h.getBlockEntity(P, KineticBlockEntity.class);
        var counter = new Counting(kinetic, SmartBlockEntityTestFixtures.INJECTED, "KineticAddon", false);
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> { if (event.getBlockEntity() == kinetic) event.attach(counter); })) {
            KineticBlockEntity.tick(h.getLevel(), kinetic.getBlockPos(), kinetic.getBlockState(), kinetic);
        }
        h.assertTrue(counter.initializations == 1 && counter.ticks == 1, "Actual registered shaft reaches Smart lifecycle once");
        h.setBlock(P, AllBlocks.DEPOT); var depot = h.getBlockEntity(P, DepotBlockEntity.class);
        var depotCounter = new Counting(depot, SmartBlockEntityTestFixtures.INJECTED, "DepotAddon", false);
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> { if (event.getBlockEntity() == depot) event.attach(depotCounter); })) {
            DepotBlockEntity.tick(h.getLevel(), depot.getBlockPos(), depot.getBlockState(), depot);
        }
        h.assertTrue(depotCounter.initializations == 1 && depotCounter.ticks == 1, "Actual registered nonkinetic depot reaches the same source lifecycle");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void eventRemovalCannotRecreateActualNativeInputThroughFallback(GameTestHelper h) {
        h.setBlock(P, AllBlocks.DEPOT); var owner = h.getBlockEntity(P, DepotBlockEntity.class);
        var actualInput = owner.depotBehaviour.directInput;
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> event.forType(AllBlockEntityTypes.DEPOT, actual -> {
            h.assertTrue(actual == owner && actual.getBehaviour(DirectBeltInputBehaviour.TYPE) == actualInput, "Event sees the genuine pre-existing input");
            h.assertTrue(event.remove(DirectBeltInputBehaviour.TYPE) == actualInput, "Removal returns that same actual object");
        }))) { read(h, owner, new CompoundTag()); }
        h.assertTrue(BlockEntityBehaviour.get(owner, DirectBeltInputBehaviour.TYPE) == null,
            "Public lookup preserves the removed native member");
        DepotBlockEntity.tick(h.getLevel(), owner.getBlockPos(), owner.getBlockState(), owner);
        h.assertTrue(owner.getBehaviour(DirectBeltInputBehaviour.TYPE) == null && owner.getBehaviour(DepotBehaviour.TYPE) == owner.depotBehaviour,
            "Tick cannot recreate removed inputs or replace the real inventory-owning depot"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void nativeDepotReloadHydratesOneCanonicalInventoryAndOneAddonRead(GameTestHelper h) {
        h.setBlock(P, AllBlocks.DEPOT); var original = h.getBlockEntity(P, DepotBlockEntity.class);
        var storage = ItemStorage.SIDED.find(h.getLevel(), original.getBlockPos(), Direction.UP);
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(storage.insert(ItemVariant.of(Items.DIAMOND), 7, tx), 7L, "Finite stock enters the actual native depot"); tx.commit();
        }
        var saved = original.saveWithFullMetadata(h.getLevel().registryAccess()); saved.putInt("AddonValue", 73);
        original.onChunkUnloaded(); h.getLevel().removeBlockEntity(original.getBlockPos());
        var reads = new AtomicInteger(); final Counting[] injected = new Counting[1]; final DepotBlockEntity[] restored = new DepotBlockEntity[1];
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> event.forType(AllBlockEntityTypes.DEPOT, actual -> {
            reads.incrementAndGet(); restored[0] = actual;
            h.assertTrue(actual != original && actual.inventory == actual.depotBehaviour.inventory, "Native factory constructs one canonical new inventory owner");
            h.assertTrue(actual.getHeldItem().isEmpty(), "Event precedes canonical inventory hydration");
            event.attach(injected[0] = new Counting(actual, SmartBlockEntityTestFixtures.INJECTED, "DepotAddon", false));
        }))) {
            var decoded = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved, h.getLevel().registryAccess());
            h.assertTrue(decoded == restored[0] && decoded.getLevel() == null, "Native loader delivers that exact concrete BE before level assignment");
            h.getLevel().setBlockEntity(decoded);
        }
        h.assertTrue(reads.get() == 1 && injected[0].reads == 1 && injected[0].value == 73, "Injected behaviour reads the original native payload once");
        h.assertTrue(original.isRemoved() && restored[0].getHeldItem().getCount() == 7 && restored[0].inventory.outputs.size() == 8,
            "Only the new world owner exposes the original seven-item stock and eight real outputs");
        restored[0].saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertValueEqual(injected[0].writes, 1, "Native save serializes the addon once"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void nativeDestroyAndRemovalHaveSeparateExactlyOnceHooks(GameTestHelper h) {
        var owner = probe(h); tick(h, owner); h.setBlock(P, Blocks.AIR);
        h.assertTrue(owner.base.destroys == 1 && owner.base.unloads == 1 && owner.removals == 1,
            "Real native replacement destroys then unloads the same behaviour once");
        owner.setRemoved(); owner.destroy();
        h.assertTrue(owner.base.destroys == 1 && owner.base.unloads == 1, "Repeated native terminal calls cannot repeat side effects"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void markedChunkUnloadUsesNativeRemovalWithoutDestroyOrMovementRemoval(GameTestHelper h) {
        var owner = probe(h); tick(h, owner); owner.onChunkUnloaded(); h.getLevel().removeBlockEntity(owner.getBlockPos());
        h.assertTrue(owner.isChunkUnloaded() && owner.isRemoved() && owner.base.unloads == 1 && owner.base.destroys == 0 && owner.removals == 0,
            "Source chunk-deactivation mark reaches actual native removal and only unloads"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void sourceSafeSerializationIncludesOnlyActualSafeBehaviourState(GameTestHelper h) {
        var owner = probe(h); final Counting[] safe = new Counting[1];
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> {
            if (event.getBlockEntity() == owner) event.attach(safe[0] = new Counting(owner, SmartBlockEntityTestFixtures.SAFE, "SafeValue", true));
        })) { read(h, owner, payload(7, 31)); }
        var tag = new CompoundTag(); owner.writeSafe(tag, h.getLevel().registryAccess());
        h.assertTrue(tag.getIntOr("SafeValue", -1) == 31 && !tag.contains("Base") && !tag.contains("Deferred") && !tag.contains("Owner") && !tag.contains("Seed"),
            "Safe native schematic serialization writes safe behaviour data without ordinary persistent fields");
        h.assertValueEqual(safe[0].writes, 1, "Safe behaviour serializer executes once"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void nativePacketIOPreservesClientFlagAndServerSaveFlag(GameTestHelper h) {
        var owner = probe(h); read(h, owner, payload(7, 31)); owner.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertFalse(owner.base.clientWrite, "Actual native world save uses clientPacket false");
        var packet = owner.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(owner.base.clientWrite, "Actual native update tag dispatches clientPacket true");
        owner.readClient(packet, h.getLevel().registryAccess());
        h.assertTrue(owner.base.clientRead, "Source packet bridge preserves client flag on the same actual owner");
        read(h, owner, payload(8, 47)); h.assertFalse(owner.base.clientRead, "Following native save load does not inherit a stale client scope"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void initializationReentryCannotRunNativeProductWorkOrBehaviourTwice(GameTestHelper h) {
        var owner = probe(h); var business = new AtomicInteger();
        owner.base.onInitialize = () -> SmartBlockEntity.tickNative(h.getLevel(), owner.getBlockPos(), owner.getBlockState(), owner, business::incrementAndGet);
        tick(h, owner);
        h.assertTrue(business.get() == 0 && owner.base.initializations == 1 && owner.base.ticks == 1 && owner.productTicks == 1,
            "A real initialize callback cannot run owner business during its unfinished phase"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void eventWorldReplacementPreventsStaleConcreteOwnerInitializationAndProductTick(GameTestHelper h) {
        var owner = probe(h);
        try (var scope = SmartBlockEntityTestFixtures.observe(event -> {
            if (event.getBlockEntity() == owner) h.setBlock(P, Blocks.STONE);
        })) { tick(h, owner); }
        h.assertTrue(owner.isRemoved() && owner.base.initializations == 0 && owner.base.ticks == 0 && owner.productTicks == 0,
            "Real native ticker revalidates actual world owner after the public event"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void nativeAndSourceAdvancementBehaviourShareTheSamePersistedOwner(GameTestHelper h) {
        h.setBlock(P, AllBlocks.MECHANICAL_PUMP); var owner = h.getBlockEntity(P, PumpBlockEntity.class);
        try (var session = NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(owner.getBlockPos()).add(0, 0, 2))) {
            var player = session.player(); owner.sourceAdvancements.place(player);
            var behaviour = owner.getBehaviour(AdvancementBehaviour.TYPE);
            h.assertTrue(behaviour != null && behaviour.isOwnerPresent() && owner.advancementOwnerStorage() == owner.sourceAdvancements,
                "Source API exposes the native device's same canonical Owner object");
            var tag = owner.saveWithFullMetadata(h.getLevel().registryAccess());
            var decoded = (PumpBlockEntity) BlockEntity.loadStatic(owner.getBlockPos(), owner.getBlockState(), tag, h.getLevel().registryAccess());
            h.assertTrue(decoded.getLevel() == null && player.getUUID().equals(decoded.sourceAdvancements.uuid())
                    && decoded.getBehaviour(AdvancementBehaviour.TYPE).isOwnerPresent(),
                "Native pre-level read restores one shared UUID without adopting a nearby player");
        }
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void cachedBoundsAndVirtualTraitUseTheActualNativeOwner(GameTestHelper h) {
        var owner = probe(h); var bounds = owner.getRenderBoundingBox();
        h.assertTrue(bounds == owner.getRenderBoundingBox(), "Source bounds cache retains its actual value");
        owner.invalidateBounds(); h.assertTrue(bounds != owner.getRenderBoundingBox(), "Explicit source invalidation rebuilds the cache");
        owner.markVirtual(); h.assertTrue(owner.isVirtual() && owner.getLevel() == h.getLevel(),
            "Exact Ponder trait records virtual mode on the original native owner without another world backend"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void lateAttachmentRebindsActualOwnerAndLiveCollectionWhileRemovalUnloadsOnce(GameTestHelper h) {
        var first = probe(h); var nextPos = P.east(2);
        h.setBlock(nextPos, SmartBlockEntityTestFixtures.FALLBACK_BLOCK); var second = h.getBlockEntity(nextPos, Probe.class);
        tick(h, second); var live = second.getAllBehaviours(); int before = live.size();
        var late = new Counting(first, SmartBlockEntityTestFixtures.INJECTED, "Late", false);
        second.attachBehaviourLate(late);
        h.assertTrue(late.blockEntity == second && late.getPos().equals(second.getBlockPos()) && late.initializations == 1
                && live.size() == before + 1 && live.contains(late),
            "Late source attachment rebinds the same object and updates the actual values view");
        tick(h, second); h.assertValueEqual(late.ticks, 1, "Late object ticks once in the actual target owner");
        second.removeBehaviour(SmartBlockEntityTestFixtures.INJECTED); tick(h, second);
        h.assertTrue(late.unloads == 1 && late.ticks == 1 && !live.contains(late), "Removed object unloads once and cannot rejoin the next phase"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void ownerLazyTickKeepsSourcePostDecrementBoundary(GameTestHelper h) {
        var owner = probe(h); owner.setLazyTickRate(2);
        tick(h, owner); tick(h, owner);
        h.assertValueEqual(owner.ownerLazyTicks, 1, "Initialization supplies the first actual lazy tick");
        tick(h, owner);
        h.assertTrue(owner.ownerLazyTicks == 2 && owner.base.ticks == 3 && owner.productTicks == 3,
            "Source counter-- <= 0 boundary survives native/static frame composition"); h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void sourceTriggerIsTheActualNativeRegisteredCriterionAndAwardsRealProgress(GameTestHelper h) {
        var id = com.simibubi.create.Create.asResource("water_wheel_builtin");
        var actual = net.minecraft.core.registries.BuiltInRegistries.TRIGGER_TYPES.getValue(id);
        h.assertTrue(actual instanceof SimpleCreateTrigger trigger && trigger.getId().equals(id),
            "Genuine source trigger owns the actual native registry entry");
        try (var session = NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(h.absolutePos(P)))) {
            var player = session.player(); var advancement = player.level().getServer().getAdvancements()
                .get(com.simibubi.create.Create.asResource("water_wheel"));
            h.assertTrue(advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "Fresh real player starts with the actual native criterion incomplete");
            AllAdvancements.WATER_WHEEL.awardTo(player);
            h.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "Source API dispatch reaches native PlayerAdvancements and the datapack's real progress");
        }
        h.succeed();
    }
}

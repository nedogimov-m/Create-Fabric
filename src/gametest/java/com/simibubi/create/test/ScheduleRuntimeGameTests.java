package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.ItemThresholdCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Every runtime owner comes ONLY from actual station preflight/capture/native forest acceptance/Train adoption. */
public final class ScheduleRuntimeGameTests {
    private static final String STRUCTURE = "create_test:reservoir";
    private static final BlockPos R = new BlockPos(4, 6, 4), T = R.east(), S = T.south(2);
    private static final BlockPos B = T.east().above(), CARGO = B.above().south(), BURNER = B.above().east();
    private record Fixture(StationBlockEntity station, ServerPlayer player, Train train, ItemVariant originalCargo) {}

    private static ItemStack cargo(int count) {
        var item = new ItemStack(Items.APPLE, count);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Original schedule freight"));
        return item;
    }
    private static long actualCargo(Train train, ItemVariant variant) {
        long count = 0;
        for (var carriage : train.carriages) for (var view : carriage.storage.getAllItems())
            if (view.getResource().equals(variant)) count += view.getAmount();
        return count;
    }
    private static void run(GameTestHelper h, int count, ToIntFunction<Fixture> assertions) {
        var rail = AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO);
        for (int i = 0; i < 15; i++) { h.setBlock(R.east(i).below(), Blocks.STONE); h.setBlock(R.east(i), rail); }
        for (int i = 0; i < 15; i++) h.assertTrue(TrackPropagator.reconcile(h.getLevel(), h.absolutePos(R.east(i)), rail, false).complete(), "Actual world rails discover a real graph");
        h.setBlock(S.below(), Blocks.STONE);
        h.setBlock(S, AllBlocks.TRACK_STATION);
        var station = h.getBlockEntity(S, StationBlockEntity.class);
        h.assertTrue(station.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(), h.absolutePos(T)), true, new Vec3(1, 0, 0), Optional.empty())), "Actual station binds its real target");
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(station.getBlockPos()).add(0, 0, 1));
        h.assertTrue(station.configure(player, "Schedule fixture", true, DoorControl.ALL), "Actual player enters source assembly mode");
        h.setBlock(B, AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X));
        h.setBlock(B.above(), AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING, Direction.EAST));
        h.setBlock(B.above().east(2), AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING, Direction.WEST));
        h.setBlock(BURNER, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        h.setBlock(CARGO, Blocks.BARREL);
        var originalCargo = cargo(count);
        h.getBlockEntity(CARGO, BarrelBlockEntity.class).setItem(0, originalCargo);
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(B), h.absolutePos(CARGO.east(2))))), "Actual registered glue joins the physical body, burner, controls and original barrel");
        var fixture = new AtomicReference<Fixture>();
        var expected = new AtomicInteger(count);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(station.preflight(player).readyForCapture(), "Real loaded entity/wheel-point preflight must become ready naturally"))
                .thenExecute(() -> {
                    h.assertTrue(station.tryAssemble(player), "Actual source station must perform genuine accepted capture and Train adoption");
                    var train = station.getStation().getPresentTrain();
                    h.assertTrue(train != null && TrainManager.get(h.getLevel()).get(train.id) == train, "Only the actual globally adopted Train is used");
                    h.assertTrue(train.carriages.size() == 1 && train.carriages.getFirst().storage.ownsContents(), "Actual carriage owns the captured physical storage");
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(CARGO)).isAir(), "Original barrel physically transferred from the world");
                    var actual = new Fixture(station, player, train, ItemVariant.of(originalCargo));
                    fixture.set(actual);
                    h.assertValueEqual(actualCargo(train, actual.originalCargo), (long) count, "Original named freight is in actual native mounted storage");
                    expected.set(assertions.applyAsInt(actual));
                    if (train.runtime.getSchedule() != null) {
                        var returned = train.runtime.returnSchedule(h.getLevel().registryAccess());
                        player.getInventory().placeItemBackInInventory(returned, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                    h.assertTrue(station.tryDisassemble(player), "Real station starts actual native body/cargo restoration");
                })
                .thenWaitUntil(() -> {
                    var actual = fixture.get();
                    h.assertTrue(TrainManager.get(h.getLevel()).get(actual.train.id) == null, "Actual restored Train retires from its real manager");
                    h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(CARGO)) instanceof BarrelBlockEntity, "Actual original barrel position is restored");
                    var restored = h.getBlockEntity(CARGO, BarrelBlockEntity.class).getItem(0);
                    h.assertTrue(ItemVariant.of(restored).equals(actual.originalCargo) && restored.getCount() == expected.get(), "Native restored freight has exact original identity and remaining physical count");
                }).thenSucceed();
    }
    private static Schedule instantaneous() {
        var first = new ChangeThrottleInstruction(); first.getData().putInt("Value", 25);
        var title = new ChangeTitleInstruction(); title.getData().putString("Text", "Night freight");
        var last = new ChangeThrottleInstruction(); last.getData().putInt("Value", 80);
        return new Schedule(new ArrayList<>(List.of(new ScheduleEntry(first, new ArrayList<>()), new ScheduleEntry(title, new ArrayList<>()), new ScheduleEntry(last, new ArrayList<>()))), false, 0);
    }
    private static void applyPaid(GameTestHelper h, Fixture f, Schedule schedule) {
        var held = new ItemStack(AllItems.SCHEDULE);
        held.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(h.getLevel().registryAccess()));
        f.player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var front = f.train.carriages.getFirst().anyAvailableEntity();
        h.assertTrue(front != null && front.isGloballyManaged(), "Actual accepted native carriage front owns conductor interaction");
        var local = BURNER.subtract(B);
        var captured = front.getContraption().getBlocks().get(local);
        var behavior = MovingInteractionBehaviour.REGISTRY.get(captured.state());
        h.assertTrue(behavior != null && behavior.handlePlayerInteraction(f.player, InteractionHand.MAIN_HAND, local, front), "Actual registered source burner conductor handles the real paid schedule");
        h.assertTrue(f.player.getMainHandItem().isEmpty() && f.train.runtime.getSchedule() != null, "Exactly one original physical schedule is consumed into actual Train custody");
    }

    @GameTest(structure=STRUCTURE,maxTicks=200) public void realAssembledTrainExecutesThrottleTitleAndNonCyclicCompletion(GameTestHelper h) {
        run(h, 11, f -> {
            applyPaid(h, f, instantaneous());
            f.train.runtime.tick(h.getLevel());
            h.assertTrue(f.train.throttle == .25f && f.train.runtime.currentEntry == 1, "Source throttle instruction changes actual Train and advances once");
            f.train.runtime.tick(h.getLevel());
            h.assertValueEqual(f.train.runtime.currentTitle, "Night freight", "Actual runtime title instruction");
            f.train.runtime.tick(h.getLevel()); f.train.runtime.tick(h.getLevel());
            h.assertTrue(f.train.throttle == .8f && f.train.runtime.paused && f.train.runtime.completed && f.train.runtime.currentEntry == 0, "Actual non-cyclic schedule ends with source completed/paused/reset semantics");
            var returned = f.train.runtime.returnSchedule(h.getLevel().registryAccess());
            h.assertTrue(returned.is(AllItems.SCHEDULE) && returned.getCount() == 1 && !ScheduleItem.getSchedule(h.getLevel().registryAccess(), returned).cyclic, "Exactly one physical source schedule returns");
            h.assertTrue(f.train.runtime.returnSchedule(h.getLevel().registryAccess()).isEmpty(), "Repeated return cannot duplicate original schedule stock");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, returned);
            return 11;
        });
    }
    @GameTest(structure=STRUCTURE,maxTicks=200) public void realRuntimeNativeNbtRestoresProgressOnTheSameActualOwner(GameTestHelper h) {
        run(h, 11, f -> {
            applyPaid(h, f, instantaneous());
            f.train.runtime.tick(h.getLevel());
            var written = f.train.runtime.write(h.getLevel().registryAccess());
            h.assertTrue(written.getIntOr("CurrentEntry", -1) == 1 && written.getIntArray("TransitTimes").orElseThrow().length == 3, "Actual source state/progress/transit keys serialized");
            f.train.runtime.read(h.getLevel().registryAccess(), written);
            h.assertTrue(f.train.runtime.train == f.train && f.train.runtime.currentEntry == 1
                    && f.train.runtime.state == ScheduleRuntime.State.PRE_TRANSIT && !f.train.runtime.paused, "Real native runtime restore retains actual Train ownership and in-progress instruction");
            f.train.runtime.tick(h.getLevel());
            h.assertTrue(f.train.runtime.currentEntry == 2 && f.train.runtime.currentTitle.equals("Night freight"), "Actual resumed next instruction executes after native decode");
            TrainManager.get(h.getLevel()).validateCustody();
            return 11;
        });
    }
    @GameTest(structure=STRUCTURE,maxTicks=200) public void actualAutomaticScheduleReturnDoesNotManufacturePhysicalItem(GameTestHelper h) {
        run(h, 11, f -> {
            f.train.runtime.setSchedule(instantaneous(), true);
            h.assertTrue(f.train.runtime.returnSchedule(h.getLevel().registryAccess()).isEmpty()
                    && f.train.runtime.getSchedule() == null && f.train.navigation.destination == null, "Actual automatic schedule has no physical paid item to return and cancels source navigation");
            return 11;
        });
    }
    @GameTest(structure=STRUCTURE,maxTicks=200) public void actualCargoThresholdObservesOnlyCommittedNativeMountedStorageChanges(GameTestHelper h) {
        run(h, 11, f -> {
            var condition = new ItemThresholdCondition();
            condition.getData().putInt("Operator", 2); condition.getData().putString("Threshold", "8");
            var context = new CompoundTag();
            h.assertFalse(condition.tickCompletion(h.getLevel(), f.train, context), "Eleven actual named apples do not satisfy equal eight");
            int version = f.train.carriages.getFirst().storage.getVersion();
            var storage = f.train.carriages.getFirst().storage.getAllItems();
            try (var tx = Transaction.openOuter()) { h.assertValueEqual(storage.extract(f.originalCargo, 3, tx), 3L, "Actual cargo extraction preview"); }
            h.assertTrue(actualCargo(f.train, f.originalCargo) == 11 && f.train.carriages.getFirst().storage.getVersion() == version, "Aborted native transaction preserves actual stock and lazy condition version");
            for (int i = 0; i < 20; i++) h.assertFalse(condition.tickCompletion(h.getLevel(), f.train, context), "Actual unchanged cargo remains unsatisfied through lazy interval");
            try (var tx = Transaction.openOuter()) { h.assertValueEqual(storage.extract(f.originalCargo, 3, tx), 3L, "Actual physical cargo extraction"); tx.commit(); }
            f.player.getInventory().placeItemBackInInventory(f.originalCargo.toStack(3), net.minecraft.util.Prediction.SERVER_ONLY);
            h.assertTrue(f.train.carriages.getFirst().storage.getVersion() != version, "Real final native commit changes source cargo version");
            long delivered = 0;
            for (int i = 0; i < f.player.getInventory().getContainerSize(); i++) {
                var item = f.player.getInventory().getItem(i);
                if (ItemVariant.of(item).equals(f.originalCargo)) delivered += item.getCount();
            }
            h.assertValueEqual(delivered, 3L, "Exactly three debited physical apples reach the actual receiving player");
            boolean completed = false;
            for (int i = 0; i < 20; i++) completed |= condition.tickCompletion(h.getLevel(), f.train, context);
            h.assertTrue(completed && actualCargo(f.train, f.originalCargo) == 8 && context.getIntOr("CurrentDisplay", -1) == 8, "Actual lazy condition sees committed native stock and updates real waiting status snapshot");
            return 8;
        });
    }
}

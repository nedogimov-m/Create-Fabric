package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.display.*;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

/** Genuine native source/menu/owner tests; actual Train is created only by physical station capture/adoption. UNRUN. */
public final class StationDepartureGameTests {
    private static final String STRUCTURE = "create_test:reservoir";
    private static final BlockPos R = new BlockPos(4,6,4), T = R.east(), S = T.south(2), L = S.above(), D = S.south(3);
    private static final BlockPos B = T.east().above(), BURNER = B.above().east();
    private record Station(StationBlockEntity block, ServerPlayer player, NativeSurvivalTestPlayer session) {}
    private record Fixture(Station station, Train train) {}
    private static DisplayLinkBlockEntity link(GameTestHelper h) { return h.getBlockEntity(L, DisplayLinkBlockEntity.class); }
    private static DisplayLinkContext context(GameTestHelper h) { return new DisplayLinkContext(h.getLevel(), link(h)); }
    private static CompoundTag config(DisplaySource source) { var tag = new CompoundTag(); tag.putString("Id", source.getId().toString()); return tag; }
    private static DisplayTargetStats stats(int rows) { return new DisplayTargetStats(rows, 32, AllDisplayTargets.DISPLAY_BOARD); }
    private static void sign(GameTestHelper h) { h.setBlock(D.below(), Blocks.STONE); h.setBlock(D, Blocks.OAK_SIGN); }
    private static void reader(GameTestHelper h) {
        sign(h); h.setBlock(L, AllBlocks.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING, Direction.UP));
        link(h).target(h.absolutePos(D));
    }
    private static Station station(GameTestHelper h, boolean assembling) {
        var rail = AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO);
        for (int i = 0; i < 15; i++) { h.setBlock(R.east(i).below(), Blocks.STONE); h.setBlock(R.east(i), rail); }
        for (int i = 0; i < 15; i++) h.assertTrue(TrackPropagator.reconcile(h.getLevel(), h.absolutePos(R.east(i)), rail, false).complete(), "Actual loaded rails discover their native graph");
        h.setBlock(S.below(), Blocks.STONE); h.setBlock(S, AllBlocks.TRACK_STATION);
        var station = h.getBlockEntity(S, StationBlockEntity.class);
        h.assertTrue(station.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(), h.absolutePos(T)), true, new Vec3(1,0,0), Optional.empty())), "Actual station binds its real graph point");
        h.setBlock(S.south().below(), Blocks.STONE);
        var session = NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(station.getBlockPos()).add(0,0,1)); var player = session.player();
        h.assertTrue(station.configure(player, "Departure " + station.getBlockPos().asLong(), assembling, DoorControl.ALL), "Actual permitted player configures the source station");
        return new Station(station, player, session);
    }
    private static void board(GameTestHelper h) {
        for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) h.setBlock(D.east(x).below(y), AllBlocks.DISPLAY_BOARD.defaultBlockState().setValue(FlapDisplayBlock.HORIZONTAL_FACING, Direction.SOUTH));
        h.setBlock(D.west(), AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        h.setBlock(D.west().north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));
        h.getBlockEntity(D.west().north(), KineticBlockEntity.class).setGeneratedSpeed(64);
    }
    private static void placeReader(GameTestHelper h, ServerPlayer player) {
        var stack = new ItemStack(AllBlocks.DISPLAY_LINK, 2);
        stack.set(AllDataComponents.CLICK_TO_LINK_DATA, new ClickToLinkBlockItem.ClickToLinkData(h.absolutePos(D), h.getLevel().dimension().identifier()));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var pos = h.absolutePos(S);
        h.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0), Direction.UP, pos, false))).consumesAction(), "Actual native link item places on the real source");
        h.assertTrue(h.getBlockState(L).is(AllBlocks.DISPLAY_LINK) && stack.getCount() == 1 && !stack.has(AllDataComponents.CLICK_TO_LINK_DATA), "Exactly one original physical link item is debited and selection is cleared");
    }

    @GameTest(structure=STRUCTURE) public void nativeRailwaySourcesResolveActualBlocksAndOriginalAliases(GameTestHelper h) {
        var actual = station(h, false); reader(h);
        var sources = DisplaySource.getAll(h.getLevel(), h.absolutePos(S));
        h.assertTrue(sources.contains(AllDisplaySources.STATION_SUMMARY) && sources.contains(AllDisplaySources.TRAIN_STATUS), "Both genuine station consumers are associated with the actual station");
        h.assertTrue(DisplaySource.get(Create.asResource("track_station_source_station_summary")) == AllDisplaySources.STATION_SUMMARY && DisplaySource.get(Create.asResource("track_station_source_train_status")) == AllDisplaySources.TRAIN_STATUS, "Original source aliases resolve genuine registered consumers");
        h.setBlock(S, AllBlocks.TRACK_OBSERVER);
        h.assertTrue(h.getBlockEntity(S, TrackObserverBlockEntity.class).getObserver() == null && DisplaySource.getAll(h.getLevel(), h.absolutePos(S)).contains(AllDisplaySources.OBSERVED_TRAIN_NAME), "Unbound actual observer exposes its genuine source");
        h.assertTrue(DisplaySource.get(Create.asResource("track_observer_source_observed_train_name")) == AllDisplaySources.OBSERVED_TRAIN_NAME && AllDisplaySources.OBSERVED_TRAIN_NAME.getPassiveRefreshTicks() == 400 && AllDisplaySources.OBSERVED_TRAIN_NAME.provideText(context(h), stats(1)).equals(DisplaySource.EMPTY), "Original alias and refresh semantics do not invent an observed Train");
        actual.session.close(); h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realMenuSnapshotsStationNameWithoutChangingActiveConfiguration(GameTestHelper h) {
        var actual = station(h, false); reader(h);
        var before = link(h).getSourceConfig().copy();
        var opening = DisplayLinkMenu.snapshot(link(h));
        var entry = opening.sources().stream().filter(source -> source.id().equals(AllDisplaySources.STATION_SUMMARY.getId())).findFirst().orElseThrow();
        h.assertTrue(entry.defaults().getStringOr("Filter", "").equals(actual.block.getStation().name()) && entry.defaults().getIntOr("NameColumn", -1) == 50 && entry.defaults().getIntOr("PlatformColumn", -1) == 3, "Actual owning station supplies its original source defaults");
        h.assertTrue(link(h).getSourceConfig().equals(before) && link(h).activeSource == null, "Browsing genuine source defaults cannot mutate active native link state");
        var name = entry.options().stream().filter(option -> option.key().equals("NameColumn")).findFirst().orElseThrow();
        var platform = entry.options().stream().filter(option -> option.key().equals("PlatformColumn")).findFirst().orElseThrow();
        h.assertTrue(name.isNumber() && name.maximum() == 73 && name.shiftStep() == 12 && platform.maximum() == 16 && platform.shiftStep() == 4 && name.widgetLine() == platform.widgetLine() && name.widgetOffset() + name.widgetWidth() <= platform.widgetOffset(), "Full original ranges have genuine numeric controls on one non-overlapping row");
        actual.session.close(); h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void authoritativeNativeConfigurationClampsNumbersAndPreservesRealDefaults(GameTestHelper h) {
        var actual = station(h, false); reader(h);
        var conf = config(AllDisplaySources.STATION_SUMMARY); conf.putInt("NameColumn", Integer.MAX_VALUE); conf.putInt("PlatformColumn", Integer.MIN_VALUE);
        h.assertTrue(link(h).configure(conf, 0), "Actual source is configured through its real native link");
        h.assertTrue(link(h).getSourceConfig().getIntOr("NameColumn", -1) == 72 && link(h).getSourceConfig().getIntOr("PlatformColumn", -1) == 0 && link(h).getSourceConfig().getStringOr("Filter", "").equals(actual.block.getStation().name()), "Untrusted numeric extremes are bounded without deleting the actual station default");
        var partial = config(AllDisplaySources.STATION_SUMMARY); partial.putInt("NameColumn", 25);
        h.assertTrue(link(h).configure(partial, 0) && link(h).getSourceConfig().getIntOr("PlatformColumn", -1) == 0 && link(h).getSourceConfig().getStringOr("Filter", "").equals(actual.block.getStation().name()), "Partial real source edits preserve existing platform/filter data");
        actual.session.close(); h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualOpeningCodecCarriesFullNumericRangesAndIndependentDefaults(GameTestHelper h) {
        var actual = station(h, false); reader(h); var opening = DisplayLinkMenu.snapshot(link(h));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            DisplayLinkMenu.Opening.CODEC.encode(buffer, opening); var decoded = DisplayLinkMenu.Opening.CODEC.decode(buffer);
            h.assertTrue(decoded.equals(opening) && buffer.readableBytes() == 0, "Actual registered menu codec roundtrips every source option, target capacity and station defaults");
            var before = opening.sources().getFirst().defaults().copy(); decoded.sources().getFirst().defaults().putString("Filter", "Edited only after decode");
            h.assertTrue(opening.sources().getFirst().defaults().equals(before), "Decoded mutable native drafts cannot alias the server snapshot");
        } finally { buffer.release(); }
        actual.session.close(); h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void oldChoiceAndTextSchemasRemainValidOnTheGenuineOpeningCodec(GameTestHelper h) {
        var text = new DisplaySourceConfiguration("Label", List.of(), 256);
        var choice = new DisplaySourceConfiguration("Format", List.of("first", "second"), 0);
        var defaults = new CompoundTag(); defaults.putString("Label", "Original"); defaults.putInt("Format", 1);
        var entry = new DisplayLinkMenu.SourceEntry(AllDisplaySources.SCOREBOARD.getId(), List.of(text, choice), defaults);
        defaults.putString("Label", "Later caller edit");
        var opening = new DisplayLinkMenu.Opening(h.absolutePos(L), List.of(entry), null, new CompoundTag(), 0, 0);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            DisplayLinkMenu.Opening.CODEC.encode(buffer, opening); var decoded = DisplayLinkMenu.Opening.CODEC.decode(buffer);
            h.assertTrue(decoded.equals(opening) && decoded.sources().getFirst().defaults().getStringOr("Label", "").equals("Original") && text.isText() && !choice.isText() && !choice.isNumber(), "Existing three-argument schema and native defaults retain actual text/choice semantics");
        } finally { buffer.release(); }
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void noActualPredictionsProduceOriginalFourOrFiveColumnPadding(GameTestHelper h) {
        var actual = station(h, false); reader(h); var conf = config(AllDisplaySources.STATION_SUMMARY);
        conf.putString("Filter", actual.block.getStation().name()); h.assertTrue(link(h).configure(conf, 0), "Real station consumer configured");
        var data = GlobalTrainDisplayData.get(h.getLevel().getServer()); data.refresh(h.getLevel().getServer());
        var rows = AllDisplaySources.STATION_SUMMARY.provideFlapDisplayText(context(h), stats(3));
        h.assertTrue(rows.size() == 3 && rows.stream().allMatch(row -> row.size() == 4 && row.get(1).getString().equals(" . ")), "Empty real timetable retains original four-column padding");
        conf.putString("Filter", actual.block.getStation().name() + " Platform *"); h.assertTrue(link(h).configure(conf, 0), "Real platform filter configured");
        rows = AllDisplaySources.STATION_SUMMARY.provideFlapDisplayText(context(h), stats(2));
        h.assertTrue(rows.size() == 2 && rows.stream().allMatch(row -> row.size() == 5) && AllDisplaySources.STATION_SUMMARY.provideText(context(h), stats(2)).equals(DisplaySource.EMPTY) && AllDisplaySources.TRAIN_STATUS.provideText(context(h), stats(1)).equals(DisplaySource.EMPTY), "Wildcard padding has its actual fifth platform column and no invented Train/status on a sign");
        actual.session.close(); h.succeed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=45) public void actualPoweredBoardReceivesSourceConfiguredPlatformSectionsAndPadding(GameTestHelper h) {
        var actual = station(h, false); reader(h); board(h);
        h.runAfterDelay(12, () -> {
            var conf = config(AllDisplaySources.STATION_SUMMARY); conf.putString("Filter", actual.block.getStation().name() + " Platform *");
            h.assertTrue(link(h).configure(conf, 0), "Actual native source transfers to the genuine powered board");
            var board = h.getBlockEntity(D, FlapDisplayBlockEntity.class);
            h.assertTrue(board.isController && board.xSize == 4 && board.getLines().size() == 4 && board.isSpeedRequirementFulfilled(), "Actual cog/motor graph powers a native four-by-two controller");
            h.assertTrue(board.getLines().stream().allMatch(row -> row.getSections().size() == 5 && row.getSections().get(1).getText().getString().equals(" . ")), "Actual source configures real five-column layouts and clears all unused board rows with original padding");
            actual.session.close(); h.succeed();
        });
    }
    @GameTest(structure=STRUCTURE) public void nativePhysicalLinkPlacementPersistsActualPlayerOwnerBeforeAWorldExists(GameTestHelper h) {
        var actual = station(h, false); sign(h); placeReader(h, actual.player);
        h.assertTrue(actual.player.getUUID().equals(link(h).advancementOwner()), "Actual native placement assigns the real same-world player owner");
        var saved = link(h).saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.read("Owner", UUIDUtil.CODEC).orElseThrow().equals(actual.player.getUUID()), "Original Owner native UUID schema is saved");
        var copy = (DisplayLinkBlockEntity) BlockEntity.loadStatic(h.absolutePos(L), h.getBlockState(L), saved, h.getLevel().registryAccess());
        h.assertTrue(copy != null && copy.getLevel() == null && actual.player.getUUID().equals(copy.advancementOwner()) && copy.saveWithFullMetadata(h.getLevel().registryAccess()).read("Owner", UUIDUtil.CODEC).orElseThrow().equals(actual.player.getUUID()), "Native saved owner survives original no-world load/resave");
        actual.session.close(); h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualPlacedNixieTransferAwardsNativeProgressThroughItsRegisteredCriterion(GameTestHelper h) {
        h.setBlock(S, AllBlocks.ORANGE_NIXIE_TUBE); h.getBlockEntity(S, NixieTubeBlockEntity.class).displayCustomText("\"Actual source\"", 0); sign(h);
        h.setBlock(S.south().below(), Blocks.STONE);
        var session = NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(h.absolutePos(S)).add(0,0,1)); var player = session.player();
        var holder = h.getLevel().getServer().getAdvancements().get(Create.asResource("display_link"));
        var boardHolder = h.getLevel().getServer().getAdvancements().get(Create.asResource("display_board_0"));
        h.assertTrue(holder != null && boardHolder != null && !player.getAdvancements().getOrStartProgress(holder).isDone(), "Genuine source advancements and full parent closure must load before the actual owner test");
        placeReader(h, player); h.assertTrue(link(h).configure(config(AllDisplaySources.NIXIE_TUBE), 0), "Actual placed source performs a genuine text transfer");
        h.assertValueEqual(h.getBlockEntity(D, SignBlockEntity.class).getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString(), "Actual source", "Physical target really receives native text");
        h.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone() && !player.getAdvancements().getOrStartProgress(boardHolder).isDone() && player.getUUID().equals(link(h).advancementOwner()), "Real native criterion awards source progress while retaining owner for the still-unearned timetable advancement");
        session.close(); h.succeed();
    }

    private static void runTrain(GameTestHelper h, Consumer<Fixture> assertions) {
        var actual = station(h, true); reader(h);
        h.setBlock(B, AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X));
        h.setBlock(B.above(), AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING, Direction.EAST));
        h.setBlock(B.above().east(2), AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING, Direction.WEST));
        h.setBlock(BURNER, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(B), h.absolutePos(B.above().east(2))))), "Actual native glue joins physical bogey/controls/burner");
        var fixture = new AtomicReference<Fixture>();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(actual.block.preflight(actual.player).readyForCapture(), "Actual loaded owner/body preflight becomes ready naturally"))
                .thenExecute(() -> {
                    h.assertTrue(actual.block.tryAssemble(actual.player), "Actual station captures/adopts the genuine native Train");
                    var train = actual.block.getStation().getPresentTrain();
                    h.assertTrue(train != null && TrainManager.get(h.getLevel()).get(train.id) == train && train.carriages.getFirst().anyAvailableEntity().isGloballyManaged(), "Only the real globally adopted physical Train enters this test");
                    var f = new Fixture(actual, train); fixture.set(f); assertions.accept(f);
                    if (train.runtime.getSchedule() != null) actual.player.getInventory().placeItemBackInInventory(train.runtime.returnSchedule(h.getLevel().registryAccess()), net.minecraft.util.Prediction.SERVER_ONLY);
                    h.assertTrue(actual.block.tryDisassemble(actual.player), "Actual owner begins native restoration after consumer checks");
                }).thenWaitUntil(() -> {
                    var train = fixture.get().train;
                    h.assertTrue(TrainManager.get(h.getLevel()).get(train.id) == null && h.getBlockState(B).is(AllBlocks.SMALL_BOGEY) && h.getBlockState(BURNER).is(AllBlocks.BLAZE_BURNER), "Genuine Train retires only after actual body restoration");
                }).thenExecute(actual.session::close).thenSucceed();
    }
    private static void applyPaid(GameTestHelper h, Fixture f, Schedule schedule) {
        var item = new ItemStack(AllItems.SCHEDULE); item.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(h.getLevel().registryAccess()));
        f.station.player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var entity = f.train.carriages.getFirst().anyAvailableEntity(); var local = BURNER.subtract(B);
        var behaviour = MovingInteractionBehaviour.REGISTRY.get(entity.getContraption().getBlocks().get(local).state());
        h.assertTrue(behaviour != null && behaviour.handlePlayerInteraction(f.station.player, InteractionHand.MAIN_HAND, local, entity) && item.isEmpty() && f.train.runtime.getSchedule() != null, "Actual source conductor consumes one original physical schedule into genuine Train custody");
    }
    @GameTest(structure=STRUCTURE,maxTicks=200) public void realPaidTrainFeedsTimetableFromItsOwnRuntimeWithoutInjectedPredictions(GameTestHelper h) {
        runTrain(h, f -> {
            var title = new ChangeTitleInstruction(); title.getData().putString("Text", "Night freight");
            var destination = new DestinationInstruction(); destination.getData().putString("Text", f.station.block.getStation().name() + " Platform 7");
            var other = new DestinationInstruction(); other.getData().putString("Text", "A different real schedule filter");
            var schedule = new Schedule(new ArrayList<>(List.of(new ScheduleEntry(title, new ArrayList<>()), new ScheduleEntry(destination, new ArrayList<>()), new ScheduleEntry(other, new ArrayList<>()))), true, 0);
            applyPaid(h, f, schedule); f.train.runtime.tick(h.getLevel());
            h.assertTrue(f.train.runtime.currentEntry == 1 && f.train.runtime.currentTitle.equals("Night freight") && f.train.runtime.state == ScheduleRuntime.State.PRE_TRANSIT, "Only actual source title execution advances the genuine runtime");
            var conf = config(AllDisplaySources.STATION_SUMMARY); conf.putString("Filter", f.station.block.getStation().name() + " Platform *");
            h.assertTrue(link(h).configure(conf, 0), "Actual source filter selects its original physical Train schedule");
            var data = GlobalTrainDisplayData.get(h.getLevel().getServer()); data.refresh(h.getLevel().getServer());
            var rows = AllDisplaySources.STATION_SUMMARY.provideFlapDisplayText(context(h), stats(2));
            h.assertTrue(rows.size() == 2 && rows.getFirst().size() == 5 && rows.getFirst().get(1).getString().equals(" ~ ") && rows.getFirst().get(2).equals(f.train.name) && rows.getFirst().get(3).getString().equals("Night freight") && rows.getFirst().get(4).getString().equals("7"), "Real PRE_TRANSIT source prediction retains original unknown-time/title/name/platform semantics");
            h.assertTrue(rows.get(1).get(1).getString().equals(" . ") && AllDisplaySources.TRAIN_STATUS.provideText(context(h), stats(1)).equals(DisplaySource.EMPTY), "Only the genuine matching departure is present; actual PRE_TRANSIT Train has no fabricated waiting status");
            var returned = f.train.runtime.returnSchedule(h.getLevel().registryAccess()); f.station.player.setItemInHand(InteractionHand.MAIN_HAND, returned);
            data.refresh(h.getLevel().getServer());
            h.assertTrue(AllDisplaySources.STATION_SUMMARY.provideFlapDisplayText(context(h), stats(2)).stream().allMatch(row -> row.get(1).getString().equals(" . ")) && f.train.runtime.returnSchedule(h.getLevel().registryAccess()).isEmpty(), "Actual schedule return clears real departure ownership and cannot duplicate paid schedule stock");
        });
    }

    @GameTest(structure=STRUCTURE,maxTicks=200) public void realStationReusableTimetableAppliesOnlyAfterPhysicalInsertionAndNormalArrivalTick(GameTestHelper h) {
        runTrain(h, f -> {
            var destination=new DestinationInstruction();destination.getData().putString("Text",f.station.block.getStation().name());
            var schedule=new Schedule(new ArrayList<>(List.of(new ScheduleEntry(destination,new ArrayList<>()))),true,0);
            var template=new ItemStack(AllItems.SCHEDULE);template.set(AllDataComponents.TRAIN_SCHEDULE,schedule.write(h.getLevel().registryAccess()));
            var variant=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(template);
            var storage=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(h.getLevel(),f.station.block.getBlockPos(),Direction.UP);
            h.assertTrue(storage!=null&&f.train.runtime.getSchedule()==null,"Actual adopted physical Train starts without fabricated schedule custody");
            try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
                h.assertTrue(storage.insert(variant,1,tx)==1,"Actual station accepts tentative real template");
            }
            h.assertTrue(f.station.block.getAutoSchedule().isEmpty()&&f.train.runtime.getSchedule()==null,"Aborted actual transfer neither mints station stock nor configures Train runtime");
            try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
                h.assertTrue(storage.insert(variant,1,tx)==1,"Committed insertion accepts one actual timetable");tx.commit();
            }
            h.assertTrue(f.station.block.getAutoSchedule().getCount()==1&&f.train.runtime.getSchedule()!=null&&f.train.runtime.isAutoSchedule,"Committed real held template configures actual present Train through source callback");
            h.assertTrue(f.train.runtime.returnSchedule(h.getLevel().registryAccess()).isEmpty()&&f.station.block.getAutoSchedule().getCount()==1,"Returning auto runtime cannot manufacture another physical timetable from reusable template");
            StationBlockEntity.tick(h.getLevel(),f.station.block.getBlockPos(),f.station.block.getBlockState(),f.station.block);
            h.assertTrue(f.train.runtime.getSchedule()!=null&&f.train.runtime.isAutoSchedule&&f.station.block.getAutoSchedule().getCount()==1,"Normal actual station arrival tick reapplies its still-owned template once");
            try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
                h.assertTrue(storage.extract(variant,64,tx)==1,"One real held timetable returns to caller custody");tx.commit();
            }
            h.assertTrue(f.station.block.getAutoSchedule().isEmpty()&&f.train.runtime.returnSchedule(h.getLevel().registryAccess()).isEmpty(),"Runtime auto data has no extractable material after canonical station template extraction");
        });
    }
}

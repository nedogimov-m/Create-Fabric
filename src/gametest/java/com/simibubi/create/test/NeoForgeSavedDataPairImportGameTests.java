package com.simibubi.create.test;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.TrainTrafficSavedData;
import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataPairImport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Genuine native railway/traffic values, native SavedDataStorage and compressed source files. UNRUN. */
public final class NeoForgeSavedDataPairImportGameTests {
    private record Directory(Path root) implements AutoCloseable {
        static Directory create() throws IOException { return new Directory(Files.createTempDirectory("create-original-pair-")); }
        Path source() { return root.resolve("create_tracks.dat"); }
        Path first() { return root.resolve("create/tracks.dat"); }
        Path second() { return root.resolve("create/trains.dat"); }
        Path journal() { return root.resolve("create/.neoforge-pair-railway-test-v1"); }
        SavedDataStorage storage(GameTestHelper h) { return new SavedDataStorage(root, DataFixers.getDataFixer(), h.getLevel().registryAccess()); }
        @Override public void close() throws IOException {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static CompoundTag originalEmpty() {
        var data = new CompoundTag();
        for (var key : new String[] {"RailGraphs", "Trains", "SignalBlocks", "DimensionPalette"}) data.put(key, new ListTag());
        return data;
    }
    private static void source(Directory directory, CompoundTag data, int version) throws IOException {
        var envelope = new CompoundTag(); envelope.putInt("DataVersion", version); envelope.put("data", data);
        NbtIo.writeCompressed(envelope, directory.source());
    }
    private static NeoForgeSavedDataPairImport.Converted emptyConverted(CompoundTag data) {
        keys(data, "empty source railway fixture", "RailGraphs", "Trains", "SignalBlocks", "DimensionPalette");
        for (var key : data.keySet()) if (!list(data, key).isEmpty()) throw invalid("This empty-source fixture cannot convert nonempty payloads");
        var graphs = new CompoundTag(); graphs.putInt("GraphFormatVersion", 1); graphs.put("RailGraphs", new ListTag());
        var retained = new CompoundTag(); retained.putInt("TrafficFormatVersion", 1);
        retained.put("Trains", new ListTag()); retained.put("SignalBlocks", new ListTag()); retained.put("DimensionPalette", new ListTag());
        var traffic = new CompoundTag(); traffic.put("TrainTraffic", retained);
        return new NeoForgeSavedDataPairImport.Converted(graphs, traffic);
    }
    private static NeoForgeSavedDataPairImport.Pair<RailwaySavedData, TrainTrafficSavedData> load(
            GameTestHelper h, Directory directory, SavedDataStorage storage) {
        return load(h, directory, storage, (data, version, registries) -> emptyConverted(data));
    }
    private static NeoForgeSavedDataPairImport.Pair<RailwaySavedData, TrainTrafficSavedData> load(
            GameTestHelper h, Directory directory, SavedDataStorage storage, NeoForgeSavedDataPairImport.Converter converter) {
        return NeoForgeSavedDataPairImport.load(storage, directory.root, h.getLevel().registryAccess(),
            RailwaySavedData.TYPE, TrainTrafficSavedData.TYPE, "create_tracks.dat", "railway-test-v1", "empty-test-v1", converter);
    }
    private static boolean owns(SavedDataStorage storage, SavedDataType<?> type) {
        var value = ((SavedDataStorageAccessor) storage).create$getCache().get(type);
        return value != null && value.isPresent();
    }
    private static void noPublication(GameTestHelper h, Directory directory, SavedDataStorage storage) {
        h.assertTrue(!owns(storage, RailwaySavedData.TYPE) && !owns(storage, TrainTrafficSavedData.TYPE)
                && !Files.exists(directory.first()) && !Files.exists(directory.second()) && !Files.exists(directory.journal().resolve("prepared.dat")),
            "No partial native cache/file owner or durable conversion intent was published");
    }
    private static boolean rejected(Runnable action) {
        try { action.run(); return false; }
        catch (IllegalArgumentException | IllegalStateException | UncheckedIOException expected) { return true; }
    }
    private static void nativeFile(Path target, CompoundTag data) throws IOException {
        Files.createDirectories(target.getParent()); var envelope = new CompoundTag(); envelope.put("data", data);
        NbtUtils.addCurrentDataVersion(envelope); NbtIo.writeCompressed(envelope, target);
    }

    @GameTest public void genuinePairInstallsBothNativeOwnersAndLeavesOriginalUntouched(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            source(directory, originalEmpty(), 3955); byte[] source = Files.readAllBytes(directory.source());
            byte[] first, second;
            try (var storage = directory.storage(h)) {
                var actual = load(h, directory, storage);
                h.assertTrue(storage.get(RailwaySavedData.TYPE) == actual.first() && storage.get(TrainTrafficSavedData.TYPE) == actual.second()
                        && !actual.first().isDirty() && !actual.second().isDirty() && actual.first().getTrackNetworks().isEmpty()
                        && actual.second().retained().getInt("TrafficFormatVersion").orElseThrow() == 1,
                    "Both real native values become the exact canonical owners after full preparation");
                h.assertTrue(Files.exists(directory.first()) && Files.exists(directory.second())
                        && Files.exists(directory.journal().resolve("committed.dat")),
                    "Both actual native files precede the durable commit and owner visibility");
                first = Files.readAllBytes(directory.first()); second = Files.readAllBytes(directory.second());
            }
            try (var storage = directory.storage(h)) {
                var actual = load(h, directory, storage, (data, version, registries) -> { throw new AssertionError("Committed native scopes must not reconvert source"); });
                h.assertTrue(storage.get(RailwaySavedData.TYPE) == actual.first() && storage.get(TrainTrafficSavedData.TYPE) == actual.second(),
                    "Actual native files reload into both genuine scopes without a second conversion");
            }
            h.assertTrue(Arrays.equals(source, Files.readAllBytes(directory.source())) && Arrays.equals(first, Files.readAllBytes(directory.first()))
                    && Arrays.equals(second, Files.readAllBytes(directory.second())),
                "Read-only native shutdown/reload cannot alter source or either native file");
        }
        h.succeed();
    }

    @GameTest public void secondActualCodecFailureCannotPublishFirstScope(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955); byte[] before = Files.readAllBytes(directory.source());
            h.assertTrue(rejected(() -> load(h, directory, storage, (data, version, registries) -> {
                var converted = emptyConverted(data); converted.second().getCompound("TrainTraffic").orElseThrow().putInt("TrafficFormatVersion", 2); return converted;
            })), "Unknown actual native traffic format must fail its real codec");
            noPublication(h, directory, storage);
            h.assertTrue(Arrays.equals(before, Files.readAllBytes(directory.source())), "A failed second codec retains the complete original file");
        }
        h.succeed();
    }

    @GameTest public void bothActualCachedOwnersWinWithoutReadingMalformedDisk(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            var first = new RailwaySavedData(); var second = new TrainTrafficSavedData();
            storage.set(RailwaySavedData.TYPE, first); storage.set(TrainTrafficSavedData.TYPE, second); first.setDirty(); second.setDirty();
            Files.writeString(directory.source(), "malformed source");
            var actual = load(h, directory, storage);
            h.assertTrue(actual.first() == first && actual.second() == second && first.isDirty() && second.isDirty()
                    && !Files.exists(directory.journal()), "Exact raw native owners and their dirty state win without decoder/filesystem side effects");
        }
        h.succeed();
    }

    @GameTest public void incompleteActualCachedOwnerCannotConsumeUnrelatedSourceRemainder(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955); var first = new RailwaySavedData(); storage.set(RailwaySavedData.TYPE, first); first.setDirty();
            h.assertTrue(rejected(() -> load(h, directory, storage)) && storage.get(RailwaySavedData.TYPE) == first && first.isDirty()
                    && !owns(storage, TrainTrafficSavedData.TYPE) && !Files.exists(directory.journal()) && !Files.exists(directory.second()),
                "An unrelated actual first native owner is preserved; migration cannot invent a second source owner around it");
        }
        h.succeed();
    }

    @GameTest public void bothOwnersInstalledDuringPreparationRemainTheExactWinners(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955); var first = new RailwaySavedData(); var second = new TrainTrafficSavedData();
            var actual = load(h, directory, storage, (data, version, registries) -> {
                var converted = emptyConverted(data); storage.set(RailwaySavedData.TYPE, first); storage.set(TrainTrafficSavedData.TYPE, second); return converted;
            });
            h.assertTrue(actual.first() == first && actual.second() == second && first.isDirty() && second.isDirty()
                    && !Files.exists(directory.first()) && !Files.exists(directory.second()) && !Files.exists(directory.journal()),
                "A genuine pair installed by preparation callbacks wins before any legacy disk publication");
        }
        h.succeed();
    }

    @GameTest public void oneOwnerInstalledDuringPreparationPreventsPartialPublication(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955); var second = new TrainTrafficSavedData();
            h.assertTrue(rejected(() -> load(h, directory, storage, (data, version, registries) -> {
                var converted = emptyConverted(data); storage.set(TrainTrafficSavedData.TYPE, second); return converted;
            })) && storage.get(TrainTrafficSavedData.TYPE) == second && second.isDirty() && !owns(storage, RailwaySavedData.TYPE)
                    && !Files.exists(directory.first()) && !Files.exists(directory.second()) && !Files.exists(directory.journal()),
                "A single callback-installed native winner is never overwritten or combined with an uncommitted source half");
        }
        h.succeed();
    }

    @GameTest public void sourceEditOrUnknownVersionCannotCreateEitherNativeScope(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 999999);
            h.assertTrue(rejected(() -> load(h, directory, storage)), "Unknown original version must fail explicitly"); noPublication(h, directory, storage);
        }
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955);
            h.assertTrue(rejected(() -> load(h, directory, storage, (data, version, registries) -> {
                var converted = emptyConverted(data);
                try { Files.writeString(directory.source(), "actual external source edit"); } catch (IOException failure) { throw new UncheckedIOException(failure); }
                return converted;
            })), "A real source edit during conversion must invalidate the prepared snapshot"); noPublication(h, directory, storage);
        }
        h.succeed();
    }

    @GameTest public void partialUnjournaledNativeFileCannotBeReplacedFromSource(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955); nativeFile(directory.first(), emptyConverted(originalEmpty()).first());
            byte[] first = Files.readAllBytes(directory.first()), source = Files.readAllBytes(directory.source());
            h.assertTrue(rejected(() -> load(h, directory, storage)) && !owns(storage, RailwaySavedData.TYPE) && !owns(storage, TrainTrafficSavedData.TYPE)
                    && !Files.exists(directory.second()) && !Files.exists(directory.journal()) && Arrays.equals(first, Files.readAllBytes(directory.first()))
                    && Arrays.equals(source, Files.readAllBytes(directory.source())),
                "An unjournaled native half is a real existing owner, never an invented receipt authorizing source merge");
        }
        h.succeed();
    }

    /** Reconstruct exactly the filesystem state after the first native link and before the second. */
    private static void interruptedAfterFirst(GameTestHelper h, Directory directory) throws IOException {
        source(directory, originalEmpty(), 3955);
        try (var storage = directory.storage(h)) { load(h, directory, storage); }
        Files.delete(directory.second()); Files.delete(directory.journal().resolve("committed.dat"));
    }

    @GameTest public void actualDurableIntentResumesTheMissingSecondNativeFile(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            interruptedAfterFirst(h, directory); byte[] first = Files.readAllBytes(directory.first()), source = Files.readAllBytes(directory.source());
            try (var storage = directory.storage(h)) {
                var actual = load(h, directory, storage, (data, version, registries) -> { throw new AssertionError("Resume must use its actual prepared bytes"); });
                h.assertTrue(storage.get(RailwaySavedData.TYPE) == actual.first() && storage.get(TrainTrafficSavedData.TYPE) == actual.second()
                        && Files.exists(directory.second()) && Files.exists(directory.journal().resolve("committed.dat"))
                        && Arrays.equals(first, Files.readAllBytes(directory.first())) && Arrays.equals(source, Files.readAllBytes(directory.source())),
                    "A genuine recorded partial publication completes both files before both native cache identities appear");
            }
        }
        h.succeed();
    }

    @GameTest public void foreignSecondOwnerOrTamperedPreparedContentCannotResume(GameTestHelper h) throws Exception {
        for (int failureMode = 0; failureMode < 3; failureMode++) {
            try (var directory = Directory.create()) {
                interruptedAfterFirst(h, directory); byte[] first = Files.readAllBytes(directory.first());
                if (failureMode == 0) Files.writeString(directory.second(), "real foreign native target");
                else if (failureMode == 1) {
                    var prepared = NbtIo.readCompressed(directory.journal().resolve("prepared.dat"), net.minecraft.nbt.NbtAccounter.create(256L * 1024 * 1024));
                    Files.writeString(directory.journal().resolve(prepared.getString("SecondSha256").orElseThrow() + ".dat"), "changed prepared content");
                } else Files.writeString(directory.source(), "source changed after durable preparation");
                try (var storage = directory.storage(h)) {
                    h.assertTrue(rejected(() -> load(h, directory, storage)) && !owns(storage, RailwaySavedData.TYPE) && !owns(storage, TrainTrafficSavedData.TYPE)
                            && !Files.exists(directory.journal().resolve("committed.dat")) && Arrays.equals(first, Files.readAllBytes(directory.first())),
                        "Conflicting actual second owner/altered blob/source never authorizes exposing or replacing either scope");
                }
            }
        }
        h.succeed();
    }

    @GameTest public void laterNativeSaveRemainsAuthoritativeOverInitialPreparedBytes(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            source(directory, originalEmpty(), 3955); var position = GlobalPos.of(h.getLevel().dimension(), new BlockPos(31, 70, -17));
            try (var storage = directory.storage(h)) {
                var actual = load(h, directory, storage); actual.first().queue(new RailwaySavedData.PendingRail(position, Blocks.RAIL.defaultBlockState(), true));
                h.assertTrue(actual.first().isDirty(), "Genuine native graph pending work marks its actual owner dirty");
            }
            Files.writeString(directory.source(), "obsolete source is now malformed");
            try (var storage = directory.storage(h)) {
                var actual = load(h, directory, storage);
                h.assertTrue(actual.first().pending().size() == 1 && actual.first().pending().getFirst().position().equals(position),
                    "Committed journal never restores stale initial graph bytes over a later genuine native dirty save");
            }
        }
        h.succeed();
    }

    @GameTest public void malformedSecondPresentNativeFileCannotPublishFirstNativeCache(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955); var converted = emptyConverted(originalEmpty()); nativeFile(directory.first(), converted.first());
            var invalid = converted.second(); invalid.getCompound("TrainTraffic").orElseThrow().putInt("TrafficFormatVersion", 9); nativeFile(directory.second(), invalid);
            byte[] first = Files.readAllBytes(directory.first()), second = Files.readAllBytes(directory.second());
            h.assertTrue(rejected(() -> load(h, directory, storage)) && !owns(storage, RailwaySavedData.TYPE) && !owns(storage, TrainTrafficSavedData.TYPE)
                    && Arrays.equals(first, Files.readAllBytes(directory.first())) && Arrays.equals(second, Files.readAllBytes(directory.second())),
                "Both existing native values must decode before this loader publishes the first cache identity");
        }
        h.succeed();
    }

    @GameTest public void realNativeCodecCallbackCannotReplaceTheWinningOtherScope(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            var converted = emptyConverted(originalEmpty()); nativeFile(directory.first(), converted.first()); nativeFile(directory.second(), converted.second());
            Files.writeString(directory.source(), "obsolete source must never be read"); var winner = new RailwaySavedData();
            // A real native SavedDataType and codec still decode a genuine TrainTrafficSavedData. The
            // delegating codec exposes the actual reentry boundary without substituting a fake owner.
            var trafficType = new SavedDataType<>(TrainTrafficSavedData.TYPE.id(), TrainTrafficSavedData::new,
                TrainTrafficSavedData.CODEC.xmap(value -> { storage.set(RailwaySavedData.TYPE, winner); return value; }, value -> value), null);
            var actual = NeoForgeSavedDataPairImport.load(storage, directory.root, h.getLevel().registryAccess(),
                RailwaySavedData.TYPE, trafficType, "create_tracks.dat", "railway-test-v1", "empty-test-v1", (data, version, registries) -> {
                    throw new AssertionError("Both actual native files must remain authoritative");
                });
            h.assertTrue(actual.first() == winner && storage.get(RailwaySavedData.TYPE) == winner && winner.isDirty()
                    && storage.get(trafficType) == actual.second() && !actual.second().isDirty() && !Files.exists(directory.journal()),
                "A genuine native codec callback winner retains exact identity/dirty state instead of being replaced by the earlier decoded graph candidate");
        }
        h.succeed();
    }

    @GameTest public void resumeDecodeCannotOverwriteAnActualNewCanonicalOwner(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, originalEmpty(), 3955); var winner = new RailwaySavedData(); var decodes = new AtomicInteger();
            var trafficType = new SavedDataType<>(TrainTrafficSavedData.TYPE.id(), TrainTrafficSavedData::new,
                TrainTrafficSavedData.CODEC.xmap(value -> { if (decodes.incrementAndGet() == 2) storage.set(RailwaySavedData.TYPE, winner); return value; }, value -> value), null);
            h.assertTrue(rejected(() -> NeoForgeSavedDataPairImport.load(storage, directory.root, h.getLevel().registryAccess(),
                RailwaySavedData.TYPE, trafficType, "create_tracks.dat", "railway-test-v1", "empty-test-v1", (data, version, registries) -> emptyConverted(data)))
                    && decodes.get() == 2 && storage.get(RailwaySavedData.TYPE) == winner && winner.isDirty() && !owns(storage, trafficType)
                    && Files.exists(directory.journal().resolve("prepared.dat")) && !Files.exists(directory.journal().resolve("committed.dat"))
                    && !Files.exists(directory.first()) && !Files.exists(directory.second()),
                "Final actual decode reentry cannot replace or complete around a newly current native owner; only the validated durable intent remains");
        }
        h.succeed();
    }

    @GameTest public void separatedGlobalSourceAndActualPairFolderSurviveNativeSaveAndFreshReload(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            var nativeDirectory = directory.root.resolve("actual-overworld-owner"); Files.createDirectories(nativeDirectory);
            var firstFile = nativeDirectory.resolve("create/tracks.dat"); var secondFile = nativeDirectory.resolve("create/trains.dat");
            var journal = nativeDirectory.resolve("create/.neoforge-pair-railway-test-v1");
            source(directory, originalEmpty(), 3955); byte[] original = Files.readAllBytes(directory.source());
            var position = GlobalPos.of(h.getLevel().dimension(), new BlockPos(37, 71, -29));
            try (var storage = new SavedDataStorage(nativeDirectory, DataFixers.getDataFixer(), h.getLevel().registryAccess())) {
                var actual = NeoForgeSavedDataPairImport.load(storage, directory.root, nativeDirectory, h.getLevel().registryAccess(),
                    RailwaySavedData.TYPE, TrainTrafficSavedData.TYPE, "create_tracks.dat", "railway-test-v1", "empty-test-v1",
                    (data, version, registries) -> emptyConverted(data));
                h.assertTrue(Files.exists(firstFile) && Files.exists(secondFile) && Files.exists(journal.resolve("committed.dat"))
                        && !Files.exists(directory.first()) && !Files.exists(directory.second()) && !Files.exists(directory.journal()),
                    "Both initial native files and their actual durable intent use the real owner's folder");
                actual.first().queue(new RailwaySavedData.PendingRail(position, Blocks.RAIL.defaultBlockState(), true));
                actual.second().setDirty(); storage.saveAndJoin();
            }
            try (var fresh = new SavedDataStorage(nativeDirectory, DataFixers.getDataFixer(), h.getLevel().registryAccess())) {
                var first = fresh.get(RailwaySavedData.TYPE); var second = fresh.get(TrainTrafficSavedData.TYPE);
                h.assertTrue(first != null && second != null && first.pending().size() == 1
                        && first.pending().getFirst().position().equals(position)
                        && second.retained().getIntOr("TrafficFormatVersion", -1) == 1,
                    "Fresh actual native storage directly reloads both scopes and the later genuine graph pending work");
                var actual = NeoForgeSavedDataPairImport.load(fresh, directory.root, nativeDirectory, h.getLevel().registryAccess(),
                    RailwaySavedData.TYPE, TrainTrafficSavedData.TYPE, "create_tracks.dat", "railway-test-v1", "empty-test-v1",
                    (data, version, registries) -> { throw new AssertionError("Actual committed native owners must not reconvert source"); });
                h.assertTrue(actual.first() == first && actual.second() == second && !Files.exists(directory.first())
                        && !Files.exists(directory.second()) && !Files.exists(directory.journal()),
                    "Committed reload retains both actual native identities and cannot publish stale global-directory targets");
            }
            h.assertTrue(Arrays.equals(original, Files.readAllBytes(directory.source())),
                "Paired native publication, dirty save and reload preserve the original compressed global file");
        }
        h.succeed();
    }

    @GameTest public void bothPresentActualPairTargetsWinBeforeInvalidSeparateGlobalSource(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            var nativeDirectory = directory.root.resolve("actual-overworld-owner");
            var first = nativeDirectory.resolve("create/tracks.dat"); var second = nativeDirectory.resolve("create/trains.dat");
            var converted = emptyConverted(originalEmpty()); nativeFile(first, converted.first()); nativeFile(second, converted.second());
            Files.writeString(directory.source(), "malformed obsolete global source"); byte[] firstBytes = Files.readAllBytes(first), secondBytes = Files.readAllBytes(second);
            try (var storage = new SavedDataStorage(nativeDirectory, DataFixers.getDataFixer(), h.getLevel().registryAccess())) {
                var actual = NeoForgeSavedDataPairImport.load(storage, directory.root, nativeDirectory, h.getLevel().registryAccess(),
                    RailwaySavedData.TYPE, TrainTrafficSavedData.TYPE, "create_tracks.dat", "railway-test-v1", "empty-test-v1",
                    (data, version, registries) -> { throw new AssertionError("Both actual native target files must win"); });
                h.assertTrue(storage.get(RailwaySavedData.TYPE) == actual.first() && storage.get(TrainTrafficSavedData.TYPE) == actual.second()
                        && !Files.exists(directory.first()) && !Files.exists(directory.second()) && !Files.exists(directory.journal()),
                    "The two actual native files win together without parsing or publishing beside the invalid old source");
            }
            h.assertTrue(Arrays.equals(firstBytes, Files.readAllBytes(first)) && Arrays.equals(secondBytes, Files.readAllBytes(second)),
                "Read-only native target precedence preserves both actual native files");
        }
        h.succeed();
    }

}

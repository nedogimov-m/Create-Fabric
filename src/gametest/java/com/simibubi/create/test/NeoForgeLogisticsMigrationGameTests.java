package com.simibubi.create.test;

import com.simibubi.create.content.logistics.packagerLink.LogisticsNetworkSavedData;
import com.simibubi.create.content.logistics.packagerLink.NeoForgeLogisticsImport;
import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Real native storage/registry/DFU and real compressed original files; all authored cases UNRUN. */
public final class NeoForgeLogisticsMigrationGameTests {
    private record Directory(Path root) implements AutoCloseable {
        static Directory create() throws IOException { return new Directory(Files.createTempDirectory("create-neoforge-logistics-")); }
        Path source() { return root.resolve("create_logistics.dat"); }
        Path target() { return root.resolve("create/logistics.dat"); }
        SavedDataStorage storage(GameTestHelper h) { return new SavedDataStorage(root, DataFixers.getDataFixer(), h.getLevel().registryAccess()); }
        long reports() throws IOException {
            if (!Files.isDirectory(target().getParent())) return 0;
            try (var files = Files.list(target().getParent())) {
                return files.filter(path -> path.getFileName().toString().contains(".neoforge-migration-")).count();
            }
        }
        @Override public void close() throws IOException {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static CompoundTag network(UUID id, UUID owner, boolean withPromise) {
        var result = new CompoundTag(); result.store("Id", UUIDUtil.CODEC, id); result.store("Owner", UUIDUtil.CODEC, owner); result.putBoolean("Locked", true);
        var promises = new CompoundTag(); var list = new ListTag();
        if (withPromise) {
            var item = new CompoundTag(); item.putString("id", "minecraft:diamond"); item.putInt("count", 1);
            var components = new CompoundTag(); var custom = new CompoundTag(); custom.putString("source-owner", owner.toString());
            components.put("minecraft:custom_data", custom); item.put("components", components);
            var big = new CompoundTag(); big.put("item_stack", item); big.putInt("count", 65);
            var promise = new CompoundTag(); promise.putInt("ticks_existed", 123); promise.put("promised_stack", big); list.add(promise);
        }
        promises.put("List", list); result.put("Promises", promises);
        var links = new ListTag(); links.add(link(new BlockPos(11, 70, -19), false)); links.add(link(new BlockPos(-7, 38, 27), true));
        result.put("Links", links); return result;
    }
    private static CompoundTag link(BlockPos pos, boolean nether) {
        var result = new CompoundTag(); var block = new CompoundTag(); block.putInt("X", pos.getX()); block.putInt("Y", pos.getY()); block.putInt("Z", pos.getZ());
        result.put("Pos", block); if (nether) result.putString("Dim", "minecraft:the_nether"); return result;
    }
    private static CompoundTag scope(CompoundTag... networks) { var result = new CompoundTag(); var values = new ListTag(); for (var network : networks) values.add(network); result.put("LogisticsNetworks", values); return result; }
    private static void source(Directory directory, CompoundTag data, int version) throws IOException {
        var root = new CompoundTag(); root.putInt("DataVersion", version); root.put("data", data); NbtIo.writeCompressed(root, directory.source());
    }
    private static void nativeFile(Directory directory, CompoundTag data) throws IOException {
        Files.createDirectories(directory.target().getParent()); var root = new CompoundTag(); root.put("data", data); NbtUtils.addCurrentDataVersion(root); NbtIo.writeCompressed(root, directory.target());
    }
    private static LogisticsNetworkSavedData load(GameTestHelper h, Directory directory, SavedDataStorage storage) {
        return NeoForgeSavedDataImport.load(storage, directory.root, h.getLevel().registryAccess(), LogisticsNetworkSavedData.TYPE,
            "create_logistics.dat", NeoForgeLogisticsImport.VERSION, NeoForgeLogisticsImport::convert);
    }
    private static boolean hasOwner(SavedDataStorage storage) {
        var value = ((SavedDataStorageAccessor) storage).create$getCache().get(LogisticsNetworkSavedData.TYPE); return value != null && value.isPresent();
    }

    @GameTest public void actualSourceFileMigratesOwnersDimensionsPromisesAndItemComponents(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            UUID id = UUID.randomUUID(), owner = UUID.randomUUID(); source(directory, scope(network(id, owner, true)), 3955);
            byte[] original = Files.readAllBytes(directory.source());
            try (var storage = directory.storage(h)) {
                var migrated = load(h, directory, storage); var actual = migrated.getLogisticsNetworks().get(id);
                h.assertTrue(actual != null && actual.id.equals(id) && actual.owner.equals(owner) && actual.locked
                        && actual.totalLinks.contains(GlobalPos.of(Level.OVERWORLD, new BlockPos(11, 70, -19)))
                        && actual.totalLinks.contains(GlobalPos.of(Level.NETHER, new BlockPos(-7, 38, 27))),
                    "Actual source UUID/owner/lock and both real original dimensions/positions reach the native canonical network");
                var promises = actual.panelPromises.flatten(false); var promise = promises.getFirst();
                h.assertTrue(promises.size() == 1 && promise.ticksExisted == 123 && promise.promisedStack.count == 65
                        && promise.promisedStack.stack.is(Items.DIAMOND) && promise.promisedStack.stack.getCount() == 1
                        && promise.promisedStack.stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("source-owner").orElseThrow().equals(owner.toString()),
                    "Genuine ITEM_STACK DFU and native registry codecs preserve promise age/count and actual original item components");
                h.assertTrue(storage.get(LogisticsNetworkSavedData.TYPE) == migrated && Files.exists(directory.target())
                        && directory.reports() == 1 && Arrays.equals(original, Files.readAllBytes(directory.source())),
                    "One actual native data owner/file/report is installed; the real original compressed save remains untouched");
            }
            h.assertTrue(Arrays.equals(original, Files.readAllBytes(directory.source())), "Native storage shutdown cannot modify the original source file");
        }
        h.succeed();
    }

    @GameTest public void realCachedCanonicalOwnerWinsWithoutReadingInvalidLegacyDisk(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            var owner = new LogisticsNetworkSavedData(); storage.set(LogisticsNetworkSavedData.TYPE, owner);
            Files.writeString(directory.source(), "not NBT at all");
            h.assertTrue(load(h, directory, storage) == owner && directory.reports() == 0 && !Files.exists(directory.target()),
                "Existing raw native cache identity wins without disk/parser callbacks or legacy replacement");
        }
        h.succeed();
    }

    @GameTest public void realPresentNativeFileWinsAndRemainsByteIdenticalAfterShutdown(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            UUID id = UUID.randomUUID(); var old = scope(network(id, UUID.randomUUID(), false));
            var nativeData = NeoForgeLogisticsImport.convert(old, 3955, h.getLevel().registryAccess()); nativeFile(directory, nativeData);
            Files.writeString(directory.source(), "invalid old source that must not be parsed"); byte[] nativeBytes = Files.readAllBytes(directory.target());
            try (var storage = directory.storage(h)) {
                var actual = load(h, directory, storage);
                h.assertTrue(actual.getLogisticsNetworks().containsKey(id) && !actual.isDirty() && directory.reports() == 0,
                    "Actual native disk owner is authoritative and is not marked modified by legacy migration");
            }
            h.assertTrue(Arrays.equals(nativeBytes, Files.readAllBytes(directory.target())),
                "Merely finding original data cannot rewrite a present native owner on storage shutdown");
        }
        h.succeed();
    }

    @GameTest public void unknownVersionDuplicateIdentityAndUnknownSchemaFailBeforeOwnership(GameTestHelper h) throws Exception {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID(); var a = network(id, owner, false);
        var future = scope(a.copy()); future.putInt("FutureFormatVersion", 8);
        for (var data : new CompoundTag[]{scope(a.copy(), a.copy()), future}) {
            try (var directory = Directory.create(); var storage = directory.storage(h)) {
                source(directory, data, 3955); byte[] original = Files.readAllBytes(directory.source()); boolean rejected = false;
                try { load(h, directory, storage); } catch (IllegalArgumentException expected) { rejected = true; }
                h.assertTrue(rejected && !hasOwner(storage) && !Files.exists(directory.target()) && directory.reports() == 0
                        && Arrays.equals(original, Files.readAllBytes(directory.source())),
                    "Duplicate identity/unknown original schema cannot establish an empty/default replacement owner");
            }
        }
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, scope(a), 999999); boolean rejected = false;
            try { load(h, directory, storage); } catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected && !hasOwner(storage) && !Files.exists(directory.target()) && directory.reports() == 0,
                "Unknown actual source data version fails explicitly before report or owner publication");
        }
        h.succeed();
    }

    @GameTest public void actualSourceEditedDuringPreparationCannotPublishAStaleOwner(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, scope(network(UUID.randomUUID(), UUID.randomUUID(), false)), 3955); boolean rejected = false;
            try {
                NeoForgeSavedDataImport.load(storage, directory.root, h.getLevel().registryAccess(), LogisticsNetworkSavedData.TYPE,
                    "create_logistics.dat", NeoForgeLogisticsImport.VERSION, (old, version, registries) -> {
                        var converted = NeoForgeLogisticsImport.convert(old, version, registries);
                        try { source(directory, scope(), 3955); } catch (IOException failure) { throw new UncheckedIOException(failure); }
                        return converted;
                    });
            } catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected && !hasOwner(storage) && !Files.exists(directory.target()) && directory.reports() == 0,
                "A real source edit between decode and publication rejects the stale prepared snapshot");
        }
        h.succeed();
    }

    @GameTest public void actualNativeFileCreatedDuringPreparationWinsWithoutLegacyOverwrite(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            UUID oldId = UUID.randomUUID(), nativeId = UUID.randomUUID(); source(directory, scope(network(oldId, UUID.randomUUID(), false)), 3955);
            var result = NeoForgeSavedDataImport.load(storage, directory.root, h.getLevel().registryAccess(), LogisticsNetworkSavedData.TYPE,
                "create_logistics.dat", NeoForgeLogisticsImport.VERSION, (old, version, registries) -> {
                    var converted = NeoForgeLogisticsImport.convert(old, version, registries);
                    try { nativeFile(directory, NeoForgeLogisticsImport.convert(scope(network(nativeId, UUID.randomUUID(), false)), version, registries)); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                    return converted;
                });
            h.assertTrue(result.getLogisticsNetworks().containsKey(nativeId) && !result.getLogisticsNetworks().containsKey(oldId)
                    && storage.get(LogisticsNetworkSavedData.TYPE) == result && directory.reports() == 0,
                "A genuine native file appearing during preparation becomes the only actual canonical owner");
        }
        h.succeed();
    }

    @GameTest public void actualCanonicalOwnerInstalledDuringPreparationRetainsItsExactIdentity(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, scope(network(UUID.randomUUID(), UUID.randomUUID(), false)), 3955);
            var canonical = new LogisticsNetworkSavedData();
            var actual = NeoForgeSavedDataImport.load(storage, directory.root, h.getLevel().registryAccess(), LogisticsNetworkSavedData.TYPE,
                "create_logistics.dat", NeoForgeLogisticsImport.VERSION, (old, version, registries) -> {
                    var converted = NeoForgeLogisticsImport.convert(old, version, registries);
                    storage.set(LogisticsNetworkSavedData.TYPE, canonical);
                    return converted;
                });
            h.assertTrue(actual == canonical && storage.get(LogisticsNetworkSavedData.TYPE) == canonical
                    && !Files.exists(directory.target()) && directory.reports() == 0,
                "The actual canonical native identity installed during conversion wins before any migration disk publication");
        }
        h.succeed();
    }

    @GameTest public void malformedPresentNativeOwnerCannotFallBackToValidOriginalData(GameTestHelper h) throws Exception {
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, scope(network(UUID.randomUUID(), UUID.randomUUID(), false)), 3955);
            var malformed = new CompoundTag(); malformed.putString("LogisticsNetworks", "not an actual network list"); nativeFile(directory, malformed);
            byte[] nativeBytes = Files.readAllBytes(directory.target()); boolean rejected = false;
            try { load(h, directory, storage); } catch (IllegalStateException | IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected && !hasOwner(storage) && directory.reports() == 0 && Arrays.equals(nativeBytes, Files.readAllBytes(directory.target())),
                "Invalid present native data fails explicitly; a valid old file cannot authorize replacing that native owner");
        }
        h.succeed();
    }

    @GameTest public void migratedQueueRemainsBoundToActualNativeDirtySaveAndReload(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            UUID id = UUID.randomUUID(); source(directory, scope(network(id, UUID.randomUUID(), true)), 3955);
            try (var storage = directory.storage(h)) {
                var actual = load(h, directory, storage); actual.setDirty(false);
                actual.getLogisticsNetworks().get(id).panelPromises.tick();
                h.assertTrue(actual.isDirty(), "Real native promise aging remains bound to its actual persistent SavedData owner");
            }
            try (var storage = directory.storage(h)) {
                var restored = load(h, directory, storage);
                h.assertTrue(restored.getLogisticsNetworks().get(id).panelPromises.flatten(false).getFirst().ticksExisted == 124
                        && directory.reports() == 1, "Actual native storage shutdown/reload retains the aged promise and does not import the old source twice");
            }
        }
        h.succeed();
    }

    @GameTest public void globalOriginalAndDifferentActualNativeFolderSurviveDirtySaveAndFreshReload(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            var nativeDirectory = directory.root.resolve("actual-overworld-owner"); Files.createDirectories(nativeDirectory);
            var nativeFile = nativeDirectory.resolve("create/logistics.dat");
            UUID id = UUID.randomUUID(), owner = UUID.randomUUID(); source(directory, scope(network(id, owner, true)), 3955);
            byte[] original = Files.readAllBytes(directory.source());
            try (var storage = new SavedDataStorage(nativeDirectory, DataFixers.getDataFixer(), h.getLevel().registryAccess())) {
                var actual = NeoForgeSavedDataImport.load(storage, directory.root, nativeDirectory, h.getLevel().registryAccess(),
                    LogisticsNetworkSavedData.TYPE, "create_logistics.dat", NeoForgeLogisticsImport.VERSION, NeoForgeLogisticsImport::convert);
                h.assertTrue(Files.exists(nativeFile) && !Files.exists(directory.target())
                        && NeoForgeSavedDataImport.nativeDirectory(storage).equals(nativeDirectory.toAbsolutePath().normalize()),
                    "The global original remains separate from the actual native owner's initial target and later save path");
                actual.setDirty(false); actual.getLogisticsNetworks().get(id).panelPromises.tick();
                h.assertTrue(actual.isDirty(), "A genuine promise mutation dirties that exact actual native owner");
                storage.saveAndJoin();
            }
            try (var fresh = new SavedDataStorage(nativeDirectory, DataFixers.getDataFixer(), h.getLevel().registryAccess())) {
                var reloaded = fresh.get(LogisticsNetworkSavedData.TYPE);
                h.assertTrue(reloaded != null && reloaded.getLogisticsNetworks().get(id).owner.equals(owner)
                        && reloaded.getLogisticsNetworks().get(id).panelPromises.flatten(false).getFirst().ticksExisted == 124,
                    "A fresh native owner directly reloads the real aged promise from its own saved target");
                var authoritative = NeoForgeSavedDataImport.load(fresh, directory.root, nativeDirectory, h.getLevel().registryAccess(),
                    LogisticsNetworkSavedData.TYPE, "create_logistics.dat", NeoForgeLogisticsImport.VERSION,
                    (old, version, registries) -> { throw new AssertionError("Fresh native owner must win over original reimport"); });
                h.assertTrue(authoritative == reloaded && !Files.exists(directory.target()),
                    "Later native identity remains authoritative and no stale target appears beside the global source");
            }
            h.assertTrue(Arrays.equals(original, Files.readAllBytes(directory.source())),
                "Initial publication, actual native dirty save and fresh reload preserve every original compressed source byte");
        }
        h.succeed();
    }

    @GameTest public void actualSeparateNativeTargetWinsBeforeMalformedGlobalSource(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            var nativeDirectory = directory.root.resolve("actual-overworld-owner"); var target = nativeDirectory.resolve("create/logistics.dat");
            Files.createDirectories(target.getParent()); UUID id = UUID.randomUUID();
            var envelope = new CompoundTag(); envelope.put("data", NeoForgeLogisticsImport.convert(
                scope(network(id, UUID.randomUUID(), false)), 3955, h.getLevel().registryAccess()));
            NbtUtils.addCurrentDataVersion(envelope); NbtIo.writeCompressed(envelope, target);
            Files.writeString(directory.source(), "malformed obsolete global source"); byte[] present = Files.readAllBytes(target);
            try (var storage = new SavedDataStorage(nativeDirectory, DataFixers.getDataFixer(), h.getLevel().registryAccess())) {
                var actual = NeoForgeSavedDataImport.load(storage, directory.root, nativeDirectory, h.getLevel().registryAccess(),
                    LogisticsNetworkSavedData.TYPE, "create_logistics.dat", NeoForgeLogisticsImport.VERSION,
                    (old, version, registries) -> { throw new AssertionError("Present actual native file is authoritative"); });
                h.assertTrue(actual.getLogisticsNetworks().containsKey(id) && !actual.isDirty() && !Files.exists(directory.target()),
                    "The actual native target wins without reading or publishing beside an invalid global source");
            }
            h.assertTrue(Arrays.equals(present, Files.readAllBytes(target)), "Reading the current actual native target is byte-preserving");
        }
        h.succeed();
    }

    @GameTest public void mismatchedDeclaredTargetRefusesBeforePublication(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            var nativeDirectory = directory.root.resolve("actual-overworld-owner"); Files.createDirectories(nativeDirectory);
            source(directory, scope(network(UUID.randomUUID(), UUID.randomUUID(), false)), 3955);
            byte[] original = Files.readAllBytes(directory.source()); boolean refused = false;
            try (var storage = new SavedDataStorage(nativeDirectory, DataFixers.getDataFixer(), h.getLevel().registryAccess())) {
                try {
                    NeoForgeSavedDataImport.load(storage, directory.root, directory.root, h.getLevel().registryAccess(),
                        LogisticsNetworkSavedData.TYPE, "create_logistics.dat", NeoForgeLogisticsImport.VERSION, NeoForgeLogisticsImport::convert);
                } catch (IllegalArgumentException expected) { refused = true; }
                h.assertTrue(refused && !hasOwner(storage) && !Files.exists(directory.target())
                        && !Files.exists(nativeDirectory.resolve("create/logistics.dat")),
                    "A declared target different from the actual native storage cannot create a file/cache owner");
            }
            h.assertTrue(Arrays.equals(original, Files.readAllBytes(directory.source())), "Target-scope refusal retains the actual original bytes");
        }
        h.succeed();
    }

}

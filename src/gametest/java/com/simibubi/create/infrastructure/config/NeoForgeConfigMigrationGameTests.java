package com.simibubi.create.infrastructure.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import com.simibubi.create.infrastructure.config.CServer;
import com.simibubi.create.infrastructure.config.CClient;
import com.simibubi.create.infrastructure.config.CCommon;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Real source CServer files/store/typed values. Authored native regression cases; execution UNRUN. */
public final class NeoForgeConfigMigrationGameTests {
    private record Directory(Path root) implements AutoCloseable {
        static Directory create() throws IOException { return new Directory(Files.createTempDirectory("create-neoforge-toml-")); }
        @Override public void close() throws IOException {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static Path write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent()); Files.writeString(path, text); return path;
    }
    private static JsonObject json(Path path) throws IOException { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static long reports(Path target) throws IOException {
        try (var files = Files.list(target.getParent())) {
            return files.filter(path -> path.getFileName().toString().startsWith(target.getFileName() + ".neoforge-migration-")).count();
        }
    }

    @GameTest public void genuineSourceTomlValuesAndUnknownFieldsSurviveNativeMigration(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            Path nativeFile = directory.root.resolve("world/serverconfig/create-server.json");
            Path source = write(nativeFile.resolveSibling("create-server.toml"), """
                # Original source tables and scalar types, not a synthetic JSON alias file.
                [infrastructure]
                tickrateSyncTimer = 37
                [kinetics]
                maxRotationSpeed = 96
                [kinetics.stressValues.v2.capacity]
                water_wheel = inf
                creative_motor = 2_048.5
                [fluids]
                bottomlessFluidMode = "DENY_ALL"
                [foreign."literal.dotted.key"]
                keep = ["keep # inside the string", 4, { untouched = true }]
                """);
            byte[] original = Files.readAllBytes(source);
            var config = new CServer(); config.store().reload(nativeFile);
            h.assertTrue(config.tickrateSyncTimer.getServer() == 37 && config.kinetics.maxRotationSpeed.getServer() == 96,
                "Real original source fields and tables reach the actual typed native store");
            h.assertTrue(config.fluids.bottomlessFluidMode.getServer() == BottomlessFluidMode.DENY_ALL,
                "Actual source enum choice remains the native configured policy");
            h.assertTrue(Double.isInfinite((Double) config.store().definition("kinetics.stressValues.v2.capacity.water_wheel").getServer())
                    && (Double) config.store().definition("kinetics.stressValues.v2.capacity.creative_motor").getServer() == 2048.5,
                "Actual source unrestricted stress doubles retain TOML infinity and precision");
            var nativeDocument = json(nativeFile);
            h.assertTrue(nativeDocument.getAsJsonObject("foreign").getAsJsonObject("literal.dotted.key").getAsJsonArray("keep").size() == 3,
                "Unknown user fields, quoted dotted keys, arrays and inline tables survive the native file");
            h.assertTrue(Arrays.equals(original, Files.readAllBytes(source)) && reports(nativeFile) == 1,
                "The original TOML is untouched and a separate ownership/checksum report is retained");
            try (var files = Files.list(nativeFile.getParent())) {
                var report = json(files.filter(path -> path.getFileName().toString().contains(".neoforge-migration-")).findFirst().orElseThrow());
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
                h.assertTrue(report.get("sourceSha256").getAsString().equals(hash) && !report.getAsJsonArray("unknownFields").isEmpty(),
                    "Migration report identifies the actual original bytes and all preserved unknown fields");
            }
        }
        h.succeed();
    }

    @GameTest public void existingNativeOwnerWinsEvenWhenOldTomlIsInvalid(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            Path target = write(directory.root.resolve("create-server.json"), "{\"kinetics\":{\"maxRotationSpeed\":144},\"nativeOnly\":true}");
            Path source = write(directory.root.resolve("create-server.toml"), "[fluids]\nbottomlessFluidMode = not_valid_toml\n");
            byte[] original = Files.readAllBytes(source);
            var config = new CServer(); config.store().reload(target);
            h.assertTrue(config.kinetics.maxRotationSpeed.getServer() == 144 && json(target).get("nativeOnly").getAsBoolean(),
                "Existing native choices and unknown fields remain authoritative without parsing old TOML");
            h.assertTrue(Arrays.equals(original, Files.readAllBytes(source)) && reports(target) == 0,
                "No old source is changed or falsely reported as migrated when native ownership already exists");
        }
        h.succeed();
    }

    @GameTest public void actualClientAndCommonTomlScopesKeepTheirSourceTables(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            Path configDirectory = directory.root.resolve("config");
            Path clientFile = configDirectory.resolve("create-client.json");
            write(configDirectory.resolve("create-client.toml"), "[client]\nenableTooltips=false\nfanParticleDensity=0.125\n"
                + "[client.goggleOverlay]\noverlayOffsetX=-42\n[client.recipeViewerIntegration]\nsyncRecipeViewerSearch=\"NONE\"\n");
            Path commonFile = configDirectory.resolve("create-common.json");
            write(configDirectory.resolve("create-common.toml"), "[worldgen]\ndisableWorldGen=true\n");
            var client = new CClient(); client.store().reload(clientFile);
            var common = new CCommon(); common.store().reload(commonFile);
            h.assertTrue(!client.tooltips.getServer() && client.fanParticleDensity.getServer() == .125d
                    && client.overlayOffsetX.getServer() == -42 && client.syncRecipeViewerSearch.getServer() == CClient.SearchSyncMode.NONE,
                "Actual client booleans, numeric precision, nested goggle table and source enum retain their typed values");
            h.assertTrue(common.worldGen.disable.getServer() && reports(clientFile) == 1 && reports(commonFile) == 1,
                "Actual COMMON scope remains separate from CLIENT and both own their original-file report");
        }
        h.succeed();
    }

    @GameTest public void defaultAndWorldSpecificSourceTomlHaveDistinctOwners(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            Path seed = directory.root.resolve("seed-only/defaultconfigs/create-server.json");
            write(seed.resolveSibling("create-server.toml"), "[kinetics]\nmaxRotationSpeed=224\n");
            var seeded = new CServer();
            seeded.store().reload(seed, new JsonObject(), false,
                NeoForgeTomlImport.defaultServerSource(directory.root.resolve("seed-only/config"), seed.getParent()));
            h.assertTrue(seeded.kinetics.maxRotationSpeed.getServer() == 224 && reports(seed) == 1,
                "A genuine defaultconfigs TOML seeds the native default scope when no actual global source exists");
            Path defaults = directory.root.resolve("defaultconfigs/create-server.json");
            write(defaults.resolveSibling("create-server.toml"), "[kinetics]\nmaxRotationSpeed=128\n[logistics]\nvaultCapacity=37\n");
            write(directory.root.resolve("config/create-server.toml"), "[kinetics]\nmaxRotationSpeed=160\n[logistics]\nvaultCapacity=41\n");
            var inherited = new CServer(); inherited.store().reload(defaults, new JsonObject(), false,
                NeoForgeTomlImport.defaultServerSource(directory.root.resolve("config"), defaults.getParent()));
            Path oneFile = directory.root.resolve("world-one/serverconfig/create-server.json");
            write(oneFile.resolveSibling("create-server.toml"), "[kinetics]\nmaxRotationSpeed=192\n");
            var one = new CServer(); one.store().reload(oneFile, inherited.store().serverDocument(), false);
            Path twoFile = directory.root.resolve("world-two/serverconfig/create-server.json");
            var two = new CServer(); two.store().reload(twoFile, inherited.store().serverDocument(), false);
            h.assertTrue(one.kinetics.maxRotationSpeed.getServer() == 192 && two.kinetics.maxRotationSpeed.getServer() == 160
                    && one.logistics.vaultCapacity.getServer() == 20 && two.logistics.vaultCapacity.getServer() == 41,
                "Real source global file wins over the template; a world TOML is a complete override whose missing fields use source defaults");
            h.assertTrue(reports(defaults) == 1 && reports(oneFile) == 1 && reports(twoFile) == 0,
                "Migration receipts belong only to scopes that actually consumed an original TOML file");
        }
        h.succeed();
    }

    @GameTest public void unknownSourceEnumFailsBeforeFileOrSnapshotPublication(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            Path target = directory.root.resolve("create-server.json");
            Path source = write(directory.root.resolve("create-server.toml"), "[fluids]\nbottomlessFluidMode=\"not_a_source_enum\"\n");
            byte[] original = Files.readAllBytes(source);
            var config = new CServer(); var store = config.store(); var before = store.serverValues();
            var applied = new AtomicInteger();
            store.configure(() -> false, Runnable::run, values -> () -> applied.incrementAndGet(), ignored -> {});
            boolean rejected = false;
            try { store.reload(target); } catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected && !Files.exists(target) && reports(target) == 0 && applied.get() == 0
                    && before.equals(store.serverValues()) && Arrays.equals(original, Files.readAllBytes(source)),
                "Unknown actual source enum is rejected explicitly before any new native owner, report or consumer is published");
        }
        h.succeed();
    }

    @GameTest public void concurrentNativeFileCreationCannotBeOverwrittenByPreparedToml(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            Path target = directory.root.resolve("create-server.json");
            write(directory.root.resolve("create-server.toml"), "[kinetics]\nmaxRotationSpeed=96\n");
            var config = new CServer(); var store = config.store(); var prepared = new AtomicBoolean(); var applied = new AtomicInteger();
            store.configure(() -> false, Runnable::run, values -> {
                if (prepared.compareAndSet(false, true)) {
                    try { write(target, "{\"kinetics\":{\"maxRotationSpeed\":144},\"actualNativeWinner\":true}"); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                }
                return () -> applied.incrementAndGet();
            }, ignored -> {});
            store.reload(target);
            h.assertTrue(config.kinetics.maxRotationSpeed.getServer() == 144 && json(target).get("actualNativeWinner").getAsBoolean()
                    && applied.get() == 1 && reports(target) == 0,
                "A real native scope created between preparation and publication wins; only its actual snapshot reaches consumers");
        }
        h.succeed();
    }

    @GameTest public void changedOriginalTomlIsRejectedBeforePreparedSnapshotPublication(GameTestHelper h) throws Exception {
        try (var directory = Directory.create()) {
            Path target = directory.root.resolve("create-server.json");
            Path source = write(directory.root.resolve("create-server.toml"), "[kinetics]\nmaxRotationSpeed=96\n");
            var config = new CServer(); var store = config.store(); var before = store.serverValues(); var applied = new AtomicInteger();
            store.configure(() -> false, Runnable::run, values -> {
                try { write(source, "[kinetics]\nmaxRotationSpeed=144\n"); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                return () -> applied.incrementAndGet();
            }, ignored -> {});
            boolean rejected = false;
            try { store.reload(target); } catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected && !Files.exists(target) && reports(target) == 0 && before.equals(store.serverValues()) && applied.get() == 0,
                "A changed source checksum prevents stale prepared choices from becoming a native owner");
            h.assertTrue(Files.readString(source).contains("144"), "The actual later source edit remains intact");
        }
        h.succeed();
    }
}

package net.createmod.ponder.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.test.mixin.PonderSceneScheduleAuditAccessor;
import net.createmod.ponder.test.mixin.PonderWorldParticlesAuditAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;

/** Real installed registry, compiler, native scene scheduler, UI and Vulkan/PIP execution. UNRUN. */
public final class PonderRegisteredProgramsTest implements FabricClientGameTest {
    private static final int FRAME_PERIOD = 40;
    private static final int MAX_TICKS_PER_BINDING = 100_000;
    private static final Path REPORT = Path.of(System.getProperty("create.test.ponderReport",
        "build/reports/create-ponder/registered-program-runtime.json")).toAbsolutePath();

    private static final class BindingResult {
        int ordinal, executedTicks;
        String component, schematic, actualProgramTitle, actualCallbackClass, status = "STARTED", failure;
        boolean nativeCompile, actualScheduleDrained, actualSourceFinished, backwardReplay, forwardSeek,
            freshOwnerRetirement, hostFootprintUnchanged, nativeVisibleGeometry,
            initialPlacementCurveEndpoints, replayPlacementCurveEndpoints;
        int initialLocalBlocks, initialLocalOwners, initialLocalEntities;
        final List<PonderRegistryRenderAudit.FrameReceipt> frames = new ArrayList<>();
        String screenshotPath, screenshotSHA256;
    }
    private static final class Report {
        String status = "RUNNING";
        String countDefinition = "179 is a pinned AST source-record count,167 is unique pinned source methods. "
            + "Native bindings and program keys below are observed independently from the installed registry.";
        String sourceCommit, actualNativeBackend, actualNativeDevice;
        boolean sodiumLoaded;
        int expectedSourceRegistrationRecords, expectedDistinctSourcePrograms;
        int observedAllNamespaceBindings, observedCreateBindings;
        final Set<String> observedNativeProgramTitles = new TreeSet<>();
        final Set<String> observedNativeSchematicKeys = new TreeSet<>();
        final Set<String> executedSourceProgramMethods = new TreeSet<>();
        final Set<String> missingSourceTitles = new TreeSet<>();
        final List<BindingResult> bindings = new ArrayList<>();
        String overallFailure;
    }
    private record NativeSession(PonderScene scene, PonderUI ui, LocalSnapshot initial, HostSnapshot host) {}
    private record SavedOwner(BlockEntity owner, CompoundTag data) {}

    @Override public void runTest(ClientGameTestContext context) {
        var report = new Report();
        var expected = readSourceExpectations();
        report.sourceCommit = expected.get("createCommit").getAsString();
        report.expectedSourceRegistrationRecords = expected.get("sourceRegistrationRecords").getAsInt();
        report.expectedDistinctSourcePrograms = expected.get("distinctSourcePrograms").getAsInt();
        var methodsByTitle = new LinkedHashMap<String, String>();
        for (var value : expected.getAsJsonArray("programs")) {
            var program = value.getAsJsonObject();
            String prior = methodsByTitle.put(program.get("titleId").getAsString(), program.get("sourceProgram").getAsString());
            require(prior == null, "Pinned source title expectations must be unambiguous");
        }
        require(methodsByTitle.size() == report.expectedDistinctSourcePrograms, "Exact pinned source expectation manifest required");
        report.missingSourceTitles.addAll(methodsByTitle.keySet());
        try (var joined = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var device = RenderSystem.getDevice().getDeviceInfo();
                report.actualNativeBackend = device.backendName(); report.actualNativeDevice = device.name();
                report.sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
                require(report.actualNativeBackend.toLowerCase(java.util.Locale.ROOT).contains("vulkan"),
                    "Actual native Vulkan backend required, observed: " + report.actualNativeBackend);
                require(!report.sodiumLoaded, "Actual runtime must satisfy the requested no-Sodium configuration");
            });
            var entries = context.computeOnClient(client -> {
                require(client.level != null, "Actual joined native client world required");
                return List.copyOf(PonderIndex.getSceneAccess().getRegisteredEntries());
            });
            report.observedAllNamespaceBindings = entries.size();
            var createEntries = entries.stream().map(Map.Entry::getValue)
                .filter(entry -> entry.getNamespace().equals("create")).toList();
            report.observedCreateBindings = createEntries.size();
            require(!createEntries.isEmpty(), "Installed Create registry cannot be empty");
            write(report);
            var firstScreenshotForTitle = new HashSet<String>();
            for (int i = 0; i < createEntries.size(); i++) {
                StoryBoardEntry entry = createEntries.get(i);
                var result = new BindingResult(); result.ordinal = i;
                result.component = entry.getComponent().toString();
                result.schematic = entry.getSchematicLocation().toString();
                result.actualCallbackClass = entry.getBoard().getClass().getName();
                report.observedNativeSchematicKeys.add(result.schematic);
                report.bindings.add(result);
                write(report);
                NativeSession session = null;
                try {
                    session = context.computeOnClient(client -> {
                        var compiled = PonderIndex.getSceneAccess().compile(List.of(entry));
                        require(compiled.size() == 1, "One actual registry entry must compile to one actual scene");
                        var scene = compiled.getFirst();
                        require(scene.getLocation().equals(entry.getComponent()) && scene.getWorld() != null,
                            "Actual compiler must retain its registered subject and actual scene world");
                        result.nativeCompile = true;
                        assertInitialCrafterInputs(scene.getWorld(), entry.getSchematicLocation());
                        result.initialPlacementCurveEndpoints = assertPlacementCurveEndpoints(scene.getWorld(), entry.getSchematicLocation());
                        result.actualProgramTitle = scene.getId().toString();
                        require(methodsByTitle.containsKey(result.actualProgramTitle),
                            "Unexpected actual source scene title: " + result.actualProgramTitle);
                        var ui = new PonderUI(List.of(scene), null);
                        client.gui.setScreen(ui);
                        ui.setFocused(null);
                        require(ui.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0)),
                            "Actual native Ponder UI pause action must accept its source key event");
                        require(client.gui.screen() == ui, "Native pause action must retain the real Ponder screen");
                        var initial = LocalSnapshot.capture(scene.getWorld());
                        var host = HostSnapshot.capture(client.level, scene.getWorld(), client.particleEngine.countParticles());
                        result.initialLocalBlocks = initial.blocks.size();
                        result.initialLocalOwners = initial.owners.size();
                        result.initialLocalEntities = initial.entities.size();
                        return new NativeSession(scene, ui, initial, host);
                    });
                    final var current = session;
                    report.observedNativeProgramTitles.add(result.actualProgramTitle);
                    int pausedAt = context.computeOnClient(client -> current.scene.getCurrentTime());
                    context.waitTicks(2);
                    require(context.computeOnClient(client -> current.scene.getCurrentTime() == pausedAt),
                        "Actual source UI pause must hold scene time while native frames continue");
                    capture(context, current, result);
                    boolean screenshot = firstScreenshotForTitle.add(result.actualProgramTitle);
                    while (context.computeOnClient(client -> hasSchedule(current.scene))) {
                        context.runOnClient(client -> {
                            int batch = 0;
                            do {
                                require(result.executedTicks < MAX_TICKS_PER_BINDING,
                                    "Actual scheduler did not drain within explicit safety bound: " + result.component + "/" + result.schematic);
                                current.scene.tick(); result.executedTicks++; batch++;
                            } while (hasSchedule(current.scene) && batch < FRAME_PERIOD
                                && !atKeyframe(current.scene));
                        });
                        capture(context, current, result);
                        if (screenshot && result.frames.size() >= 3) {
                            var path = context.takeScreenshot("create-ponder-" + result.ordinal + "-"
                                + result.actualProgramTitle.replace(':', '_').replace('/', '_'));
                            result.screenshotPath = path.toAbsolutePath().toString();
                            result.screenshotSHA256 = sha256(path); screenshot = false;
                        }
                    }
                    context.runOnClient(client -> {
                        require(current.scene.isFinished() && !hasSchedule(current.scene),
                            "Actual source completion and complete native scheduler drain are both required");
                        result.actualScheduleDrained = !hasSchedule(current.scene);
                        result.actualSourceFinished = current.scene.isFinished();
                        assertSourceCrafterInputs(current.scene.getWorld(), result.actualProgramTitle);
                        var oldOwners = ownerList(current.scene.getWorld());
                        var oldEntities = List.copyOf(current.scene.getWorld().getEntityList());
                        current.ui.seekToTime(0);
                        require(current.scene.getCurrentTime() == 0, "Actual UI backward seek must replay before rewinding");
                        current.initial.assertRestored(current.scene.getWorld());
                        assertInitialCrafterInputs(current.scene.getWorld(), entry.getSchematicLocation());
                        result.replayPlacementCurveEndpoints = assertPlacementCurveEndpoints(current.scene.getWorld(), entry.getSchematicLocation());
                        for (var owner : oldOwners) require(owner.isRemoved(), "Replay must retire every old native BE owner");
                        for (var entity : oldEntities) require(entity.isRemoved(), "Replay must retire every old native entity owner");
                        result.freshOwnerRetirement = result.backwardReplay = true;
                        int target = Math.max(1, current.scene.getTotalTime() / 2);
                        current.ui.seekToTime(target);
                        require(current.scene.getCurrentTime() == target || current.scene.isFinished(),
                            "Actual forward seek must execute source instructions to its requested time or source completion");
                        result.forwardSeek = true;
                        current.host.assertUnchanged(client.level, client.particleEngine.countParticles());
                        result.hostFootprintUnchanged = true;
                    });
                    capture(context, current, result);
                    require(result.nativeVisibleGeometry, "At least one actual submitted native frame must contain source geometry");
                    report.executedSourceProgramMethods.add(methodsByTitle.get(result.actualProgramTitle));
                    result.status = "PASS";
                    report.missingSourceTitles.remove(result.actualProgramTitle);
                } catch (Throwable failure) {
                    result.status = "FAIL"; result.failure = describe(failure);
                } finally {
                    final var retired = session;
                    context.runOnClient(client -> {
                        PonderRegistryRenderAudit.clear();
                        client.gui.setScreen(null);
                        if (retired != null) {
                            ((PonderWorldParticlesAuditAccessor) retired.scene.getWorld()).create$actualLocalParticles().invalidateResources();
                            for (var owner : ownerList(retired.scene.getWorld())) owner.setRemoved();
                            for (var entity : List.copyOf(retired.scene.getWorld().getEntityList())) entity.discard();
                        }
                    });
                    write(report);
                }
            }
            report.missingSourceTitles.addAll(methodsByTitle.keySet());
            for (var binding : report.bindings) if (binding.status.equals("PASS")) report.missingSourceTitles.remove(binding.actualProgramTitle);
            require(report.bindings.size() == report.observedCreateBindings, "Every actual expanded Create binding must be attempted");
            require(report.bindings.stream().allMatch(binding -> binding.status.equals("PASS")), "One or more genuine registry bindings failed; see " + REPORT);
            require(report.missingSourceTitles.isEmpty() && report.executedSourceProgramMethods.size() == methodsByTitle.size(),
                "Every pinned source program must be covered by actual native title/execution receipts; see " + REPORT);
            report.status = "PASS";
        } catch (Throwable failure) {
            report.status = "FAIL"; report.overallFailure = describe(failure); throw asAssertion(failure);
        } finally { write(report); }
    }

    private static boolean assertPlacementCurveEndpoints(PonderLevel world, net.minecraft.resources.Identifier schematic) {
        if (!schematic.toString().equals("create:train_track/placement")) return false;
        // Exact original placement.nbt pair: direct XYZ endpoints (0,0,0)/(6,0,-6),
        // Starts(1,0,.5)/(6.5,0,-5), axes X/Z, normals Y, Primary1/Girder0.
        // Material is absent in this2975 asset: the source legacy default is Andesite.
        var origin = new BlockPos(2, 1, 8); var target = origin.offset(6, 0, -6);
        var first = world.getBlockEntity(origin); var second = world.getBlockEntity(target);
        require(first instanceof com.simibubi.create.content.trains.track.TrackBlockEntity
            && second instanceof com.simibubi.create.content.trains.track.TrackBlockEntity,
            "Pinned placement curve must retain both actual source track owners");
        var owner = (com.simibubi.create.content.trains.track.TrackBlockEntity) first;
        var reciprocalOwner = (com.simibubi.create.content.trains.track.TrackBlockEntity) second;
        require(owner.isVirtual() && reciprocalOwner.isVirtual() && owner.getLevel() == world
            && reciprocalOwner.getLevel() == world && !owner.isRemoved() && !reciprocalOwner.isRemoved(),
            "Pinned placement endpoints must belong only to the current local Ponder world");
        require(owner.getConnections().size() == 1 && reciprocalOwner.getConnections().size() == 1,
            "Pinned placement pair must retain exactly its original connection per endpoint");
        var curve = owner.getConnections().get(target); var reverse = reciprocalOwner.getConnections().get(origin);
        require(curve != null && reverse != null && curve.bePositions.getFirst().equals(origin)
            && curve.bePositions.getSecond().equals(target) && reverse.bePositions.getFirst().equals(target)
            && reverse.bePositions.getSecond().equals(origin),
            "Pinned placement endpoints must preserve the real6,0,-6 offset without a ZERO fallback");
        require(curve.starts.getFirst().equals(new net.minecraft.world.phys.Vec3(3, 1, 8.5))
            && curve.starts.getSecond().equals(new net.minecraft.world.phys.Vec3(8.5, 1, 3))
            && curve.axes.getFirst().equals(new net.minecraft.world.phys.Vec3(1, 0, 0))
            && curve.axes.getSecond().equals(new net.minecraft.world.phys.Vec3(0, 0, 1))
            && curve.normals.getFirst().equals(new net.minecraft.world.phys.Vec3(0, 1, 0))
            && curve.normals.getSecond().equals(new net.minecraft.world.phys.Vec3(0, 1, 0)),
            "Actual placement curve must retain its exact finite source geometry");
        require(curve.primary && !reverse.primary && !curve.hasGirder && !reverse.hasGirder
            && curve.getMaterial() == com.simibubi.create.content.trains.track.TrackMaterial.ANDESITE
            && reverse.getMaterial() == curve.getMaterial() && curve.smoothing == null && reverse.smoothing == null
            && reverse.equalsSansMaterial(curve.secondary()),
            "Pinned source reciprocal curve must retain geometry,primary,girder,material and smoothing identity");
        return true;
    }

    private static void assertInitialCrafterInputs(PonderLevel world, net.minecraft.resources.Identifier schematic) {
        // Pinned templates have37 singleton inputs and one reciprocal nine-owner repackager grid.
        var repackager = schematic.toString().equals("create:high_logistics/repackager");
        Set<BlockPos> original = repackager ? crafterGrid(3, 2, 4, 5) : Set.of();
        int checked = 0;
        for (var owner : ownerList(world)) if (owner instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity crafter) {
            assertCrafterInputGroup(world, repackager ? original : Set.of(crafter.getBlockPos()));
            checked++;
        }
        if (repackager) require(checked == 9, "Pinned repackager must restore all nine actual crafter owners");
    }
    private static Set<BlockPos> crafterGrid(int width, int minY, int maxY, int z) {
        var expected = new HashSet<BlockPos>();
        for (int x = 1; x <= width; x++) for (int y = minY; y <= maxY; y++) expected.add(new BlockPos(x, y, z));
        return Set.copyOf(expected);
    }
    private static void assertCrafterInputGroup(PonderLevel world, Set<BlockPos> expected) {
        var owners = new HashSet<com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity>();
        for (var pos : expected) {
            var owner = com.simibubi.create.content.kinetics.crafter.CrafterHelper.getCrafter(world, pos);
            require(owner != null && owner.isVirtual() && owner.getLevel() == world && !owner.isRemoved(),
                "Crafter input member must be an actual live owner of its local Ponder world: " + pos);
            owners.add(owner);
        }
        for (var owner : owners) {
            var data = (com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity.InputRenderData) owner.getRenderData();
            var actual = com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler.getMembers(owner);
            require(data.members().equals(expected) && actual.size() == expected.size() && new HashSet<>(actual).equals(owners),
                "Actual reciprocal crafter input owners differ from the pinned source group at " + owner.getBlockPos() + ": expected=" + expected + ", actual=" + data.members());
        }
    }
    private static void assertSourceCrafterInputs(PonderLevel world, String title) {
        if (title.equals("create:mechanical_crafter_connect"))
            assertCrafterInputGroup(world, crafterGrid(2, 1, 3, 2));
        if (title.equals("create:mechanical_crafter_covers"))
            assertCrafterInputGroup(world, Set.of(new BlockPos(1, 2, 2), new BlockPos(2, 1, 2), new BlockPos(2, 2, 2), new BlockPos(3, 2, 2)));
    }

    private static void capture(ClientGameTestContext context, NativeSession session, BindingResult result) {
        var sample = context.computeOnClient(client -> PonderRegistryRenderAudit.begin(session.scene));
        context.waitFor(client -> sample.failure() != null || sample.completed() != null, 200);
        if (sample.failure() != null) throw asAssertion(sample.failure());
        var frame = sample.completed();
        require(frame != null && frame.nativeSubmissionReturned() && frame.nativeBlitReturned(),
            "Actual source extraction, native renderer submission and native texture blit must complete");
        result.frames.add(frame);
        result.nativeVisibleGeometry |= frame.blockModels() > 0 || frame.blockEntities() > 0
            || frame.entities() > 0 || frame.sourceItems() > 0;
        context.runOnClient(client -> PonderRegistryRenderAudit.clear());
    }

    private static boolean hasSchedule(PonderScene scene) {
        return !((PonderSceneScheduleAuditAccessor) scene).create$actualActiveSchedule().isEmpty();
    }
    private static boolean atKeyframe(PonderScene scene) {
        for (int i = 0; i < scene.getKeyframeCount(); i++) if (scene.getKeyframeTime(i) == scene.getCurrentTime()) return true;
        return false;
    }
    private static List<BlockEntity> ownerList(PonderLevel world) {
        var result = new ArrayList<BlockEntity>(); world.getBlockEntities().forEach(result::add); return result;
    }

    private record LocalSnapshot(Map<BlockPos, BlockState> blocks, Map<BlockPos, SavedOwner> owners,
        Map<String, CompoundTag> entities) {
        static LocalSnapshot capture(PonderLevel world) {
            var owners = new HashMap<BlockPos, SavedOwner>();
            for (var owner : ownerList(world)) {
                require(owner.getLevel() == world && !owner.isRemoved(), "Every actual local BE must remain owned by its scene");
                owners.put(owner.getBlockPos(), new SavedOwner(owner, owner.saveWithFullMetadata(world.registryAccess()).copy()));
            }
            var entities = new HashMap<String, CompoundTag>();
            for (var entity : world.getEntityList()) {
                require(entity.level() == world, "Every actual local entity must retain its scene owner");
                var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
                require(entity.save(out), "Actual native entity snapshot must be serializable");
                entities.put(entity.getUUID().toString(), out.buildResult().copy());
            }
            return new LocalSnapshot(Map.copyOf(world.getBlockMap()), Map.copyOf(owners), Map.copyOf(entities));
        }
        void assertRestored(PonderLevel world) {
            var restored = capture(world);
            require(blocks.equals(restored.blocks), "Actual backward replay must restore the original local block map");
            require(owners.keySet().equals(restored.owners.keySet()), "Actual replay must restore the original native owner positions");
            for (var entry : owners.entrySet()) {
                var replacement = restored.owners.get(entry.getKey());
                require(entry.getValue().owner != replacement.owner && entry.getValue().data.equals(replacement.data),
                    "Replay must use a fresh owner with the same actual native inventory/component state at " + entry.getKey());
            }
            require(entities.equals(restored.entities), "Actual replay must restore native entity identity/component snapshots");
        }
    }

    private record HostSnapshot(Map<BlockPos, BlockState> blocks, Map<BlockPos, SavedOwner> owners, String particles) {
        static HostSnapshot capture(ClientLevel host, PonderLevel scene, String particles) {
            var blocks = new HashMap<BlockPos, BlockState>(); var owners = new HashMap<BlockPos, SavedOwner>();
            var bounds = scene.getBounds();
            for (int x = bounds.minX() - 2; x <= bounds.maxX() + 2; x++)
                for (int y = bounds.minY() - 2; y <= bounds.maxY() + 2; y++)
                    for (int z = bounds.minZ() - 2; z <= bounds.maxZ() + 2; z++) {
                        var pos = new BlockPos(x, y, z); blocks.put(pos, host.getBlockState(pos));
                        var owner = host.getBlockEntity(pos);
                        if (owner != null) owners.put(pos, new SavedOwner(owner, owner.saveWithFullMetadata(host.registryAccess()).copy()));
                    }
            return new HostSnapshot(Map.copyOf(blocks), Map.copyOf(owners), particles);
        }
        void assertUnchanged(ClientLevel host, String liveParticles) {
            for (var entry : blocks.entrySet()) {
                require(host.getBlockState(entry.getKey()) == entry.getValue(), "Actual host block changed at " + entry.getKey());
                var expected = owners.get(entry.getKey()); var owner = host.getBlockEntity(entry.getKey());
                require(expected == null ? owner == null : owner == expected.owner
                    && expected.data.equals(owner.saveWithFullMetadata(host.registryAccess())),
                    "Actual host owner/inventory/components changed at " + entry.getKey());
            }
            require(particles.equals(liveParticles), "Actual source particles must not enter the live host particle engine");
        }
    }

    private static JsonObject readSourceExpectations() {
        try (var input = PonderRegisteredProgramsTest.class.getResourceAsStream("/create-ponder-source-programs.json")) {
            require(input != null, "Pinned source expectation resource must be installed in the actual test mod");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void write(Report report) {
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static String sha256(Path path) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
        catch (Exception failure) { throw new IllegalStateException("Actual screenshot could not be hashed", failure); }
    }
    private static String describe(Throwable failure) {
        var out = new StringWriter(); failure.printStackTrace(new PrintWriter(out)); return out.toString();
    }
    private static AssertionError asAssertion(Throwable failure) {
        return failure instanceof AssertionError assertion ? assertion : new AssertionError("Actual registered Ponder execution failed", failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

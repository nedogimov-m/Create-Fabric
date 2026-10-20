package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.impl.client.gametest.FabricClientGameTestRunner;
import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl;
import net.fabricmc.loader.api.FabricLoader;

/** Receipts from the real Fabric runner, after its own final cleanup; never runs or replaces tests. */
public final class ClientGameTestCompletionReport {
    private static final String PREFIX = "create.test.clientCompletion";
    private static final boolean ENABLED = System.getProperty(PREFIX + "Report") != null;
    private static final List<String> started = new ArrayList<>(), completed = new ArrayList<>();
    private static List<String> expected = List.of();
    private static String runId, candidateSHA, requestedArtifactSHA, actualArtifactSHA, artifactPath, backend;
    private static String active, failure, state = "NOT_STARTED";
    private static boolean configured, suiteLoopReturned, cancelled;

    private static String required(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) throw new AssertionError("Missing native client completion binding: " + suffix);
        return value;
    }
    private static void configure() {
        if (configured) return;
        runId = required("RunId"); candidateSHA = required("CandidateSHA256");
        requestedArtifactSHA = System.getProperty(PREFIX + "ArtifactSHA256");
        artifactPath = System.getProperty(PREFIX + "ArtifactPath");
        try {
            String[] names = new com.google.gson.Gson().fromJson(Files.readString(Path.of(required("Expected"))), String[].class);
            expected = List.of(names);
        } catch (Exception error) { throw new AssertionError("Cannot load exact native client entrypoint inventory", error); }
        if (expected.isEmpty() || new HashSet<>(expected).size() != expected.size()) throw new AssertionError("Invalid native client completion inventory");
        configured = true;
    }
    private static String digest(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = new byte[8192]; int length;
            while ((length = stream.read(bytes)) != -1) digest.update(bytes, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static synchronized void begin() {
        if (!ENABLED) return;
        configure();
        if (!state.equals("NOT_STARTED")) throw new AssertionError("Native client suite started twice");
        state = "RUNNING";
        try {
            var actual = FabricLoader.getInstance().getEntrypointContainers("fabric-client-gametest", FabricClientGameTest.class).stream()
                .filter(entry -> entry.getProvider().getMetadata().getId().equals("create_test"))
                .map(entry -> entry.getDefinition()).toList();
            if (!actual.equals(expected) || !"create_test".equals(System.getProperty("fabric.client.gametest.modid")))
                throw new AssertionError("Actual native client entrypoint inventory/filter differs from the exact launch binding: " + actual);
            backend = RenderSystem.getDevice().getDeviceInfo().backendName();
            String expectedBackend = System.getProperty(PREFIX + "ExpectedBackend", "vulkan").toLowerCase(Locale.ROOT);
            if (!(expectedBackend.equals("vulkan") || expectedBackend.equals("opengl"))
                    || !backend.toLowerCase(Locale.ROOT).equals(expectedBackend))
                throw new AssertionError("Native client completion requires " + expectedBackend + ": " + backend);
            if (artifactPath != null) {
                Path artifact = Path.of(artifactPath).toRealPath();
                var origins = FabricLoader.getInstance().getModContainer("create").orElseThrow().getOrigin().getPaths();
                boolean actualOrigin = false;
                for (Path origin : origins) if (origin.toRealPath().equals(artifact)) actualOrigin = true;
                if (!actualOrigin) throw new AssertionError("Actual Create runtime origin differs from the bound production JAR: " + origins);
                actualArtifactSHA = digest(artifact);
                if (!actualArtifactSHA.equals(requestedArtifactSHA)) throw new AssertionError("Actual Create production JAR digest changed before client execution");
            }
            write();
        } catch (Throwable error) { fail(error); throw new AssertionError("Cannot establish actual native client completion bindings", error); }
    }
    private static String current() {
        var entry = FabricClientGameTestRunner.currentlyRunningGameTest;
        if (entry == null || !entry.getProvider().getMetadata().getId().equals("create_test")) throw new AssertionError("No genuine current owned native entrypoint");
        return entry.getDefinition();
    }
    public static synchronized void started() {
        if (!ENABLED) return;
        String name = current();
        if (!state.equals("RUNNING") || active != null || !expected.contains(name) || started.contains(name)) throw new AssertionError("Invalid native client start transition: " + name);
        active = name; started.add(name); write();
    }
    public static synchronized void completedAfterNativeCleanup() {
        if (!ENABLED) return;
        String name = current();
        if (!name.equals(active) || completed.contains(name)) throw new AssertionError("Invalid native client completion transition: " + name);
        completed.add(name); active = null; write();
    }
    public static synchronized void suiteReturned(int actualSuiteSize) {
        if (!ENABLED) return;
        if (ThreadingImpl.testFailureException != null || ThreadingImpl.isGameCrashed() || cancelled || active != null
                || actualSuiteSize != expected.size() || !started.equals(expected) || !completed.equals(expected)) {
            fail(ThreadingImpl.testFailureException == null ? new AssertionError("Actual native client loop returned without the complete expected suite") : ThreadingImpl.testFailureException);
            throw new AssertionError("Native client completion is incomplete or failed: " + failure);
        }
        suiteLoopReturned = true; state = "COMPLETE"; write();
    }
    public static synchronized void onClientStop() {
        if (!ENABLED || suiteLoopReturned) return;
        configure(); cancelled = true;
        if (ThreadingImpl.testFailureException != null) fail(ThreadingImpl.testFailureException);
        else { state = "CANCELLED"; write(); }
    }
    private static void fail(Throwable error) {
        failure = error.getClass().getName() + ": " + error.getMessage(); state = "FAILED"; write();
    }
    private static void write() {
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1); report.put("state", state); report.put("runId", runId);
        report.put("candidateEvidenceSHA256", candidateSHA); report.put("requestedProductionArtifactSHA256", requestedArtifactSHA);
        report.put("actualProductionArtifactSHA256", actualArtifactSHA); report.put("productionArtifactPath", artifactPath); report.put("graphicsBackend", backend);
        report.put("expectedEntrypoints", expected); report.put("startedEntrypoints", List.copyOf(started)); report.put("completedEntrypoints", List.copyOf(completed));
        report.put("startedCount", started.size()); report.put("completedCount", completed.size());
        report.put("activeEntrypoint", active); report.put("suiteLoopReturned", suiteLoopReturned); report.put("cancelled", cancelled); report.put("failure", failure);
        report.put("frameworkTestFailure", ThreadingImpl.testFailureException == null ? null : ThreadingImpl.testFailureException.toString());
        report.put("frameworkGameCrashed", ThreadingImpl.isGameCrashed()); report.put("frameworkServerRunning", ThreadingImpl.isServerRunning);
        try {
            Path target = Path.of(required("Report")); Files.createDirectories(target.getParent());
            Path pending = target.resolveSibling(target.getFileName() + ".pending");
            Files.writeString(pending, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report) + "\n");
            try { Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception error) { throw new AssertionError("Cannot persist actual native client completion receipt", error); }
    }
    private ClientGameTestCompletionReport() {}
}

package net.createmod.ponder.test;

import java.util.IdentityHashMap;
import java.util.Map;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderViewport;

/** Observes actual extraction, native source submission and native PIP blitting. No synthetic callbacks. */
public final class PonderRegistryRenderAudit {
    private static volatile Sample active;

    public record FrameReceipt(int sceneTime, int simulationTime, int sections, int blockModels,
        int blockEntities, int entities, int sourceItems, int texts, int inputs, int outlines,
        int fluidSections, int breakingSections, boolean nativeSubmissionReturned, boolean nativeBlitReturned) {}

    public static final class Sample {
        final PonderScene scene;
        final Map<PonderViewport.State, Observation> observations = new IdentityHashMap<>();
        volatile Throwable failure;
        Sample(PonderScene scene) { this.scene = scene; }
        public synchronized FrameReceipt completed() {
            for (var entry : observations.entrySet()) {
                var observation = entry.getValue();
                if (observation.submitted && observation.blitted) return receipt(entry.getKey(), observation);
            }
            return null;
        }
        public synchronized PonderViewport.State completedState() {
            for (var entry : observations.entrySet()) {
                var observation = entry.getValue();
                if (observation.submitted && observation.blitted) return entry.getKey();
            }
            return null;
        }
        public Throwable failure() { return failure; }
        private FrameReceipt receipt(PonderViewport.State state, Observation observation) {
            var frame = state.frame();
            return new FrameReceipt(observation.time, observation.simulationTime, frame.sections().size(),
                frame.sections().stream().mapToInt(section -> section.blocks().size()).sum(),
                frame.sections().stream().mapToInt(section -> section.blockEntities().size()).sum(),
                frame.entities().size(), frame.items().size(), frame.texts().size(), frame.inputs().size(),
                frame.outlines().size(), state.fluids().size(), state.breaking().size(), observation.submitted, observation.blitted);
        }
    }

    private static final class Observation {
        final int time, simulationTime;
        boolean submitted, blitted;
        Observation(PonderScene scene) { time = scene.getCurrentTime(); simulationTime = scene.getWorld().simulationTicks(); }
    }

    public static Sample begin(PonderScene scene) {
        var sample = new Sample(scene); active = sample; return sample;
    }
    public static void clear() { active = null; }

    public static void extracted(PonderScene scene, PonderViewport.State state) {
        var sample = active;
        if (sample == null || sample.scene != scene) return;
        synchronized (sample) { sample.observations.put(state, new Observation(scene)); }
    }

    public static void submitted(PonderViewport.State state) {
        var sample = active;
        if (sample == null) return;
        synchronized (sample) {
            var observation = sample.observations.get(state);
            if (observation == null) return;
            for (var section : state.frame().sections()) for (var owner : section.blockEntities()) {
                if (owner.failure().hasFailed()) {
                    sample.failure = new AssertionError("Actual native BE submission failed at " + owner.position()
                        + ", state=" + owner.state().getClass().getName());
                    return;
                }
            }
            observation.submitted = true;
        }
    }

    public static void blitted(PonderViewport.State state) {
        var sample = active;
        if (sample == null) return;
        synchronized (sample) {
            var observation = sample.observations.get(state);
            if (observation != null) observation.blitted = true;
        }
    }

    private PonderRegistryRenderAudit() {}
}

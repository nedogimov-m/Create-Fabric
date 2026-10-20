package com.simibubi.create.content.kinetics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KineticGraphTest {
    private static final class Graph {
        final Map<String, List<KineticGraph.Edge<String>>> edges = new LinkedHashMap<>();

        Graph connect(String from, String to, double ratio) {
            edges.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new KineticGraph.Edge<>(to, ratio));
            edges.computeIfAbsent(to, ignored -> new ArrayList<>()).add(new KineticGraph.Edge<>(from, 1 / ratio));
            return this;
        }

        KineticGraph.Result<String> solve(Map<String, Double> sources) {
            return KineticGraph.solve(edges, sources, 256);
        }
    }

    @Test
    void motorDrivesShaftChain() {
        var result = new Graph().connect("motor", "shaft", 1).connect("shaft", "end", 1)
                .solve(Map.of("motor", 16.0));
        assertEquals(Map.of("motor", 16.0, "shaft", 16.0, "end", 16.0), result.speeds());
        assertTrue(result.conflicted().isEmpty());
    }

    @Test
    void smallCogsReverseRotation() {
        var result = new Graph().connect("a", "b", -1).connect("b", "c", -1).solve(Map.of("a", 32.0));
        assertEquals(-32, result.speeds().get("b"));
        assertEquals(32, result.speeds().get("c"));
    }

    @Test
    void largeToSmallCogDoublesSpeed() {
        var result = new Graph().connect("large", "small", -2).solve(Map.of("large", 64.0));
        assertEquals(-128, result.speeds().get("small"));
    }

    @Test
    void smallToLargeCogHalvesSpeed() {
        var result = new Graph().connect("large", "small", -2).solve(Map.of("small", 64.0));
        assertEquals(-32, result.speeds().get("large"));
    }

    @Test
    void detachedNetworkStops() {
        var graph = new Graph().connect("a", "b", 1);
        assertEquals(16, graph.solve(Map.of("a", 16.0)).speeds().get("b"));
        assertEquals(Map.of("a", 0.0, "b", 0.0), graph.solve(Map.of()).speeds());
    }

    @Test
    void disconnectedComponentsHaveIndependentSources() {
        var result = new Graph().connect("a", "b", 1).connect("c", "d", 1)
                .solve(Map.of("a", 16.0, "c", -64.0));
        assertEquals(16, result.speeds().get("b"));
        assertEquals(-64, result.speeds().get("d"));
    }

    @Test
    void fasterCompatibleMotorWins() {
        var result = new Graph().connect("slow", "fast", 1).solve(Map.of("slow", 16.0, "fast", 64.0));
        assertEquals(64, result.speeds().get("slow"));
        assertTrue(result.conflicted().isEmpty());
    }

    @Test
    void sourcesAreComparedAfterGearRatios() {
        var result = new Graph().connect("large", "small", -2).solve(Map.of("large", 16.0, "small", -64.0));
        assertEquals(32, result.speeds().get("large"));
        assertTrue(result.conflicted().isEmpty());
    }

    @Test
    void opposingMotorsStopTheComponent() {
        var result = new Graph().connect("a", "b", 1).solve(Map.of("a", 16.0, "b", -16.0));
        assertEquals(Map.of("a", 0.0, "b", 0.0), result.speeds());
        assertEquals(2, result.conflicted().size());
    }

    @Test
    void inconsistentGearLoopStopsEvenWithoutSource() {
        var result = new Graph().connect("a", "b", -1).connect("b", "c", -1).connect("c", "a", -1)
                .solve(Map.of());
        assertEquals(3, result.conflicted().size());
    }

    @Test
    void compatibleLoopRuns() {
        var result = new Graph().connect("a", "b", -1).connect("b", "c", -1)
                .connect("c", "d", -1).connect("d", "a", -1).solve(Map.of("a", 16.0));
        assertEquals(-16, result.speeds().get("d"));
        assertTrue(result.conflicted().isEmpty());
    }

    @Test
    void overspeedStopsComponent() {
        var result = new Graph().connect("large", "small", -2).solve(Map.of("large", 256.0));
        assertEquals(2, result.conflicted().size());
        assertEquals(0, result.speeds().get("small"));
    }

    @Test
    void invalidNumbersCannotEnterNetwork() {
        assertThrows(IllegalArgumentException.class, () -> new KineticGraph.Edge<>("node", 0));
        assertThrows(IllegalArgumentException.class, () -> new KineticGraph.Edge<>("node", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new KineticGraph.Edge<>("node", Double.POSITIVE_INFINITY));
        var result = new Graph().connect("a", "b", 1).solve(Map.of("a", Double.NaN));
        assertEquals(2, result.conflicted().size());
    }

    @Test
    void longShaftChainDoesNotRecurse() {
        var graph = new Graph();
        for (int i = 0; i < 10000; i++) {
            graph.connect("node" + i, "node" + (i + 1), 1);
        }
        var result = graph.solve(Map.of("node0", 16.0));
        assertEquals(16, result.speeds().get("node10000"));
        assertTrue(result.conflicted().isEmpty());
    }

    @Test
    void stressImpactUsesActualGearRatioAndCapacityUsesGeneratedRpm() {
        var graph = new Graph().connect("crank", "large", 1).connect("large", "small", -2);
        var result = KineticGraph.solve(graph.edges, Map.of("crank", 32.0),
                Map.of("crank", new KineticGraph.Load(0, 8), "small", new KineticGraph.Load(4, 0)), 256);
        assertEquals(256, result.stress().get("small").impact());
        assertEquals(256, result.stress().get("small").capacity());
        assertFalse(result.stress().get("small").overstressed());
        assertEquals(-64, result.speeds().get("small"));
    }

    @Test
    void overloadPreservesTheoreticalSpeedAndRecoversAfterLoadRemoval() {
        var graph = new Graph().connect("crank", "machine", 1);
        var loads = Map.of("crank", new KineticGraph.Load(0, 8), "machine", new KineticGraph.Load(9, 0));
        var overloaded = KineticGraph.solve(graph.edges, Map.of("crank", 32.0), loads, 256);
        assertTrue(overloaded.stress().get("machine").overstressed());
        assertEquals(0, overloaded.speeds().get("machine"));
        assertEquals(32, overloaded.theoreticalSpeeds().get("machine"));
        assertTrue(overloaded.conflicted().isEmpty());
        var recovered = KineticGraph.solve(graph.edges, Map.of("crank", 32.0),
                Map.of("crank", new KineticGraph.Load(0, 8)), 256);
        assertEquals(32, recovered.speeds().get("machine"));
    }

    @Test
    void compatibleGeneratorsCombineCapacity() {
        var graph = new Graph().connect("left", "machine", 1).connect("machine", "right", 1);
        var result = KineticGraph.solve(graph.edges, Map.of("left", 32.0, "right", 32.0),
                Map.of("left", new KineticGraph.Load(0, 8), "right", new KineticGraph.Load(0, 8),
                        "machine", new KineticGraph.Load(12, 0)), 256);
        assertEquals(512, result.stress().get("machine").capacity());
        assertEquals(384, result.stress().get("machine").impact());
        assertFalse(result.stress().get("machine").overstressed());
    }
}

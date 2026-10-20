package com.simibubi.create.content.kinetics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SequencedKineticGraphTest {
    private static final class Graph {
        final Map<String,List<KineticGraph.Edge<String>>> edges=new LinkedHashMap<>();
        final List<SequencedKineticGraph.Split<String>> splits=new ArrayList<>();
        void node(String node){edges.computeIfAbsent(node,key->new ArrayList<>());}
        void edge(String from,String to,double ratio){node(from);node(to);edges.get(from).add(new KineticGraph.Edge<>(to,ratio));edges.get(to).add(new KineticGraph.Edge<>(from,1/ratio));}
        void split(String negative,String positive,int modifier){node(negative);node(positive);splits.add(new SequencedKineticGraph.Split<>(negative,positive,modifier));}
        SequencedKineticGraph.Result<String> solve(Map<String,Double> sources){return SequencedKineticGraph.solve(edges,sources,Map.of(),List.of(),splits,Map.of(),256);}
    }
    private static Graph single(int modifier){var graph=new Graph();graph.edge("left","in",1);graph.split("in","out",modifier);graph.edge("out","machine",1);return graph;}
    @Test void doubledOutputCannotBecomeItsOwnInput(){
        var result=single(2).solve(Map.of("left",64d));assertTrue(result.converged());assertEquals("in",result.inputs().get("in"));
        assertEquals(64,result.kinetics().speeds().get("in"));assertEquals(128,result.kinetics().speeds().get("out"));
        assertEquals("left",result.kinetics().generators().get("machine"));assertEquals("in",result.kinetics().parents().get("out"));
    }
    @Test void sourceFromPositiveFaceActuallyReversesOwnership(){
        var result=single(2).solve(Map.of("machine",32d));assertEquals("out",result.inputs().get("in"));
        assertEquals(32,result.kinetics().speeds().get("out"));assertEquals(64,result.kinetics().speeds().get("in"));assertEquals("machine",result.kinetics().generators().get("left"));
    }
    @Test void allFourSourceModifiersConveySignedOutput(){
        for(int modifier:new int[]{-2,-1,1,2}){var result=single(modifier).solve(Map.of("left",32d));assertEquals(32d*modifier,result.kinetics().speeds().get("machine"));assertEquals("in",result.inputs().get("in"));}
    }
    @Test void pauseDisconnectsOutputAndNeverManufacturesAZeroEdge(){
        var result=single(0).solve(Map.of("left",64d));assertEquals(64,result.kinetics().speeds().get("in"));assertEquals(0,result.kinetics().speeds().get("out"));assertFalse(result.kinetics().generators().containsKey("machine"));
    }
    @Test void pausedIndependentlyDrivenFacesRemainSeparateComponents(){
        var result=single(0).solve(Map.of("left",64d,"machine",-32d));assertEquals(64,result.kinetics().speeds().get("in"));assertEquals(-32,result.kinetics().speeds().get("out"));assertTrue(result.kinetics().conflicted().isEmpty());
    }
    @Test void chainedSequencersDiscoverRealUpstreamThroughSeveralPasses(){
        var graph=single(2);graph.split("b-in","b-out",-1);graph.edge("machine","b-in",1);graph.edge("b-out","tip",1);
        var result=graph.solve(Map.of("left",32d));assertTrue(result.converged());assertEquals("in",result.inputs().get("in"));assertEquals("b-in",result.inputs().get("b-in"));assertEquals(-64,result.kinetics().speeds().get("tip"));assertEquals("left",result.kinetics().generators().get("tip"));
    }
    @Test void weakerGeneratorOnOutputDoesNotStealInputAtTwoTimesSpeed(){
        var result=single(2).solve(Map.of("left",64d,"machine",32d));assertEquals("in",result.inputs().get("in"));assertEquals(128,result.kinetics().speeds().get("machine"));assertEquals("left",result.kinetics().generators().get("machine"));
    }
    @Test void strongerIndependentSourceCanTakeOverFromTheOtherSide(){
        var result=single(2).solve(Map.of("left",16d,"machine",64d));assertEquals("out",result.inputs().get("in"));assertEquals(128,result.kinetics().speeds().get("left"));assertEquals("machine",result.kinetics().generators().get("left"));
    }
    @Test void downstreamGearingRetainsTheRealGeneratorAndRatio(){
        var graph=single(2);graph.edge("machine","large",-.5);var result=graph.solve(Map.of("left",64d));assertEquals(-64,result.kinetics().speeds().get("large"));assertEquals("left",result.kinetics().generators().get("large"));
    }
    @Test void stressKeepsTheoreticalInputAndProvenanceWhileStoppingMotion(){
        var graph=single(2);var result=SequencedKineticGraph.solve(graph.edges,Map.of("left",32d),Map.of("machine",new KineticGraph.Load(100,0)),List.of(),graph.splits,Map.of(),256);
        assertEquals(0,result.kinetics().speeds().get("machine"));assertEquals(64,result.kinetics().theoreticalSpeeds().get("machine"));assertEquals("left",result.kinetics().generators().get("machine"));assertTrue(result.kinetics().stress().get("machine").overstressed());
    }
    @Test void overspeedFailsItsComponentWithoutAddingAnySpeedSource(){
        var result=single(2).solve(Map.of("left",256d));assertTrue(result.kinetics().conflicted().contains("machine"));assertEquals(0,result.kinetics().speeds().get("in"));assertTrue(result.kinetics().generators().isEmpty());
    }
    @Test void contradictoryLoopDoesNotDisableAnUnrelatedHealthyComponent(){
        var graph=single(2);graph.edge("in","out",1);graph.edge("healthy-motor","healthy-machine",1);var result=graph.solve(Map.of("left",16d,"healthy-motor",32d));
        assertTrue(result.kinetics().conflicted().contains("machine"));assertEquals(32,result.kinetics().speeds().get("healthy-machine"));assertFalse(result.kinetics().conflicted().contains("healthy-machine"));
    }
    @Test void absoluteSpeedControllerCanFeedASequencerWithoutBecomingAGenerator(){
        var graph=single(2);graph.node("motor");var controls=List.of(new ControlledKineticGraph.Controller<>("motor","left",64d));
        var result=SequencedKineticGraph.solve(graph.edges,Map.of("motor",16d),Map.of(),controls,graph.splits,Map.of(),256);
        assertEquals(64,result.kinetics().speeds().get("in"));assertEquals(128,result.kinetics().speeds().get("machine"));assertEquals("motor",result.kinetics().generators().get("machine"));
    }
    @Test void reverseEdgesReallyUseTheReciprocalConstraint(){
        var graph=new Graph();graph.edge("input","output",-2);var result=KineticGraph.solve(graph.edges,Map.of("output",64d),256);
        assertEquals(-32,result.speeds().get("input"));assertEquals(64,result.speeds().get("output"));assertEquals("output",result.generators().get("input"));
    }
}

package com.simibubi.create.content.kinetics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlledKineticGraphTest {
    private static final class Graph {
        final Map<String,List<KineticGraph.Edge<String>>> edges=new LinkedHashMap<>();
        final List<ControlledKineticGraph.Controller<String>> controls=new ArrayList<>();
        Graph node(String key){edges.computeIfAbsent(key,ignored->new ArrayList<>());return this;}
        Graph fixed(String from,String to,double ratio){node(from);node(to);edges.get(from).add(new KineticGraph.Edge<>(to,ratio));edges.get(to).add(new KineticGraph.Edge<>(from,1/ratio));return this;}
        Graph control(String shaft,String cog,double target){node(shaft);node(cog);controls.add(new ControlledKineticGraph.Controller<>(shaft,cog,target));return this;}
        KineticGraph.Result<String> solve(Map<String,Double> sources){return ControlledKineticGraph.solve(edges,sources,Map.of(),controls,256);}
    }
    @Test void shaftDriveSetsAbsoluteCogSpeedAndReversesIndependentlyOfInput(){
        var graph=new Graph().fixed("motor","shaft",1).control("shaft","cog",-96).fixed("cog","output",-2);
        for(double drive:new double[]{16,-32,128}){var result=graph.solve(Map.of("motor",drive));assertEquals(drive,result.speeds().get("shaft"));assertEquals(-96,result.speeds().get("cog"));assertEquals(192,result.speeds().get("output"));assertTrue(result.conflicted().isEmpty());}
    }
    @Test void cogDriveSetsAbsoluteShaftSpeedAndSourceRemovalStopsBoth(){
        var graph=new Graph().control("shaft","cog",-64).fixed("cog","motor",1).fixed("shaft","machine",1);
        var running=graph.solve(Map.of("motor",16d));assertEquals(16,running.speeds().get("cog"));assertEquals(-64,running.speeds().get("machine"));
        assertTrue(graph.solve(Map.of()).speeds().values().stream().allMatch(speed->speed==0));
    }
    @Test void controllersCanBeChainedInEitherDirectionWithoutInventingSources(){
        var graph=new Graph().control("a","b",32).fixed("b","c",-1).control("c","d",-80).fixed("d","e",1);
        var forward=graph.solve(Map.of("a",16d));assertEquals(16,forward.speeds().get("a"));assertEquals(32,forward.speeds().get("b"));assertEquals(-32,forward.speeds().get("c"));assertEquals(-80,forward.speeds().get("e"));
        var reverse=graph.solve(Map.of("e",16d));assertEquals(-80,reverse.speeds().get("c"));assertEquals(80,reverse.speeds().get("b"));assertEquals(32,reverse.speeds().get("a"));
    }
    @Test void zeroTargetDisconnectsLoadAndDoesNotBrakeIndependentSource(){
        var graph=new Graph().control("shaft","cog",0).fixed("cog","machine",1);
        var idle=graph.solve(Map.of("shaft",32d));assertEquals(32,idle.speeds().get("shaft"));assertEquals(0,idle.speeds().get("machine"));
        var both=graph.solve(Map.of("shaft",32d,"cog",64d));assertEquals(32,both.speeds().get("shaft"));assertEquals(64,both.speeds().get("cog"));assertTrue(both.conflicted().isEmpty());
    }
    @Test void fasterCompatibleIndependentDestinationRetainsSpeed(){
        var result=new Graph().control("shaft","cog",32).solve(Map.of("shaft",16d,"cog",64d));assertEquals(16,result.speeds().get("shaft"));assertEquals(64,result.speeds().get("cog"));assertTrue(result.conflicted().isEmpty());
    }
    @Test void opposingIndependentSourceInvalidatesOnlyConnectedComponent(){
        var result=new Graph().control("shaft","cog",-32).fixed("independent","remote",1).solve(Map.of("shaft",16d,"cog",64d,"independent",32d));
        assertEquals(0,result.speeds().get("shaft"));assertEquals(0,result.speeds().get("cog"));assertEquals(32,result.speeds().get("remote"));assertEquals(Set.of("shaft","cog"),result.conflicted());
    }
    @Test void gearRatiosAfterControllerCanOverspeed(){
        var result=new Graph().control("shaft","cog",192).fixed("cog","small",-2).solve(Map.of("shaft",16d));assertEquals(3,result.conflicted().size());assertTrue(result.speeds().values().stream().allMatch(speed->speed==0));
    }
    @Test void rigidFeedbackCannotBeAcceleratedByController(){
        var graph=new Graph().control("shaft","cog",64).fixed("shaft","cog",1);var result=graph.solve(Map.of("shaft",16d));assertEquals(2,result.conflicted().size());
        var coasting=new Graph().control("shaft","cog",8).fixed("shaft","cog",1).solve(Map.of("shaft",16d));assertTrue(coasting.conflicted().isEmpty());assertEquals(16,coasting.speeds().get("cog"));
    }
    @Test void multipleControllerFeedbackDoesNotHangOrCreatePerpetualMotion(){
        var graph=new Graph().control("a","b",32).control("b","a",64);var result=graph.solve(Map.of("a",16d));assertEquals(Set.of("a","b"),result.conflicted());
        assertTrue(graph.solve(Map.of()).speeds().values().stream().allMatch(speed->speed==0));
    }
    @Test void stressUsesDrivenRpmAndPhysicalGeneratorCapacity(){
        var graph=new Graph().control("shaft","cog",128).fixed("cog","machine",1);
        var result=ControlledKineticGraph.solve(graph.edges,Map.of("shaft",32d),Map.of("shaft",new KineticGraph.Load(0,8),"machine",new KineticGraph.Load(4,0)),graph.controls,256);
        assertTrue(result.stress().get("machine").overstressed());assertEquals(256,result.stress().get("machine").capacity());assertEquals(512,result.stress().get("machine").impact());assertEquals(128,result.theoreticalSpeeds().get("machine"));assertEquals(0,result.speeds().get("shaft"));
    }
    @Test void flexibleSteamSourceFollowsRegulatedDirectionWithoutReversingRigidMotor(){
        var graph=new Graph().fixed("steam","cog",1).control("shaft","cog",-32);var changes=new HashMap<String,Double>();
        var result=ControlledKineticGraph.solve(graph.edges,Map.of("shaft",16d,"steam",64d),Map.of(),graph.controls,Set.of("steam"),changes::put,256);
        assertTrue(result.conflicted().isEmpty());assertEquals(16,result.speeds().get("shaft"));assertEquals(-64,result.speeds().get("cog"));assertEquals(Map.of("steam",-64d),changes);
    }
    @Test void cogDrivenControllerCanOverpowerAndReverseFlexibleSourceOnShaft(){
        var graph=new Graph().fixed("steam","shaft",1).control("shaft","cog",-128);var changes=new HashMap<String,Double>();
        var result=ControlledKineticGraph.solve(graph.edges,Map.of("steam",64d,"cog",16d),Map.of(),graph.controls,Set.of("steam"),changes::put,256);
        assertTrue(result.conflicted().isEmpty());assertEquals(-128,result.speeds().get("shaft"));assertEquals(16,result.speeds().get("cog"));assertEquals(Map.of("steam",-64d),changes);
    }
}

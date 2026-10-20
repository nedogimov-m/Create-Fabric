package com.simibubi.create.content.kinetics;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Source-owned split constraints. Zero output is an absent edge, never an RPM source or zero-ratio edge. */
public final class SequencedKineticGraph {
    public record Split<K>(K negative,K positive,int modifier){
        public Split{Objects.requireNonNull(negative);Objects.requireNonNull(positive);if(negative.equals(positive)||modifier< -2||modifier>2)throw new IllegalArgumentException("Invalid sequencer ports/modifier");}
        public K opposite(K port){if(negative.equals(port))return positive;if(positive.equals(port))return negative;throw new IllegalArgumentException("Not a sequencer port");}
    }
    public record Result<K>(KineticGraph.Result<K> kinetics,Map<K,K> inputs,boolean converged){}

    public static <K> Result<K> solve(Map<K,List<KineticGraph.Edge<K>>> rigid,Map<K,Double> sources,
            Map<K,KineticGraph.Load> loads,List<ControlledKineticGraph.Controller<K>> controllers,
            List<Split<K>> splits,Map<K,K> previousInputs,double maxRpm){
        return solve(rigid,(graph,commit)->sources,loads,controllers,splits,previousInputs,Set.of(),(node,speed)->{},maxRpm);
    }

    public static <K> Result<K> solve(Map<K,List<KineticGraph.Edge<K>>> rigid,
            BiFunction<Map<K,List<KineticGraph.Edge<K>>>,Boolean,Map<K,Double>> sourceProvider,
            Map<K,KineticGraph.Load> loads,List<ControlledKineticGraph.Controller<K>> controllers,
            List<Split<K>> splits,Map<K,K> previousInputs,Set<K> reversibleSources,
            BiConsumer<K,Double> onSourceReversed,double maxRpm){
        for(var split:splits)if(!rigid.containsKey(split.negative)||!rigid.containsKey(split.positive))throw new IllegalArgumentException("Missing sequencer endpoint");
        if(splits.isEmpty())return new Result<>(solveGraph(rigid,sourceProvider,loads,controllers,reversibleSources,onSourceReversed,Set.of(),maxRpm,true),Map.of(),true);
        var inputs=new LinkedHashMap<K,K>();
        // Establish initial ownership with ALL sequencer internal connections absent.
        var result=solveGraph(rigid,sourceProvider,loads,controllers,reversibleSources,onSourceReversed,Set.of(),maxRpm,false);
        for(var split:splits){var input=chooseInput(split,result,previousInputs.get(split.negative));if(input!=null)inputs.put(split.negative,input);}
        var seen=new HashSet<Map<K,K>>();
        int budget=Math.max(16,(splits.size()+controllers.size()+1)*8);
        for(int iteration=0;iteration<budget;iteration++){
            if(!seen.add(Map.copyOf(inputs)))break;
            var graph=connect(rigid,splits,inputs);
            result=solveGraph(graph,sourceProvider,loads,controllers,reversibleSources,onSourceReversed,Set.of(),maxRpm,false);
            var next=new LinkedHashMap<K,K>();
            for(var split:splits){var input=chooseInput(split,result,inputs.get(split.negative));if(input!=null)next.put(split.negative,input);}
            if(next.equals(inputs)){
                var committed=solveGraph(graph,sourceProvider,loads,controllers,reversibleSources,onSourceReversed,Set.of(),maxRpm,true);
                return new Result<>(committed,Map.copyOf(inputs),true);
            }
            inputs=next;
        }
        // A contradictory ownership cycle fails the physical component. Keep the genuine constraints;
        // never repair it by substituting ratio1, zero edges, or synthetic speed sources.
        var graph=connect(rigid,splits,inputs);
        var probe=solveGraph(graph,sourceProvider,loads,controllers,reversibleSources,onSourceReversed,Set.of(),maxRpm,false);
        var invalid=new HashSet<K>();for(var split:splits)if(!Objects.equals(inputs.get(split.negative),chooseInput(split,probe,inputs.get(split.negative)))){invalid.add(split.negative);invalid.add(split.positive);}
        return new Result<>(solveGraph(graph,sourceProvider,loads,controllers,reversibleSources,onSourceReversed,invalid,maxRpm,true),Map.copyOf(inputs),false);
    }

    private static <K> KineticGraph.Result<K> solveGraph(Map<K,List<KineticGraph.Edge<K>>> graph,
            BiFunction<Map<K,List<KineticGraph.Edge<K>>>,Boolean,Map<K,Double>> sourceProvider,
            Map<K,KineticGraph.Load> loads,List<ControlledKineticGraph.Controller<K>> controllers,
            Set<K> reversibleSources,BiConsumer<K,Double> reversed,Set<K> invalid,double maxRpm,boolean commit){
        return ControlledKineticGraph.solve(graph,sourceProvider.apply(graph,commit),loads,controllers,reversibleSources,
            commit?reversed:(node,speed)->{},invalid,maxRpm);
    }
    private static <K> K chooseInput(Split<K> split,KineticGraph.Result<K> result,K previous){
        K a=split.negative,b=split.positive;
        boolean aPowered=result.generators().containsKey(a),bPowered=result.generators().containsKey(b);
        if(aPowered!=bPowered)return aPowered?a:b;
        if(!aPowered)return a.equals(previous)||b.equals(previous)?previous:null;
        if(Objects.equals(result.generators().get(a),result.generators().get(b))){
            int aDepth=result.depths().getOrDefault(a,Integer.MAX_VALUE),bDepth=result.depths().getOrDefault(b,Integer.MAX_VALUE);
            if(aDepth!=bDepth)return aDepth<bDepth?a:b;
        }else{
            // Independently driven, disconnected ports compete using their external source speeds.
            // Once connected the chosen generator/path, not the doubled output RPM, owns the input.
            double aSpeed=Math.abs(result.theoreticalSpeeds().getOrDefault(a,0d)),bSpeed=Math.abs(result.theoreticalSpeeds().getOrDefault(b,0d));
            if(Math.abs(aSpeed-bSpeed)>1e-6)return aSpeed>bSpeed?a:b;
        }
        return a.equals(previous)||b.equals(previous)?previous:a;
    }
    private static <K> Map<K,List<KineticGraph.Edge<K>>> connect(Map<K,List<KineticGraph.Edge<K>>> rigid,List<Split<K>> splits,Map<K,K> inputs){
        var graph=new LinkedHashMap<K,List<KineticGraph.Edge<K>>>();rigid.forEach((node,edges)->graph.put(node,new ArrayList<>(edges)));
        for(var split:splits){K input=inputs.get(split.negative);if(input==null||split.modifier==0)continue;K output=split.opposite(input);
            graph.get(input).add(new KineticGraph.Edge<>(output,split.modifier));
            graph.get(output).add(new KineticGraph.Edge<>(input,1d/split.modifier));
        }return graph;
    }
    private SequencedKineticGraph(){}
}

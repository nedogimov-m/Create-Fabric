package com.simibubi.create.content.kinetics;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A world-independent solver. Each edge describes target RPM / source RPM. */
public final class KineticGraph {
    public record Edge<K>(K target, double ratio) {
        public Edge {
            if (!Double.isFinite(ratio) || ratio == 0) {
                throw new IllegalArgumentException("A kinetic connection needs a finite, nonzero ratio");
            }
        }
    }

    public record Load(double impact, double capacity,boolean sourceParameters) {
        public Load(double impact,double capacity){this(impact,capacity,false);}
        /** The pinned provider is narrowed to float and deliberately carries signed/nonfinite stress. */
        public static Load source(double impact,double capacity){return new Load((float)impact,(float)capacity,true);}
        public static final Load NONE = new Load(0, 0);

        public Load {
            if (!sourceParameters&&(!Double.isFinite(impact) || impact < 0 || !Double.isFinite(capacity) || capacity < 0)) {
                throw new IllegalArgumentException("Stress values must be finite and nonnegative");
            }
        }
    }

    public record Stress(double capacity, double impact, boolean overstressed) {}

    public record Result<K>(Map<K, Double> speeds, Set<K> conflicted, Map<K, Double> theoreticalSpeeds,
                            Map<K, Stress> stress,Map<K,K> generators,Map<K,K> parents,Map<K,Integer> depths) {
        public Result(Map<K,Double> speeds,Set<K> conflicted,Map<K,Double> theoreticalSpeeds,Map<K,Stress> stress){this(speeds,conflicted,theoreticalSpeeds,stress,Map.of(),Map.of(),Map.of());}
    }

    public static <K> Result<K> solve(Map<K, List<Edge<K>>> graph, Map<K, Double> sources, double maxRpm) {
        return solve(graph, sources, Map.of(), maxRpm);
    }

    public static <K> Result<K> solve(Map<K, List<Edge<K>>> graph, Map<K, Double> sources,
                                      Map<K, Load> loads, double maxRpm) {
        return solve(graph, sources, loads, Set.of(), maxRpm);
    }

    public static <K> Result<K> solve(Map<K, List<Edge<K>>> graph, Map<K, Double> sources,
                                      Map<K, Load> loads, Set<K> invalidNodes, double maxRpm) {
        if (!Double.isFinite(maxRpm) || maxRpm <= 0) {
            throw new IllegalArgumentException("Maximum RPM must be finite and positive");
        }
        boolean sourceStress=loads.values().stream().anyMatch(Load::sourceParameters);
        Map<K, Double> speeds = new LinkedHashMap<>();
        Map<K, Double> theoreticalSpeeds = new LinkedHashMap<>();
        Map<K, Stress> stresses = new LinkedHashMap<>();
        Map<K,K> generators=new LinkedHashMap<>(),parents=new LinkedHashMap<>();
        Map<K,Integer> depths=new LinkedHashMap<>();
        Set<K> conflicted = new HashSet<>();
        Set<K> visited = new HashSet<>();
        for (K root : graph.keySet()) {
            if (visited.contains(root)) {
                continue;
            }
            Map<K, Double> ratios = new LinkedHashMap<>();
            var queue = new ArrayDeque<K>();
            ratios.put(root, 1.0);
            visited.add(root);
            queue.add(root);
            boolean conflict = false;
            while (!queue.isEmpty()) {
                K current = queue.remove();
                for (Edge<K> edge : graph.getOrDefault(current, List.of())) {
                    if (!graph.containsKey(edge.target())) {
                        throw new IllegalArgumentException("Connection to a missing kinetic node");
                    }
                    double ratio = ratios.get(current) * edge.ratio();
                    if (!Double.isFinite(ratio) || ratio == 0) {
                        conflict = true;
                        ratio = 1;
                    }
                    Double previous = ratios.putIfAbsent(edge.target(), ratio);
                    if (previous == null) {
                        visited.add(edge.target());
                        queue.add(edge.target());
                    } else if (!approximatelyEqual(previous, ratio)) {
                        conflict = true;
                    }
                }
            }

            if (ratios.keySet().stream().anyMatch(invalidNodes::contains)) conflict = true;
            double rootSpeed = 0;K generator=null;
            for (var entry : ratios.entrySet()) {
                double generated = sources.getOrDefault(entry.getKey(), 0.0);
                if (!Double.isFinite(generated)) {
                    conflict = true;
                    continue;
                }
                if (generated == 0) {
                    continue;
                }
                double candidate = generated / entry.getValue();
                if (rootSpeed != 0 && Math.signum(rootSpeed) != Math.signum(candidate)) {
                    conflict = true;
                }
                if (Math.abs(candidate) > Math.abs(rootSpeed)) {
                    rootSpeed = candidate;generator=entry.getKey();
                }
            }
            for (double ratio : ratios.values()) {
                if (!Double.isFinite(rootSpeed * ratio) || Math.abs(rootSpeed * ratio) > maxRpm) {
                    conflict = true;
                }
            }
            // Build an actual shortest source path, independent of RPM magnitude at ratio-changing ports.
            if(!conflict&&generator!=null){
                var path=new ArrayDeque<K>();path.add(generator);depths.put(generator,0);generators.put(generator,generator);
                while(!path.isEmpty()){
                    K from=path.remove();
                    for(var edge:graph.getOrDefault(from,List.of()))if(!generators.containsKey(edge.target())){
                        generators.put(edge.target(),generator);parents.put(edge.target(),from);depths.put(edge.target(),depths.get(from)+1);path.add(edge.target());
                    }
                }
            }
            double capacity = 0;
            double impact = 0;
            for (var entry : ratios.entrySet()) {
                Load load = loads.getOrDefault(entry.getKey(), Load.NONE);
                double generated = sources.getOrDefault(entry.getKey(), 0.0);
                if(sourceStress){
                    // Source KineticNetwork keeps only active generators in its capacity map;
                    // member stress and both running totals use float arithmetic without clamping.
                    if(generated!=0)capacity=(float)((float)capacity+(float)load.capacity()*Math.abs((float)generated));
                    impact=(float)((float)impact+(float)load.impact()*Math.abs((float)(conflict?0:rootSpeed*entry.getValue())));
                }else{
                    capacity += load.capacity() * Math.abs(Double.isFinite(generated) ? generated : 0);
                    impact += load.impact() * Math.abs(conflict ? 0 : rootSpeed * entry.getValue());
                }
            }
            boolean overstressed = !conflict && impact > capacity;
            Stress stress = new Stress(capacity, impact, overstressed);
            for (var entry : ratios.entrySet()) {
                double theoretical = conflict ? 0.0 : rootSpeed * entry.getValue();
                theoreticalSpeeds.put(entry.getKey(), theoretical);
                stresses.put(entry.getKey(), stress);
                speeds.put(entry.getKey(), conflict || overstressed ? 0.0 : theoretical);
                if (conflict) {
                    conflicted.add(entry.getKey());
                }
            }
        }
        return new Result<>(Map.copyOf(speeds), Set.copyOf(conflicted), Map.copyOf(theoreticalSpeeds), Map.copyOf(stresses),Map.copyOf(generators),Map.copyOf(parents),Map.copyOf(depths));
    }

    private static boolean approximatelyEqual(double a, double b) {
        return Math.abs(a - b) <= 1e-6 * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
    }

    private KineticGraph() {}
}

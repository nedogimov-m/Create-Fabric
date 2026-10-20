package com.simibubi.create.content.kinetics;

import java.util.*;

/** Resolves the source-owned, absolute-speed connections of rotation speed controllers. */
public final class ControlledKineticGraph {
    public record Controller<K>(K shaft, K cog, double target) {
        public Controller {
            if (!Double.isFinite(target)) throw new IllegalArgumentException("Controller target must be finite");
            Objects.requireNonNull(shaft); Objects.requireNonNull(cog);
        }
    }
    private static final class Component<K> {
        final Map<K, Double> ratios = new LinkedHashMap<>();
        final List<Controller<K>> connections = new ArrayList<>();
        double speed;
        Controller<K> owner;
        Component<K> parent = this;
        boolean invalid;
        boolean onlyReversibleSources;
        Component<K> root() {
            Component<K> root = this;
            while (root.parent != root) root = root.parent;
            Component<K> cursor = this;
            while (cursor.parent != cursor) { var next = cursor.parent; cursor.parent = root; cursor = next; }
            return root;
        }
        double speedAt(K node) { return speed * ratios.get(node); }
    }
    public static <K> KineticGraph.Result<K> solve(Map<K, List<KineticGraph.Edge<K>>> rigid,
            Map<K, Double> sources, Map<K, KineticGraph.Load> loads, List<Controller<K>> controllers, double maxRpm) {
        return solve(rigid,sources,loads,controllers,Set.of(),(node,speed)->{},maxRpm);
    }
    public static <K> KineticGraph.Result<K> solve(Map<K, List<KineticGraph.Edge<K>>> rigid,
            Map<K, Double> sources, Map<K, KineticGraph.Load> loads, List<Controller<K>> controllers,
            Set<K> reversibleSources,java.util.function.BiConsumer<K,Double> onSourceReversed,double maxRpm) {
        return solve(rigid,sources,loads,controllers,reversibleSources,onSourceReversed,Set.of(),maxRpm);
    }
    public static <K> KineticGraph.Result<K> solve(Map<K,List<KineticGraph.Edge<K>>> rigid,
            Map<K,Double> sources,Map<K,KineticGraph.Load> loads,List<Controller<K>> controllers,
            Set<K> reversibleSources,java.util.function.BiConsumer<K,Double> onSourceReversed,Set<K> invalidNodes,double maxRpm){
        if (controllers.isEmpty()) return KineticGraph.solve(rigid, sources, loads, invalidNodes,maxRpm);
        var workingSources=new HashMap<>(sources);
        var components = new ArrayList<Component<K>>();
        var membership = new HashMap<K, Component<K>>();
        for (var start : rigid.keySet()) {
            if (membership.containsKey(start)) continue;
            var component = new Component<K>(); components.add(component);
            var queue = new ArrayDeque<K>(); queue.add(start); component.ratios.put(start, 1d); membership.put(start, component);
            while (!queue.isEmpty()) {
                K from = queue.remove();
                for (var edge : rigid.get(from)) {
                    if (!rigid.containsKey(edge.target())) throw new IllegalArgumentException("Connection to missing node");
                    double ratio = component.ratios.get(from) * edge.ratio();
                    if (!Double.isFinite(ratio) || ratio == 0) { component.invalid = true; ratio = 1; }
                    var previous = component.ratios.putIfAbsent(edge.target(), ratio);
                    if (previous == null) { membership.put(edge.target(), component); queue.add(edge.target()); }
                    else if (!equal(previous, ratio)) component.invalid = true;
                }
            }
            boolean hasReversible=false,hasRigid=false;
            for (var entry : component.ratios.entrySet()) {
                double generated = sources.getOrDefault(entry.getKey(), 0d);
                if (!Double.isFinite(generated)) { component.invalid = true; continue; }
                double candidate = generated / entry.getValue();
                if (candidate == 0) continue;
                if(reversibleSources.contains(entry.getKey()))hasReversible=true;else hasRigid=true;
                if (opposite(component.speed, candidate)) component.invalid = true;
                if (Math.abs(candidate) > Math.abs(component.speed)) component.speed = candidate;
            }
            component.onlyReversibleSources=hasReversible&&!hasRigid;
        }
        for (var controller : controllers) {
            var shaft = membership.get(controller.shaft()); var cog = membership.get(controller.cog());
            if (shaft == null || cog == null) throw new IllegalArgumentException("Controller endpoint missing");
            if (controller.target() == 0) continue;
            shaft.connections.add(controller);
            if (shaft != cog) cog.connections.add(controller);
        }
        // Rigid generators establish direction before reversible steam sources. Otherwise source
        // insertion order decides ownership, as in propagation; a driven side is never a generator.
        var pending = new ArrayDeque<Component<K>>(); var queued = new HashSet<Component<K>>();
        for (boolean flexible : new boolean[]{false,true})for (var component : components) if (component.speed != 0&&component.onlyReversibleSources==flexible) { pending.add(component); queued.add(component); }
        var connected = new LinkedHashSet<Controller<K>>();
        long steps = 0, limit = Math.max(64L, (long)(rigid.size() + controllers.size()) * Math.max(4, controllers.size() + 1) * 4);
        while (!pending.isEmpty()) {
            var from = pending.remove(); queued.remove(from);
            if (++steps > limit) {
                for (var connection : connected) { membership.get(connection.shaft()).invalid = true; membership.get(connection.cog()).invalid = true; }
                break;
            }
            for (var connection : from.connections) {
                var shaft = membership.get(connection.shaft()); var cog = membership.get(connection.cog());
                for (int direction = 0; direction < (shaft == cog ? 2 : 1); direction++) {
                    boolean towardShaft = shaft == cog ? direction == 0 : from == cog;
                    var to = towardShaft ? shaft : cog;
                    K targetNode = towardShaft ? connection.shaft() : connection.cog();
                    double shaftSpeed = shaft.speedAt(connection.shaft()), cogSpeed = cog.speedAt(connection.cog());
                    double desired;
                    if (towardShaft) {
                        if (cogSpeed == 0) continue;
                        desired = shaft.speed == 0 || shaft.owner == connection || (shaft.onlyReversibleSources && !from.onlyReversibleSources) ? connection.target() : shaftSpeed;
                    } else {
                        if (shaftSpeed == 0) continue;
                        desired = shaft.owner == connection ? cogSpeed : connection.target();
                    }
                    double existing = to.speedAt(targetNode);
                    if(opposite(desired,existing)&&to.onlyReversibleSources&&(to.owner==null||to.owner==connection)){
                        to.speed=-to.speed;existing=-existing;
                        for(var node:to.ratios.keySet())if(reversibleSources.contains(node)&&workingSources.getOrDefault(node,0d)!=0){double value=-workingSources.get(node);workingSources.put(node,value);onSourceReversed.accept(node,value);}
                        if(queued.add(to))pending.add(to);
                    }
                    // Upstream getConveyedSpeed retains a faster independently driven destination.
                    double candidate = desired >= 0 && existing >= 0 ? Math.max(desired, existing)
                        : desired < 0 && existing < 0 ? Math.min(desired, existing) : desired;
                    if (candidate == 0) continue;
                    if (opposite(candidate, existing)) { from.invalid = to.invalid = true; connected.add(connection); continue; }
                    boolean faster = Math.abs(candidate) > Math.abs(existing) + Math.max(1e-6, Math.abs(existing) / 65536);
                    if (faster) {
                        if (existing != 0 && from.root() == to.root() && to.owner != connection) {
                            from.invalid = to.invalid = true;
                        } else {
                            to.speed = candidate / to.ratios.get(targetNode); to.owner = connection;
                            if (queued.add(to)) pending.add(to);
                        }
                    }
                    if (shaft.speed != 0 && cog.speed != 0) {
                        connected.add(connection);
                        var a = shaft.root(); var b = cog.root(); if (a != b) b.parent = a;
                    }
                }
            }
        }
        var graph = new LinkedHashMap<K, List<KineticGraph.Edge<K>>>();
        rigid.forEach((node, edges) -> graph.put(node, new ArrayList<>(edges)));
        var invalid = new HashSet<K>(invalidNodes);
        for (var component : components) if (component.invalid) invalid.addAll(component.ratios.keySet());
        for (var connection : connected) {
            var shaft = membership.get(connection.shaft()); var cog = membership.get(connection.cog());
            double from = shaft.speedAt(connection.shaft()), to = cog.speedAt(connection.cog());
            // No fabricated ratio for invalid endpoints: fail both attached components explicitly.
            double ratio=from==0||to==0?Double.NaN:to/from;
            if(!Double.isFinite(ratio)||ratio==0){invalid.addAll(shaft.ratios.keySet());invalid.addAll(cog.ratios.keySet());continue;}
            graph.get(connection.shaft()).add(new KineticGraph.Edge<>(connection.cog(), ratio));
            graph.get(connection.cog()).add(new KineticGraph.Edge<>(connection.shaft(), 1 / ratio));
        }
        return KineticGraph.solve(graph, workingSources, loads, invalid, maxRpm);
    }
    private static boolean opposite(double a, double b) { return a != 0 && b != 0 && Math.signum(a) != Math.signum(b); }
    private static boolean equal(double a, double b) { return Math.abs(a - b) <= 1e-6 * Math.max(1, Math.max(Math.abs(a), Math.abs(b))); }
    private ControlledKineticGraph() {}
}

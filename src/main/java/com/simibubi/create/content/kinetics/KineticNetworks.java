package com.simibubi.create.content.kinetics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import com.simibubi.create.content.kinetics.transmission.GearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.ClutchBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;

/** Owns only loaded block entities; never loads chunks while discovering connections. */
public final class KineticNetworks {
    private static final Map<ServerLevel, LoadedKinetics> LEVELS = new IdentityHashMap<>();
    public static final int MAX_ROTATION_SPEED = 256;
    public static int maxRotationSpeed(){return com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get();}

    private record Node(BlockPos pos, Direction port) {}

    private static List<Direction> ports(BlockState state) {
        if(state.getBlock() instanceof SequencedGearshiftBlock shaft){
            return List.of(Direction.get(Direction.AxisDirection.NEGATIVE,shaft.getRotationAxis(state)),Direction.get(Direction.AxisDirection.POSITIVE,shaft.getRotationAxis(state)));
        }
        if (state.getBlock() instanceof GearshiftBlock shaft) {
            return List.of(Direction.get(Direction.AxisDirection.NEGATIVE, shaft.getRotationAxis(state)),
                    Direction.get(Direction.AxisDirection.POSITIVE, shaft.getRotationAxis(state)));
        }
        if (state.getBlock() instanceof GearboxBlock box) {
            return java.util.Arrays.stream(Direction.values()).filter(face -> face.getAxis() != box.getRotationAxis(state)).toList();
        }
        return List.of();
    }

    private static List<Direction> ports(KineticBlockEntity owner) {
        if(owner instanceof com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity
                && owner.getBlockState().getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate block) {
            var axis=block.getRotationAxis(owner.getBlockState());
            return List.of(Direction.get(Direction.AxisDirection.NEGATIVE,axis),Direction.get(Direction.AxisDirection.POSITIVE,axis));
        }
        return ports(owner.getBlockState());
    }
    private static Node node(KineticBlockEntity owner,Direction face) {
        return new Node(owner.getBlockPos(),ports(owner).isEmpty()?null:face);
    }

    private static Node referenceNode(KineticBlockEntity owner) {
        var ports=ports(owner);
        if(ports.isEmpty()) return new Node(owner.getBlockPos(),null);
        var input=owner.getKineticSourceFace();
        return new Node(owner.getBlockPos(),input!=null&&ports.contains(input)?input:ports.getFirst());
    }

    private static final class LoadedKinetics {
        final Map<BlockPos, KineticBlockEntity> members = new LinkedHashMap<>();
        final Map<BlockPos,Double> sourceSpeeds=new HashMap<>();
        // Topology and retry bookkeeping only; genuine KineticNetwork maps own all stress.
        final java.util.Set<KineticBlockEntity> pendingDestruction = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        boolean dirty = true;
    }

    public static boolean isLive(ServerLevel level, KineticBlockEntity entity) {
        return entity.getLevel()==level && !entity.isRemoved() && level.isLoaded(entity.getBlockPos())
            && com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,entity.getBlockPos())==entity;
    }

    public static void generatorChanged(ServerLevel level, KineticBlockEntity entity) {
        if (entity.isVirtual() || !isLive(level,entity)) return;
        track(level,entity);
        LEVELS.get(level).sourceSpeeds.put(entity.getBlockPos(),entity.getDirectionalGeneratedSpeed());
        LEVELS.get(level).dirty=true;
    }

    public static void notifyCapacityChange(ServerLevel level, KineticBlockEntity entity, float capacity) {
        if (!isLive(level,entity) || entity.isVirtual()) return;
        var group=entity.getOrCreateNetwork();
        if(group!=null) group.updateCapacityFor(entity,capacity);
    }

    public static void track(ServerLevel level, KineticBlockEntity entity) {
        if (entity.isVirtual() || !isLive(level,entity)) return;
        LoadedKinetics network = LEVELS.computeIfAbsent(level, ignored -> new LoadedKinetics());
        if(entity.isConflictDestructionPending() && network.pendingDestruction.add(entity)) network.dirty=true;
        if (network.members.put(entity.getBlockPos(), entity) != entity) {
            network.sourceSpeeds.remove(entity.getBlockPos());
            network.dirty = true;
        }
    }

    /** Source movement/reset clears the same canonical membership and rechecks the actual provider. */
    public static void kineticInformationCleared(ServerLevel level,KineticBlockEntity entity) {
        if(entity.isVirtual() || !isLive(level,entity)) return;
        if(entity.hasNetwork()) entity.getOrCreateNetwork().unlinkForRebind(entity);
        var index=LEVELS.get(level);
        if(index!=null && index.members.get(entity.getBlockPos())==entity) {
            index.sourceSpeeds.remove(entity.getBlockPos());index.dirty=true;
        }
    }

    public static void remove(ServerLevel level, KineticBlockEntity entity) {
        var index=LEVELS.get(level);
        if(index!=null && index.members.remove(entity.getBlockPos(),entity)) {
            index.pendingDestruction.remove(entity);
            if(entity.hasNetwork()) {
                var group=entity.getOrCreateNetwork();
                if(entity.isChunkUnloaded()) group.unload(entity); else group.unlinkForRebind(entity);
            }
            index.dirty=true;
        }
    }

    public static void markDirty(ServerLevel level) {
        LoadedKinetics network = LEVELS.get(level);
        if (network != null) {
            network.dirty = true;
        }
    }

    /** Config providers replace the source parameters for all actual loaded graph members. */
    public static void refreshStressProviders(ServerLevel level) {
        var network=LEVELS.get(level);
        if(network==null) return;
        for(var group:TorquePropagator.loadedNetworks(level)) {
            for(var member:java.util.List.copyOf(group.members.keySet()))
                if(isLive(level,member)) group.members.put(member,member.calculateStressApplied());
            for(var source:java.util.List.copyOf(group.sources.keySet()))
                if(isLive(level,source)) group.sources.put(source,source.calculateAddedStressCapacity());
            group.updateNetwork();
            group.sync();
        }
        network.dirty=true;
    }

    public static void unload(ServerLevel level) {
        LEVELS.remove(level);
        com.simibubi.create.Create.TORQUE_PROPAGATOR.onUnloadWorld(level);
    }

    public static void tick(ServerLevel level) {
        LoadedKinetics network = LEVELS.get(level);
        if (network == null) {
            return;
        }
        for(var entity:java.util.List.copyOf(network.members.values())) {
            if(isLive(level,entity)) continue;
            network.members.remove(entity.getBlockPos(),entity);
            network.pendingDestruction.remove(entity);
            if(entity.hasNetwork()) {
                var group=entity.getOrCreateNetwork();
                if(entity.isChunkUnloaded() || !level.isLoaded(entity.getBlockPos())) group.unload(entity);
                else group.unlinkForRebind(entity);
            }
            network.dirty=true;
        }
        for(var entity:java.util.List.copyOf(network.pendingDestruction)) {
            if(tryDestroyForConflict(level,entity)) { network.pendingDestruction.remove(entity); network.dirty=true; }
        }
        network.sourceSpeeds.keySet().retainAll(network.members.keySet());
        for(var entity:java.util.List.copyOf(network.members.values())) {
            double generated=entity.getDirectionalGeneratedSpeed();
            var previous=network.sourceSpeeds.put(entity.getBlockPos(),generated);
            if(previous==null || Double.compare(previous,generated)!=0) {
                network.dirty=true;
                if(entity instanceof com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity generator
                        && !network.pendingDestruction.contains(entity)) generator.updateGeneratedRotation();
            }
        }
        // Plain public map.put/entry.setValue/map replacement is authoritative even without a topology edit.
        for(var group:TorquePropagator.loadedNetworks(level)) group.updateNetwork();
        if(!network.dirty) return;
        discoverLoadedOwners(level,network);
        for(var entity:java.util.List.copyOf(network.members.values()))
            if(isLive(level,entity) && !network.pendingDestruction.contains(entity)) entity.attachKinetics();
        network.dirty=false;
        Map<Node, List<KineticGraph.Edge<Node>>> graph = new LinkedHashMap<>();
        Map<Node, Double> sources = new HashMap<>();
        Map<Node, KineticGraph.Load> loads = new HashMap<>();
        for (var entry : network.members.entrySet()) {
            BlockState state = entry.getValue().getBlockState();
            List<Direction> ports = ports(entry.getValue());
            if (ports.isEmpty()) {
                Node body = new Node(entry.getKey(), null);
                graph.put(body, new ArrayList<>());
                sources.put(body, kinematicInput(entry.getValue()));
                loads.put(body, KineticGraph.Load.NONE);
                continue;
            }
            for (Direction face : ports) {
                Node from = new Node(entry.getKey(), face);
                var edges = new ArrayList<KineticGraph.Edge<Node>>();
                for (Direction target : ports) {
                    if (target == face || state.getBlock() instanceof SequencedGearshiftBlock || state.getBlock() instanceof ClutchBlock && state.getValue(GearshiftBlock.POWERED)) {
                        continue;
                    }
                    double ratio;
                    if(entry.getValue() instanceof com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity split) {
                        double fromModifier=split.getRotationSpeedModifier(face);
                        double toModifier=split.getRotationSpeedModifier(target);
                        if(fromModifier==0 || toModifier==0) continue;
                        ratio=toModifier/fromModifier;
                    } else ratio = state.getBlock() instanceof GearboxBlock box
                            ? box.getPortModifier(state, target) / box.getPortModifier(state, face)
                            : state.getValue(GearshiftBlock.POWERED) ? -1 : 1;
                    edges.add(new KineticGraph.Edge<>(new Node(entry.getKey(), target), ratio));
                }
                graph.put(from, edges);
            }
            double boundaryInput=kinematicInput(entry.getValue());
            if(boundaryInput!=0) {
                var input=entry.getValue().getKineticSourceFace();
                if(input==null || !ports.contains(input)) input=ports.getFirst();
                sources.put(new Node(entry.getKey(),input),boundaryInput);
            }
        }
        for (var entry : network.members.entrySet()) {
            BlockPos pos = entry.getKey();
            KineticBlockEntity entity = entry.getValue();
            for(var otherPos:RotationPropagator.getPotentialNeighbourLocations(entity)) {
                var other=network.members.get(otherPos);
                if(other==null) continue;
                var connection=RotationPropagator.getGraphConnection(entity,other);
                double ratio=connection.ratio();
                if(!Double.isFinite(ratio)||ratio==0) continue;
                var face=Direction.getNearest(otherPos.subtract(pos),Direction.NORTH);
                // Source custom factors replace integrated shaft rules and describe BE theoretical
                // speeds. Their edge therefore joins actual input references, including a closed split.
                var fromNode=connection.custom()?referenceNode(entity):node(entity,face);
                var toNode=connection.custom()?referenceNode(other):node(other,face.getOpposite());
                if(graph.containsKey(fromNode)&&graph.containsKey(toNode)) graph.get(fromNode).add(new KineticGraph.Edge<>(toNode,ratio));
            }
        }
        var controllers=new ArrayList<ControlledKineticGraph.Controller<Node>>();
        for(var entry:network.members.entrySet())if(entry.getValue() instanceof com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity controller){
            var cog=network.members.get(entry.getKey().above());
            if(cog!=null&&com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock.connectsToCog(controller.getBlockState(),cog.getBlockState()))controllers.add(new ControlledKineticGraph.Controller<>(new Node(entry.getKey(),null),new Node(cog.getBlockPos(),null),controller.getTargetSpeed()));
        }
        var reversible=new java.util.HashSet<Node>();for(var entry:network.members.entrySet())if(entry.getValue() instanceof com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity)reversible.add(new Node(entry.getKey(),null));
        var splits=new ArrayList<SequencedKineticGraph.Split<Node>>();var previousInputs=new HashMap<Node,Node>();
        for(var entry:network.members.entrySet())if(entry.getValue() instanceof SequencedGearshiftBlockEntity sequencer){
            var faces=ports(sequencer);var negative=new Node(entry.getKey(),faces.getFirst());var positive=new Node(entry.getKey(),faces.getLast());
            splits.add(new SequencedKineticGraph.Split<>(negative,positive,sequencer.getModifier()));
            if(sequencer.getSourceFacing()!=null&&faces.contains(sequencer.getSourceFacing()))previousInputs.put(negative,new Node(entry.getKey(),sequencer.getSourceFacing()));
        }
        var solved=SequencedKineticGraph.solve(graph,(candidate,commit)->{
            var aligned=new HashMap<>(sources);alignSteamEngines(network,candidate,aligned,commit);return aligned;
        },loads,controllers,splits,previousInputs,reversible,(node,speed)->{
            ((com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity)network.members.get(node.pos())).followNetworkDirection(speed>0?1:-1);
        },(float)maxRotationSpeed());
        var result=solved.kinetics();
        var frozen=new java.util.HashSet<Node>();
        var pendingNodes=new java.util.ArrayDeque<Node>();
        for(var node:graph.keySet()) if(network.pendingDestruction.stream().anyMatch(owner->owner.getBlockPos().equals(node.pos))) {
            frozen.add(node); pendingNodes.add(node);
        }
        while(!pendingNodes.isEmpty()) for(var edge:graph.getOrDefault(pendingNodes.remove(),List.of()))
            if(frozen.add(edge.target())) pendingNodes.add(edge.target());
        var owners=java.util.List.copyOf(network.members.values());
        for(var owner:owners) owner.beginNetworkUpdate();
        var primaryNodes=new HashMap<BlockPos,Node>();
        try {
        for (var entry : network.members.entrySet()) {
            BlockState state = entry.getValue().getBlockState();
            Node primary = new Node(entry.getKey(), null);
            var portSpeeds = new EnumMap<Direction, Float>(Direction.class);
            for (Direction port : ports(entry.getValue())) {
                Node candidate = new Node(entry.getKey(), port);
                portSpeeds.put(port, result.theoreticalSpeeds().getOrDefault(candidate, 0.0).floatValue());
                if (!graph.containsKey(primary) || Math.abs(result.theoreticalSpeeds().getOrDefault(candidate, 0.0))
                        > Math.abs(result.theoreticalSpeeds().getOrDefault(primary, 0.0))) {
                    primary = candidate;
                }
            }
            // Source KBE.speed is the actual input speed, rather than an arbitrary equal-magnitude output port.
            Node inputPort=null;
            for(var port:ports(entry.getValue())) {
                var candidate=new Node(entry.getKey(),port);var parent=result.parents().get(candidate);
                if(parent!=null && !parent.pos.equals(entry.getKey())
                        && (inputPort==null || result.depths().getOrDefault(candidate,Integer.MAX_VALUE)
                            <result.depths().getOrDefault(inputPort,Integer.MAX_VALUE))) inputPort=candidate;
                if(generatorIsThisPort(result,candidate,entry.getKey())) inputPort=candidate;
            }
            if(inputPort!=null) primary=inputPort;
            if(entry.getValue() instanceof SequencedGearshiftBlockEntity){
                var negative=new Node(entry.getKey(),ports(entry.getValue()).getFirst());primary=solved.inputs().getOrDefault(negative,negative);
            }
            primaryNodes.put(entry.getKey(),primary);
            var generator=result.generators().get(primary);var upstream=result.parents().get(primary);var guard=new java.util.HashSet<Node>();
            while(upstream!=null&&upstream.pos.equals(entry.getKey())&&guard.add(upstream))upstream=result.parents().get(upstream);
            Direction sourceFace=upstream==null?null:Direction.getNearest(upstream.pos.subtract(entry.getKey()),Direction.NORTH);
            if(entry.getValue() instanceof SequencedGearshiftBlockEntity&&primary.port!=null)sourceFace=primary.port;
            var owner=entry.getValue();
            boolean conflict=result.conflicted().contains(primary)||frozen.contains(primary);
            if(!conflict) {
                var root=generator==null?null:network.members.get(generator.pos);
                Long id=root==null?null:root.network;
                if(root!=null && id==null) id=root instanceof com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity generated
                    ? generated.createNetworkId():root.getBlockPos().asLong();
                rebindNetwork(owner,id);
                // A cached unloaded upstream is a source boundary, not a new generator cell.
                if(!(root==owner && owner.hasSource() && !level.isLoaded(owner.getSource())))
                    owner.setKineticProvenance(root!=null && root.hasSource() && !level.isLoaded(root.getSource())
                        && root.getGeneratingSource()!=null?root.getGeneratingSource():generator==null?null:generator.pos,
                        upstream==null?null:upstream.pos,sourceFace);
            }
            float theoretical=conflict?owner.getTheoreticalSpeed():result.theoreticalSpeeds().getOrDefault(primary,0.0).floatValue();
            var totals=new KineticGraph.Stress(owner.getStressCapacity(),owner.getStressImpact(),owner.isOverStressed());
            owner.setNetworkState(owner.appliedSpeedFor(theoretical,conflict,totals.overstressed()),theoretical,
                conflict,totals,conflict?owner.getTheoreticalPortSpeeds():portSpeeds);
        }
        for(var group:TorquePropagator.loadedNetworks(level)) group.updateNetwork();
        // Notify actual gauge owners using the canonical source totals after all members have their theoretical RPM.
        for(var owner:owners) {
            if(!owner.hasNetwork()) owner.updateFromNetwork(0,0,0);
            owner.setNetworkState(owner.appliedSpeedFor(owner.getTheoreticalSpeed(),owner.isConflicted(),owner.isOverStressed()),owner.getTheoreticalSpeed(),owner.isConflicted(),
                new KineticGraph.Stress(owner.getStressCapacity(),owner.getStressImpact(),owner.isOverStressed()),owner.getTheoreticalPortSpeeds());
        }
        } finally { for(var owner:owners) owner.endNetworkUpdate(); }
        // Callbacks above can abort a stopped sequence. Read contexts only after all speed snapshots
        // have been applied; never restore a stale context captured before onSpeedChanged.
        var flows=sequenceFlows(network,result,solved.inputs());
        for(var entry:network.members.entrySet())if(!(entry.getValue() instanceof SequencedGearshiftBlockEntity) && !(entry.getValue() instanceof com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity)){
            if(entry.getValue().isConflicted()) continue;
            var flow=flows.get(primaryNodes.get(entry.getKey()));
            entry.getValue().applySequenceContext(flow==null?null:flow.context,flow==null?null:flow.phase);
        }
    }
    private static boolean generatorIsThisPort(KineticGraph.Result<Node> result,Node candidate,BlockPos owner) {
        var generator=result.generators().get(candidate);
        return candidate.equals(generator)&&generator.pos.equals(owner);
    }
    private static void discoverLoadedOwners(ServerLevel level,LoadedKinetics index) {
        var queue=new java.util.ArrayDeque<>(index.members.values());
        var seen=new java.util.HashSet<>(index.members.keySet());
        while(!queue.isEmpty()) {
            var current=queue.remove();
            for(var other:RotationPropagator.getConnectedNeighbours(current)) {
                if(!seen.add(other.getBlockPos())||!isLive(level,other)) continue;
                track(level,other);queue.add(other);
            }
        }
    }
    private static double kinematicInput(KineticBlockEntity be) {
        if(be.hasSource()) {
            return !com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(be.getLevel(),be.getSource())
                ? be.getTheoreticalSpeed():0;
        }
        return be.isSource()?be.getTheoreticalSpeed():0;
    }
    private static void rebindNetwork(KineticBlockEntity owner,Long id) {
        if(java.util.Objects.equals(owner.network,id)) return;
        if(owner.hasNetwork()) owner.getOrCreateNetwork().unlinkForRebind(owner);
        owner.network=id;
        if(id!=null) {
            var group=owner.getOrCreateNetwork();
            group.initialized=true;group.linkForRebind(owner);
        }
        owner.setChanged();
    }

    /** Real source conflict teardown: restore paid cargo before the native block/drop lifecycle. */
    public static boolean destroyForConflict(KineticBlockEntity owner) {
        if(!(owner.getLevel() instanceof ServerLevel level)||owner.isVirtual()||!isLive(level,owner)) return false;
        track(level,owner);
        if(tryDestroyForConflict(level,owner)) return true;
        owner.setConflictDestructionPending(true);
        var index=LEVELS.get(level);index.pendingDestruction.add(owner);index.dirty=true;
        var queue=new java.util.ArrayDeque<KineticBlockEntity>();
        var seen=java.util.Collections.newSetFromMap(new IdentityHashMap<KineticBlockEntity,Boolean>());
        queue.add(owner);seen.add(owner);
        while(!queue.isEmpty()) {
            var current=queue.remove();
            current.setNetworkState(0,current.getTheoreticalSpeed(),true,
                new KineticGraph.Stress(current.getStressCapacity(),current.getStressImpact(),current.isOverStressed()),current.getTheoreticalPortSpeeds());
            for(var next:RotationPropagator.getConnectedNeighbours(current)) if(seen.add(next)) queue.add(next);
        }
        return false;
    }
    private static boolean tryDestroyForConflict(ServerLevel level,KineticBlockEntity owner) {
        if(!isLive(level,owner)) return true;
        boolean restored=true;
        if(owner instanceof com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity bearing)
            restored=bearing.disassembleForMovement();
        else if(owner instanceof com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity bearing)
            restored=bearing.disassembleForMovement();
        else if(owner instanceof com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity pulley)
            restored=pulley.disassembleForMovement();
        else if(owner instanceof com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity piston)
            restored=piston.canDisassembleNow()&&piston.disassemble();
        if(!restored) return false;
        // Restoration can publish real blocks and invoke arbitrary callbacks; recheck this exact owner.
        if(!isLive(level,owner)) return true;
        boolean destroyed=level.destroyBlock(owner.getBlockPos(),true);
        if(destroyed) owner.setConflictDestructionPending(false);
        return destroyed;
    }

    private record SequenceFlow(SequencedGearshiftBlockEntity.SequenceContext context,SequencePhase phase){}
    private static Map<Node,SequenceFlow> sequenceFlows(LoadedKinetics network,KineticGraph.Result<Node> result,Map<Node,Node> inputs){
        var flows=new HashMap<Node,SequenceFlow>();
        var ordered=new ArrayList<>(result.depths().keySet());ordered.sort(java.util.Comparator.comparingInt(node->result.depths().get(node)));
        for(var node:ordered){
            var parent=result.parents().get(node);var flow=parent==null?null:flows.get(parent);
            var owner=network.members.get(node.pos);
            if(parent==null && owner.hasSource()
                    && !com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(owner.getLevel(),owner.getSource())
                    && owner.sequenceContext!=null && owner.getSequencePhase()!=null)
                flow=new SequenceFlow(owner.sequenceContext,owner.getSequencePhase());
            if(owner instanceof SequencedGearshiftBlockEntity sequencer){
                var negative=new Node(node.pos,ports(sequencer).getFirst());var input=inputs.get(negative);
                if(input!=null&&!node.equals(input)&&input.equals(parent)){
                    var context=sequencer.getOwnSequenceContext();var phase=sequencer.getSequencePhase();flow=context==null||phase==null?null:new SequenceFlow(context,phase);
                }
            }
            if (network.members.get(node.pos) instanceof com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity valve
                    && parent == null && valve.getDirectionalGeneratedSpeed() != 0) {
                var context = valve.getOwnSequenceContext(); var phase = valve.getSequencePhase();
                flow = context == null || phase == null ? null : new SequenceFlow(context, phase);
            }
            if(flow!=null)flows.put(node,flow);
        }return flows;
    }

    /** Engines on a driven shaft follow the existing network direction; rigid source conflicts still use the solver. */
    private static void alignSteamEngines(LoadedKinetics network,Map<Node,List<KineticGraph.Edge<Node>>> graph,Map<Node,Double> sources,boolean commit){
        if(network.members.values().stream().noneMatch(entity->entity instanceof com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity))return;
        var visited=new java.util.HashSet<Node>();
        for(var root:graph.keySet()){
            if(!visited.add(root))continue;
            var ratios=new LinkedHashMap<Node,Double>();ratios.put(root,1.0);var queue=new java.util.ArrayDeque<Node>();queue.add(root);
            while(!queue.isEmpty()){
                var current=queue.remove();
                for(var edge:graph.get(current))if(!ratios.containsKey(edge.target())){
                    double ratio=ratios.get(current)*edge.ratio();if(!Double.isFinite(ratio)||ratio==0)continue;
                    ratios.put(edge.target(),ratio);visited.add(edge.target());queue.add(edge.target());
                }
            }
            double rigid=0,flexible=0;
            for(var entry:ratios.entrySet()){
                double generated=sources.getOrDefault(entry.getKey(),0.0);if(generated==0)continue;double candidate=generated/entry.getValue();
                if(network.members.get(entry.getKey().pos()) instanceof com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity){if(Math.abs(candidate)>Math.abs(flexible))flexible=candidate;}
                else if(Math.abs(candidate)>Math.abs(rigid))rigid=candidate;
            }
            double direction=rigid!=0?rigid:flexible;if(direction==0)continue;
            for(var entry:ratios.entrySet())if(network.members.get(entry.getKey().pos()) instanceof com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity shaft){
                double generated=sources.getOrDefault(entry.getKey(),0.0);if(generated==0)continue;
                double aligned=Math.copySign(Math.abs(generated),direction*entry.getValue());
                if(generated!=aligned){if(commit)shaft.followNetworkDirection(aligned>0?1:-1);sources.put(entry.getKey(),aligned);}
            }
        }
    }

    private KineticNetworks() {}
}

package com.simibubi.create.content.contraptions;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.bearing.StabilizedContraption;
import com.simibubi.create.content.contraptions.behaviour.dispenser.*;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Native owners form the graph. This coordinator never contains a second serialized cargo copy. */
public final class ContraptionAssemblyGraph {
    public record Result(boolean complete, AssemblyException failure) {}
    public record Root(AbstractContraptionEntity owner, Contraption structure,BlockPos removalOffset) {public Root(AbstractContraptionEntity owner,Contraption structure){this(owner,structure,BlockPos.ZERO);}public Root{removalOffset=removalOffset.immutable();}}
    @FunctionalInterface public interface ReservationFactory { OrientedContraptionEntity create(ServerLevel level, StabilizedContraption structure); }
    private record Node(AbstractContraptionEntity owner, Contraption structure, Node parent, StabilizedSubContraptions.Location location,BlockPos offset) {}
    private record Restoration(AbstractContraptionEntity owner, StructureTransform transform) {}
    public record RestorationRoot(AbstractContraptionEntity owner,StructureTransform transform){}
    @FunctionalInterface private interface Checked<T>{T run()throws AssemblyException;}
    private static final ThreadLocal<TransferScope> TRANSFERS=new ThreadLocal<>();
    private static final class TransferScope implements AutoCloseable{
        private final TransferScope previous=TRANSFERS.get();private final Set<Contraption> structures=Collections.newSetFromMap(new IdentityHashMap<>());private Contraption permitted;
        TransferScope(Collection<Contraption> structures){this.structures.addAll(structures);TRANSFERS.set(this);}
        <T>T allow(Contraption structure,Checked<T> operation)throws AssemblyException{var old=permitted;permitted=structure;try{return operation.run();}finally{permitted=old;}}
        @Override public void close(){TRANSFERS.set(previous);}
    }
    public static boolean isTransferPermitted(Contraption structure){for(var scope=TRANSFERS.get();scope!=null;scope=scope.previous)if(scope.structures.contains(structure)&&scope.permitted!=structure)return false;return true;}
    private static final int MAX_DEPTH = 64, MAX_OWNERS = 1024;

    /** All supplied roots must already be accepted empty native reservations. */
    public static Result assemble(ServerLevel level, List<Root> roots, Consumer<Boolean> bindControllers) throws AssemblyException {
        return assemble(level, roots, bindControllers, (server, structure) -> new OrientedContraptionEntity(AllEntityTypes.ORIENTED_CONTRAPTION, server));
    }
    public static Result assemble(ServerLevel level, List<Root> roots, Consumer<Boolean> bindControllers, ReservationFactory factory) throws AssemblyException {
        if (roots.isEmpty()) throw new IllegalArgumentException("Empty ownership forest");
        var nodes = new ArrayList<Node>(); var claimed = new HashSet<BlockPos>(); var children = new ArrayList<OrientedContraptionEntity>();
        for (var root : roots) {
            requireReservation(level, root.owner());
            if (root.owner().getContraption() != null) throw new IllegalArgumentException("Root reservation already bound");
            root.structure().validateSourceBlocks(level, root.removalOffset()); claim(root.structure(),root.removalOffset(), claimed); nodes.add(new Node(root.owner(), root.structure(), null, null,root.removalOffset()));
        }
        boolean complete = false; AssemblyException failure = null;Throwable unexpected=null;TransferScope scope=null;
        try {
            for (int index = 0; index < nodes.size(); index++) {
                var parent = nodes.get(index); if (depth(parent) > MAX_DEPTH || nodes.size() > MAX_OWNERS) throw AssemblyException.structureTooLarge();
                for (var location : parent.structure().getStabilizedSubContraptions().pending()) {
                    var bearing = parent.structure().anchor.offset(location.bearing()).offset(parent.offset()); var front = bearing.relative(location.facing());
                    if (claimed.contains(front)) continue;
                    var structure = new StabilizedContraption(location.facing(), claimed);
                    if (!structure.assemble(level, bearing)) continue;
                    structure.validateSourceBlocks(level, BlockPos.ZERO); claim(structure,BlockPos.ZERO, claimed);
                    var child = factory.create(level, structure); child.setInitialOrientation(location.facing()); child.setParent(parent.owner().getUUID(), location); child.setPos(Vec3.atLowerCornerOf(structure.anchor).add(.5, 0, .5));
                    children.add(child);
                    if (!level.addFreshEntity(child)) throw AssemblyException.structureChanged(bearing);
                    requireReservation(level, child); nodes.add(new Node(child, structure, parent, location,BlockPos.ZERO));
                }
            }
            scope=new TransferScope(nodes.stream().map(Node::structure).toList());
            for (var node : nodes) {
                requireReservation(level, node.owner());scope.allow(node.structure(),()->{node.structure().validateSourceBlocks(level,node.offset());return null;});
                ContraptionDataPacket.split(0, node.owner().getUUID(), true, node.structure().writeNBT(level.registryAccess(), true));
            }
            // No native neighbor callbacks occur until every successfully removed node has an owner.
            for (var node : nodes) if (!scope.allow(node.structure(),()->node.structure().tryRemoveBlocksFromWorld(level,node.offset(),false))) throw AssemblyException.structureChanged(node.structure().anchor);
            complete = true;
        } catch (AssemblyException refused) { failure = refused; }
        catch (RuntimeException|Error failed) { unexpected=failed; }
        finally {
            try {
                var binding=bindRemovedForest(level,roots,nodes,children,bindControllers,complete,unexpected);
                complete=binding.complete();unexpected=binding.unexpected();
            } finally { if(scope!=null)scope.close(); }
        }
        if(unexpected instanceof RuntimeException runtime)throw runtime;
        if(unexpected instanceof Error error)throw error;
        if (!complete && failure == null) failure = AssemblyException.entitiesNotReady(roots.getFirst().structure().anchor);
        return new Result(complete, failure);
    }
    private record BindingResult(boolean complete,Throwable unexpected){}
    private static BindingResult bindRemovedForest(ServerLevel level,List<Root> roots,List<Node> nodes,
        List<OrientedContraptionEntity> children,Consumer<Boolean> bindControllers,boolean complete,Throwable unexpected){
        boolean anyOwned=false;
        try{
            // Pointer writes only: even the first actor/bounds callback must see every actual owner.
            for(var node:nodes)if(node.structure().phase()==Contraption.Phase.REMOVED){
                anyOwned=true;node.owner().bindRemovedContraptionPure(node.structure());
            }
            for(var node:nodes)if(node.structure().phase()==Contraption.Phase.REMOVED&&node.parent()!=null)
                node.parent().structure().getStabilizedSubContraptions().remember(node.owner().getUUID(),node.location());
            if(anyOwned){
                setRecovery(roots,!complete);
                // Each real native consumer records all of its UUIDs before pose/sync callbacks.
                // Invoke once; source completion side effects run after the successful result.
                bindControllers.accept(complete);
                if(unexpected==null){
                    for(var node:nodes)if(node.structure().phase()==Contraption.Phase.REMOVED)
                        node.owner().setContraption(node.structure());
                    for(var node:nodes)if(node.structure().phase()==Contraption.Phase.REMOVED&&node.parent()!=null)
                        if(!node.owner().startRiding(node.parent().owner(),true,true))complete=false;
                    // A cart consumer can independently request recovery when its real mount refuses.
                    for(var root:roots)if(root.structure().phase()==Contraption.Phase.REMOVED
                        &&root.structure().getStabilizedSubContraptions().isRecovering())complete=false;
                    if(!complete)setRecovery(roots,true);
                    for(var node:nodes)if(node.structure().phase()==Contraption.Phase.REMOVED)
                        for(var player:PlayerLookup.tracking(node.owner()))ContraptionNetworking.sendFull(node.owner(),player);
                    for(var node:nodes)if(node.structure().phase()==Contraption.Phase.REMOVED)
                        node.structure().notifyStructureNeighbors(level,new StructureTransform(node.structure().anchor.offset(node.offset()),0,0,0));
                }else complete=false;
            }
        }catch(RuntimeException|Error failed){complete=false;if(unexpected==null)unexpected=failed;else if(unexpected!=failed)unexpected.addSuppressed(failed);}
        finally{
            for(var child:children)if(child.getContraption()==null)try{child.discard();}
                catch(RuntimeException|Error failed){complete=false;if(unexpected==null)unexpected=failed;else if(unexpected!=failed)unexpected.addSuppressed(failed);}
            if(anyOwned&&(!complete||unexpected!=null))setRecovery(roots,true);
        }
        return new BindingResult(complete,unexpected);
    }
    private static void setRecovery(List<Root> roots,boolean recovering){
        for(var root:roots)if(root.structure().phase()==Contraption.Phase.REMOVED)
            root.structure().getStabilizedSubContraptions().setRecovering(recovering);
    }
    private static void claim(Contraption structure,BlockPos offset, Set<BlockPos> claimed) {
        for (var local : structure.getBlocks().keySet()) if (!claimed.add(structure.anchor.offset(local).offset(offset))) throw new IllegalArgumentException("Overlapping source ownership in contraption graph");
    }
    private static int depth(Node node) { int value = 0; for (var parent = node.parent(); parent != null; parent = parent.parent()) value++; return value; }
    private static void requireReservation(ServerLevel level, AbstractContraptionEntity owner) throws AssemblyException {
        if (owner.isRemoved() || level.getEntity(owner.getUUID()) != owner) throw AssemblyException.structureChanged(owner.blockPosition());
    }
    /** Missing saved children block restoration; no chunk tickets or broad entity scans are used. */
    private static void collect(ServerLevel level, AbstractContraptionEntity owner, StructureTransform transform, List<Restoration> result, Set<UUID> visited, int depth) throws AssemblyException {
        if (depth > MAX_DEPTH || !visited.add(owner.getUUID()) || visited.size() > MAX_OWNERS) throw AssemblyException.structureTooLarge();
        var structure = owner.getContraption();
        if (structure == null) throw AssemblyException.entitiesNotReady(owner.blockPosition());
        for (var entry : structure.getStabilizedSubContraptions().children().entrySet()) {
            var child = structure.getStabilizedSubContraptions().resolve(level, entry.getKey());
            collect(level, child, child.restorationAt(transform.apply(entry.getValue().connected())), result, visited, depth + 1);
        }
        result.add(new Restoration(owner, transform));
    }
    public static List<AbstractContraptionEntity> preflightTree(AbstractContraptionEntity root) throws AssemblyException {
        if (!(root.level() instanceof ServerLevel server)) throw new IllegalArgumentException("Server graph required");
        var nodes = new ArrayList<Restoration>(); collect(server, root, root.getRestorationTransform(), nodes, new HashSet<>(), 0);
        for(var node:nodes)if(!node.owner().hasOwnedStructure())throw AssemblyException.entitiesNotReady(node.owner().blockPosition());
        try(var scope=new TransferScope(nodes.stream().map(node->node.owner().getContraption()).toList())){preflight(server,nodes,scope);}
        return nodes.stream().map(Restoration::owner).toList();
    }
    private static void preflight(ServerLevel server,List<Restoration> nodes,TransferScope scope)throws AssemblyException{
        for(var node:nodes)for(var actor:node.owner().getContraption().getActors())
            if(actor.right!=null&&MovementBehaviour.REGISTRY.get(actor.left.state()) instanceof com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour
                &&!com.simibubi.create.content.kinetics.deployer.DeployerSchematicPrinter.prepareDisassembly(actor.right))
                throw AssemblyException.entitiesNotReady(node.transform().apply(actor.left.pos()));
        for(var node:nodes)scope.allow(node.owner().getContraption(),()->{node.owner().getContraption().validateRestoration(server,node.transform());return null;});
        for(var node:nodes)for(var actor:node.owner().getContraption().getActors())if(actor.right!=null&&MovementBehaviour.REGISTRY.get(actor.left.state()) instanceof DropperMovementBehaviour&&!MountedDispenseExecution.prepareDisassembly(actor.right,node.transform().apply(actor.left.pos())))throw AssemblyException.entitiesNotReady(node.owner().blockPosition());
    }
    /** A controller's durable journal holds already world-owned cells from any completed root/tree. */
    public static Result restoreForest(List<RestorationRoot> roots,Collection<BlockPos> journal,Consumer<AbstractContraptionEntity> rootCompleted){
        return restoreForest(roots,journal,rootCompleted,()->{});
    }
    public static Result restoreForest(List<RestorationRoot> roots,Collection<BlockPos> journal,Consumer<AbstractContraptionEntity> rootCompleted,Runnable beforePlacement){
        if(roots.isEmpty())return new Result(true,null);
        if(!(roots.getFirst().owner().level() instanceof ServerLevel server))return new Result(false,null);
        var locked=new ArrayList<StabilizedSubContraptions>();var nodes=new ArrayList<Restoration>();var rootSet=Collections.newSetFromMap(new IdentityHashMap<AbstractContraptionEntity,Boolean>());
        try{
            var visited=new HashSet<UUID>();
            for(var root:roots){if(!root.owner().isAlive()||!root.owner().hasOwnedStructure()||restoringAncestor(root.owner()))return new Result(false,null);var graph=root.owner().getContraption().getStabilizedSubContraptions();if(!graph.enterTransfer())return new Result(false,null);locked.add(graph);graph.setRecovering(true);holdAncestors(root.owner());rootSet.add(root.owner());collect(server,root.owner(),root.transform(),nodes,visited,0);}
            try(var scope=new TransferScope(nodes.stream().map(node->node.owner().getContraption()).toList())){
                preflight(server,nodes,scope);beforePlacement.run();int rootsRemaining=roots.size();
                for(var node:nodes){var owner=node.owner();var structure=owner.getContraption();
                    var oldJournal=structure.getStabilizedSubContraptions().restorationJournal();if(oldJournal!=journal)journal.addAll(oldJournal);
                    if(!scope.allow(structure,()->structure.tryAddBlocksToWorld(server,node.transform(),false)))return new Result(false,AssemblyException.entitiesNotReady(owner.blockPosition()));
                    journal.addAll(structure.restorationNotificationPositions(node.transform()));
                    if(rootSet.contains(owner)&&--rootsRemaining==0){DeferredContraptionRestoration.finishOrQueue(server,journal);journal.clear();}
                    completeEntity(owner,node.transform());if(rootSet.contains(owner))rootCompleted.accept(owner);
                }
            }
            return new Result(true,null);
        }catch(AssemblyException unavailable){return new Result(false,unavailable);}
        finally{for(var graph:locked)graph.leaveTransfer();}
    }
    /** Single-root journal remains on the real REMOVED root until its last placement succeeds. */
    public static boolean restore(AbstractContraptionEntity root) {
        if (!(root.level() instanceof ServerLevel server) || root.getContraption() == null || !root.isAlive() || restoringAncestor(root)) return false;
        if (root instanceof OrientedContraptionEntity child && child.getParentId() != null && (!(server.getEntity(child.getParentId()) instanceof AbstractContraptionEntity parent) || parent.getContraption() == null || !parent.getContraption().getStabilizedSubContraptions().accepts(child))) return false;
        var graph = root.getContraption().getStabilizedSubContraptions();
        return restoreForest(List.of(new RestorationRoot(root,root.getRestorationTransform())),graph.restorationJournal(),owner->{}).complete();
    }
    public static boolean isFrozen(AbstractContraptionEntity owner){
        return isRecovering(owner)||owner.level() instanceof ServerLevel server&&owner.getContraption().getStabilizedSubContraptions().hasStalledChild(server);
    }
    /** Actor work must continue through ordinary stalls, while every recovery ancestor holds motion. */
    public static boolean isRecovering(AbstractContraptionEntity owner){
        var cursor=owner;var visited=new HashSet<UUID>();
        while(true){
            if(!visited.add(cursor.getUUID())||visited.size()>MAX_DEPTH||!cursor.hasOwnedStructure())return true;
            var graph=cursor.getContraption().getStabilizedSubContraptions();if(graph.isRecovering()||graph.isTransferring())return true;
            if(!(cursor instanceof OrientedContraptionEntity child)||child.getParentId()==null)return false;
            var parent=loadedParent(cursor);if(parent==null||parent.getContraption()==null||!parent.getContraption().getStabilizedSubContraptions().accepts(child))return true;cursor=parent;
        }
    }
    private static AbstractContraptionEntity loadedParent(AbstractContraptionEntity owner){
        if(owner.getVehicle() instanceof AbstractContraptionEntity parent)return parent;
        return owner instanceof OrientedContraptionEntity child&&child.getParentId()!=null&&owner.level() instanceof ServerLevel server&&server.getEntity(child.getParentId()) instanceof AbstractContraptionEntity parent?parent:null;
    }
    private static void holdAncestors(AbstractContraptionEntity owner){var visited=new HashSet<UUID>();for(var parent=loadedParent(owner);parent!=null&&visited.add(parent.getUUID())&&visited.size()<=MAX_DEPTH;parent=loadedParent(parent))parent.setStalled(true);}
    private static boolean restoringAncestor(AbstractContraptionEntity owner) {
        var visited = new HashSet<UUID>(); AbstractContraptionEntity cursor = owner;
        while (true) {
            AbstractContraptionEntity parent = cursor.getVehicle() instanceof AbstractContraptionEntity vehicle ? vehicle : null;
            if (parent == null && cursor instanceof OrientedContraptionEntity child && child.getParentId() != null && cursor.level() instanceof ServerLevel level && level.getEntity(child.getParentId()) instanceof AbstractContraptionEntity resolved) parent = resolved;
            if (parent == null) return false;
            if (!visited.add(parent.getUUID()) || visited.size() > MAX_DEPTH) return true;
            if (parent.getContraption() != null && (parent.getContraption().getStabilizedSubContraptions().isTransferring()||parent.getContraption().getStabilizedSubContraptions().isRecovering())) return true;
            cursor = parent;
        }
    }
    private static void completeEntity(AbstractContraptionEntity owner, StructureTransform transform) {
        var structure = owner.getContraption();
        if (!structure.disassembled || structure.getStorage().ownsContents()) throw new IllegalStateException("Cannot discard an owned graph node");
        if (owner instanceof OrientedContraptionEntity child && child.getParentId() != null && owner.level() instanceof ServerLevel server && server.getEntity(child.getParentId()) instanceof AbstractContraptionEntity parent && parent.getContraption() != null && parent.getContraption().getStabilizedSubContraptions().accepts(child)) {
            parent.getContraption().getStabilizedSubContraptions().completed(child.getUUID());
            for (var watcher : PlayerLookup.tracking(parent)) ContraptionNetworking.sendFull(parent, watcher);
        }
        structure.addPassengersToWorld(owner.level(), transform, owner.getPassengers()); owner.ejectPassengers(); owner.discard();
    }
    private ContraptionAssemblyGraph() {}
}

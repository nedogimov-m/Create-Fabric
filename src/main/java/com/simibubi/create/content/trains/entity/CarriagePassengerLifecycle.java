package com.simibubi.create.content.trains.entity;

import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Per-node material custody. A read-only live snapshot is never an additional paid NPC source. */
public final class CarriagePassengerLifecycle {
    public interface Owner {
        Map<Integer,CompoundTag> roots();
        boolean current();
        boolean owns(CarriageContraptionEntity front);
        void dirty();
    }
    private final Owner owner;
    private final Map<UUID,WeakReference<Entity>> actuals=new HashMap<>();
    private boolean working,snapshotting;private Entity externalRemoval;
    public CarriagePassengerLifecycle(Owner owner){this.owner=Objects.requireNonNull(owner);}
    private static CarriagePassengerGraph graph(CompoundTag tag){return CarriagePassengerGraph.fromLegacy(tag,UUID.randomUUID());}
    private boolean current(int seat,CompoundTag expected,CarriageContraptionEntity front){return expected!=null&&owner.current()&&owner.owns(front)&&owner.roots().get(seat)==expected&&CarriagePlayerReferenceTransitions.frontCurrent(front);}
    private void put(int seat,CompoundTag expected,CarriagePassengerGraph next){if(owner.roots().get(seat)!=expected)throw new IllegalStateException("Actual passenger material owner changed");owner.roots().put(seat,next.write());owner.dirty();}
    private Entity remembered(UUID id){var weak=actuals.get(id);return weak==null?null:weak.get();}
    private static boolean actualCurrent(Entity actual){return actual!=null&&!actual.isRemoved()&&actual.level() instanceof ServerLevel level&&(CarriageNativeSources.current(actual)||NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(actual));}
    private Entity resolve(ServerLevel level,UUID id){
        Entity found=null;for(var world:level.getServer().getAllLevels()){var candidate=CarriageNativeSources.lookup(world,id);if(candidate!=null){if(found!=null&&found!=candidate)return null;found=candidate;}}
        var remembered=remembered(id);if(found==null&&actualCurrent(remembered))found=remembered;return found;
    }
    private static boolean anyNativeOwner(ServerLevel level,UUID id){for(var world:level.getServer().getAllLevels())if(world.getEntity(id)!=null)return true;return false;}
    static void validateSavedSeatBounds(CompoundTag body,Collection<Integer> selected){
        var cells=CarriageCargoPersistence.bodyPositions(body);var positions=new HashSet<net.minecraft.core.BlockPos>();var seats=body.getListOrEmpty("Seats");
        for(var value:seats){if(!(value instanceof CompoundTag row))throw new IllegalArgumentException("Malformed native body seat");var position=row.contains("Pos")?row.read("Pos",net.minecraft.core.BlockPos.CODEC).orElseThrow():new net.minecraft.core.BlockPos(row.getInt("X").orElseThrow(),row.getInt("Y").orElseThrow(),row.getInt("Z").orElseThrow());if(!positions.add(position)||!cells.contains(position))throw new IllegalArgumentException("Duplicate or outside-body native seat");}
        for(int seat:selected)if(seat<0||seat>=seats.size())throw new IllegalArgumentException("Passenger root outside exact saved native body seats");
    }
    public void read(Map<Integer,CompoundTag> incoming,Set<UUID> bodyIds){
        var prepared=new LinkedHashMap<Integer,CompoundTag>();var seen=new HashSet<UUID>();
        for(var entry:incoming.entrySet()){
            if(entry.getKey()<0)throw new IllegalArgumentException("Negative native passenger seat");var parsed=graph(entry.getValue());parsed.requireDisjointBodies(bodyIds);
            for(var id:parsed.identities())if(!seen.add(id))throw new IllegalArgumentException("Duplicate actual NPC/player passenger identity across seats");
            prepared.put(entry.getKey(),parsed.forNativeReload().write());
        }
        owner.roots().clear();owner.roots().putAll(prepared);actuals.clear();
    }
    private static CarriagePassengerGraph pruneConsumedReferences(CarriagePlayerReferences journal,UUID body,CarriagePassengerGraph graph){var parents=new HashSet<UUID>();for(var node:graph.nodes())if(consumedAncestor(graph,node))parents.add(node.id());if(!parents.isEmpty())journal.supersedeParents(body,graph.root(),parents);return journal.pruneSuperseded(body,graph);}
    /** Capture only actual current roots; originals retain native ownership throughout serialization. */
    public void capture(CarriageContraptionEntity front){
        if(!owner.current()||!owner.owns(front)||!CarriagePlayerReferenceTransitions.frontCurrent(front))throw new IllegalStateException("Actual current passenger snapshot owner required");
        if(working)return;if(CarriagePassengerSnapshots.inProgress()){if(!reentrantSourceViewCurrent(front))throw new IllegalStateException("Native passenger serializer reentry cannot publish an unfinished or changed source view");return;}if(snapshotting)throw new IllegalStateException("Native passenger serializer reentry cannot publish an unfinished snapshot");snapshotting=true;
        try{
            var journal=CarriagePlayerReferences.get(((ServerLevel)front.level()).getServer());
            for(var entry:List.copyOf(owner.roots().entrySet())){
                var previous=graph(entry.getValue());if(previous.hasPendingNpc())continue;var root=resolve((ServerLevel)front.level(),previous.root());if(!actualCurrent(root)||root.getVehicle()==null)continue;
                var currentFront=root.getVehicle() instanceof CarriageContraptionEntity carrier?carrier:null;
                var currentSeat=currentFront==null||currentFront.getContraption()==null?null:currentFront.getContraption().getSeatMapping().get(root.getUUID());
                if(currentFront==null||!owner.owns(currentFront)||currentSeat==null||currentSeat!=entry.getKey()){owner.roots().remove(entry.getKey(),entry.getValue());owner.dirty();}
            }
            for(var root:List.copyOf(front.getPassengers())){
                if(root.getVehicle()!=front)continue;
                Integer seat=front.getContraption().getSeatMapping().get(root.getUUID());if(seat==null)continue;var oldTag=owner.roots().get(seat);var old=oldTag==null?null:pruneConsumedReferences(journal,front.getUUID(),graph(oldTag));
                if(old!=null&&!old.root().equals(root.getUUID())&&old.hasPendingNpc())throw new IllegalStateException("A paid pending NPC root still owns this native seat");
                var captured=CarriagePassengerSnapshots.capture(root,old!=null&&old.root().equals(root.getUUID())?old.revision():UUID.randomUUID());
                if(!captured.current()||!owner.current()||!owner.owns(front)||owner.roots().get(seat)!=oldTag)throw new IllegalStateException("Native root changed during genuine passenger save");
                var next=old!=null&&old.root().equals(root.getUUID())?merge(old,captured.graph()):captured.graph();next=journal.bindReferences(front.getUUID(),next);
                for(var actual:root.getSelfAndPassengers().toList())if(!(actual instanceof Player)){actuals.put(actual.getUUID(),new WeakReference<>(actual));CarriageNpcMaterialOwners.remember(this,front,seat,actual);}
                owner.roots().put(seat,next.write());owner.dirty();
            }
        }finally{snapshotting=false;}
    }
    private boolean reentrantSourceViewCurrent(CarriageContraptionEntity front){
        var journal=CarriagePlayerReferences.peekCurrent(((ServerLevel)front.level()).getServer());if(journal==null)return false;
        for(var root:front.getPassengers()){
            var seat=front.getContraption().getSeatMapping().get(root.getUUID());var tag=seat==null?null:owner.roots().get(seat);if(tag==null||root.getVehicle()!=front)return false;var graph=graph(tag);if(!graph.root().equals(root.getUUID()))return false;
            for(var actual:root.getSelfAndPassengers().toList()){
                var node=graph.node(actual.getUUID());var parent=actual.getVehicle();if(node==null||!actualCurrent(actual)||actual!=root&&parent==null||!node.parent().equals(actual==root?Optional.empty():Optional.of(parent.getUUID())))return false;
                if(actual instanceof ServerPlayer player){var link=journal.link(player.getUUID());if(!(node instanceof CarriagePassengerGraph.PlayerReference reference)||link==null||!journal.matches(link,player)||!reference.parent().equals(Optional.of(link.token().parent()))||!reference.relationship().equals(Optional.of(link.token().revision()))||!link.body().equals(front.getUUID())||!link.root().equals(root.getUUID()))return false;}
                else if(!(node instanceof CarriagePassengerGraph.Npc npc)||npc.material()!=CarriagePassengerGraph.Material.SOURCE_LIVE||remembered(actual.getUUID())!=actual)return false;
                var children=actual.getPassengers().stream().map(Entity::getUUID).toList();var expected=graph.children(actual.getUUID()).stream().map(CarriagePassengerGraph.Node::id).filter(children::contains).toList();if(!children.equals(expected))return false;
            }
        }return true;
    }
    void rememberActualEdge(CarriageContraptionEntity front,int seat,Entity actual){actuals.put(actual.getUUID(),new WeakReference<>(actual));CarriageNpcMaterialOwners.remember(this,front,seat,actual);}
    boolean permitsMove(int seat,Entity actual,Entity parent,CarriageContraptionEntity target){
        var tag=owner.roots().get(seat);if(tag==null)return true;var graph=graph(tag);var node=graph.node(actual.getUUID());if(node==null)return true;var pending=new ArrayDeque<CarriagePassengerGraph.Node>();pending.add(node);boolean debt=false;while(!pending.isEmpty()){var child=pending.removeFirst();if(child instanceof CarriagePassengerGraph.Npc npc&&npc.material()==CarriagePassengerGraph.Material.PENDING)debt=true;pending.addAll(graph.children(child.id()));}if(!debt)return true;
        // An owed, not-yet-admitted child stays with its exact current source; normal retry of the planned edge is allowed.
        return owner.current()&&target!=null&&target.passengerLifecycleForNative()==this&&(node.parent().isEmpty()?parent==target&&CarriagePlayerReferenceTransitions.preparedSeatIndex(target,actual,seat):node.parent().orElseThrow().equals(parent.getUUID()));
    }
    /** One existing actual direct reference and its exact live source edge; no journal/token is created. */
    static final class DirectPlayerSource {
        private final CarriagePassengerLifecycle lifecycle;private final CarriageContraptionEntity front;private final ServerPlayer actual;private final int seat;private final CompoundTag expected;
        private DirectPlayerSource(CarriagePassengerLifecycle lifecycle,CarriageContraptionEntity front,ServerPlayer actual,int seat,CompoundTag expected){this.lifecycle=lifecycle;this.front=front;this.actual=actual;this.seat=seat;this.expected=expected;}
        boolean current(){return lifecycle.directPlayerSourceCurrent(front,actual,seat,expected);}
    }
    private boolean directPlayerSourceCurrent(CarriageContraptionEntity front,ServerPlayer actual,int seat,CompoundTag expected){
        if(!current(seat,expected,front)||actual.getVehicle()!=front||actual.level()!=front.level()||actual.isRemoved()
                ||!(front.level() instanceof ServerLevel level)||level.getEntity(actual.getUUID())!=actual||level.getServer().getPlayerList().getPlayer(actual.getUUID())!=actual
                ||front.getPassengers().stream().noneMatch(value->value==actual)||!Objects.equals(front.getContraption().getSeatMapping().get(actual.getUUID()),seat))return false;
        // The actual body UUID is an existing pure validation revision for legacy direct rows.
        var saved=CarriagePassengerGraph.fromLegacy(expected,front.getUUID());
        return saved.root().equals(actual.getUUID())&&saved.nodes().size()==1&&saved.node(saved.root()) instanceof CarriagePassengerGraph.PlayerReference reference
                &&reference.parent().isEmpty()&&reference.relationship().isEmpty();
    }
    DirectPlayerSource directPlayerSource(CarriageContraptionEntity front,ServerPlayer actual){
        var seat=front.getContraption()==null?null:front.getContraption().getSeatMapping().get(actual.getUUID());if(seat==null)return null;
        var expected=owner.roots().get(seat);return expected!=null&&directPlayerSourceCurrent(front,actual,seat,expected)?new DirectPlayerSource(this,front,actual,seat,expected):null;
    }
    void supersedeActualDirectPlayerUnlink(CarriageContraptionEntity front,ServerPlayer actual){supersedeActualDirectPlayerTeleport(front,actual);}
    /** An explicit current native player teleport supersedes direct seat references too.
     * Direct roots intentionally have no nested-player journal/token or material payload. */
    void supersedeActualDirectPlayerTeleport(CarriageContraptionEntity front,ServerPlayer actual){
        if(!owner.current()||!owner.owns(front)||!CarriagePlayerReferenceTransitions.frontCurrent(front)||actual.getVehicle()!=front
                ||!(front.level() instanceof ServerLevel level)||actual.level()!=level||level.getEntity(actual.getUUID())!=actual
                ||level.getServer().getPlayerList().getPlayer(actual.getUUID())!=actual||actual.isRemoved()
                ||front.getPassengers().stream().noneMatch(value->value==actual)||!front.getContraption().getSeatMapping().containsKey(actual.getUUID()))return;
        for(var entry:List.copyOf(owner.roots().entrySet())){
            var expected=entry.getValue();var saved=graph(expected);
            if(saved.root().equals(actual.getUUID())&&saved.nodes().size()==1&&saved.node(saved.root()) instanceof CarriagePassengerGraph.PlayerReference reference
                    &&reference.parent().isEmpty()&&reference.relationship().isEmpty()&&owner.roots().remove(entry.getKey(),expected))owner.dirty();
        }
    }
    /** A real later voluntary unlink relinquishes unpaid source views, while owed descendants stay owned. */
    void afterVoluntaryNativeUnlink(CarriageContraptionEntity front,int seat,Entity actual){
        var expected=owner.roots().get(seat);if(expected==null||!owner.current()||!owner.owns(front)||remembered(actual.getUUID())!=actual||!actualCurrent(actual)||actual.getVehicle()!=null)return;var previous=graph(expected);var node=previous.node(actual.getUUID());if(!(node instanceof CarriagePassengerGraph.Npc))return;
        var removed=new HashSet<UUID>();var pending=new ArrayDeque<CarriagePassengerGraph.Node>();pending.add(node);while(!pending.isEmpty()){var child=pending.removeFirst();if(child instanceof CarriagePassengerGraph.Npc npc&&npc.material()==CarriagePassengerGraph.Material.PENDING)return;removed.add(child.id());pending.addAll(previous.children(child.id()));}
        if(node.parent().isEmpty())owner.roots().remove(seat,expected);else{var rows=new LinkedHashMap<UUID,CarriagePassengerGraph.Node>();for(var retained:previous.nodes())if(!removed.contains(retained.id()))rows.put(retained.id(),retained);owner.roots().put(seat,ordered(previous,previous,rows).write());}
        for(var id:removed)actuals.remove(id);owner.dirty();
    }
    void transferIndependentTo(CarriagePassengerLifecycle target,CarriageContraptionEntity front){
        if(owner.roots().isEmpty())return;if(target==this)return;if(!owner.current()||!target.owner.current()||!target.owner.owns(front)||!owner.owns(front)||!target.owner.roots().isEmpty())throw new IllegalStateException("Exact unique actual independent-to-Carriage passenger owner required");
        target.owner.roots().putAll(owner.roots());owner.roots().clear();target.actuals.putAll(actuals);actuals.clear();
        for(var entry:target.owner.roots().entrySet())for(var node:graph(entry.getValue()).nodes())if(node instanceof CarriagePassengerGraph.Npc){var actual=target.remembered(node.id());if(actualCurrent(actual))CarriageNpcMaterialOwners.remember(target,front,entry.getKey(),actual);}
        target.owner.dirty();owner.dirty();
    }
    private CarriagePassengerGraph merge(CarriagePassengerGraph previous,CarriagePassengerGraph actual){
        var rows=new LinkedHashMap<UUID,CarriagePassengerGraph.Node>();for(var node:previous.nodes())rows.put(node.id(),node);
        // A real later native mount can move a complete world-owned branch to another seat/root.
        for(var node:previous.nodes())if(node instanceof CarriagePassengerGraph.Npc npc&&actual.node(node.id())==null&&npc.material()!=CarriagePassengerGraph.Material.PENDING&&npc.material()!=CarriagePassengerGraph.Material.CONSUMED){
            var moved=remembered(node.id());if(!actualCurrent(moved)||moved.getVehicle()==null||node.parent().isEmpty()||moved.getVehicle().getUUID().equals(node.parent().get()))continue;
            var subtree=new ArrayList<CarriagePassengerGraph.Node>();var pending=new ArrayDeque<CarriagePassengerGraph.Node>();pending.add(node);boolean owed=false;while(!pending.isEmpty()){var next=pending.removeFirst();subtree.add(next);if(next instanceof CarriagePassengerGraph.Npc child&&child.material()==CarriagePassengerGraph.Material.PENDING)owed=true;pending.addAll(previous.children(next.id()));}if(!owed)for(var removed:subtree)rows.remove(removed.id());
        }
        for(var node:actual.nodes())rows.put(node.id(),node);return ordered(previous,actual,rows);
    }
    private static CarriagePassengerGraph ordered(CarriagePassengerGraph previous,CarriagePassengerGraph actual,Map<UUID,CarriagePassengerGraph.Node> rows){
        var parents=new LinkedHashSet<UUID>();for(var node:rows.values())node.parent().ifPresent(parents::add);
        for(var parent:parents){
            var order=new ArrayList<>(actual.children(parent).stream().map(CarriagePassengerGraph.Node::id).filter(rows::containsKey).toList());var prior=previous.children(parent).stream().map(CarriagePassengerGraph.Node::id).filter(rows::containsKey).toList();
            for(int index=0;index<prior.size();index++){var id=prior.get(index);if(order.contains(id))continue;int insertion=order.size();for(int next=index+1;next<prior.size();next++){int at=order.indexOf(prior.get(next));if(at>=0){insertion=at;break;}}order.add(insertion,id);}
            for(int index=0;index<order.size();index++){var row=rows.get(order.get(index));if(row instanceof CarriagePassengerGraph.Npc npc)rows.put(row.id(),new CarriagePassengerGraph.Npc(npc.id(),npc.parent(),index,npc.type(),npc.payload(),npc.material()));else{var player=(CarriagePassengerGraph.PlayerReference)row;rows.put(row.id(),new CarriagePassengerGraph.PlayerReference(player.id(),player.parent(),index,player.relationship()));}}
        }
        return new CarriagePassengerGraph(previous.root(),previous.revision(),List.copyOf(rows.values()));
    }
    /** Reentrant saves refresh still-live actual nodes without reviving a consumed or pending node. */
    public Map<Integer,CompoundTag> write(CarriageContraptionEntity front){
        return write((ServerLevel)front.level(),front.getUUID());
    }
    Map<Integer,CompoundTag> writeWithoutFront(net.minecraft.server.MinecraftServer server,UUID body){
        if(server!=null&&body!=null)return write(server.overworld(),body);
        for(var tag:owner.roots().values())for(var node:graph(tag).nodes())if(node instanceof CarriagePassengerGraph.Npc npc&&npc.material()==CarriagePassengerGraph.Material.SOURCE_LIVE)throw new IllegalStateException("An unpaid original source snapshot requires its actual native server witness");
        var copied=new LinkedHashMap<Integer,CompoundTag>();owner.roots().forEach((seat,tag)->copied.put(seat,tag.copy()));return Map.copyOf(copied);
    }
    private Map<Integer,CompoundTag> write(ServerLevel level,UUID body){
        var output=new LinkedHashMap<Integer,CompoundTag>();
        for(var entry:List.copyOf(owner.roots().entrySet())){
            var expected=entry.getValue();var snapshot=graph(expected);snapshot=pruneConsumedReferences(CarriagePlayerReferences.get(level.getServer()),body,snapshot);
            for(var node:snapshot.nodes())if(node instanceof CarriagePassengerGraph.Npc npc&&npc.material()!=CarriagePassengerGraph.Material.PENDING&&npc.material()!=CarriagePassengerGraph.Material.CONSUMED){
                var actual=resolve(level,npc.id());var rememberedSource=remembered(npc.id());
                if(!actualCurrent(actual)&&rememberedSource!=null&&!rememberedSource.isRemoved())throw new IllegalStateException("An unready live native NPC source cannot be replaced by an empty copied snapshot");
                if(actual==null&&rememberedSource==null&&npc.material()==CarriagePassengerGraph.Material.SOURCE_LIVE)throw new IllegalStateException("An unpaid original NPC source witness cannot silently become replayable material or disappear");
                var actualFront=actual!=null&&actual.getRootVehicle() instanceof CarriageContraptionEntity carrier?carrier:null;
                boolean exactOwner=actualCurrent(actual)&&actualFront!=null&&owner.owns(actualFront)&&CarriagePlayerReferenceTransitions.frontCurrent(actualFront)&&actualTreeOwner(actualFront,snapshot,actual);
                if(!exactOwner&&actual!=null&&CarriagePlayerReferenceTransitions.protectsNativeTransferRoot(actual.getRootVehicle()))throw new IllegalStateException("Unfinished native source unlink cannot publish a half-transferred passenger snapshot");
                if(!exactOwner){
                    // An unattached native entity owns its own storage. This reference may never replay its material.
                    boolean destroyed=rememberedSource!=null&&rememberedSource.getRemovalReason()!=null&&rememberedSource.getRemovalReason().shouldDestroy();snapshot=snapshot.replaceNpc(npc.id(),destroyed?CarriagePassengerGraph.Material.CONSUMED:CarriagePassengerGraph.Material.ADMITTED,Optional.empty());continue;
                }
                if(CarriagePassengerSnapshots.inProgress()){
                    if(npc.material()!=CarriagePassengerGraph.Material.SOURCE_LIVE)throw new IllegalStateException("An unfinished first native NPC snapshot cannot be published by reentry");continue;
                }
                var payload=CarriagePassengerSnapshots.captureNpc(actual);
                if(!actualCurrent(actual)||!owner.current()||!owner.owns(actualFront)||!CarriagePlayerReferenceTransitions.frontCurrent(actualFront)||owner.roots().get(entry.getKey())!=expected)throw new IllegalStateException("Actual passenger changed during native persistence");
                snapshot=snapshot.replaceNpc(npc.id(),CarriagePassengerGraph.Material.SOURCE_LIVE,Optional.of(payload));
            }
            snapshot=pruneConsumedReferences(CarriagePlayerReferences.get(level.getServer()),body,snapshot);var written=snapshot.write();output.put(entry.getKey(),written.copy());
            // Keep the real ledger synchronized even after the last frontend retired. A later
            // save must never reinterpret an obsolete unpaid view as independently paid material.
            if(!written.equals(expected)){if(!owner.current()||owner.roots().get(entry.getKey())!=expected)throw new IllegalStateException("Actual passenger material ledger changed during persistence");owner.roots().put(entry.getKey(),written);owner.dirty();}
        }
        return Map.copyOf(output);
    }
    private boolean actualTreeOwner(CarriageContraptionEntity front,CarriagePassengerGraph graph,Entity actual){
        var node=graph.node(actual.getUUID());if(!(node instanceof CarriagePassengerGraph.Npc)||actual.level()!=front.level())return false;int depth=0;
        while(node.parent().isPresent()){
            if(++depth>CarriagePassengerGraph.MAX_DEPTH)return false;var parent=actual.getVehicle();if(parent==null||!parent.getUUID().equals(node.parent().get())||!actualCurrent(parent))return false;node=graph.node(parent.getUUID());if(!(node instanceof CarriagePassengerGraph.Npc))return false;actual=parent;
        }
        return actual.getUUID().equals(graph.root())&&actual.getVehicle()==front&&front.getContraption().getSeatMapping().containsKey(actual.getUUID());
    }
    public void load(CarriageContraptionEntity front,Vec3 anchor,int min,int max){
        if(working||!owner.current()||!owner.owns(front)||!(front.level() instanceof ServerLevel level)||!CarriagePlayerReferenceTransitions.frontCurrent(front)||!LoadedEntityAccess.isReady(level,front.blockPosition()))return;working=true;
        try{
            var journal=CarriagePlayerReferences.get(level.getServer());
            for(var entry:List.copyOf(owner.roots().entrySet())){
                int seat=entry.getKey();if(seat<0||seat>=front.getContraption().getSeats().size())throw new IllegalStateException("Actual passenger seat outside native body");
                if(!front.isLocalCoordWithin(front.getContraption().getSeats().get(seat),min,max))continue;var expected=owner.roots().get(seat);if(expected!=entry.getValue())continue;
                var parsed=pruneConsumedReferences(journal,front.getUUID(),graph(expected));if(!parsed.write().equals(expected)){put(seat,expected,parsed);expected=owner.roots().get(seat);}
                if(parsed.node(parsed.root()) instanceof CarriagePassengerGraph.PlayerReference){
                    var player=level.getServer().getPlayerList().getPlayer(parsed.root());if(player!=null&&player.level()==level&&level.getEntity(player.getUUID())==player&&!player.isRemoved()&&(player.getVehicle()==null||player.getVehicle()==front)&&front.addSittingPassenger(player,seat)&&current(seat,expected,front)){owner.roots().remove(seat,expected);owner.dirty();}continue;
                }
                for(var node:breadthFirst(parsed))if(node instanceof CarriagePassengerGraph.Npc npc){
                    expected=owner.roots().get(seat);if(expected==null)break;parsed=graph(expected);var currentNode=(CarriagePassengerGraph.Npc)parsed.node(npc.id());if(currentNode==null||currentNode.material()==CarriagePassengerGraph.Material.CONSUMED)continue;
                    Entity actual=resolve(level,npc.id());
                    if(actual!=null){
                        if(!actualCurrent(actual)||actual instanceof Player||!LoadedEntityAccess.isReady((ServerLevel)actual.level(),actual.blockPosition())||!EntityType.getKey(actual.getType()).equals(npc.type()))continue;
                        if(currentNode.material()==CarriagePassengerGraph.Material.PENDING&&current(seat,expected,front)){
                            // A genuine existing native UUID owner already owns this material. Relinquish the stale disk copy before callbacks.
                            put(seat,expected,parsed.afterNpcAdmission(npc.id()));actuals.put(npc.id(),new WeakReference<>(actual));CarriageNpcMaterialOwners.remember(this,front,seat,actual);
                        }
                    }else if(currentNode.material()==CarriagePassengerGraph.Material.PENDING&&!anyNativeOwner(level,npc.id())&&anchor!=null&&LoadedEntityAccess.isReady(level,net.minecraft.core.BlockPos.containing(anchor))){
                        var problems=new ProblemReporter.Collector();var input=TagValueInput.create(problems,level.registryAccess(),currentNode.payload().orElseThrow());
                        var decoded=EntityType.create(input,level,new EntitySpawnRequest(EntitySpawnReason.LOAD,false)).orElse(null);
                        if(!problems.isEmpty()||decoded==null||decoded instanceof Player||!npc.id().equals(decoded.getUUID())||!EntityType.getKey(decoded.getType()).equals(npc.type())||!decoded.getPassengers().isEmpty())throw new IllegalStateException("Actual individual NPC failed native decoding: "+problems.getReport());
                        decoded.snapTo(anchor);if(!current(seat,expected,front)||anyNativeOwner(level,npc.id()))continue;
                        var sourceTag=expected;var after=parsed.afterNpcAdmission(npc.id()).write();
                        try(var admission=NativeEntityAcceptance.prepare(decoded,()->current(seat,sourceTag,front)&&!anyNativeOwner(level,npc.id()),proof->{proof.requireCommitting(decoded);owner.roots().put(seat,after);actuals.put(npc.id(),new WeakReference<>(decoded));CarriageNpcMaterialOwners.remember(this,front,seat,decoded);owner.dirty();})){admission.add();if(admission.insertion()==null)continue;actual=decoded;}
                    }
                    if(!actualCurrent(actual)||actual.level()!=level||!current(seat,owner.roots().get(seat),front))continue;
                    if(npc.parent().isEmpty()){
                        if(actual.getVehicle()==front)continue;if(actual.getVehicle()!=null)continue;
                        try(var transfer=CarriagePlayerReferenceTransitions.preserveRootMount(actual,front)){front.addSittingPassenger(actual,seat);}
                    }else{
                        var parent=resolve(level,npc.parent().get());if(!actualCurrent(parent)||parent.level()!=level||actual.getVehicle()!=null&&actual.getVehicle()!=parent)continue;
                        if(actual.getVehicle()!=parent){try(var transfer=CarriagePlayerReferenceTransitions.preserveRootMount(actual,parent)){actual.startRiding(parent,true,true);}}
                    }
                }
            }
        }finally{working=false;}
    }
    private static List<CarriagePassengerGraph.Node> breadthFirst(CarriagePassengerGraph graph){var result=new ArrayList<CarriagePassengerGraph.Node>();var pending=new ArrayDeque<CarriagePassengerGraph.Node>();pending.add(graph.node(graph.root()));while(!pending.isEmpty()){var next=pending.removeFirst();result.add(next);pending.addAll(graph.children(next.id()));}return result;}
    /** All actual sources are captured before the first unlink. Each retirement pays exactly one node. */
    public boolean retire(CarriageContraptionEntity front,Entity root,int seat){
        if(working)return false;capture(front);var expected=owner.roots().get(seat);if(expected==null)return false;var parsed=graph(expected);if(!parsed.root().equals(root.getUUID())||root.getVehicle()!=front)return false;
        working=true;
        try{
            var original=CarriagePassengerSnapshots.capture(root,parsed.revision());if(!original.current()||!current(seat,expected,front))return false;
            for(var actual:root.getSelfAndPassengers().toList())if(actual instanceof ServerPlayer player){
                if(player.getVehicle()==null)continue;try(var transfer=CarriagePlayerReferenceTransitions.preserve(player,null)){if(!transfer.current())return false;player.stopRiding();}if(player.getVehicle()!=null)return false;
            }
            var ordered=new ArrayList<>(original.graph().nodes());Collections.reverse(ordered);
            for(var node:ordered)if(node instanceof CarriagePassengerGraph.Npc){
                Entity actual=remembered(node.id());if(!actualCurrent(actual)||actual.level()!=front.level()||!actual.getPassengers().isEmpty())return false;
                expected=owner.roots().get(seat);if(!current(seat,expected,front))return false;
                if(actual.getVehicle()!=null){try(var transfer=CarriagePlayerReferenceTransitions.preserveRootUnlink(actual)){transfer.unlink();}}
                if(!actualCurrent(actual)||actual.getVehicle()!=null||!actual.getPassengers().isEmpty())return false;
                try(var retirement=CarriageNpcRetirement.prepare(this,front,seat,actual)){if(actual==externalRemoval)actual.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);else actual.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);if(!retirement.committed())return false;}
            }
            return true;
        }finally{working=false;}
    }
    boolean beforeExternalUnload(CarriageContraptionEntity front,int seat,Entity actual){
        var tag=owner.roots().get(seat);if(actual.getRootVehicle()!=front)return true;
        if(working||!owner.current()||!owner.owns(front)||!CarriagePlayerReferenceTransitions.frontCurrent(front)||!actualCurrent(actual))return false;
        Entity root=actual;while(root.getVehicle()!=front){if(root.getVehicle()==null)return false;root=root.getVehicle();}
        if(!Objects.equals(front.getContraption().getSeatMapping().get(root.getUUID()),seat))return false;
        externalRemoval=actual;try{retire(front,root,seat);return false;}finally{externalRemoval=null;}
    }
    /** Called at the real native retirement boundary; serializers run while the original is still live. */
    CarriageNpcRetirement.Payment preparePayment(CarriageContraptionEntity front,int seat,Entity actual){
        var expected=owner.roots().get(seat);if(expected==null||!current(seat,expected,front)||!actualCurrent(actual)||remembered(actual.getUUID())!=actual||actual.getVehicle()!=null||!actual.getPassengers().isEmpty()||!CarriageNativeSources.current(actual))return null;
        var parsed=graph(expected);if(!(parsed.node(actual.getUUID()) instanceof CarriagePassengerGraph.Npc npc)||npc.material()==CarriagePassengerGraph.Material.PENDING)return null;
        var payload=CarriagePassengerSnapshots.captureNpc(actual);
        if(!current(seat,expected,front)||!actualCurrent(actual)||actual.getVehicle()!=null||!actual.getPassengers().isEmpty()||!CarriageNativeSources.current(actual))return null;
        var next=parsed.replaceNpc(actual.getUUID(),CarriagePassengerGraph.Material.PENDING,Optional.of(payload)).write();
        return new CarriageNpcRetirement.Payment(()->current(seat,expected,front)&&actualCurrent(actual)&&remembered(actual.getUUID())==actual&&actual.getVehicle()==null&&actual.getPassengers().isEmpty(),()->{owner.roots().put(seat,next);actuals.remove(actual.getUUID());owner.dirty();});
    }
    /** Actual destruction spends this source; external native unload/dimension storage owns its own material. */
    void nativeSourceConsumed(CarriageContraptionEntity front,int seat,Entity actual,boolean destroyed){
        var expected=owner.roots().get(seat);if(expected==null||!owner.current()||!owner.owns(front)||remembered(actual.getUUID())!=actual)return;var parsed=graph(expected);
        if(!(parsed.node(actual.getUUID()) instanceof CarriagePassengerGraph.Npc npc)||npc.material()==CarriagePassengerGraph.Material.PENDING)return;
        put(seat,expected,parsed.replaceNpc(npc.id(),destroyed?CarriagePassengerGraph.Material.CONSUMED:CarriagePassengerGraph.Material.ADMITTED,Optional.empty()));actuals.remove(npc.id());
        if(destroyed){var parents=new HashSet<UUID>();var pending=new ArrayDeque<CarriagePassengerGraph.Node>();pending.add(npc);while(!pending.isEmpty()){var node=pending.removeFirst();parents.add(node.id());pending.addAll(parsed.children(node.id()));}var journal=CarriagePlayerReferences.peekCurrent(((ServerLevel)front.level()).getServer());if(journal!=null)journal.supersedeParents(front.getUUID(),parsed.root(),parents);}
    }
    private static boolean consumedAncestor(CarriagePassengerGraph graph,CarriagePassengerGraph.Node node){while(node!=null){if(node instanceof CarriagePassengerGraph.Npc npc&&npc.material()==CarriagePassengerGraph.Material.CONSUMED)return true;node=node.parent().map(graph::node).orElse(null);}return false;}
    public void worldReferences(ServerLevel world,CarriageContraptionEntity front,com.simibubi.create.content.contraptions.StructureTransform transform){
        var journal=CarriagePlayerReferences.get(world.getServer());for(var entry:owner.roots().entrySet()){
            var parsed=graph(entry.getValue());if(!(parsed.node(parsed.root()) instanceof CarriagePassengerGraph.Npc root)||root.material()==CarriagePassengerGraph.Material.CONSUMED)continue;
            if(parsed.hasPendingNpc())throw new IllegalStateException("Paid NPC material cannot be relinquished by world reference handoff");
            if(!journal.worldHandoff(world,front,parsed.root(),transform.apply(front.getContraption().getSeats().get(entry.getKey()))))throw new IllegalStateException("Exact world-owned NPC root reference handoff refused");
        }
    }
    public boolean hasPendingNpc(){for(var tag:owner.roots().values())if(graph(tag).hasPendingNpc())return true;return false;}
    void releaseWorldReferences(CarriageContraptionEntity front){
        if(working||snapshotting||hasPendingNpc()||front.getContraption()==null||front.getContraption().phase()!=com.simibubi.create.content.contraptions.Contraption.Phase.DISASSEMBLED||front.getContraption().getStorage().ownsContents())throw new IllegalStateException("Only actual world-restored native material may release passenger references");
        owner.roots().clear();actuals.clear();owner.dirty();
    }
    /** NPC payload debt must become real native material before world blocks can own the restored body. */
    public Map<UUID,Integer> pendingDirectPlayers(CarriageContraptionEntity front){
        if(working||snapshotting)return null;
        // A complete native root may have moved since the last save. Prune its old reference
        // before preflight; restored bodies are no longer owners and must not be recaptured.
        if(owner.current()&&owner.owns(front)&&CarriagePlayerReferenceTransitions.frontCurrent(front))capture(front);
        var result=new LinkedHashMap<UUID,Integer>();var level=(ServerLevel)front.level();for(var entry:List.copyOf(owner.roots().entrySet())){
            var parsed=graph(entry.getValue());if(parsed.node(parsed.root()) instanceof CarriagePassengerGraph.PlayerReference){result.put(parsed.root(),entry.getKey());continue;}
            if(parsed.hasPendingNpc())return null;
            if(consumedAncestor(parsed,parsed.node(parsed.root()))){CarriagePlayerReferences.get(level.getServer()).supersedeRoot(front.getUUID(),parsed.root());owner.roots().remove(entry.getKey(),entry.getValue());owner.dirty();continue;}
            var root=resolve(level,parsed.root());if(!actualCurrent(root)||root.level()!=level||root.getVehicle()!=null&&root.getVehicle()!=front)return null;
            for(var node:parsed.nodes())if(node instanceof CarriagePassengerGraph.Npc&&!consumedAncestor(parsed,node)){var actual=resolve(level,node.id());if(!actualCurrent(actual)||actual.level()!=level||actual.getVehicle()!=(node.parent().isEmpty()?root.getVehicle():resolve(level,node.parent().get())))return null;}
            if(root.getVehicle()==null)result.put(root.getUUID(),entry.getKey());
        }return Map.copyOf(result);
    }
}

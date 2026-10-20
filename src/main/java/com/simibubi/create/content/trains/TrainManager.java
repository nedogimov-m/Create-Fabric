package com.simibubi.create.content.trains;

import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.station.StationEdgePoints;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

/** Actual native server-wide Train custody, source traffic ordering and complete save ownership. */
public final class TrainManager {
    private static final Map<MinecraftServer,TrainManager> SERVERS=Collections.synchronizedMap(new WeakHashMap<>());
    private record Reservation(Train train,SignalBoundary boundary){}
    private final MinecraftServer server;
    private final RailwayGraphManager graphs;
    private final TrainTrafficSavedData savedData;
    private final Map<UUID,Train> trains=new LinkedHashMap<>();
    private final Map<UUID,SignalEdgeGroup> groups=new LinkedHashMap<>();
    private final Map<UUID,Reservation> reservations=new LinkedHashMap<>();
    private final List<Train> moving=new ArrayList<>(),waiting=new ArrayList<>();
    private final Set<UUID> hiddenRestoring=new HashSet<>();
    private final UUID epoch=UUID.randomUUID();
    private long sequence;
    private boolean ready,ticking;
    private TrainManager(MinecraftServer server){
        this.server=Objects.requireNonNull(server);graphs=RailwayGraphManager.get(server);savedData=TrainTrafficSavedData.load(server);
        var saved=savedData.retained();var dimensions=DimensionPalette.read(saved);
        for(var entry:saved.getListOrEmpty("SignalBlocks")){if(!(entry instanceof CompoundTag tag))throw new IllegalArgumentException("Malformed actual signal group record");putGroup(SignalEdgeGroup.read(tag));}
        refreshGroups();
        for(var entry:saved.getListOrEmpty("Trains")){if(!(entry instanceof CompoundTag tag))throw new IllegalArgumentException("Malformed actual Train record");var train=Train.read(tag,server.registryAccess(),graphs.getTrackNetworks(),dimensions);addOwned(train,false);}
        savedData.bind(this);graphs.addListener(this::graphChanged);ready=true;
    }
    public static TrainManager get(MinecraftServer server){return SERVERS.computeIfAbsent(server,TrainManager::new);}
    public static TrainManager get(ServerLevel level){return get(level.getServer());}
    /** Current already-loaded owner only; original-player witnesses must never construct a manager. */
    public static TrainManager peekCurrent(MinecraftServer actualServer){var current=SERVERS.get(actualServer);return current!=null&&current.isCurrent()&&current.isReady()?current:null;}
    public MinecraftServer server(){return server;}
    /** Pure current native manager/journal witness; never loads or constructs SavedData. */
    public boolean isCurrent(){return SERVERS.get(server)==this&&com.simibubi.create.foundation.utility.NativeSavedDataIdentity.isCurrent(server.overworld(),TrainTrafficSavedData.TYPE,savedData);}
    public boolean isReady(){return ready;}
    public Map<UUID,Train> trains(){return Collections.unmodifiableMap(trains);}
    public Map<UUID,SignalEdgeGroup> signalEdgeGroups(){return Collections.unmodifiableMap(groups);}
    public Train get(UUID id){return trains.get(id);}
    public void markDirty(){savedData.setDirty();}
    public void add(Train train){addOwned(train,true);}
    private void addOwned(Train train,boolean publish){
        Objects.requireNonNull(train);
        var existing=trains.get(train.id);
        if(existing!=null&&existing!=train)throw new IllegalArgumentException("Another actual Train owns UUID "+train.id);
        if(train.trafficManager()!=null&&train.trafficManager()!=this)
            throw new IllegalArgumentException("Train belongs to another actual native traffic owner");
        var candidates=new ArrayList<>(trains.values());
        if(existing==null)candidates.add(train);
        validateCustody(candidates);
        if(existing==train)return;
        train.bindManager(this);
        trains.put(train.id,train);
        if(!train.isRestoring()&&train.getCurrentStation()!=null)train.getCurrentStation().reserveFor(train);
        moving.add(train);train.updateSignalBlocks=true;markDirty();if(publish)publish(train);
    }
    /** Preflight every real owner before publishing or serializing any part of the global registry. */
    public void validateCustody(){
        for(var entry:trains.entrySet())
            if(!entry.getKey().equals(entry.getValue().id)||entry.getValue().trafficManager()!=this)
                throw new IllegalStateException("Native Train registry identity or owner changed");
        validateCustody(trains.values());
    }
    private void validateCustody(Collection<Train> candidates){
        var trainIds=new HashSet<UUID>();
        var carriageOwners=new IdentityHashMap<Carriage,Train>();
        var cargoOwners=new IdentityHashMap<Object,Carriage>();
        var entityOwners=new HashMap<UUID,Carriage>();
        for(var train:candidates){
            train.validateMotion();
            if(train.id==null||!trainIds.add(train.id))
                throw new IllegalArgumentException("Duplicate actual native Train UUID");
            if(train.graph!=null&&train.graph.manager()!=graphs)
                throw new IllegalArgumentException("Train graph belongs to another native server");
            if(train.carriages.isEmpty()||train.carriages.size()>Train.MAX_CARRIAGES
                ||train.carriageSpacing.size()!=train.carriages.size()-1)
                throw new IllegalArgumentException("Invalid actual native Train formation");
            for(var carriage:train.carriages){
                if(carriage==null||carriage.train!=train||carriage.storage==null
                    ||(carriage.isWorldRestored()?carriage.storage.ownsContents():!carriage.storage.ownsContents()))
                    throw new IllegalArgumentException("Only actual globally owned carriage bodies/cargo may enter native Train custody");
                if(carriageOwners.put(carriage,train)!=null||cargoOwners.put(carriage.storage,carriage)!=null)
                    throw new IllegalArgumentException("Actual carriage or cargo custody is shared");
                var identities=carriage.custodyIdentities();
                if(identities.bodies().size()!=1)
                    throw new IllegalArgumentException("One actual original body UUID is required per server-owned carriage");
                for(var body:identities.bodies())claimIdentity(entityOwners,body,carriage);
                for(var passenger:identities.passengers())claimIdentity(entityOwners,passenger,carriage);
            }
        }
    }
    private static void claimIdentity(Map<UUID,Carriage> owners,UUID identity,Carriage carriage){
        if(identity==null||owners.putIfAbsent(identity,carriage)!=null)
            throw new IllegalArgumentException("Actual carriage body/passenger UUID custody is shared");
    }

    public boolean remove(UUID id){
        var train=trains.remove(id);if(train==null)return false;moving.remove(train);waiting.remove(train);hiddenRestoring.remove(id);
        for(var group:groups.values())group.trains.remove(train);
        for(var entry:List.copyOf(reservations.entrySet()))if(entry.getValue().train==train){var group=groups.get(entry.getKey());if(group!=null&&group.reserved==entry.getValue().boundary)group.reserved=null;reservations.remove(entry.getKey());}
        for(var graph:graphs.getTrackNetworks().values())for(var station:graph.getPoints(StationEdgePoints.STATION))station.cancelReservation(train);
        train.bindManager(null);markDirty();broadcast(List.of(),List.of(id));return true;
    }
    public boolean reservedBy(SignalEdgeGroup group,Train train){
        var reservation=reservations.get(group.id);
        return reservation!=null&&reservation.train==train&&group.reserved==reservation.boundary;
    }
    private void requireReservationOwner(Train train,SignalEdgeGroup group,SignalBoundary boundary){
        if(trains.get(train.id)!=train||train.trafficManager()!=this||train.isRestoring()||groups.get(group.id)!=group
            ||boundary.graph()==null||boundary.graph().manager()!=graphs
            ||!group.id.equals(boundary.group(true))&&!group.id.equals(boundary.group(false)))
            throw new IllegalArgumentException("Foreign actual Train/group/boundary reservation");
    }
    private boolean canReserve(Train train,SignalEdgeGroup group,SignalBoundary boundary){
        requireReservationOwner(train,group,boundary);
        boolean[] available={true};
        group.walkIntersecting(intersecting->{
            if(!intersecting.topologyReady||intersecting.trains.stream().anyMatch(other->other!=train)
                ||intersecting.reserved!=null&&!reservedBy(intersecting,train))available[0]=false;
        });
        return available[0];
    }
    public boolean reserve(Train train,SignalEdgeGroup group,SignalBoundary boundary){
        if(!canReserve(train,group,boundary))return false;
        reservations.put(group.id,new Reservation(train,boundary));group.reserved=boundary;return true;
    }
    /** Every genuine chain group is checked before the first reservation changes. */
    public boolean reserveChain(Train train,Map<UUID,net.createmod.catnip.data.Pair<SignalBoundary,Boolean>> chain){
        var selected=new LinkedHashMap<SignalEdgeGroup,SignalBoundary>();
        for(var entry:chain.entrySet()){
            var group=groups.get(entry.getKey());var boundary=entry.getValue().getFirst();
            if(group==null||!entry.getKey().equals(boundary.group(entry.getValue().getSecond())))return false;
            if(!canReserve(train,group,boundary))return false;
            selected.put(group,boundary);
        }
        selected.forEach((group,boundary)->{reservations.put(group.id,new Reservation(train,boundary));group.reserved=boundary;});
        return true;
    }
    private void putGroup(SignalEdgeGroup group){var previous=groups.putIfAbsent(group.id,group);if(previous!=null&&previous!=group)throw new IllegalArgumentException("Duplicate persistent native signal group");group.bind(this);}
    private SignalEdgeGroup ensure(UUID id,boolean fallback){if(id==null)return null;var group=groups.get(id);if(group==null){group=new SignalEdgeGroup(id);group.fallbackGroup=fallback;putGroup(group);markDirty();}return group;}
    /** Must run after actual graph point partitioning/intersection resolution, before real Train early ticks. */
    public void refreshGroups(){
        var used=new LinkedHashSet<UUID>();var allGraphs=graphs.getTrackNetworks().values();
        for(var graph:allGraphs){used.add(graph.id);ensure(graph.id,true);for(var nodeLocation:graph.getNodes()){var node=graph.locateNode(nodeLocation);for(var edge:graph.getConnectionsFrom(node).values()){var id=edge.getEdgeData().getEffectiveEdgeGroupId(graph);if(id!=null){used.add(id);ensure(id,id.equals(graph.id));}}}for(var boundary:graph.getPoints(SignalEdgePoints.SIGNAL))for(boolean side:new boolean[]{true,false}){var id=boundary.group(side);if(id!=null){used.add(id);ensure(id,id.equals(graph.id));}}}
        for(var group:groups.values()){group.adjacent.clear();group.intersectingResolved.clear();group.intersecting.clear();group.topologyReady=true;}
        for(var graph:allGraphs){
            for(var boundary:graph.getPoints(SignalEdgePoints.SIGNAL)){var first=groups.get(boundary.group(true));var second=groups.get(boundary.group(false));if(first!=null&&second!=null&&first!=second){first.adjacent.add(second.id);second.adjacent.add(first.id);}}
            var visited=Collections.newSetFromMap(new IdentityHashMap<TrackEdge,Boolean>());
            for(var location:graph.getNodes()){var node=graph.locateNode(location);for(var entry:graph.getConnectionsFrom(node).entrySet()){
                var edge=entry.getValue();if(!visited.add(edge))continue;var reverse=graph.getConnectionsFrom(entry.getKey()).get(node);if(reverse!=null)visited.add(reverse);
                for(var crossing:edge.getEdgeData().getIntersections()){
                    var local=groups.get(edge.getEdgeData().getGroupAtPosition(graph,crossing.location));if(local==null)continue;SignalEdgeGroup other=null;
                    for(var targetGraph:allGraphs){var first=targetGraph.locateNode(crossing.target.getFirst());var second=targetGraph.locateNode(crossing.target.getSecond());if(first==null||second==null)continue;var target=targetGraph.getConnectionsFrom(first).get(second);if(target==null)continue;var candidate=groups.get(target.getEdgeData().getGroupAtPosition(targetGraph,crossing.targetLocation));if(candidate==null)continue;if(other!=null&&other!=candidate)throw new IllegalStateException("Ambiguous actual crossing group ownership");other=candidate;}
                    if(other==null){local.topologyReady=false;continue;}local.intersecting.put(crossing.id,other.id);other.intersecting.put(crossing.id,local.id);
                }
            }}
        }
        for(var id:List.copyOf(groups.keySet()))if(!used.contains(id)){var removed=groups.remove(id);removed.bind(null);markDirty();}
        for(var group:groups.values())group.resolveColor();
    }
    /** Source phase0: all genuine occupancy and transient reservations are republished every native tick. */
    public void beginTick(){for(var group:groups.values()){group.trains.clear();group.reserved=null;}reservations.clear();}
    public void tickTrains(){
        if(ticking)throw new IllegalStateException("Recursive native Train tick");validateCustody();ticking=true;
        try{var level=server.overworld();for(var train:List.copyOf(waiting))if(trains.get(train.id)==train)train.earlyTick(level);for(var train:List.copyOf(moving))if(trains.get(train.id)==train)train.earlyTick(level);for(var train:List.copyOf(waiting))if(trains.get(train.id)==train)train.tick(level);for(var train:List.copyOf(moving))if(trains.get(train.id)==train)train.tick(level);
            for(var train:List.copyOf(trains.values())){if(train.invalid){remove(train.id);continue;}boolean shouldWait=train.navigation.waitingForSignal!=null;if(shouldWait&&moving.remove(train))waiting.add(train);else if(!shouldWait&&waiting.remove(train))moving.add(train);}
            validateCustody();
        }finally{ticking=false;}
    }
    private void graphChanged(RailwayGraphManager.Mutation mutation){
        for(var train:List.copyOf(trains.values())){
            if(train.graph==null)continue;if(!train.graph.id.equals(mutation.graph())&&(mutation.other()==null||!train.graph.id.equals(mutation.other())))continue;
            if(mutation.kind()==RailwayGraphManager.Kind.REMOVED||mutation.kind()==RailwayGraphManager.Kind.MERGED||mutation.kind()==RailwayGraphManager.Kind.SPLIT||mutation.kind()==RailwayGraphManager.Kind.NODE_REMOVED)train.detachFromTracks();else train.updateSignalBlocks=true;
        }
        markDirty();
    }
    public CompoundTag writeSnapshot(){
        validateCustody();var tag=new CompoundTag();tag.putInt("TrafficFormatVersion",1);var dimensions=new DimensionPalette();var trainTags=new ListTag();for(var train:trains.values())trainTags.add(train.write(dimensions,server.registryAccess()));tag.put("Trains",trainTags);var groupTags=new ListTag();for(var group:groups.values())if(!group.fallbackGroup||graphs.getTrackNetworks().containsKey(group.id))groupTags.add(group.write());tag.put("SignalBlocks",groupTags);dimensions.write(tag);return tag;
    }
    public void hideRestoring(Train train){
        if(trains.get(train.id)!=train||!train.isRestoring())throw new IllegalArgumentException("Actual registered restoring Train required");
        if(hiddenRestoring.add(train.id))broadcast(List.of(),List.of(train.id));
    }
    public void publish(Train train){
        if(trains.get(train.id)!=train)throw new IllegalArgumentException("Unregistered actual Train description");
        if(train.isRestoring()){hideRestoring(train);return;}
        broadcast(List.of(train),List.of());
    }
    private long nextSequence(){if(sequence==Long.MAX_VALUE)throw new IllegalStateException("Native Train connection sequence exhausted");return ++sequence;}
    private void broadcast(List<Train> additions,List<UUID> removals){var packet=new TrainSyncPacket(epoch,nextSequence(),false,TrainConfig.server(),additions,removals);for(var player:server.getPlayerList().getPlayers())ServerPlayNetworking.send(player,packet);}
    public void sendAll(ServerPlayer player){if(player.level().getServer()!=server)throw new IllegalArgumentException("Foreign actual native Train connection");ServerPlayNetworking.send(player,new TrainSyncPacket(epoch,nextSequence(),true,TrainConfig.server(),List.of(),List.of()));for(var train:trains.values())if(!train.isRestoring())ServerPlayNetworking.send(player,new TrainSyncPacket(epoch,nextSequence(),false,TrainConfig.server(),List.of(train),List.of()));}
    public static void register(){TrainSyncPacket.register();ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->get(server).sendAll(handler.player));ServerLifecycleEvents.SERVER_STOPPED.register(server->SERVERS.remove(server));}
}

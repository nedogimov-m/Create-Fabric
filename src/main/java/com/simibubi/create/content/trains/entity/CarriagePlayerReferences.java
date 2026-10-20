package com.simibubi.create.content.trains.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.NativeSavedDataIdentity;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.phys.Vec3;

/** Global reference-only journal; PlayerList/PlayerData alone own every player and inventory. */
public final class CarriagePlayerReferences extends SavedData {
    public record Token(UUID revision,UUID parent){
        public Token {Objects.requireNonNull(revision);Objects.requireNonNull(parent);}
        public static final Codec<Token> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Revision").forGetter(Token::revision),UUIDUtil.CODEC.fieldOf("Parent").forGetter(Token::parent)).apply(i,Token::new));
    }
    public record Link(UUID player,Token token,UUID body,UUID root,ResourceKey<Level> originDimension,Vec3 origin,Optional<BlockPos> worldSeat,Optional<ResourceKey<Level>> worldDimension){
        public Link {Objects.requireNonNull(player);Objects.requireNonNull(token);Objects.requireNonNull(body);Objects.requireNonNull(root);Objects.requireNonNull(originDimension);Objects.requireNonNull(origin);worldSeat=Objects.requireNonNull(worldSeat).map(BlockPos::immutable);Objects.requireNonNull(worldDimension);if(worldSeat.isPresent()!=worldDimension.isPresent())throw new IllegalArgumentException("An actual restored world seat requires its exact dimension");if(body.equals(root)||body.equals(token.parent())||player.equals(token.parent())||player.equals(root)||player.equals(body)||!Double.isFinite(origin.x)||!Double.isFinite(origin.y)||!Double.isFinite(origin.z))throw new IllegalArgumentException("Ambiguous or non-finite actual nested player relationship");}
        public Link(UUID player,Token token,UUID body,UUID root,ResourceKey<Level> originDimension,Vec3 origin,Optional<BlockPos> worldSeat){this(player,token,body,root,originDimension,origin,worldSeat,worldSeat.map(ignored->originDimension));}
        static final Codec<Link> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Player").forGetter(Link::player),Token.CODEC.fieldOf("Token").forGetter(Link::token),UUIDUtil.CODEC.fieldOf("Body").forGetter(Link::body),UUIDUtil.CODEC.fieldOf("Root").forGetter(Link::root),Level.RESOURCE_KEY_CODEC.fieldOf("OriginDimension").forGetter(Link::originDimension),Vec3.CODEC.fieldOf("Origin").forGetter(Link::origin),BlockPos.CODEC.optionalFieldOf("WorldSeat").forGetter(Link::worldSeat),Level.RESOURCE_KEY_CODEC.optionalFieldOf("WorldDimension").forGetter(Link::worldDimension)).apply(i,Link::new));
    }
    private record ParentOrder(UUID parent,List<UUID> children){
        private ParentOrder {Objects.requireNonNull(parent);children=List.copyOf(children);if(children.size()>CarriagePassengerGraph.MAX_NODES||children.contains(parent)||new HashSet<>(children).size()!=children.size())throw new IllegalArgumentException("Invalid actual parent child ordering");}
        static final Codec<ParentOrder> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Parent").forGetter(ParentOrder::parent),UUIDUtil.CODEC.listOf().fieldOf("Children").forGetter(ParentOrder::children)).apply(i,ParentOrder::new));
    }
    private record Saved(int version,List<Link> links,List<ParentOrder> orders){
        private Saved {if(version!=1)throw new IllegalArgumentException("Unsupported actual player-reference journal version");}
        static final Codec<Saved> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("ReferenceVersion").forGetter(Saved::version),Link.CODEC.listOf().fieldOf("References").forGetter(Saved::links),ParentOrder.CODEC.listOf().fieldOf("ParentOrder").forGetter(Saved::orders)).apply(i,Saved::new));
    }
    public static final Codec<CarriagePlayerReferences> CODEC=Saved.CODEC.xmap(CarriagePlayerReferences::new,data->new Saved(1,List.copyOf(data.links.values()),data.orders.entrySet().stream().map(e->new ParentOrder(e.getKey(),e.getValue())).toList()));
    public static final SavedDataType<CarriagePlayerReferences> TYPE=new SavedDataType<>(Create.asResource("carriage_player_references"),CarriagePlayerReferences::new,CODEC,null);
    private Map<UUID,Link> links=Map.of();private Map<UUID,List<UUID>> orders=Map.of();private ServerLevel ownerLevel;
    private boolean processing;private static boolean registered;
    public CarriagePlayerReferences(){}
    private CarriagePlayerReferences(Saved saved){
        var indexed=new LinkedHashMap<UUID,Link>();var parentOrders=new LinkedHashMap<UUID,List<UUID>>();
        for(var link:saved.links())if(indexed.putIfAbsent(link.player(),link)!=null)throw new IllegalArgumentException("Two relationship claims for one actual player UUID");
        for(var order:saved.orders())if(parentOrders.putIfAbsent(order.parent(),order.children())!=null)throw new IllegalArgumentException("Duplicate native parent child ordering");
        for(var link:indexed.values()){var order=parentOrders.get(link.token().parent());if(order==null||!order.contains(link.player()))throw new IllegalArgumentException("Nested player reference omitted its ordered actual parent edge");}
        links=Map.copyOf(indexed);orders=Map.copyOf(parentOrders);
    }
    public static CarriagePlayerReferences get(MinecraftServer server){var level=server.overworld();var data=level.getDataStorage().computeIfAbsent(TYPE);if(data.ownerLevel!=null&&data.ownerLevel!=level)throw new IllegalStateException("Player relationship journal belongs to one actual server world");data.ownerLevel=level;return data;}
    /** Raw loaded owner only; admission validators must never load or decode this journal. */
    public static CarriagePlayerReferences peekCurrent(MinecraftServer server){var level=server.overworld();var slot=((com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor)level.getDataStorage()).create$getCache().get(TYPE);return slot!=null&&slot.orElse(null) instanceof CarriagePlayerReferences data&&data.current()?data:null;}
    public boolean current(){return ownerLevel!=null&&ownerLevel.getServer().overworld()==ownerLevel&&NativeSavedDataIdentity.isCurrent(ownerLevel,TYPE,this);}
    public Link link(UUID player){return current()?links.get(player):null;}
    public List<Link> links(){return current()?List.copyOf(links.values()):List.of();}
    public static Token token(ServerPlayer player){return ((CarriagePlayerReferenceHolder)player).create$getCarriageReferenceToken();}
    public static void tokenPure(ServerPlayer player,Token token){((CarriagePlayerReferenceHolder)player).create$setCarriageReferenceToken(token);}
    public boolean matches(Link link,ServerPlayer player){return current()&&links.get(link.player())==link&&link.player().equals(player.getUUID())&&ownerLevel.getServer().getPlayerList().getPlayer(player.getUUID())==player&&Objects.equals(token(player),link.token());}
    /** Prepare without callbacks; native edge commit installs the returned immutable maps. */
    public Prepared prepare(List<Link> replacements,Map<UUID,List<UUID>> actualOrders){return prepareRetainingOrder(replacements,actualOrders,Set.of());}
    /** Native restoration keeps the original desired order separate from the one actual mount proof. */
    Prepared prepareRetainingOrder(List<Link> replacements,Map<UUID,List<UUID>> actualOrders,Set<UUID> retainedOrderParents){
        if(!current()||!ownerLevel.getServer().isSameThread())throw new IllegalStateException("Only the current actual server-thread relationship journal can publish references");var next=new LinkedHashMap<>(links);for(var link:replacements)next.put(link.player(),link);var nextOrders=new LinkedHashMap<>(orders);
        var nativeOrders=new LinkedHashMap<UUID,List<UUID>>();
        for(var entry:actualOrders.entrySet()){
            var parent=entry.getKey();var actual=entry.getValue();var proposed=mergeOrder(parent,actual,next);nativeOrders.put(parent,proposed);var original=orders.get(parent);
            boolean retain=retainedOrderParents.contains(parent)&&original!=null&&original.containsAll(actual);
            for(var link:replacements)if(link.token().parent().equals(parent)){var previous=links.get(link.player());if(previous==null||!previous.token().equals(link.token())||!previous.root().equals(link.root()))retain=false;}
            nextOrders.put(parent,retain?original:proposed);
        }
        for(var link:replacements){var order=nextOrders.get(link.token().parent());if(order==null||!order.contains(link.player()))throw new IllegalArgumentException("Prepared actual nested player edge lacks native parent order");}
        var actors=new HashMap<UUID,ServerPlayer>();var offline=new HashSet<UUID>();var oldTokens=new HashMap<UUID,Optional<Token>>();for(var link:replacements){var actor=ownerLevel.getServer().getPlayerList().getPlayer(link.player());if(actor==null){var previous=links.get(link.player());if(previous==null||!previous.token().equals(link.token()))throw new IllegalStateException("Cannot invent a relationship epoch for an offline player");offline.add(link.player());}else{if(actor.isRemoved())throw new IllegalStateException("Prepared relationship has a retired PlayerList actor");actors.put(link.player(),actor);oldTokens.put(link.player(),Optional.ofNullable(token(actor)));}}
        return new Prepared(this,links,orders,Map.copyOf(next),Map.copyOf(nextOrders),Map.copyOf(nativeOrders),List.copyOf(replacements),Map.copyOf(actors),Set.copyOf(offline),Map.copyOf(oldTokens));
    }
    public static final class Prepared {
        private final CarriagePlayerReferences owner;private Map<UUID,Link> previous;private Map<UUID,List<UUID>> previousOrders;private final Map<UUID,Link> next;private final Map<UUID,List<UUID>> nextOrders,nativeOrders;private final List<Link> replacements;private final Map<UUID,ServerPlayer> actors;private final Set<UUID> offline;private Map<UUID,Optional<Token>> previousTokens;private boolean committed;
        private Prepared(CarriagePlayerReferences owner,Map<UUID,Link> previous,Map<UUID,List<UUID>> previousOrders,Map<UUID,Link> next,Map<UUID,List<UUID>> nextOrders,Map<UUID,List<UUID>> nativeOrders,List<Link> replacements,Map<UUID,ServerPlayer> actors,Set<UUID> offline,Map<UUID,Optional<Token>> previousTokens){this.owner=owner;this.previous=previous;this.previousOrders=previousOrders;this.next=next;this.nextOrders=nextOrders;this.nativeOrders=nativeOrders;this.replacements=replacements;this.actors=actors;this.offline=offline;this.previousTokens=previousTokens;}
        public boolean current(){if(committed||!owner.current()||owner.links!=previous||owner.orders!=previousOrders)return false;for(var id:offline)if(owner.ownerLevel.getServer().getPlayerList().getPlayer(id)!=null)return false;for(var entry:actors.entrySet())if(owner.ownerLevel.getServer().getPlayerList().getPlayer(entry.getKey())!=entry.getValue()||!Optional.ofNullable(token(entry.getValue())).equals(previousTokens.get(entry.getKey())))return false;return true;}
        /** Only the exact real native source unlink of this prospective actor can rebase its own preparation. */
        public void afterExactSourceUnlink(Link removed,ServerPlayer actor){
            if(committed||!owner.current()||actors.get(actor.getUUID())!=actor||previous.get(actor.getUUID())!=removed||owner.links.containsKey(actor.getUUID())||owner.links.size()+1!=previous.size()||owner.orders!=previousOrders||token(actor)!=null)return;
            for(var entry:previous.entrySet())if(!entry.getKey().equals(actor.getUUID())&&owner.links.get(entry.getKey())!=entry.getValue())return;previous=owner.links;var updated=new HashMap<>(previousTokens);updated.put(actor.getUUID(),Optional.empty());previousTokens=Map.copyOf(updated);
        }
        /** Caller must supply actual current native parent/child-list proof, never a mount return value. */
        public boolean commitActualEdges(Map<UUID,ServerPlayer> actualPlayers,Map<UUID,Entity> actualParents){
            if(!current())return false;
            for(var link:replacements){
                var parent=actualParents.get(link.token().parent());if(parent==null||!actualOwnerCurrent(link,parent)||!actualOrderMatches(nativeOrders.get(link.token().parent()),parent)||parent.isRemoved()||!(parent.level() instanceof ServerLevel world)||world.getEntity(parent.getUUID())!=parent)return false;
                var previousLink=previous.get(link.player());boolean sameIntent=previousLink!=null&&previousLink.token().equals(link.token());var player=actualPlayers.get(link.player());
                if(offline.contains(link.player())){if(player!=null||!sameIntent)return false;continue;}
                if(player==null||player!=actors.get(link.player())||player.isRemoved()||owner.ownerLevel.getServer().getPlayerList().getPlayer(player.getUUID())!=player)return false;
                boolean actualEdge=player.level()==parent.level()&&world.getEntity(player.getUUID())==player&&player.getVehicle()==parent&&parent.getPassengers().stream().anyMatch(child->child==player);
                boolean retainedPending=sameIntent&&Objects.equals(token(player),link.token())&&player.getVehicle()==null;
                if(!actualEdge&&!retainedPending)return false;
            }
            owner.links=next;owner.orders=nextOrders;owner.setDirty();committed=true;for(var link:replacements){var player=actualPlayers.get(link.player());if(player!=null)tokenPure(player,link.token());}return true;
        }
    }
    private static boolean actualOrderMatches(List<UUID> expected,Entity parent){
        if(expected==null)return false;var actual=parent.getPassengers();int matched=0;for(var id:expected){boolean present=false;for(var child:actual)if(child.getUUID().equals(id)){present=true;break;}if(present){if(matched>=actual.size()||!actual.get(matched++).getUUID().equals(id))return false;}}return matched==actual.size();
    }
    static boolean actualOwnerCurrent(Link link,Entity parent){
        if(parent instanceof net.minecraft.world.entity.player.Player||!parent.getType().canSerialize()||!(parent.level() instanceof ServerLevel level)||!LoadedEntityAccess.isReady(level,parent.blockPosition()))return false;Entity root=parent;int depth=0;while(!root.getUUID().equals(link.root())){if(++depth>CarriagePassengerGraph.MAX_DEPTH||root.getVehicle()==null)return false;root=root.getVehicle();}
        if(level.getEntity(root.getUUID())!=root||root.isRemoved()||!LoadedEntityAccess.isReady(level,root.blockPosition()))return false;
        if(link.worldSeat().isPresent())return link.worldDimension().orElseThrow().equals(level.dimension())&&root.getVehicle() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity seat&&seat.blockPosition().equals(link.worldSeat().get())&&level.getEntity(seat.getUUID())==seat&&!seat.isRemoved()&&LoadedEntityAccess.isReady(level,seat.blockPosition())&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,seat.blockPosition()).getBlock() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
        if(!(root.getVehicle() instanceof CarriageContraptionEntity front)||!front.getUUID().equals(link.body())||level.getEntity(front.getUUID())!=front||front.isRemoved()||!front.hasOwnedStructure()||front.getContraption().getSeatMapping().get(root.getUUID())==null)return false;
        var carriage=front.getCarriage();if(!front.isGloballyManaged())return front.isIndependentNativeOwner();var train=carriage.train;var manager=train==null?null:train.trafficManager();return manager!=null&&manager.isCurrent()&&manager.get(train.id)==train&&train.carriages.contains(carriage);
    }
    private List<UUID> mergeOrder(UUID parent,List<UUID> actual,Map<UUID,Link> retained){
        if(actual.size()>CarriagePassengerGraph.MAX_NODES||new HashSet<>(actual).size()!=actual.size())throw new IllegalArgumentException("Ambiguous actual native child list");var result=new ArrayList<>(actual);var offline=new HashSet<UUID>();for(var link:retained.values())if(link.token().parent().equals(parent)&&!actual.contains(link.player()))offline.add(link.player());var prior=orders.getOrDefault(parent,List.of());
        for(int index=0;index<prior.size();index++){var id=prior.get(index);if(!offline.remove(id))continue;int insert=result.size();for(int next=index+1;next<prior.size();next++){int at=result.indexOf(prior.get(next));if(at>=0){insert=at;break;}}result.add(insert,id);}
        if(!offline.isEmpty())throw new IllegalStateException("An offline player reference has no prior native child order");return List.copyOf(result);
    }
    /** Voluntary actions supersede only this exact retained relationship, never a later revision. */
    public void supersede(Link expected,ServerPlayer actual){
        if(!current()||links.get(expected.player())!=expected)return;var next=new LinkedHashMap<>(links);next.remove(expected.player());links=Map.copyOf(next);setDirty();if(actual!=null&&actual.getUUID().equals(expected.player())&&Objects.equals(token(actual),expected.token()))tokenPure(actual,null);
    }
    /** Capture the real player pose before native logout saves PlayerData or internal detachment. */
    public Link suspended(Link expected,ServerPlayer actual){
        if(!matches(expected,actual)||actual.isRemoved())return null;var updated=new Link(expected.player(),expected.token(),expected.body(),expected.root(),actual.level().dimension(),actual.position(),expected.worldSeat(),expected.worldDimension());var next=new LinkedHashMap<>(links);next.put(updated.player(),updated);links=Map.copyOf(next);setDirty();return updated;
    }
    /** World blocks own the restored seat before the Carriage relinquishes this reference metadata. */
    public boolean worldHandoff(ServerLevel world,CarriageContraptionEntity actualOwner,UUID root,BlockPos actualWorldSeat){
        if(!current()||actualOwner.level()!=world||world.getServer()!=ownerLevel.getServer()||actualOwner.getContraption()==null||actualOwner.getContraption().phase()!=com.simibubi.create.content.contraptions.Contraption.Phase.DISASSEMBLED||actualOwner.getContraption().getStorage().ownsContents()||!LoadedEntityAccess.isReady(world,actualWorldSeat)||!(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(world,actualWorldSeat).getBlock() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatBlock))return false;var body=actualOwner.getUUID();var next=new LinkedHashMap<>(links);boolean changed=false;for(var link:links.values())if(link.body().equals(body)&&link.root().equals(root)){next.put(link.player(),new Link(link.player(),link.token(),link.body(),link.root(),link.originDimension(),link.origin(),Optional.of(actualWorldSeat),Optional.of(world.dimension())));changed=true;}if(changed){links=Map.copyOf(next);setDirty();}return true;
    }
    private static boolean matches(UUID body,UUID root,CarriagePassengerGraph.PlayerReference player,Link link){return link!=null&&link.body().equals(body)&&link.root().equals(root)&&link.worldSeat().isEmpty()&&player.parent().equals(Optional.of(link.token().parent()))&&player.relationship().equals(Optional.of(link.token().revision()));}
    /** Superseded player leaves relinquish only relationship intent. Every NPC material row survives. */
    public CarriagePassengerGraph pruneSuperseded(UUID body,CarriagePassengerGraph graph){
        if(!current())throw new IllegalStateException("Retired relationship journal cannot prune a passenger graph");
        var retained=new ArrayList<CarriagePassengerGraph.Node>();for(var node:graph.nodes())if(!(node instanceof CarriagePassengerGraph.PlayerReference player)||player.parent().isEmpty()||matches(body,graph.root(),player,links.get(player.id())))retained.add(node);
        var nextOrder=new HashMap<UUID,Integer>();var ordered=new ArrayList<CarriagePassengerGraph.Node>();for(var node:retained.stream().sorted(Comparator.comparingInt(CarriagePassengerGraph.Node::order)).toList()){
            int order=node.parent().isEmpty()?0:nextOrder.merge(node.parent().get(),1,Integer::sum)-1;
            if(node instanceof CarriagePassengerGraph.Npc npc)ordered.add(new CarriagePassengerGraph.Npc(npc.id(),npc.parent(),order,npc.type(),npc.payload(),npc.material()));else{var player=(CarriagePassengerGraph.PlayerReference)node;ordered.add(new CarriagePassengerGraph.PlayerReference(player.id(),player.parent(),order,player.relationship()));}
        }
        return new CarriagePassengerGraph(graph.root(),graph.revision(),ordered);
    }
    /** Ordinary native subtree unlink supersedes its relationship claims before old-parent callbacks. */
    public void supersedeBelow(Entity source){
        if(!current())return;var ids=new HashSet<UUID>();source.getSelfAndPassengers().forEach(entity->ids.add(entity.getUUID()));
        for(var link:List.copyOf(links.values()))if(ids.contains(link.token().parent()))supersede(link,ownerLevel.getServer().getPlayerList().getPlayer(link.player()));
    }
    public static void register(){
        if(registered)return;registered=true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->get(server).tick());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{if(entity instanceof CarriageContraptionEntity front)front.resumeIndependentPassengers();});
    }
    /** Only current literal PlayerList actors may fulfill a retained relationship. Never loads chunks. */
    public void tick(){
        if(processing||!current()||links.isEmpty())return;processing=true;
        try{for(var link:List.copyOf(links.values()))restore(link);}finally{processing=false;}
    }
    private void restore(Link expected){
        if(!current()||links.get(expected.player())!=expected)return;var server=ownerLevel.getServer();var player=server.getPlayerList().getPlayer(expected.player());
        if(player==null||player.isRemoved())return;
        if(!matches(expected,player)){supersede(expected,null);return;}
        Entity parent=null;for(var world:server.getAllLevels()){var candidate=world.getEntity(expected.token().parent());if(candidate==null)continue;if(parent!=null&&parent!=candidate)return;parent=candidate;}
        if(parent==null||parent.isRemoved()||!actualOwnerCurrent(expected,parent))return;
        if(player.getVehicle()==parent&&parent.getPassengers().stream().anyMatch(child->child==player)){restoreOrder(expected,parent);return;}
        if(player.getVehicle()!=null||!player.level().dimension().equals(expected.originDimension())||player.position().distanceToSqr(expected.origin())>16){supersede(expected,player);return;}
        var source=player.level();if(source.getEntity(player.getUUID())!=player||!LoadedEntityAccess.isReady(source,player.blockPosition()))return;
        var target=(ServerLevel)parent.level();var destination=parent.position();
        try(var transfer=CarriagePlayerReferenceTransitions.preserve(player,parent)){
            if(!transfer.current())return;
            if(player.level()!=target&&!transfer.teleport(target,destination))return;
            if(!transfer.current()||player.level()!=target||target.getEntity(player.getUUID())!=player||parent.isRemoved()||target.getEntity(parent.getUUID())!=parent||!actualOwnerCurrent(transfer.reference(),parent)||!LoadedEntityAccess.isReady(target,parent.blockPosition()))return;
            player.startRiding(parent,true,true);
            var retained=link(player.getUUID());if(retained!=null&&matches(retained,player)&&player.getVehicle()==parent)restoreOrder(retained,parent);
        }
    }
    /** Reorders the same actual children only after every genuine native mount and exact set proof. */
    private void restoreOrder(Link witness,Entity parent){
        if(!current()||links.get(witness.player())!=witness||!actualOwnerCurrent(witness,parent)||!(parent.level() instanceof ServerLevel level)||level.getEntity(parent.getUUID())!=parent)return;
        var expected=orders.get(witness.token().parent());if(expected==null)return;var actual=List.copyOf(parent.getPassengers());var children=new HashMap<UUID,Entity>();
        for(var child:actual){if(child.isRemoved()||child.getVehicle()!=parent||child.level()!=level||level.getEntity(child.getUUID())!=child||children.putIfAbsent(child.getUUID(),child)!=null||!expected.contains(child.getUUID()))return;if(child instanceof ServerPlayer player){var link=links.get(child.getUUID());if(link==null||!matches(link,player)||!link.token().parent().equals(parent.getUUID())||!link.body().equals(witness.body())||!link.root().equals(witness.root())||!link.worldSeat().equals(witness.worldSeat())||!link.worldDimension().equals(witness.worldDimension()))return;}}
        var ordered=new ArrayList<Entity>();for(var id:expected){var actualChild=children.get(id);if(actualChild!=null)ordered.add(actualChild);}if(ordered.size()!=actual.size())return;
        boolean same=true;for(int index=0;index<actual.size();index++)same&=actual.get(index)==ordered.get(index);if(same)return;
        if(!current()||links.get(witness.player())!=witness||!actualOwnerCurrent(witness,parent)||!sameObjects(parent.getPassengers(),actual))return;
        ((com.simibubi.create.foundation.mixin.accessor.EntityPassengersAccessor)parent).create$setActualPassengerOrder(com.google.common.collect.ImmutableList.copyOf(ordered));
        level.getChunkSource().sendToTrackingPlayersAndSelf(parent,new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(parent));
    }
    private static boolean sameObjects(List<Entity> left,List<Entity> right){if(left.size()!=right.size())return false;for(int index=0;index<left.size();index++)if(left.get(index)!=right.get(index))return false;return true;}
    public void supersedeParents(UUID body,UUID root,Set<UUID> parents){if(!current())return;for(var link:List.copyOf(links.values()))if(link.body().equals(body)&&link.root().equals(root)&&parents.contains(link.token().parent()))supersede(link,ownerLevel.getServer().getPlayerList().getPlayer(link.player()));}
    public void supersedeRoot(UUID body,UUID root){if(!current())return;for(var link:List.copyOf(links.values()))if(link.body().equals(body)&&link.root().equals(root))supersede(link,ownerLevel.getServer().getPlayerList().getPlayer(link.player()));}
    public List<UUID> orderedChildren(UUID parent){return current()?orders.getOrDefault(parent,List.of()):List.of();}
    /** Exact current metadata only; no actual player object is retained for offline UUIDs. */
    public List<Link> forRoot(UUID body,UUID root){return current()?links.values().stream().filter(link->link.body().equals(body)&&link.root().equals(root)).toList():List.of();}
    /** Include offline/pending player leaves without encoding or resolving any player entity. */
    public CarriagePassengerGraph bindReferences(UUID body,CarriagePassengerGraph graph){
        if(!current())throw new IllegalStateException("Retired relationship journal cannot bind a passenger snapshot");var rows=new LinkedHashMap<UUID,CarriagePassengerGraph.Node>();for(var node:graph.nodes())rows.put(node.id(),node);var byParent=new LinkedHashMap<UUID,List<Link>>();
        for(var link:links.values())if(link.body().equals(body)&&link.root().equals(graph.root())&&link.worldSeat().isEmpty()){
            if(!(rows.get(link.token().parent()) instanceof CarriagePassengerGraph.Npc))throw new IllegalStateException("Reference parent is absent from the exact captured NPC root");var old=rows.get(link.player());if(old!=null&&(!(old instanceof CarriagePassengerGraph.PlayerReference)||!old.parent().equals(Optional.of(link.token().parent()))))throw new IllegalStateException("Player reference changed actual parent or native role");byParent.computeIfAbsent(link.token().parent(),ignored->new ArrayList<>()).add(link);
        }
        for(var entry:byParent.entrySet()){
            var live=graph.children(entry.getKey()).stream().map(CarriagePassengerGraph.Node::id).toList();var order=mergeOrder(entry.getKey(),live,links);for(var link:entry.getValue())rows.put(link.player(),new CarriagePassengerGraph.PlayerReference(link.player(),Optional.of(link.token().parent()),order.indexOf(link.player()),Optional.of(link.token().revision())));
            for(var id:order){var node=rows.get(id);if(node==null)throw new IllegalStateException("Native child order names an absent NPC/player node");int index=order.indexOf(id);if(node instanceof CarriagePassengerGraph.Npc npc)rows.put(id,new CarriagePassengerGraph.Npc(npc.id(),npc.parent(),index,npc.type(),npc.payload(),npc.material()));else{var player=(CarriagePassengerGraph.PlayerReference)node;rows.put(id,new CarriagePassengerGraph.PlayerReference(player.id(),player.parent(),index,player.relationship()));}}
        }
        for(var node:rows.values())if(node instanceof CarriagePassengerGraph.PlayerReference player&&player.parent().isPresent()){
            var link=links.get(player.id());if(!matches(body,graph.root(),player,link))throw new IllegalStateException("Actual nested player snapshot has no exact current body/root/parent/epoch relationship");
        }
        return new CarriagePassengerGraph(graph.root(),graph.revision(),List.copyOf(rows.values()));
    }
}

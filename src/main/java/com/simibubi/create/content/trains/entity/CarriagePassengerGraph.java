package com.simibubi.create.content.trains.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/** Versioned ordered NPC payloads and player relationship references; no player entity data. */
public final class CarriagePassengerGraph {
    public static final int VERSION=1,MAX_NODES=1024,MAX_DEPTH=64;
    public static final Codec<CarriagePassengerGraph> CODEC=CompoundTag.CODEC.comapFlatMap(tag->{try{return DataResult.success(read(tag));}catch(IllegalArgumentException failure){return DataResult.error(failure::getMessage);}},CarriagePassengerGraph::write);
    public sealed interface Node permits Npc,PlayerReference {
        UUID id();Optional<UUID> parent();int order();
    }
    public enum Material { SOURCE_LIVE, PENDING, ADMITTED, CONSUMED }
    public record Npc(UUID id,Optional<UUID> parent,int order,Identifier type,Optional<CompoundTag> payload,Material material) implements Node {
        public Npc {Objects.requireNonNull(id);Objects.requireNonNull(parent);Objects.requireNonNull(type);Objects.requireNonNull(material);payload=Objects.requireNonNull(payload).map(CompoundTag::copy);if(((material==Material.ADMITTED||material==Material.CONSUMED)==payload.isPresent()))throw invalid("Only pending/source-live NPC rows have a native payload; admitted nodes cannot retain a second material copy");}
        public Npc(UUID id,Optional<UUID> parent,int order,Identifier type,Optional<CompoundTag> payload){this(id,parent,order,type,payload,payload.isPresent()?Material.PENDING:Material.ADMITTED);}
        @Override public Optional<CompoundTag> payload(){return payload.map(CompoundTag::copy);}
    }
    public record PlayerReference(UUID id,Optional<UUID> parent,int order,Optional<UUID> relationship) implements Node {
        public PlayerReference {Objects.requireNonNull(id);Objects.requireNonNull(parent);Objects.requireNonNull(relationship);}
        public PlayerReference(UUID id,Optional<UUID> parent,int order){this(id,parent,order,Optional.empty());}
    }
    private final UUID root,revision;
    private final List<Node> nodes;
    private final Map<UUID,Node> byId;
    private final Map<UUID,List<Node>> children;
    public CarriagePassengerGraph(UUID root,UUID revision,List<? extends Node> nodes){
        this.root=Objects.requireNonNull(root);this.revision=Objects.requireNonNull(revision);this.nodes=List.copyOf(nodes);
        if(nodes.isEmpty()||nodes.size()>MAX_NODES)throw invalid("Passenger graph node bound");
        var indexed=new LinkedHashMap<UUID,Node>();var childLists=new HashMap<UUID,List<Node>>();int roots=0;
        for(var node:this.nodes){
            if(node.order()<0||indexed.putIfAbsent(node.id(),node)!=null)throw invalid("Duplicate UUID or negative native child order");
            if(node.parent().isEmpty()){if(!node.id().equals(root)||node.order()!=0)throw invalid("Invalid graph root");roots++;}
            else childLists.computeIfAbsent(node.parent().get(),ignored->new ArrayList<>()).add(node);
            if(node instanceof Npc npc){
                var nativeType=BuiltInRegistries.ENTITY_TYPE.getOptional(npc.type()).orElseThrow(()->invalid("Unknown NPC type: "+npc.type()));
                if(!nativeType.canSerialize())throw invalid("A non-serializable native player type cannot own an NPC payload");
                if(npc.payload.isPresent()){
                    var payload=npc.payload.get();
                    if(payload.contains("Passengers")||payload.contains("PlayerPassenger")||!npc.id().equals(uuid(payload,"UUID"))||!payload.getString("id").orElseThrow(()->invalid("NPC native id absent")).equals(npc.type().toString()))throw invalid("Flat native NPC payload does not match its exact identity/type");
                }
            }
        }
        if(roots!=1||!indexed.containsKey(root))throw invalid("Exactly one actual graph root required");
        for(var entry:childLists.entrySet()){
            var parent=indexed.get(entry.getKey());if(parent==null||parent instanceof PlayerReference)throw invalid("Unknown parent or native-refused child below a player");
            entry.getValue().sort(Comparator.comparingInt(Node::order));
            for(int order=0;order<entry.getValue().size();order++)if(entry.getValue().get(order).order()!=order)throw invalid("Duplicate or missing native sibling order");
            entry.setValue(List.copyOf(entry.getValue()));
        }
        record Visit(UUID id,int depth){}
        var pending=new ArrayDeque<Visit>();var reached=new HashSet<UUID>();pending.add(new Visit(root,0));
        while(!pending.isEmpty()){var visit=pending.removeFirst();if(visit.depth()>MAX_DEPTH||!reached.add(visit.id()))throw invalid("Cycle or excessive passenger depth");for(var child:childLists.getOrDefault(visit.id(),List.of()))pending.addLast(new Visit(child.id(),visit.depth()+1));}
        if(reached.size()!=nodes.size())throw invalid("Disconnected or cyclic native passenger parent edges");
        this.byId=Map.copyOf(indexed);this.children=Map.copyOf(childLists);
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException(reason);}
    private static UUID uuid(CompoundTag tag,String key){return tag.read(key,UUIDUtil.CODEC).orElseThrow(()->invalid("Missing or invalid passenger UUID: "+key));}
    public UUID root(){return root;}public UUID revision(){return revision;}public List<Node> nodes(){return nodes;}
    public Node node(UUID id){return byId.get(id);}public Set<UUID> identities(){return byId.keySet();}
    public List<Node> children(UUID id){return children.getOrDefault(id,List.of());}
    public Set<UUID> players(){var result=new HashSet<UUID>();for(var node:nodes)if(node instanceof PlayerReference)result.add(node.id());return Set.copyOf(result);}
    public void requireDisjointBodies(Set<UUID> bodies){if(!Collections.disjoint(bodies,byId.keySet()))throw invalid("A native body cannot also be a graph passenger or player reference");}
    /** Prepare off-world; the actual native admission commit only installs this prepared graph. */
    public CarriagePassengerGraph afterNpcAdmission(UUID id){
        if(!(byId.get(id) instanceof Npc npc)||npc.material()!=Material.PENDING||npc.payload.isEmpty())throw invalid("Only a genuinely pending NPC payload can be revoked after actual admission");
        var prepared=new ArrayList<Node>(nodes.size());for(var node:nodes)prepared.add(node==npc?new Npc(npc.id(),npc.parent(),npc.order(),npc.type(),Optional.empty(),Material.ADMITTED):node);return new CarriagePassengerGraph(root,revision,prepared);
    }
    public CarriagePassengerGraph replaceNpc(UUID id,Material material,Optional<CompoundTag> payload){
        if(!(byId.get(id) instanceof Npc original))throw invalid("Exact actual NPC graph node required");var next=new ArrayList<Node>();for(var node:nodes)next.add(node==original?new Npc(original.id(),original.parent(),original.order(),original.type(),payload,material):node);return new CarriagePassengerGraph(root,revision,next);
    }
    /** A fresh native owner reads material snapshots only after its genuine admission. Live UUID guards still apply. */
    public CarriagePassengerGraph forNativeReload(){var next=new ArrayList<Node>();for(var node:nodes)next.add(node instanceof Npc npc&&npc.material()==Material.SOURCE_LIVE?new Npc(npc.id(),npc.parent(),npc.order(),npc.type(),npc.payload(),Material.PENDING):node);return new CarriagePassengerGraph(root,revision,next);}
    public boolean hasPendingNpc(){return nodes.stream().anyMatch(node->node instanceof Npc npc&&npc.material()==Material.PENDING);}
    public CompoundTag write(){
        var output=new CompoundTag();output.putInt("GraphVersion",VERSION);output.store("Root",UUIDUtil.CODEC,root);output.store("Revision",UUIDUtil.CODEC,revision);var values=new ListTag();
        for(var node:nodes){var row=new CompoundTag();row.store("UUID",UUIDUtil.CODEC,node.id());node.parent().ifPresent(id->row.store("Parent",UUIDUtil.CODEC,id));row.putInt("Order",node.order());
            if(node instanceof PlayerReference player){row.putString("Kind","player");player.relationship().ifPresent(id->row.store("Reference",UUIDUtil.CODEC,id));}else{var npc=(Npc)node;row.putString("Kind","npc");row.putString("Type",npc.type().toString());row.putString("Material",npc.material().name());npc.payload().ifPresent(payload->row.put("Payload",payload));}values.add(row);}
        output.put("Nodes",values);return output;
    }
    public static CarriagePassengerGraph read(CompoundTag input){
        if(!input.keySet().equals(Set.of("GraphVersion","Root","Revision","Nodes"))||input.getIntOr("GraphVersion",-1)!=VERSION)throw invalid("Unknown or malformed native passenger graph version");
        var root=uuid(input,"Root");var revision=uuid(input,"Revision");var rows=input.getList("Nodes").orElseThrow(()->invalid("Graph node list absent"));if(rows.isEmpty()||rows.size()>MAX_NODES)throw invalid("Graph node bound");var nodes=new ArrayList<Node>();
        for(var tag:rows){if(!(tag instanceof CompoundTag row))throw invalid("Graph node must be a compound");var id=uuid(row,"UUID");Optional<UUID> parent=row.contains("Parent")?Optional.of(uuid(row,"Parent")):Optional.empty();int order=row.getInt("Order").orElseThrow(()->invalid("Graph child order absent"));var keys=new HashSet<>(row.keySet());keys.removeAll(Set.of("UUID","Parent","Order","Kind"));
            switch(row.getString("Kind").orElseThrow(()->invalid("Graph role absent"))){
                case "player" -> {keys.remove("Reference");if(!keys.isEmpty())throw invalid("Player reference cannot contain entity NBT, inventory or an NPC type");Optional<UUID> relationship=row.contains("Reference")?Optional.of(uuid(row,"Reference")):Optional.empty();nodes.add(new PlayerReference(id,parent,order,relationship));}
                case "npc" -> {keys.removeAll(Set.of("Type","Payload","Material"));if(!keys.isEmpty())throw invalid("Unknown NPC graph row fields");var type=row.read("Type",Identifier.CODEC).orElseThrow(()->invalid("NPC type absent or invalid"));Optional<CompoundTag> payload=row.contains("Payload")?Optional.of(row.getCompound("Payload").orElseThrow(()->invalid("NPC payload is not a compound"))):Optional.empty();Material material;try{material=row.contains("Material")?Material.valueOf(row.getString("Material").orElseThrow()):payload.isPresent()?Material.PENDING:Material.ADMITTED;}catch(RuntimeException failure){throw invalid("Unknown NPC material lifecycle state");}nodes.add(new Npc(id,parent,order,type,payload,material));}
                default -> throw invalid("Unknown player/NPC graph role");
            }
        }
        return new CarriagePassengerGraph(root,revision,nodes);
    }
    /** Pure migration from the existing native NPC tree/direct-player format; never creates an entity. */
    public static CarriagePassengerGraph fromLegacy(CompoundTag saved,UUID revision){
        if(saved.contains("GraphVersion"))return read(saved);
        if(saved.contains("PlayerPassenger")){if(!saved.keySet().equals(Set.of("PlayerPassenger")))throw invalid("Legacy direct player reference contains native entity data");var id=uuid(saved,"PlayerPassenger");return new CarriagePassengerGraph(id,revision,List.of(new PlayerReference(id,Optional.empty(),0)));}
        record Saved(CompoundTag data,Optional<UUID> parent,int order,int depth){}
        var pending=new ArrayDeque<Saved>();var nodes=new ArrayList<Node>();pending.add(new Saved(saved,Optional.empty(),0,0));var root=uuid(saved,"UUID");
        while(!pending.isEmpty()){
            var entry=pending.removeFirst();if(entry.depth()>MAX_DEPTH||nodes.size()>=MAX_NODES)throw invalid("Legacy passenger tree exceeds native graph bounds");var id=uuid(entry.data(),"UUID");var type=entry.data().read("id",Identifier.CODEC).orElseThrow(()->invalid("Legacy NPC native id absent or invalid"));var payload=entry.data().copy();payload.remove("Passengers");nodes.add(new Npc(id,entry.parent(),entry.order(),type,Optional.of(payload)));int order=0;
            if(entry.data().contains("Passengers"))for(var child:entry.data().getList("Passengers").orElseThrow(()->invalid("Legacy native passengers are not a list"))){if(!(child instanceof CompoundTag childData))throw invalid("Legacy NPC child is not a compound");pending.addLast(new Saved(childData,Optional.of(id),order++,entry.depth()+1));}
        }
        return new CarriagePassengerGraph(root,revision,nodes);
    }
}

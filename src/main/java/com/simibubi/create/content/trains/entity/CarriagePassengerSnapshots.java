package com.simibubi.create.content.trains.entity;

import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;

/** Read-only exact-node native snapshots. Original world entities, mounts and inventories stay authoritative. */
public final class CarriagePassengerSnapshots {
    private static final ThreadLocal<Frame> ACTIVE=new ThreadLocal<>();
    private static final class Frame {final Entity entity;int nativeDepth;Frame(Entity entity){this.entity=entity;}}
    public static final class NativeSaveScope implements AutoCloseable {
        private static final NativeSaveScope NONE=new NativeSaveScope(null);private final Frame frame;
        private NativeSaveScope(Frame frame){this.frame=frame;}
        @Override public void close(){if(frame!=null)frame.nativeDepth--;}
    }
    /** Called by the real saveWithoutId wrapper, including reentrant serializations of the same node. */
    public static NativeSaveScope enterNativeSave(Entity entity){var frame=ACTIVE.get();if(frame==null||frame.entity!=entity)return NativeSaveScope.NONE;frame.nativeDepth++;return new NativeSaveScope(frame);}
    /** Only the outer exact native NPC snapshot omits child traversal; other saves retain vanilla lists. */
    public static List<Entity> nativeSavePassengers(Entity entity,List<Entity> actual){if(entity instanceof CarriageContraptionEntity front&&front.ownsNativePassengerGraph())return List.of();var frame=ACTIVE.get();return frame!=null&&frame.entity==entity&&frame.nativeDepth==1?List.of():actual;}
    private record Actual(Entity entity,UUID id,int entityId,Entity parent,List<Entity> children){}
    public static final class Capture {
        private final CarriagePassengerGraph graph;private final ServerLevel level;private final Entity rootParent;private final List<Actual> originals;
        private Capture(CarriagePassengerGraph graph,ServerLevel level,Entity rootParent,List<Actual> originals){this.graph=graph;this.level=level;this.rootParent=rootParent;this.originals=List.copyOf(originals);}
        public CarriagePassengerGraph graph(){return graph;}
        /** Pure current native identity/topology witness, not a claim that a detached snapshot owns material. */
        public boolean current(){
            for(var original:originals){var entity=original.entity();if(entity.level()!=level||entity.isRemoved()||entity.getId()!=original.entityId()||!entity.getUUID().equals(original.id())||!CarriageNativeSources.current(entity)||entity.getVehicle()!=(entity==originals.getFirst().entity()?rootParent:original.parent())||!sameObjects(entity.getPassengers(),original.children()))return false;if(entity instanceof ServerPlayer player&&level.getServer().getPlayerList().getPlayer(player.getUUID())!=player)return false;}
            return true;
        }
        private static boolean sameObjects(List<Entity> current,List<Entity> expected){if(current.size()!=expected.size())return false;for(int i=0;i<current.size();i++)if(current.get(i)!=expected.get(i))return false;return true;}
    }
    public static Capture capture(Entity root,UUID revision){
        if(!(root.level() instanceof ServerLevel level)||!level.getServer().isSameThread()||ACTIVE.get()!=null)throw new IllegalStateException("Exact original server-thread passenger snapshot required");
        record Visit(Entity entity,Entity parent,int order,int depth){}
        var pending=new ArrayDeque<Visit>();var identities=new HashSet<UUID>();var originals=new ArrayList<Actual>();var nodes=new ArrayList<CarriagePassengerGraph.Node>();pending.add(new Visit(root,null,0,0));var rootParent=root.getVehicle();
        while(!pending.isEmpty()){
            var visit=pending.removeFirst();var actual=visit.entity();if(visit.parent()!=null&&actual.getVehicle()!=visit.parent())throw new IllegalStateException("Native child parent edge is not its actual current vehicle");Optional<UUID> parent=visit.parent()==null?Optional.empty():Optional.of(visit.parent().getUUID());boolean duplicateBefore=identities.contains(actual.getUUID());if(visit.depth()>CarriagePassengerGraph.MAX_DEPTH||nodes.size()>=CarriagePassengerGraph.MAX_NODES||!identities.add(actual.getUUID())||actual.level()!=level||actual.isRemoved()||!CarriageNativeSources.current(actual))throw captureFailure(actual,level,visit.parent()==null?rootParent:visit.parent(),visit.depth(),nodes.size(),duplicateBefore);
            var children=List.copyOf(actual.getPassengers());originals.add(new Actual(actual,actual.getUUID(),actual.getId(),actual.getVehicle(),children));
            if(actual instanceof Player){if(!(actual instanceof ServerPlayer player)||level.getServer().getPlayerList().getPlayer(player.getUUID())!=player||!children.isEmpty())throw new IllegalStateException("Only literal current native player leaves have a graph reference");nodes.add(new CarriagePassengerGraph.PlayerReference(actual.getUUID(),parent,visit.order()));}
            else{
                nodes.add(new CarriagePassengerGraph.Npc(actual.getUUID(),parent,visit.order(),net.minecraft.world.entity.EntityType.getKey(actual.getType()),Optional.of(captureNpc(actual)),CarriagePassengerGraph.Material.SOURCE_LIVE));
            }
            for(int order=0;order<children.size();order++)pending.addLast(new Visit(children.get(order),actual,order,visit.depth()+1));
        }
        var captured=new Capture(new CarriagePassengerGraph(root.getUUID(),revision,nodes),level,rootParent,originals);if(!captured.current())throw new IllegalStateException("Original native passenger tree changed while its genuine serializers ran");return captured;
    }
    /** Extra observations run only after the original guard failed; they cannot authorize capture or replace its failure. */
    private static IllegalStateException captureFailure(Entity actual,ServerLevel expectedLevel,Entity expectedParent,int depth,int size,boolean duplicateBefore){
        String original="Original passenger identities/readiness changed during native capture";
        try{
            String predicate=depth>CarriagePassengerGraph.MAX_DEPTH?"depth":size>=CarriagePassengerGraph.MAX_NODES?"size":duplicateBefore?"duplicateUUID":actual.level()!=expectedLevel?"level":actual.isRemoved()?"removed":"nativeCurrent";
            var actualLevel=actual.level() instanceof ServerLevel server?server:null;
            var lookup=actualLevel==null?null:actualLevel.getEntity(actual.getUUID());
            var expectedLookup=expectedLevel.getEntity(actual.getUUID());
            var registered=expectedLevel.getServer().getPlayerList().getPlayer(actual.getUUID());
            return new IllegalStateException(original+": failed="+predicate+", actor="+describe(actual)+", expectedParent="+describe(expectedParent)+", currentParent="+describe(actual.getVehicle())+", parentSame="+(actual.getVehicle()==expectedParent)+", expectedLevel="+expectedLevel.dimension().identifier()+", actualLevel="+actual.level().dimension().identifier()+", actualLookup="+describe(lookup)+", actualLookupSame="+(lookup==actual)+", expectedLookup="+describe(expectedLookup)+", expectedLookupSame="+(expectedLookup==actual)+", nativeReady="+(actualLevel!=null&&LoadedEntityAccess.isReady(actualLevel,actual.blockPosition()))+", nativeCurrent="+CarriageNativeSources.current(actual)+", playerListSame="+(registered==actual)+", depth="+depth+", maxDepth="+CarriagePassengerGraph.MAX_DEPTH+", size="+size+", maxNodes="+CarriagePassengerGraph.MAX_NODES+", duplicateBefore="+duplicateBefore+", removed="+actual.isRemoved()+", removalReason="+actual.getRemovalReason()+", position="+actual.position()+", block="+actual.blockPosition());
        }catch(RuntimeException|Error observationFailure){
            var failure=new IllegalStateException(original);failure.addSuppressed(observationFailure);return failure;
        }
    }
    private static String describe(Entity actual){return actual==null?"null":net.minecraft.world.entity.EntityType.getKey(actual.getType())+"/"+actual.getUUID()+"/nativeId="+actual.getId();}
    public static boolean active(Entity entity){var frame=ACTIVE.get();return frame!=null&&frame.entity==entity;}
    public static boolean inProgress(){return ACTIVE.get()!=null;}
    /** Genuine native exact-node serializer, with no temporary detach or manually copied vanilla fields. */
    public static net.minecraft.nbt.CompoundTag captureNpc(Entity actual){
        if(actual instanceof Player||!actual.getType().canSerialize()||ACTIVE.get()!=null)throw new IllegalStateException("One actual serializable native NPC snapshot required");
        var problems=new ProblemReporter.Collector(actual.problemPath());var output=TagValueOutput.createWithContext(problems,actual.registryAccess());boolean saved;
        ACTIVE.set(new Frame(actual));try{saved=actual.saveAsPassenger(output);}finally{ACTIVE.remove();}
        var payload=output.buildResult();if(!saved||!problems.isEmpty()||payload.contains("Passengers")||!actual.getUUID().equals(payload.read("UUID",UUIDUtil.CODEC).orElse(null)))throw new IllegalStateException("Actual flat native NPC serializer refused or omitted identity: "+problems.getReport());return payload;
    }
    private CarriagePassengerSnapshots(){}
}

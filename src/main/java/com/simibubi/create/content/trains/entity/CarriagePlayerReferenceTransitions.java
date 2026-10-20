package com.simibubi.create.content.trains.entity;

import com.simibubi.create.foundation.mixin.accessor.EntityVehicleAccessor;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Prepared relationship publication at the actual native immutable passenger-list assignment. */
public final class CarriagePlayerReferenceTransitions {
    private static final ThreadLocal<ArrayDeque<SeatScope>> SEATS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<MountScope>> MOUNTS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<PreserveScope>> PRESERVE=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<RootScope>> ROOTS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<NativeDetachScope>> DETACH=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<NativeDimensionScope>> DIMENSIONS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<NativeDismountScope>> DISMOUNTS=ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean sameObjects(List<Entity> left,List<Entity> right){if(left.size()!=right.size())return false;for(int i=0;i<left.size();i++)if(left.get(i)!=right.get(i))return false;return true;}
    public static final class SeatScope implements AutoCloseable {
        final CarriageContraptionEntity front;final Entity root;final int seat;
        private SeatScope(CarriageContraptionEntity front,Entity root,int seat){this.front=front;this.root=root;this.seat=seat;SEATS.get().push(this);}
        @Override public void close(){if(SEATS.get().pop()!=this)throw new IllegalStateException("Actual prepared seat scopes must close in native call order");if(SEATS.get().isEmpty())SEATS.remove();}
    }
    public static SeatScope prepareSeat(CarriageContraptionEntity front,Entity root,int seat){return new SeatScope(front,root,seat);}
    private static SeatScope seat(CarriageContraptionEntity front,Entity root){for(var scope:SEATS.get())if(scope.front==front&&scope.root==root)return scope;return null;}
    static boolean preparedSeatIndex(CarriageContraptionEntity front,Entity root,int expected){var prepared=seat(front,root);return prepared!=null?prepared.seat==expected:Objects.equals(front.getContraption().getSeatMapping().get(root.getUUID()),expected);}
    public static boolean hasPreparedSeat(CarriageContraptionEntity front,Entity root){return seat(front,root)!=null||front.getContraption()!=null&&front.getContraption().getSeatMapping().containsKey(root.getUUID());}
    static boolean frontCurrent(CarriageContraptionEntity front){
        if(!(front.level() instanceof ServerLevel level)||front.isRemoved()||!CarriageNativeSources.current(front)||!front.hasOwnedStructure())return false;var carriage=front.getCarriage();if(!front.isGloballyManaged())return front.isIndependentNativeOwner();var train=carriage.train;var manager=train==null?null:train.trafficManager();return manager!=null&&manager.isCurrent()&&manager.get(train.id)==train&&train.carriages.contains(carriage);
    }
    private record Original(Entity actual,Entity parent,UUID id,int nativeId,List<Entity> children){}
    public static final class MountScope implements AutoCloseable {
        final Entity passenger,parent;final CarriageContraptionEntity front;final Entity root;final SeatScope seat;final List<Entity> parentChildren;final List<Original> originals;final Map<UUID,Integer> seatBefore,seatAfter;final CarriagePlayerReferences data;final CarriagePlayerReferences.Prepared prepared;final Map<UUID,ServerPlayer> players;final Map<UUID,Entity> parents;boolean refused,adopted,sourceUnlinked;
        private MountScope(Entity passenger,Entity parent){
            this.passenger=passenger;this.parent=parent;front=!passenger.level().isClientSide()&&parent!=null&&parent.getRootVehicle() instanceof CarriageContraptionEntity carrier?carrier:null;
            seat=front==parent?seat(front,passenger):null;root=front==null?null:parent==front?passenger:rootBelow(parent,front);parentChildren=parent==null?List.of():List.copyOf(parent.getPassengers());
            if(front==null||!(front.level() instanceof ServerLevel level)||!frontCurrent(front)||!CarriagePassengerMountGuard.mayMount(passenger,parent)){refused=front!=null;data=passenger instanceof ServerPlayer player?CarriagePlayerReferences.peekCurrent(player.level().getServer()):null;prepared=null;originals=List.of();players=Map.of();parents=Map.of();seatBefore=Map.of();seatAfter=Map.of();MOUNTS.get().push(this);return;}
            seatBefore=Map.copyOf(front.getContraption().getSeatMapping());var after=new HashMap<>(seatBefore);if(seat!=null&&seat.seat>=0&&seat.seat<front.getContraption().getSeats().size()){after.entrySet().removeIf(e->e.getValue()==seat.seat&&!e.getKey().equals(passenger.getUUID()));after.put(passenger.getUUID(),seat.seat);}seatAfter=Map.copyOf(after);
            var source=new ArrayList<Original>();var seen=new HashSet<UUID>();record Visit(Entity entity,int depth){}var pending=new ArrayDeque<Visit>();int parentDepth=0;if(parent!=front){for(var at=parent;at.getVehicle()!=front;at=at.getVehicle())if(++parentDepth>CarriagePassengerGraph.MAX_DEPTH)throw new IllegalStateException("Target native passenger depth exceeds graph bounds");parentDepth++;}pending.add(new Visit(passenger,parentDepth));var actualPlayers=new HashMap<UUID,ServerPlayer>();var actualParents=new HashMap<UUID,Entity>();var npcIds=new HashSet<UUID>();
            while(!pending.isEmpty()){
                var visit=pending.removeFirst();var actual=visit.entity();if(visit.depth()>CarriagePassengerGraph.MAX_DEPTH)throw new IllegalStateException("Native passenger subtree exceeds graph depth bounds");if(seen.size()>=CarriagePassengerGraph.MAX_NODES||!seen.add(actual.getUUID())||actual.level()!=level||actual.isRemoved()||level.getEntity(actual.getUUID())!=actual||!LoadedEntityAccess.isReady(level,actual.blockPosition()))throw new IllegalStateException("Prepared native passenger source identity/readiness changed");var children=List.copyOf(actual.getPassengers());source.add(new Original(actual,actual.getVehicle(),actual.getUUID(),actual.getId(),children));
                if(actual instanceof Player){if(!(actual instanceof ServerPlayer player)||level.getServer().getPlayerList().getPlayer(player.getUUID())!=player||!children.isEmpty())throw new IllegalStateException("Actual current native player leaf required");actualPlayers.put(actual.getUUID(),player);}else{if(!actual.getType().canSerialize())throw new IllegalStateException("Native source passenger cannot be saved");npcIds.add(actual.getUUID());actualParents.put(actual.getUUID(),actual);}for(var child:children)pending.addLast(new Visit(child,visit.depth()+1));
            }
            originals=List.copyOf(source);data=CarriagePlayerReferences.get(level.getServer());var replacements=new LinkedHashMap<UUID,CarriagePlayerReferences.Link>();
            // Equal canonical Links retain identity so another queued literal player is not skipped in this native tick.
            for(var existing:data.links())if(npcIds.contains(existing.token().parent())||parent!=front&&existing.token().parent().equals(parent.getUUID())&&existing.body().equals(front.getUUID())&&existing.root().equals(root.getUUID())){
                var replacement=new CarriagePlayerReferences.Link(existing.player(),existing.token(),front.getUUID(),root.getUUID(),existing.originDimension(),existing.origin(),Optional.empty());replacements.put(existing.player(),replacement.equals(existing)?existing:replacement);
            }
            if(parent!=front)actualParents.put(parent.getUUID(),parent);
            for(var actor:actualPlayers.values()){
                Entity actualParent=actor==passenger?parent:actor.getVehicle();if(actualParent==front)continue;var existing=data.link(actor.getUUID());boolean preserved=actor!=passenger||preserving(actor,actualParent);var token=existing!=null&&preserved&&existing.token().parent().equals(actualParent.getUUID())&&Objects.equals(CarriagePlayerReferences.token(actor),existing.token())?existing.token():new CarriagePlayerReferences.Token(UUID.randomUUID(),actualParent.getUUID());var replacement=new CarriagePlayerReferences.Link(actor.getUUID(),token,front.getUUID(),root.getUUID(),level.dimension(),actor.position(),Optional.empty());replacements.put(actor.getUUID(),replacement.equals(existing)?existing:replacement);actualParents.put(actualParent.getUUID(),actualParent);
            }
            boolean restoring=preservingRootMount(passenger,parent)||passenger instanceof ServerPlayer player&&preserving(player,parent);var retainedOrderParents=new HashSet<UUID>();
            var orders=new HashMap<UUID,List<UUID>>();for(var replacement:replacements.values()){var actualParent=actualParents.get(replacement.token().parent());if(actualParent==null)throw new IllegalStateException("Prepared existing player parent absent from the real source subtree");var children=new ArrayList<>(actualParent.getPassengers());if(actualParent==parent&&!children.contains(passenger)){if(passenger instanceof Player&&!children.isEmpty()&&!(children.getFirst() instanceof Player))children.addFirst(passenger);else children.add(passenger);}orders.put(actualParent.getUUID(),children.stream().map(Entity::getUUID).toList());if(restoring)retainedOrderParents.add(actualParent.getUUID());}
            for(var replacement:replacements.values()){var actor=level.getServer().getPlayerList().getPlayer(replacement.player());if(actor!=null)actualPlayers.put(actor.getUUID(),actor);}
            players=Map.copyOf(actualPlayers);parents=Map.copyOf(actualParents);prepared=replacements.isEmpty()?null:data.prepareRetainingOrder(List.copyOf(replacements.values()),orders,retainedOrderParents);MOUNTS.get().push(this);
        }
        public boolean refused(){return refused;}
        private boolean beforeActualList(){
            if(front==null)return true;
            if(parent==front&&seat!=null)for(var actual:front.getPassengers())if(actual!=passenger&&Objects.equals(seatBefore.get(actual.getUUID()),seat.seat))return false;
            if(!LoadedEntityAccess.isReady(front.level(),front.blockPosition())||!frontCurrent(front)||root==null||parent==front&&!hasPreparedSeat(front,passenger)||!front.getContraption().getSeatMapping().equals(seatBefore)||!sameObjects(parent.getPassengers(),parentChildren)||prepared!=null&&!prepared.current())return false;
            for(var original:originals){var actual=original.actual();if(actual.isRemoved()||actual.level()!=front.level()||actual.getId()!=original.nativeId()||!actual.getUUID().equals(original.id())||((ServerLevel)front.level()).getEntity(original.id())!=actual||!sameObjects(actual.getPassengers(),original.children())||actual.getVehicle()!=(actual==passenger?parent:original.parent()))return false;}return true;
        }
        private void afterActualList(){
            if(front==null){
                if(passenger instanceof ServerPlayer player&&data!=null&&player.level().getServer().getPlayerList().getPlayer(player.getUUID())==player&&!preserving(player,parent)){var old=data.link(player.getUUID());if(old!=null)data.supersede(old,player);}
                else if(!(passenger instanceof Player)&&passenger.level() instanceof ServerLevel world){var journal=CarriagePlayerReferences.peekCurrent(world.getServer());if(journal!=null&&!preservingRootMount(passenger,parent)){var below=new HashSet<UUID>();passenger.getSelfAndPassengers().forEach(entity->below.add(entity.getUUID()));for(var link:journal.links())if(below.contains(link.token().parent())){Entity actualParent=world.getEntity(link.token().parent());if(actualParent==null||!CarriagePlayerReferences.actualOwnerCurrent(link,actualParent)){journal.supersedeBelow(passenger);break;}}}}
                return;}if(parent!=passenger.getVehicle()||parent.getPassengers().stream().noneMatch(child->child==passenger))throw new IllegalStateException("Actual native parent-list custody required");if(parent==front){var actual=front.getContraption().getSeatMapping();actual.clear();actual.putAll(seatAfter);}if(prepared!=null&&!prepared.commitActualEdges(players,parents))throw new IllegalStateException("Native relationship source changed after final pre-list proof");if(passenger instanceof ServerPlayer player&&parent==front){var old=data.link(player.getUUID());if(old!=null)data.supersede(old,player);}
            var rootSeat=front.getContraption().getSeatMapping().get(root.getUUID());if(rootSeat==null)throw new IllegalStateException("Actual adopted NPC root seat required before callbacks");
            var lifecycle=front.passengerLifecycleForNative();for(var original:originals)if(!(original.actual() instanceof Player))lifecycle.rememberActualEdge(front,rootSeat,original.actual());
            adopted=true;
        }
        @Override public void close(){if(MOUNTS.get().pop()!=this)throw new IllegalStateException("Native mount scopes must close in actual invocation order");if(MOUNTS.get().isEmpty())MOUNTS.remove();}
    }
    private static Entity rootBelow(Entity actual,CarriageContraptionEntity front){int depth=0;while(actual.getVehicle()!=front){if(++depth>CarriagePassengerGraph.MAX_DEPTH||actual.getVehicle()==null)throw new IllegalStateException("Actual nested passenger root is not below its canonical front");actual=actual.getVehicle();}return actual;}
    public static MountScope prepareMount(Entity passenger,Entity parent){return new MountScope(passenger,parent);}
    private static MountScope mount(Entity parent,Entity passenger){for(var scope:MOUNTS.get())if(scope.parent==parent&&scope.passenger==passenger)return scope;return null;}
    public static boolean beforeNativeParentList(Entity parent,Entity passenger){var scope=mount(parent,passenger);if(scope==null||scope.beforeActualList())return true;scope.refused=true;if(passenger.getVehicle()==parent&&parent.getPassengers().stream().noneMatch(child->child==passenger))((EntityVehicleAccessor)passenger).create$setProspectiveVehicle(null);return false;}
    public static void afterNativeParentList(Entity parent,Entity passenger){var scope=mount(parent,passenger);if(scope!=null&&!scope.refused)scope.afterActualList();}
    public static final class PreserveScope implements AutoCloseable {
        final ServerPlayer player;final Entity source,target;final CarriagePlayerReferences data;final CarriagePassengerLifecycle.DirectPlayerSource directSource;ServerLevel teleportLevel;net.minecraft.world.phys.Vec3 teleportPosition;CarriagePlayerReferences.Link link;boolean unlinked;
        private PreserveScope(ServerPlayer player,Entity target){this.player=player;this.source=player.getVehicle();this.target=target;data=CarriagePlayerReferences.get(player.level().getServer());var current=data.link(player.getUUID());link=current==null?null:data.suspended(current,player);directSource=source instanceof CarriageContraptionEntity front&&frontCurrent(front)?front.passengerLifecycleForNative().directPlayerSource(front,player):null;PRESERVE.get().push(this);}
        public boolean current(){return link!=null&&data.matches(link,player);}
        public CarriagePlayerReferences.Link reference(){return link;}
        public boolean teleport(ServerLevel level,net.minecraft.world.phys.Vec3 position){if(!current())return false;teleportLevel=level;teleportPosition=position;try{return player.teleportTo(level,position.x,position.y,position.z,Set.of(),player.getYRot(),player.getXRot(),false);}finally{teleportLevel=null;teleportPosition=null;}}
        @Override public void close(){if(PRESERVE.get().pop()!=this)throw new IllegalStateException("Exact player transfer scopes close in native call order");if(PRESERVE.get().isEmpty())PRESERVE.remove();}
    }
    /** One exact native source unlink/target mount, not a blanket exemption for callback actions. */
    public static PreserveScope preserve(ServerPlayer player,Entity target){return new PreserveScope(player,target);}
    private static boolean preserving(ServerPlayer player,Entity target){for(var scope:PRESERVE.get())if(scope.player==player&&scope.target==target&&scope.current())return true;return false;}
    /** One exact native safe-dismount teleport after this transaction consumed its source unlink. */
    public static final class NativeDismountScope implements AutoCloseable {
        final ServerPlayer player;final Entity source;final ServerLevel world;final double x,y,z;final PreserveScope transfer;final CarriagePlayerReferences.Link expected;boolean consumed;
        private NativeDismountScope(ServerPlayer player,Entity source,double x,double y,double z){
            this.player=player;this.source=source;world=player.level();this.x=x;this.y=y;this.z=z;PreserveScope found=null;
            if(source!=null&&source.level()==world&&!source.isRemoved()&&world.getEntity(source.getUUID())==source&&world.getServer().getPlayerList().getPlayer(player.getUUID())==player&&!player.isRemoved()&&player.getVehicle()==null&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z))
                for(var scope:PRESERVE.get())if(scope.player==player&&scope.source==source&&scope.unlinked&&scope.current()&&scope.link.token().parent().equals(source.getUUID())&&CarriagePlayerReferences.actualOwnerCurrent(scope.link,source)){found=scope;break;}
            transfer=found;expected=found==null?null:found.link;DISMOUNTS.get().push(this);
        }
        private boolean permits(ServerPlayer actual,ServerLevel target,double x,double y,double z){
            if(consumed||actual!=player||target!=world||player.level()!=world||player.isRemoved()||world.getEntity(player.getUUID())!=player||!LoadedEntityAccess.isReady(world,player.blockPosition())||source==null||source.level()!=world||source.isRemoved()||world.getEntity(source.getUUID())!=source||player.getVehicle()!=null||this.x!=x||this.y!=y||this.z!=z||transfer==null||transfer.link!=expected||!transfer.unlinked||transfer.source!=source||!transfer.current()||!CarriagePlayerReferences.actualOwnerCurrent(expected,source))return false;
            consumed=true;return true;
        }
        @Override public void close(){if(DISMOUNTS.get().pop()!=this)throw new IllegalStateException("Exact native dismount scopes close in invocation order");if(DISMOUNTS.get().isEmpty())DISMOUNTS.remove();}
    }
    /** Called only around LivingEntity.dismountVehicle's genuine dismountTo invocation. */
    public static NativeDismountScope nativeDismount(ServerPlayer player,Entity source,double x,double y,double z){return new NativeDismountScope(player,source,x,y,z);}
    public static final class RootScope implements AutoCloseable {
        final Entity root,source,target;final net.minecraft.core.BlockPos worldSeat;final CarriagePlayerReferences data;final List<CarriagePlayerReferences.Link> references;boolean consumed,unlinking;
        private RootScope(Entity root,Entity target,net.minecraft.core.BlockPos worldSeat){this.root=root;source=root.getVehicle();this.target=target;this.worldSeat=worldSeat==null?null:worldSeat.immutable();data=CarriagePlayerReferences.get(((ServerLevel)root.level()).getServer());var ids=new HashSet<UUID>();root.getSelfAndPassengers().forEach(entity->ids.add(entity.getUUID()));references=data.links().stream().filter(link->ids.contains(link.token().parent())).toList();ROOTS.get().push(this);}
        boolean current(){if(!data.current())return false;for(var link:references)if(data.link(link.player())!=link)return false;return true;}
        boolean target(Entity parent){return target==parent&&target!=null||worldSeat!=null&&parent instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity&&parent.level()==root.level()&&parent.blockPosition().equals(worldSeat);}
        public void unlink(){if(consumed||!current()||root.getVehicle()!=source)throw new IllegalStateException("Exact current NPC source unlink required");unlinking=true;try{root.stopRiding();}finally{unlinking=false;}}
        @Override public void close(){if(ROOTS.get().pop()!=this)throw new IllegalStateException("Exact native root scopes must close in invocation order");if(ROOTS.get().isEmpty())ROOTS.remove();}
    }
    /** Module caller invokes unlink() for one exact native source action. */
    public static RootScope preserveRootUnlink(Entity root){return rootScope(root,null,null);}
    public static RootScope preserveRootMount(Entity root,Entity parent){return rootScope(root,parent,null);}
    public static RootScope preserveRootWorld(Entity root,net.minecraft.core.BlockPos actualSeat){return rootScope(root,null,actualSeat);}
    private static RootScope rootScope(Entity root,Entity target,net.minecraft.core.BlockPos worldSeat){if(!(root.level() instanceof ServerLevel)||root instanceof Player)throw new IllegalArgumentException("Actual native NPC source root required");return new RootScope(root,target,worldSeat);}
    private static boolean preservingRootMount(Entity root,Entity parent){for(var scope:ROOTS.get())if(scope.root==root&&scope.target(parent)&&scope.current())return true;return false;}
    public static final class NativeDetachScope implements AutoCloseable {
        private final Entity source;private final CarriagePlayerReferences data;private final Map<Entity,List<CarriagePlayerReferences.Link>> children;private final Set<Entity> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
        private NativeDetachScope(Entity source,boolean unloaded,boolean worldEject){
            this.source=source;data=source.level() instanceof ServerLevel world?CarriagePlayerReferences.peekCurrent(world.getServer()):null;var prepared=new IdentityHashMap<Entity,List<CarriagePlayerReferences.Link>>();
            if(data!=null&&(unloaded&&CarriageNativeSources.current(source)||worldEject&&source instanceof CarriageContraptionEntity front&&front.getContraption()!=null&&front.getContraption().phase()==com.simibubi.create.content.contraptions.Contraption.Phase.DISASSEMBLED&&!front.getContraption().getStorage().ownsContents())){
                for(var child:List.copyOf(source.getPassengers())){
                    var ids=new HashSet<UUID>();child.getSelfAndPassengers().forEach(actual->ids.add(actual.getUUID()));var links=data.links().stream().filter(link->ids.contains(link.player())||ids.contains(link.token().parent())).toList();
                    if(worldEject&&links.stream().anyMatch(link->link.worldSeat().isEmpty()||!link.body().equals(source.getUUID())||!link.root().equals(child.getUUID())))continue;
                    for(var link:links){var actor=((ServerLevel)source.level()).getServer().getPlayerList().getPlayer(link.player());if(actor!=null&&data.matches(link,actor)&&actor.getVehicle()!=null)data.suspended(link,actor);}
                    prepared.put(child,data.links().stream().filter(link->ids.contains(link.player())||ids.contains(link.token().parent())).toList());
                }
            }
            children=Collections.unmodifiableMap(prepared);DETACH.get().push(this);
        }
        private boolean preserves(Entity child){var expected=children.get(child);if(data==null||expected==null||consumed.contains(child)||child.getVehicle()!=source||!data.current())return false;for(var link:expected)if(data.link(link.player())!=link)return false;consumed.add(child);return true;}
        private boolean protectsRoot(Entity actual){
            if(data==null||!data.current())return false;var references=new ArrayList<CarriagePlayerReferences.Link>();
            if(actual==source)for(var cohort:children.values())references.addAll(cohort);else{var cohort=children.get(actual);if(cohort==null)return false;references.addAll(cohort);}
            if(references.isEmpty())return false;for(var old:references){var current=data.link(old.player());if(current==null||!current.token().equals(old.token())||!current.body().equals(old.body())||!current.root().equals(old.root()))return false;}return true;
        }
        @Override public void close(){if(DETACH.get().pop()!=this)throw new IllegalStateException("Native source detach scopes close in actual invocation order");if(DETACH.get().isEmpty())DETACH.remove();}
    }
    public static final class NativeDimensionScope implements AutoCloseable {
        final ServerPlayer player;final ServerLevel source;final PreserveScope transfer;boolean consumed;
        private NativeDimensionScope(ServerPlayer player,ServerLevel source,net.minecraft.world.level.portal.TeleportTransition transition){this.player=player;this.source=source;PreserveScope found=null;var position=transition.position();for(var scope:PRESERVE.get())if(scope.player==player&&scope.current()&&scope.teleportLevel==transition.newLevel()&&scope.teleportPosition!=null&&scope.teleportPosition.equals(position)&&transition.relatives().isEmpty()){found=scope;break;}transfer=found;DIMENSIONS.get().push(this);}
        boolean permits(ServerPlayer actual){if(consumed||actual!=player||actual.level()!=source||transfer==null||!transfer.current())return false;consumed=true;return true;}
        @Override public void close(){if(DIMENSIONS.get().pop()!=this)throw new IllegalStateException("Exact native dimension scopes close in invocation order");if(DIMENSIONS.get().isEmpty())DIMENSIONS.remove();}
    }
    /** Only the genuine removePlayerImmediately call inside the exact prepared native transition. */
    public static NativeDimensionScope nativeDimensionRemoval(ServerPlayer player,ServerLevel source,net.minecraft.world.level.portal.TeleportTransition transition){return new NativeDimensionScope(player,source,transition);}
    private static boolean nativeDimensionPreserved(ServerPlayer player){for(var scope:DIMENSIONS.get())if(scope.permits(player))return true;return false;}
    public static NativeDetachScope nativeRemoval(Entity source,Entity.RemovalReason reason){
        if(reason.shouldDestroy()||reason==Entity.RemovalReason.CHANGED_DIMENSION){var data=source.level() instanceof ServerLevel world?CarriagePlayerReferences.peekCurrent(world.getServer()):null;if(data!=null){if(source instanceof ServerPlayer player){if(player.level().getServer().getPlayerList().getPlayer(player.getUUID())==player&&(reason!=Entity.RemovalReason.CHANGED_DIMENSION||!nativeDimensionPreserved(player))){var link=data.link(player.getUUID());if(link!=null)data.supersede(link,player);}}else if(!(source instanceof Player)&&(CarriageNativeSources.current(source)||com.simibubi.create.foundation.utility.NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(source))&&!(source instanceof CarriageContraptionEntity front&&front.getContraption()!=null&&front.getContraption().phase()==com.simibubi.create.content.contraptions.Contraption.Phase.DISASSEMBLED))data.supersedeBelow(source);}}
        return new NativeDetachScope(source,reason==Entity.RemovalReason.UNLOADED_TO_CHUNK,reason.shouldDestroy());
    }
    public static NativeDetachScope worldEjection(Entity source){return new NativeDetachScope(source,false,true);}
    /** During one real source transfer, PlayerData cannot become a second NPC material owner. */
    public static boolean protectsNativeTransferRoot(Entity root){
        if(root instanceof Player)return false;
        if(CarriageNpcRetirement.active(root))return true;
        for(var mount:MOUNTS.get())if(mount.passenger==root&&mount.front!=null&&!mount.refused)return true;
        for(var scope:ROOTS.get())if(scope.root==root)return true;
        for(var scope:DETACH.get())if(scope.protectsRoot(root))return true;
        return false;
    }
    public static void beforeNativeUnlink(Entity passenger){
        if(!(passenger.level() instanceof ServerLevel level))return;
        if(passenger instanceof ServerPlayer player){if(level.getServer().getPlayerList().getPlayer(player.getUUID())!=player)return;}else if(!CarriageNativeSources.current(passenger))return;
        for(var scope:DETACH.get())if(scope.preserves(passenger))return;
        if(!(passenger instanceof ServerPlayer player)){
            if(passenger instanceof Player)return;
            // Carry an existing same-parent NPC/player cohort across this one actual prepared native source unlink.
            for(var scope:MOUNTS.get())if(scope.passenger==passenger&&scope.front!=null&&!scope.sourceUnlinked&&frontCurrent(scope.front)&&!scope.originals.isEmpty()&&scope.originals.getFirst().parent()==passenger.getVehicle()&&(scope.prepared==null||scope.prepared.current())){scope.sourceUnlinked=true;return;}
            for(var scope:ROOTS.get())if(scope.root==passenger&&scope.source==passenger.getVehicle()&&!scope.consumed&&scope.current()&&(scope.unlinking||MOUNTS.get().stream().anyMatch(mount->mount.passenger==passenger&&scope.target(mount.parent)))){scope.consumed=true;return;}
            var data=CarriagePlayerReferences.peekCurrent(level.getServer());if(data!=null)data.supersedeBelow(passenger);return;
        }
        var parent=player.getVehicle();for(var scope:PRESERVE.get())if(scope.player==player&&!scope.unlinked&&scope.source==parent&&(scope.current()||scope.directSource!=null&&scope.directSource.current())){scope.unlinked=true;return;}
        if(parent instanceof CarriageContraptionEntity front&&frontCurrent(front))front.passengerLifecycleForNative().supersedeActualDirectPlayerUnlink(front,player);
        var data=CarriagePlayerReferences.get(level.getServer());var link=data.link(player.getUUID());if(link==null)return;data.supersede(link,player);for(var scope:MOUNTS.get())if(scope.data==data&&scope.prepared!=null)scope.prepared.afterExactSourceUnlink(link,player);
    }
    public static void beforeNativeTeleport(ServerPlayer player,ServerLevel target,double x,double y,double z){
        if(player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player)return;
        for(var scope:DISMOUNTS.get())if(scope.permits(player,target,x,y,z))return;
        for(var scope:PRESERVE.get())if(scope.player==player&&scope.current()&&scope.teleportLevel==target&&scope.teleportPosition!=null&&scope.teleportPosition.x==x&&scope.teleportPosition.y==y&&scope.teleportPosition.z==z)return;var data=CarriagePlayerReferences.get(player.level().getServer());var link=data.link(player.getUUID());if(link!=null){data.supersede(link,player);if(player.getVehicle()!=null)player.stopRiding();}else supersedeActualDirectTeleport(player);
    }
    public static void beforeExplicitRelativeTeleport(ServerPlayer player){if(player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player)return;var data=CarriagePlayerReferences.get(player.level().getServer());var link=data.link(player.getUUID());if(link!=null){data.supersede(link,player);if(player.getVehicle()!=null)player.stopRiding();}else supersedeActualDirectTeleport(player);}
    private static void supersedeActualDirectTeleport(ServerPlayer player){
        if(!(player.getVehicle() instanceof CarriageContraptionEntity front)||!frontCurrent(front))return;
        front.passengerLifecycleForNative().supersedeActualDirectPlayerTeleport(front,player);
        player.stopRiding();
    }
    private CarriagePlayerReferenceTransitions(){}
}

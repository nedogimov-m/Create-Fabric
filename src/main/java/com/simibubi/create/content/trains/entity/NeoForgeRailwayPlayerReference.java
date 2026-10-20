package com.simibubi.create.content.trains.entity;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Genuine source relationship only. The actual global Carriage alone owns bodies, NPCs and cargo. */
public record NeoForgeRailwayPlayerReference(int sourceDataVersion,UUID player,UUID train,int carriage,UUID body,
        UUID attach,UUID seatRoot,int seat,BlockPos seatPosition,Identifier dimension,Vec3 originalPosition) {
    public static final String KEY="CreateOriginalRailwayPlayerReference";
    public static final Codec<NeoForgeRailwayPlayerReference> CODEC=CompoundTag.CODEC.xmap(NeoForgeRailwayPlayerReference::read,NeoForgeRailwayPlayerReference::write);
    public interface Holder {NeoForgeRailwayPlayerReference create$getOriginalRailwayReference();void create$setOriginalRailwayReference(NeoForgeRailwayPlayerReference value);}
    public NeoForgeRailwayPlayerReference{
        Objects.requireNonNull(player);Objects.requireNonNull(train);Objects.requireNonNull(body);Objects.requireNonNull(attach);Objects.requireNonNull(seatRoot);seatPosition=Objects.requireNonNull(seatPosition).immutable();Objects.requireNonNull(dimension);Objects.requireNonNull(originalPosition);
        if(sourceDataVersion!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION||carriage<0||carriage>=Train.MAX_CARRIAGES||seat<0
                ||player.equals(body)||body.equals(seatRoot)||attach.equals(body)&&!seatRoot.equals(player)||!attach.equals(body)&&(seatRoot.equals(player)||attach.equals(player))||!Double.isFinite(originalPosition.x)||!Double.isFinite(originalPosition.y)||!Double.isFinite(originalPosition.z))throw invalid("Invalid actual original railway player relationship");
    }
    public static NeoForgeRailwayPlayerReference read(CompoundTag source){
        keys(source,"original railway player reference","SourceDataVersion","Player","Train","Carriage","Body","Attach","SeatRoot","Seat","SeatPosition","Dimension","Origin");
        return new NeoForgeRailwayPlayerReference(integer(source,"SourceDataVersion"),uuid(source,"Player"),uuid(source,"Train"),integer(source,"Carriage"),uuid(source,"Body"),uuid(source,"Attach"),uuid(source,"SeatRoot"),integer(source,"Seat"),source.read("SeatPosition",BlockPos.CODEC).orElseThrow(),Identifier.parse(string(source,"Dimension")),source.read("Origin",Vec3.CODEC).orElseThrow());
    }
    public CompoundTag write(){var source=new CompoundTag();source.putInt("SourceDataVersion",sourceDataVersion);source.store("Player",UUIDUtil.CODEC,player);source.store("Train",UUIDUtil.CODEC,train);source.putInt("Carriage",carriage);source.store("Body",UUIDUtil.CODEC,body);source.store("Attach",UUIDUtil.CODEC,attach);source.store("SeatRoot",UUIDUtil.CODEC,seatRoot);source.putInt("Seat",seat);source.store("SeatPosition",BlockPos.CODEC,seatPosition);source.putString("Dimension",dimension.toString());source.store("Origin",Vec3.CODEC,originalPosition);return source;}
    public static NeoForgeRailwayPlayerReference fromOriginalPlayer(CompoundTag actualPlayer,int version,ServerLevel actualLevel){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original railway player version");NeoForgePlayerVehicleContext.validateCompletePlayer(actualPlayer);
        var originalDimension=Identifier.parse(string(actualPlayer,"Dimension"));if(!actualLevel.dimension().identifier().equals(originalDimension))throw invalid("Actual source player level differs from its own saved dimension");
        var players=new ListTag();players.add(actualPlayer);NeoForgePlayerVehicleContext.fromOriginalPlayers(players,originalDimension);
        var originalVehicle=compound(actualPlayer,"RootVehicle");var source=compound(originalVehicle,"Entity");if(!string(source,"id").equals("create:carriage_contraption"))throw invalid("Actual source RootVehicle is not a globally owned railway front");
        var player=uuid(actualPlayer,"UUID");var trainId=uuid(source,"TrainId");int index=integer(source,"CarriageIndex");var bodyId=uuid(source,"UUID");var attach=uuid(originalVehicle,"Attach");
        if(!actualLevel.getServer().isSameThread())throw invalid("Actual source rail witness must run on its real server thread");
        var manager=TrainManager.peekCurrent(actualLevel.getServer());if(manager==null)throw invalid("Actual original railway player witness requires its current ready global TrainManager");var train=manager.get(trainId);
        if(train==null||train.trafficManager()!=manager||train.invalid||train.isRestoring()||index<0||index>=train.carriages.size())throw invalid("Actual original railway player has no matching canonical active Train/carriage owner");var carriage=train.carriages.get(index);
        if(carriage.train!=train||carriage.isWorldRestored()||!carriage.storage.ownsContents()||!carriage.custodyIdentities().bodies().equals(Set.of(bodyId)))throw invalid("Actual original railway player body/cargo custody differs from its canonical Carriage");
        var journal=CarriagePlayerReferences.peekCurrent(actualLevel.getServer());if(journal!=null&&journal.link(player)!=null)throw invalid("A current native player relationship supersedes this original source snapshot");
        var body=compound(source,"Contraption");var originalSeats=list(body,"Passengers");var stripped=source.copy();stripped.remove("Passengers");compound(stripped,"Contraption").remove("Passengers");
        var converted=NeoForgeCarriageBodyImport.convert(stripped,trainId,index,version,actualLevel.registryAccess());
        if(!compound(converted.entity(),"Contraption").equals(compound(carriage.serialisedEntity,"Contraption")))throw invalid("Original player rail body snapshot disagrees with actual canonical global body state");
        var mapping=new HashMap<UUID,Integer>();var seats=new HashSet<Integer>();
        for(var value:originalSeats){if(!(value instanceof CompoundTag row))throw invalid("Malformed original railway player seat mapping");keys(row,"actual railway seat","Id","Seat");var id=uuid(row,"Id");int seat=integer(row,"Seat");if(seat<0||seat>=converted.seats().size()||mapping.putIfAbsent(id,seat)!=null||!seats.add(seat))throw invalid("Original railway player seat mapping is duplicated or outside actual body");}
        UUID seatRoot=attach.equals(bodyId)?player:null;var direct=new HashSet<UUID>();
        if(source.contains("Passengers"))for(var value:list(source,"Passengers")){
            if(!(value instanceof CompoundTag npc))throw invalid("Malformed actual saved railway NPC root");var rootId=uuid(npc,"UUID");var seat=mapping.get(rootId);if(!direct.add(rootId)||seat==null)throw invalid("Original railway NPC has no unique actual source seat");
            var canonical=carriage.serialisedPassengers.get(seat);if(canonical==null)throw invalid("Actual global Carriage no longer owns this original NPC root");var graph=CarriagePassengerGraph.fromLegacy(canonical,bodyId);var migrated=NeoForgeStandaloneEntityImport.convertTree(npc,version,actualLevel.registryAccess());var expected=CarriagePassengerGraph.fromLegacy(migrated,graph.revision());
            if(!sameNpcSource(expected,graph,actualLevel,carriage))throw invalid("Original player NPC snapshot disagrees with actual canonical global passenger custody");
            if(expected.node(attach)!=null){if(seatRoot!=null)throw invalid("Actual original player Attach has two source tree owners");seatRoot=rootId;}
        }
        if(seatRoot==null||!mapping.containsKey(seatRoot))throw invalid("Actual source player Attach has no genuine canonical railway seat root");
        var classified=new HashSet<>(direct);if(attach.equals(bodyId))classified.add(player);if(!classified.equals(mapping.keySet()))throw invalid("Original railway body contains an unowned saved seat reference");
        if(attach.equals(bodyId)){
            var occupied=carriage.serialisedPassengers.get(mapping.get(player));if(occupied!=null){var graph=CarriagePassengerGraph.fromLegacy(occupied,bodyId);if(!(graph.node(graph.root()) instanceof CarriagePassengerGraph.PlayerReference)||!graph.root().equals(player))throw invalid("Canonical actual railway seat belongs to a different material/player owner");}
        }
        return new NeoForgeRailwayPlayerReference(version,player,trainId,index,bodyId,attach,seatRoot,mapping.get(seatRoot),converted.seats().get(mapping.get(seatRoot)),originalDimension,actualPlayer.read("Pos",Vec3.CODEC).orElseThrow());
    }
    private static boolean sameNpcSource(CarriagePassengerGraph expected,CarriagePassengerGraph actual,ServerLevel level,Carriage canonicalCarriage){
        if(!expected.root().equals(actual.root())||!expected.identities().equals(actual.identities()))return false;
        for(var value:expected.nodes()){
            if(!(value instanceof CarriagePassengerGraph.Npc source)||!(actual.node(source.id()) instanceof CarriagePassengerGraph.Npc target)||!source.type().equals(target.type())||!source.parent().equals(target.parent())||source.order()!=target.order()||target.material()==CarriagePassengerGraph.Material.CONSUMED)return false;
            // This pre-DFU source witness may not substitute a stale original payload for a
            // currently admitted material row. No existing live NPC is ever re-instantiated here.
            if(target.payload().isPresent()){if(!source.payload().equals(target.payload()))return false;}
            else {
                var admitted=level.getEntity(target.id());if(admitted==null||!CarriageNativeSources.current(admitted)||!EntityType.getKey(admitted.getType()).equals(target.type()))return false;
                if(!(admitted.getRootVehicle() instanceof CarriageContraptionEntity front)||front.getCarriage()!=canonicalCarriage||!front.isGloballyManaged())return false;
            }
        }return true;
    }
    /** A real player tick may resolve only current globally admitted objects; it never creates a front or NPC. */
    public static boolean resumeActualPlayer(ServerPlayer actual){
        var holder=(Holder)actual;var reference=holder.create$getOriginalRailwayReference();if(reference==null)return false;
        if(!actual.getUUID().equals(reference.player))throw invalid("Original railway player reference changed its actual PlayerData owner");var level=actual.level();var server=level.getServer();
        if(server.getPlayerList().getPlayer(reference.player)!=actual||level.getEntity(reference.player)!=actual||actual.isRemoved()||!server.isSameThread())return true;
        if(!level.dimension().identifier().equals(reference.dimension)||actual.position().distanceToSqr(reference.originalPosition)>16||actual.isShiftKeyDown())return clear(holder);
        var manager=TrainManager.peekCurrent(server);if(manager==null)return true;var train=manager.get(reference.train);
        if(train==null||train.invalid||train.isRestoring()||reference.carriage>=train.carriages.size())return clear(holder);var carriage=train.carriages.get(reference.carriage);
        if(train.trafficManager()!=manager||carriage.train!=train||carriage.isWorldRestored()||!carriage.storage.ownsContents()||!carriage.custodyIdentities().bodies().equals(Set.of(reference.body)))return clear(holder);
        var dimensional=carriage.getDimensionalIfPresent(level.dimension());var front=dimensional==null?null:dimensional.entity.get();
        if(front==null||front.isRemoved()||level.getEntity(reference.body)!=front||!front.getUUID().equals(reference.body)||front.getCarriage()!=carriage||!front.isGloballyManaged()||!LoadedEntityAccess.isReady(level,front.blockPosition()))return true;
        if(reference.seat>=front.getContraption().getSeats().size()||!front.getContraption().getSeats().get(reference.seat).equals(reference.seatPosition))return clear(holder);
        Entity parent=reference.attach.equals(reference.body)?front:level.getEntity(reference.attach);
        if(parent==null||parent.isRemoved()||parent.level()!=level||!LoadedEntityAccess.isReady(level,parent.blockPosition()))return true;
        Entity root=parent;int depth=0;while(root!=front&&root.getVehicle()!=front){if(++depth>CarriagePassengerGraph.MAX_DEPTH||root.getVehicle()==null)return true;root=root.getVehicle();}
        if(parent!=front&&(!root.getUUID().equals(reference.seatRoot)||!Objects.equals(front.getContraption().getSeatMapping().get(reference.seatRoot),reference.seat)))return true;
        if(actual.getVehicle()!=null&&actual.getVehicle()!=parent)return clear(holder);
        for(var passenger:front.getPassengers())if(passenger!=actual&&Objects.equals(front.getContraption().getSeatMapping().get(passenger.getUUID()),reference.seat)&&(!passenger.getUUID().equals(reference.seatRoot)||parent==front))return clear(holder);
        var journal=CarriagePlayerReferences.peekCurrent(server);var newer=journal==null?null:journal.link(reference.player);if(newer!=null)return clear(holder);
        if(parent==front)front.addSittingPassenger(actual,reference.seat);else actual.startRiding(parent,true,true);
        if(level.getEntity(reference.player)==actual&&level.getEntity(parent.getUUID())==parent&&actual.getVehicle()==parent&&parent.getPassengers().stream().anyMatch(child->child==actual))return clear(holder);
        return true;
    }
    public static void supersedeForActualAction(ServerPlayer actual){if(actual.level().getServer().getPlayerList().getPlayer(actual.getUUID())==actual)((Holder)actual).create$setOriginalRailwayReference(null);}
    private static boolean clear(Holder holder){holder.create$setOriginalRailwayReference(null);return true;}
}

package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import com.simibubi.create.foundation.utility.NeoForgeVanillaEntityImport;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

/** Read-only links derived solely from complete real saved original player records and their own vehicle trees. */
public final class NeoForgePlayerVehicleContext {
    private record Link(UUID player,UUID attach,CompoundTag sourceAttach,CompoundTag originalRootVehicle){}
    private final Identifier dimension;
    private final Map<UUID,Link> players;
    private NeoForgePlayerVehicleContext(Identifier dimension,Map<UUID,Link> players){this.dimension=dimension;this.players=Map.copyOf(players);}
    public static NeoForgePlayerVehicleContext fromOriginalPlayers(ListTag actualCompleteOriginalPlayers,Identifier actualDimension){
        Objects.requireNonNull(actualDimension);var links=new LinkedHashMap<UUID,Link>();var roots=new HashSet<UUID>();
        for(var value:actualCompleteOriginalPlayers){
            if(!(value instanceof CompoundTag actual))throw invalid("Malformed actual original player record");validateCompletePlayer(actual);
            if(!Identifier.parse(string(actual,"Dimension")).equals(actualDimension))throw invalid("Actual saved player context belongs to another dimension");
            var player=uuid(actual,"UUID");if(links.containsKey(player))throw invalid("One actual player has two saved RootVehicle owners");
            var vehicle=compound(actual,"RootVehicle");keys(vehicle,"actual player RootVehicle","Attach","Entity");var attach=uuid(vehicle,"Attach");var root=compound(vehicle,"Entity");
            if(!roots.add(uuid(root,"UUID")))throw invalid("Original RootVehicle's exactly-one-player source invariant has two player owners");
            CompoundTag actualAttach=null;var queue=new ArrayDeque<CompoundTag>();var identities=new HashSet<UUID>();queue.add(root);int count=0;
            while(!queue.isEmpty()){
                var node=queue.removeFirst();if(++count>1024)throw invalid("Actual saved player vehicle tree exceeds supported bounds");var id=uuid(node,"UUID");
                if(!identities.add(id)||id.equals(player)||string(node,"id").equals("minecraft:player"))throw invalid("Saved player body cannot be duplicated inside its actual RootVehicle tree");
                if(id.equals(attach))actualAttach=node;
                if(node.contains("Passengers"))for(var child:list(node,"Passengers")){if(!(child instanceof CompoundTag tag))throw invalid("Malformed actual saved player vehicle passenger");queue.add(tag);}
            }
            if(actualAttach==null)throw invalid("Actual player RootVehicle.Attach has no genuine node in its saved vehicle tree");
            links.put(player,new Link(player,attach,actualAttach.copy(),vehicle.copy()));
        }
        return new NeoForgePlayerVehicleContext(actualDimension,links);
    }
    public void requireDimension(Identifier actualDimension){if(!dimension.equals(actualDimension))throw invalid("Actual native entity dimension differs from the saved player vehicle context");}
    boolean allowsSeat(UUID actualPlayer,CompoundTag exactOriginalOwner){var link=players.get(actualPlayer);return link!=null&&link.attach.equals(uuid(exactOriginalOwner,"UUID"))&&link.sourceAttach.equals(exactOriginalOwner);}
    void requireNoPlayerBodies(Set<UUID> actualSavedEntityIds){for(var player:players.keySet())if(actualSavedEntityIds.contains(player))throw invalid("An external real player seat reference collides with a saved entity body owner");}
    CompoundTag rootVehicle(UUID actualPlayer){var link=players.get(actualPlayer);if(link==null)throw invalid("Missing actual saved player vehicle context");return link.originalRootVehicle.copy();}
    public static void validateCompletePlayer(CompoundTag actual){
        if(integer(actual,"DataVersion")!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original saved player version");uuid(actual,"UUID");Identifier.parse(string(actual,"Dimension"));
        if(actual.contains("id")&&!string(actual,"id").equals("minecraft:player"))throw invalid("Original player context uses a nonplayer source record");
        for(String field:Set.of("Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","Invulnerable","PortalCooldown"))if(!actual.contains(field))throw invalid("Complete actual player record lost base state "+field);
        NeoForgeVanillaEntityImport.validateBase(actual);list(actual,"Inventory");list(actual,"EnderItems");
        for(String field:Set.of("SelectedItemSlot","XpLevel","XpTotal","XpSeed","Score","foodLevel","foodTickTimer","playerGameType"))integer(actual,field);
        for(String field:Set.of("XpP","foodSaturationLevel","foodExhaustionLevel","Health"))floating(actual,field);
        if(!(actual.get("SleepTimer") instanceof ShortTag))throw invalid("Actual saved player SleepTimer must retain its source short");bool(actual,"seenCredits");
        var abilities=compound(actual,"abilities");for(String field:Set.of("invulnerable","flying","mayfly","instabuild","mayBuild"))bool(abilities,field);floating(abilities,"flySpeed");floating(abilities,"walkSpeed");
    }
}

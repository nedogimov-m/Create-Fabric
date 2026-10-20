package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.kinetics.clock.CuckooClockBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import java.util.HashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/** A client snapshot is a view. Its tags never replace the separately converted full server Data owner. */
public final class NeoForgeClientBlockEntityImport {
    private static final Set<String> IDENTITY=Set.of("id","x","y","z");

    public static CompoundTag convert(Identifier originalType,CompoundTag sourceFull,CompoundTag convertedFull,CompoundTag snapshot,
            BlockPos localPos,BlockState actualState,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION||!originalType.getNamespace().equals("create"))throw invalid("Unreviewed captured client source version/type");
        var schema=NeoForgeClientBlockEntitySchemas.get(originalType.getPath());
        var projected=sourceFull.copy();
        // Full and client serializers use the same source fields. Erase optional shared fields
        // before applying the explicit snapshot, so an older omitted Source/Network/error is not
        // silently supplied by the current full Data. Server-only fields remain required inputs.
        for(String field:schema.shared())projected.remove(field);
        for(String field:snapshot.keySet()){
            if(IDENTITY.contains(field)||schema.extras().contains(field))continue;
            if(schema.serverOnly().contains(field))throw invalid("Server-only source field in a partial client snapshot: "+field);
            if(!schema.shared().contains(field)&&!sourceFull.contains(field))throw invalid("Unaudited original captured client field "+originalType+"."+field);
            projected.put(field,snapshot.get(field).copy());
        }
        // Client packages add a real transient integer ID to the full parcel shape. Strip only
        // that audited field for the shared typed parcel conversion, then restore it below.
        if(originalType.getPath().equals("chain_conveyor"))stripChainClientIds(projected);
        // Original cannon client read/write deliberately omit this disk-only marker.
        // Keep full Data intact; a distinct client State must not be constrained by it.
        if(originalType.getPath().equals("schematicannon"))projected.remove("Running");
        var converted=!sourceFull.isEmpty()&&projected.equals(sourceFull)?convertedFull:NeoForgeBlockEntityImport.convert(originalType,projected,version,registries,scope);
        if(!(actualState.getBlock() instanceof EntityBlock factory))throw invalid("Captured client view has no actual native factory");
        var view=factory.newBlockEntity(localPos,actualState);
        if(view==null||!view.getType().isValid(actualState))throw invalid("Invalid actual native client-view factory");
        var problems=new ProblemReporter.Collector();
        view.loadWithComponents(TagValueInput.create(problems,registries,converted));
        if(!problems.isEmpty())throw invalid("Actual native client-view decoder rejected typed source snapshot: "+problems.getReport());
        // This real registered factory has no level, no tick and no publication. Only its
        // native client protocol is retained; the original/converted full owner is untouched.
        var result=view.getUpdateTag(registries).copy();
        for(String field:schema.extras()){
            result.remove(field);
            if(snapshot.contains(field))clientField(originalType.getPath(),snapshot,result,field,version,registries,scope);
        }
        if(originalType.getPath().equals("elevator_contact"))for(String field:ListFields.CONTACT_PRIVATE)result.remove(field);
        if(originalType.getPath().equals("track_station"))stationPresence(result);
        if(originalType.getPath().equals("chain_conveyor"))restoreChainClientIds(snapshot,result,registries);
        for(String field:IDENTITY)result.remove(field);
        return result;
    }

    private static void clientField(String type,CompoundTag source,CompoundTag result,String field,int version,
            HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        switch(field){
            case "ForceMovement","Redraw","Splash","ForceFluidLevel","LazySync","Particles","Ding","Pulse","Uploading",
                 "TrainPresent","TrainCanDisassemble","TrainBackwards","TrainHasSchedule","TrainHasAutoSchedule"->{
                if(!bool(source,field))throw invalid("Original client marker is only written true: "+field);
                result.putBoolean(field,true);
            }
            case "Pushing","Fistbump"->result.putBoolean(field,bool(source,field));
            case "Range","Reach","Progress"->result.putFloat(field,floating(source,field));
            case "ClientTarget","ActiveLinks","OpenCount"->result.putInt(field,integer(source,field));
            case "LastEdit","ImminentTrain"->result.store(field,UUIDUtil.CODEC,uuid(source,field));
            case "Ringing"->{
                var direction=Direction.valueOf(string(source,field));
                // The original writes Ringing only at ringingTicks==0 && isRinging.
                result.putBoolean("Ringing",true);result.putInt("RingingTicks",0);result.store("RingDirection",Direction.CODEC,direction);
            }
            case "Animation"->{CuckooClockBlockEntity.Animation.valueOf(string(source,field));result.putString(field,string(source,field));}
            case "DestroyEffect"->result.store(field,BlockPos.CODEC,pos(compound(source,field)));
            case "TargetType"->{var target=Identifier.parse(string(source,field));if(DisplayTarget.get(target)==null)throw invalid("Unavailable original client display target "+target);result.putString(field,target.toString());}
            case "Schematic"->result.putString(field,string(source,field));
            case "Particle","PlayEvent"->result.put(field,item(compound(source,field),version,registries,child(scope,"client_"+field.toLowerCase(java.util.Locale.ROOT),0)));
            case "ParticleItems","VisualizedItems"->{
                var converted=new ListTag();int index=0;for(Tag value:list(source,field)){
                    if(!(value instanceof CompoundTag stack))throw invalid("Malformed original client item event");
                    converted.add(item(stack,version,registries,child(scope,field.equals("ParticleItems")?"client_particle_items":"client_visualized_items",index++)));
                }result.put(field,converted);
            }
            case "VisualizedFluids"->{
                var converted=new ListTag();int index=0;for(Tag value:list(source,field)){
                    if(!(value instanceof CompoundTag fluid))throw invalid("Malformed original client fluid event");
                    converted.add(NeoForgeFluidStackImport.convert(fluid,version,registries,child(scope,"client_visualized_fluids",index++)).slot(registries));
                }result.put(field,converted);
            }
            case "ComputerSignal"->{
                if(!(source.get(field) instanceof ByteArrayTag))throw invalid("Original nixie computer signal is not its real byte array");
                var bytes=source.getByteArray(field).orElseThrow();if(bytes.length!=14)throw invalid("Original nixie computer signal must have two7-byte tube displays");result.putByteArray(field,bytes.clone());
            }
            case "Infinite"->{
                boolean infinite=bool(source,field);result.putBoolean(field,infinite);
                for(String mode:new String[]{"Draining","Filling"}){var search=result.getCompoundOrEmpty(mode).copy();search.putBoolean("Infinite",infinite);result.put(mode,search);}
            }
            case "components"->result.put(field,NeoForgeItemComponentImport.map(compound(source,field),version,registries,child(scope,"client_components",0)));
            default->throw invalid("Unaudited original client-only field "+type+"."+field);
        }
    }

    private static void stripChainClientIds(CompoundTag projected){
        if(projected.contains("LoopingPackages"))for(Tag value:list(projected,"LoopingPackages"))asCompound(value).remove("NetID");
        if(projected.contains("TravellingPackages"))for(Tag group:list(projected,"TravellingPackages"))
            for(Tag value:list(asCompound(group),"Packages"))asCompound(value).remove("NetID");
    }
    private static void stationPresence(CompoundTag result){
        var station=result.getCompoundOrEmpty("StationData").copy();
        for(String field:ListFields.STATION_PRESENCE){
            station.remove(field);
            if(result.contains(field)){station.put(field,result.get(field).copy());result.remove(field);}
        }
        if(!station.contains("ImminentTrain"))for(String field:ListFields.STATION_PRESENCE)
            if(!field.equals("ImminentTrain")&&station.contains(field))throw invalid("Original client station presence flags lack their supplied ImminentTrain");
        result.put("StationData",station);
        result.remove("AssemblyEntryIntent");result.remove("DisassembledTrainId");
    }
    private static void restoreChainClientIds(CompoundTag source,CompoundTag result,HolderLookup.Provider registries){
        ids(list(source,"LoopingPackages"),list(result,"LoopingPackages"));
        var groups=new HashMap<BlockPos,ListTag>();
        for(Tag value:list(source,"TravellingPackages")){var row=asCompound(value);var at=pos(compound(row,"Target"));if(groups.put(at,list(row,"Packages"))!=null)throw invalid("Duplicate original client chain target");}
        for(Tag value:list(result,"TravellingPackages")){
            var row=asCompound(value);var at=BlockPos.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),row.get("Target")).getOrThrow();var original=groups.remove(at);
            if(original==null)throw invalid("Native client chain view changed an actual original target");ids(original,list(row,"Packages"));
        }
        if(!groups.isEmpty())throw invalid("Native client chain view lost an actual source target");
    }
    private static void ids(ListTag source,ListTag target){
        if(source.size()!=target.size())throw invalid("Native client view changed original parcel cardinality");
        for(int index=0;index<source.size();index++){
            var original=asCompound(source.get(index));if(!original.contains("NetID"))throw invalid("Original client parcel lacks its actual transient NetID");asCompound(target.get(index)).putInt("NetID",integer(original,"NetID"));
        }
    }
    private static CompoundTag asCompound(Tag value){if(!(value instanceof CompoundTag row))throw invalid("Malformed original client compound row");return row;}
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope,String role,int index){return scope==null?null:scope.item(role,index);}
    private static final class ListFields {
        static final String[] CONTACT_PRIVATE={"Activate","ColumnTarget","ColumnActive","ColumnOwner","ColumnController"};
        static final String[] STATION_PRESENCE={"ImminentTrain","TrainPresent","TrainCanDisassemble","TrainBackwards","TrainHasSchedule","TrainHasAutoSchedule"};
    }
    private NeoForgeClientBlockEntityImport(){}
}

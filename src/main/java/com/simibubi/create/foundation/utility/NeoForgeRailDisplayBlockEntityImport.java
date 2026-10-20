package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.logistics.NeoForgeLogisticsBlockEntityImport;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.trains.graph.NeoForgeTrackGraphImport;
import com.simibubi.create.content.trains.graph.RailCodecs;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.NeoForgeTrackTargetingReference;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/** Six pinned original rail/display owners; shared base/behaviour conversion stays in the dispatcher. */
public final class NeoForgeRailDisplayBlockEntityImport {
    private static final Set<String> TYPES=Set.of("track","track_station","track_signal","track_observer","flap_display","display_link");
    private static final Set<String> TARGET_KEYS=Set.of("Id","TargetTrack","Ortho","TargetDirection","PrevAxis","RotatedAxis","Bezier","Migrate");
    public static Optional<CompoundTag> convert(Identifier type,CompoundTag source,int version,
            HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        if(!type.getNamespace().equals("create")||!TYPES.contains(type.getPath()))return Optional.empty();
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original rail/display version");
        Objects.requireNonNull(registries,"registries");var result=source.copy();
        switch(type.getPath()){
            case "track"->track(source,result,false);
            case "track_station"->station(source,result,version,registries,scope,false);
            case "track_signal"->{targeting(type,source,result,version,registries);SignalBlockEntity.SignalState.valueOf(string(source,"State"));SignalBlockEntity.OverlayState.valueOf(string(source,"Overlay"));bool(source,"Power");result.putLong("ConfigurationRevision",0);}
            case "track_observer"->targeting(type,source,result,version,registries);
            case "flap_display"->flap(source,result,version,registries);
            case "display_link"->display(source,result,false);
            default->throw new AssertionError(type);
        }
        return Optional.of(result);
    }
    public static Optional<CompoundTag> convertSafe(Identifier type,CompoundTag source,int version,
            HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        if(!type.getNamespace().equals("create")||!TYPES.contains(type.getPath()))return Optional.empty();
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original safe rail/display version");
        var allowed=new HashSet<>(Set.of("components","Filter","FilterAmount","UpTo","Filters","FrequencyFirst","FrequencyLast","LastKnownPosition","ScrollValue","ScrollOption","Freq","Bracket"));
        switch(type.getPath()){
            case "track"->allowed.add("Connections");
            case "track_station","track_signal","track_observer"->allowed.addAll(TARGET_KEYS);
            case "display_link"->allowed.addAll(Set.of("TargetOffset","TargetLine","Source"));
            default->{}
        }
        keys(source,"safe rail/display",allowed.toArray(String[]::new));
        var result=source.copy();
        switch(type.getPath()){
            case "track"->track(source,result,true);
            case "track_station"->station(source,result,version,registries,scope,true);
            case "track_signal","track_observer"->targeting(type,source,result,version,registries);
            case "display_link"->display(source,result,true);
            default->{}
        }
        return Optional.of(result);
    }
    private static void track(CompoundTag source,CompoundTag result,boolean safe){
        var connections=new ListTag();var targets=new HashSet<BlockPos>();
        for(Tag row:list(source,"Connections")){
            if(!(row instanceof CompoundTag curve))throw invalid("Malformed original curve row");
            keys(curve,"BE curve","Positions","Starts","Axes","Normals","Primary","Girder","Material","Smoothing");
            var next=new CompoundTag();
            for(String key:Set.of("Positions","Starts","Axes","Normals","Smoothing")){
                if(!curve.contains(key)&&key.equals("Smoothing"))continue;
                var values=list(curve,key);if(values.size()!=2)throw invalid("Original curve "+key+" requires two endpoints");var pair=new ListTag();
                for(Tag value:values){
                    if(!(value instanceof CompoundTag entry))throw invalid("Malformed original curve endpoint");
                    keys(entry,"curve endpoint",key.equals("Positions")?"Pos":"V");
                    if(key.equals("Positions"))pair.add(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE,pos(compound(entry,"Pos"))).getOrThrow());
                    else if(key.equals("Smoothing")){integer(entry,"V");pair.add(entry.get("V").copy());}
                    else pair.add(vector(list(entry,"V")));
                }
                next.put(key,pair);
            }
            next.putBoolean("Primary",bool(curve,"Primary"));next.putBoolean("Girder",bool(curve,"Girder"));next.putString("Material",string(curve,"Material"));
            var nativeCurve=BezierConnection.CODEC.parse(NbtOps.INSTANCE,next).getOrThrow();
            if(!nativeCurve.bePositions.getFirst().equals(BlockPos.ZERO)||nativeCurve.getKey().equals(BlockPos.ZERO)||!targets.add(nativeCurve.getKey()))throw invalid("Original relative curve endpoints disagree or duplicate");
            connections.add(BezierConnection.CODEC.encodeStart(NbtOps.INSTANCE,nativeCurve).getOrThrow());
        }
        result.put("Connections",connections);
        if(safe)return;
        if(source.contains("Smoothing"))number(source,"Smoothing");
        if(source.contains("BoundLocation")!=source.contains("BoundDimension"))throw invalid("Incomplete original dimensional track binding");
        if(source.contains("BoundLocation")){result.store("BoundLocation",BlockPos.CODEC,pos(compound(source,"BoundLocation")));Identifier.parse(string(source,"BoundDimension"));}
        if(source.contains("PortalBinding"))throw invalid("Original source never owned a portal binding UUID");
    }
    private static void targeting(Identifier type,CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries){
        var data=new CompoundTag();data.store("Id",UUIDUtil.CODEC,uuid(source,"Id"));
        var curve=Optional.<BezierTrackPointLocation>empty();
        if(source.contains("Bezier")){var original=compound(source,"Bezier");keys(original,"target curve","Segment","Key");curve=Optional.of(new BezierTrackPointLocation(pos(compound(original,"Key")),integer(original,"Segment")));}
        var reference=new NeoForgeTrackTargetingReference(version,pos(compound(source,"TargetTrack")),bool(source,"TargetDirection"),bool(source,"Ortho"),axis(source,"PrevAxis"),axis(source,"RotatedAxis"),curve);
        data.store("OriginalTargeting",NeoForgeTrackTargetingReference.CODEC,reference);
        if(source.contains("Migrate")){
            String pointType=switch(type.getPath()){case "track_station"->"create:station";case "track_signal"->"create:signal";default->"create:observer";};
            var migration=NeoForgeTrackGraphImport.convertMigrationSnapshot(Identifier.parse(pointType),compound(source,"Migrate"),version,registries);
            if(!uuid(migration,"Id").equals(uuid(source,"Id")))throw invalid("Original migration belongs to another railway point");
            data.put("Migrate",migration);
        }
        for(String key:TARGET_KEYS)result.remove(key);
        result.put(type.getPath().equals("track_station")?"StationData":"Targeting",data);
    }
    private static Optional<Vec3> axis(CompoundTag source,String key){return source.contains(key)?Optional.of(RailCodecs.VECTOR.parse(NbtOps.INSTANCE,vector(list(source,key))).getOrThrow()):Optional.empty();}
    private static void station(CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope,boolean safe){
        targeting(Identifier.parse("create:track_station"),source,result,version,registries);
        var data=compound(result,"StationData");data.putLong("ConfigurationRevision",0);data.putBoolean("OriginalPointConfigurationPending",true);
        if(safe)return;
        data.putInt("DoorControl",DoorControl.valueOf(string(source,"DoorControl")).ordinal());result.remove("DoorControl");
        var depot=NeoForgeLogisticsBlockEntityImport.convert(Identifier.parse("create:depot"),source,version,registries,scope).orElseThrow();
        result.put("Inventory",compound(depot,"Inventory"));result.remove("OutputBuffer");if(depot.contains("HeldItem"))result.put("HeldItem",depot.get("HeldItem").copy());
        if(depot.contains("Incoming"))result.put("Incoming",depot.get("Incoming").copy());
        int failed=integer(source,"FailedCarriageIndex");if(failed < -1)throw invalid("Invalid original failed carriage index");result.putInt("FailedCarriageIndex",failed);
        if(source.contains("PrevTrainName"))result.put("DisassembledTrainName",NeoForgeTextComponentImport.flattened(StringTag.valueOf(string(source,"PrevTrainName")),version,registries));
        result.putInt("DisassembledTrainColor",integer(source,"PrevTrainColor"));result.remove("PrevTrainName");result.remove("PrevTrainColor");
        if(source.contains("LastException")){
            var original=compound(source,"LastException");keys(original,"station assembly exception","Component","Position");var error=original.copy();
            error.putString("Component",jsonText(string(original,"Component"),version,registries));
            if(original.contains("Position")&&!(original.get("Position") instanceof LongTag))throw invalid("Original station error position is not a packed long");
            var wrapper=new CompoundTag();wrapper.put("LastException",error);result.put("TrainAssemblyFailure",wrapper);result.putString("TrainAssemblyStatus","FAILED");result.remove("LastException");
        }
        for(String packet:Set.of("ForceFlag","ImminentTrain","TrainPresent","TrainCanDisassemble","TrainBackwards","TrainHasSchedule","TrainHasAutoSchedule"))
            if(source.contains(packet))throw invalid("Client-only station field in original disk owner: "+packet);
    }
    private static void flap(CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries){
        bool(source,"Controller");int width=integer(source,"XSize"),height=integer(source,"YSize");
        if(width<1||width>32||height<1||height>33)throw invalid("Original display dimensions exceed actual native capacity");
        for(String key:source.keySet())for(String prefix:List.of("CustomLine","GlowingLine","Dye","Display"))if(key.startsWith(prefix)){
            String suffix=key.substring(prefix.length());int index;try{index=Integer.parseInt(suffix);}catch(NumberFormatException ex){throw invalid("Malformed original display line key "+key);}
            if(index<0||index>=height*2||!suffix.equals(Integer.toString(index)))throw invalid("Original display line lies outside actual height");
        }
        for(int i=0;i<height*2;i++){
            for(String prefix:List.of("CustomLine","GlowingLine"))if(source.contains(prefix+i)&&!bool(source,prefix+i))throw invalid("Original display marker is not true");
            if(source.contains("Dye"+i))DyeColor.valueOf(string(source,"Dye"+i));
            var layout=compound(source,"Display"+i);keys(layout,"display layout","Key","Sections");string(layout,"Key");var sections=list(layout,"Sections");
            if(sections.isEmpty()||sections.size()>256)throw invalid("Original layout exceeds native section capacity");
            var next=new ListTag();
            for(Tag row:sections){
                if(!(row instanceof CompoundTag section))throw invalid("Malformed original display section");
                keys(section,"display section","Width","Cycle","RightAligned","SingleFlap","Gap","Wide","Text","Transition");
                float size=floating(section,"Width");if(size<0||size>8192)throw invalid("Original section width exceeds native capacity");string(section,"Cycle");
                for(String marker:List.of("RightAligned","SingleFlap","Gap","Wide","Transition"))if(section.contains(marker)&&!bool(section,marker))throw invalid("Original section marker is not true");
                var fixed=section.copy();if(section.contains("Text"))fixed.putString("Text",jsonText(string(section,"Text"),version,registries));next.add(fixed);
            }
            var fixed=layout.copy();fixed.put("Sections",next);result.put("Display"+i,fixed);
        }
    }
    private static String jsonText(String source,int version,HolderLookup.Provider registries){
        var fixed=NeoForgeTextComponentImport.flattened(StringTag.valueOf(source),version,registries);
        var component=ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),fixed).getOrThrow();
        return ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),component).getOrThrow().toString();
    }
    private static void display(CompoundTag source,CompoundTag result,boolean safe){
        result.store("TargetOffset",BlockPos.CODEC,pos(compound(source,"TargetOffset")));int line=integer(source,"TargetLine");if(line<0||line>255)throw invalid("Original display target line exceeds native capacity");
        if(source.contains("TargetType")||source.contains("Pulse"))throw invalid("Client-only display field in original saved owner");
        if(source.contains("Source"))result.put("Source",displayConfig(compound(source,"Source")));
        if(!safe&&source.contains("LinkedGauges")){
            var values=new ListTag();var positions=new HashSet<FactoryPanelPosition>();
            for(Tag row:list(source,"LinkedGauges")){
                if(!(row instanceof CompoundTag entry))throw invalid("Malformed original linked factory panel");keys(entry,"linked factory panel","pos","slot");
                var panel=FactoryPanelPosition.CODEC.parse(NbtOps.INSTANCE,entry).getOrThrow();if(!positions.add(panel))throw invalid("Duplicate original linked factory panel");values.add(FactoryPanelPosition.CODEC.encodeStart(NbtOps.INSTANCE,panel).getOrThrow());
            }
            result.put("LinkedGauges",values);
        }
    }
    private static CompoundTag displayConfig(CompoundTag source){
        var id=Identifier.parse(string(source,"Id"));var behaviour=DisplaySource.get(id);
        if(behaviour==null||behaviour.getId()==null||!behaviour.getId().getNamespace().equals("create"))throw invalid("Original display source has no reviewed native owner: "+id);
        String type=behaviour.getId().getPath();var allowed=new HashSet<>(Set.of("Id"));
        switch(type){
            case "station_summary"->allowed.addAll(Set.of("Filter","NameColumn","PlatformColumn"));
            case "scoreboard","death_count"->allowed.addAll(Set.of("Objective","Format"));
            case "list_items","list_fluids","gauge_status"->allowed.add("Format");
            case "boiler"->{}
            case "time_of_day"->allowed.addAll(Set.of("Label","Cycle"));
            case "stopwatch"->allowed.addAll(Set.of("Label","StartTime"));
            case "kinetic_speed"->allowed.addAll(Set.of("Label","Directional"));
            case "kinetic_stress","redstone_power","fill_level"->allowed.addAll(Set.of("Label","Mode"));
            case "accumulate_items"->allowed.addAll(Set.of("Label","Collected"));
            case "item_throughput"->allowed.addAll(Set.of("Label","Collected","Interval","Rate","LastReceived","LastReceivedAmount","Index","PrevRates","Inactive"));
            case "observed_train_name","train_status","enchant_power","nixie_tube","item_names","current_floor","entity_name","count_items","count_fluids","read_package_address"->allowed.add("Label");
            default->throw invalid("Unreviewed original display source schema "+id);
        }
        keys(source,"display source "+type,allowed.toArray(String[]::new));
        for(String key:source.keySet()){
            if(Set.of("Id","Label","Objective","Filter").contains(key))string(source,key);
            else if(Set.of("StartTime","LastReceived").contains(key)){if(!(source.get(key) instanceof LongTag))throw invalid("Original display time is not a long");}
            else if(key.equals("Rate")){if(!(source.get(key) instanceof FloatTag))throw invalid("Original throughput rate is not a float");}
            else if(key.equals("Inactive")){if(!bool(source,key))throw invalid("Original Inactive marker is not true");}
            else if(key.equals("PrevRates")){
                var rates=list(source,key);if(rates.size()!=10)throw invalid("Original throughput pool requires ten rates");
                for(Tag rate:rates)if(!(rate instanceof FloatTag))throw invalid("Original throughput sample is not a float");
            }else integer(source,key);
        }
        // Same-tick original arrivals can genuinely persist +Infinity in this scalar rate pool.
        // Preserve its exact FloatTag; native display/next received sample owns its interpretation.
        var result=source.copy();result.putString("Id",behaviour.getId().toString());return result;
    }
    private NeoForgeRailDisplayBlockEntityImport(){}
}

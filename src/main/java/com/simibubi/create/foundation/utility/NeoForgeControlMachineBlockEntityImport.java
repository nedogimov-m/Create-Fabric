package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.JsonOps;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/** Reviewed original control policies, real already-burned fuel time and actual material/text metadata. */
public final class NeoForgeControlMachineBlockEntityImport {
    private static final Set<String> TYPES=Set.of("adjustable_chain_gearshift","nozzle","hand_crank","valve_handle","cuckoo_clock","packager_link","contraption_controls","drill","steam_whistle","powered_shaft","water_wheel","large_water_wheel","blaze_heater","speedometer","stressometer","nixie_tube","stockpile_switch","creative_crate","content_observer","peculiar_bell","cursed_bell","desk_bell");
    public static Optional<CompoundTag> convert(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        Objects.requireNonNull(type,"type");if(!type.getNamespace().equals("create")||!TYPES.contains(type.getPath()))return Optional.empty();
        check(version,registries);var result=Objects.requireNonNull(source,"source").copy();
        switch(type.getPath()){
            case "adjustable_chain_gearshift"->range(integer(source,"Signal"),0,15,"analogue gearshift signal");
            case "hand_crank","valve_handle"->{
                int remaining=integer(source,"InUse");if(remaining<0)throw invalid("Negative original crank use countdown");bool(source,"Backwards");
                if(type.getPath().equals("valve_handle")){
                    int total=integer(source,"TotalUseTicks");if(total<remaining)throw invalid("Original valve use countdown exceeds its actual original total");
                    integer(source,"StartAngle");integer(source,"TargetAngle");
                    if(integer(source,"ScrollValue")==Integer.MIN_VALUE)throw invalid("Original valve angle cannot form its actual positive native instruction magnitude");
                }
            }
            case "packager_link"->{uuid(source,"Freq");range(integer(source,"Power"),0,15,"logistical link power");bool(source,"Added");if(source.contains("PlacedBy"))uuid(source,"PlacedBy");}
            case "contraption_controls"->{bool(source,"Disabled");bool(source,"Powered");}
            case "drill"->{range(integer(source,"Progress"),0,9,"original drill breaking progress");if(integer(source,"NextTick")< -1)throw invalid("Invalid original drill progress countdown");if(source.contains("Breaking"))result.store("Breaking",BlockPos.CODEC,pos(compound(source,"Breaking")));}
            case "steam_whistle"->range(integer(source,"Pitch"),0,26,"original actual whistle extension pitch");
            case "powered_shaft"->{
                range(integer(source,"Direction"),-1,1,"original powered shaft direction");
                int warmup=source.contains("Warmup")?integer(source,"Warmup"):0;if(warmup<0)throw invalid("Negative original engine warmup");result.putInt("Warmup",warmup);
                if(source.contains("EnginePos")){
                    result.store("EnginePos",BlockPos.CODEC,pos(compound(source,"EnginePos")));float power=floating(source,"EnginePower");if(power<0)throw invalid("Negative original engine efficiency");
                    var block=Identifier.parse(string(source,"EngineType"));if(!BuiltInRegistries.BLOCK.containsKey(block))throw invalid("Unavailable original engine capacity block "+block);
                }else if(source.contains("EnginePower")||source.contains("EngineType"))throw invalid("Original engine metadata lacks its actual relative owner position");
            }
            case "water_wheel","large_water_wheel"->{integer(source,"FlowScore");material(source,result,version,registries);}
            case "blaze_heater"->{
                boolean creative=source.contains("isCreative")&&bool(source,"isCreative");if(source.contains("isCreative")&&!creative)throw invalid("Original creative burner marker is only written true");
                if(!creative){range(integer(source,"fuelLevel"),0,2,"original burner fuel kind");if(integer(source,"burnTimeRemaining")<0)throw invalid("Negative original already consumed fuel time");}
                for(String key:Set.of("Goggles","TrainHat"))if(source.contains(key)&&!bool(source,key))throw invalid("Original burner equipment marker is only written true");
            }
            case "speedometer","stressometer"->{floating(source,"Value");integer(source,"Color");}
            case "nixie_tube"->{
                if(source.contains("CustomText")){
                    int index=integer(source,"CustomTextIndex");if(index<0||index>Integer.MAX_VALUE/2-1)throw invalid("Unrepresentable original nixie pair index");
                    for(String key:Set.of("CustomText","RawCustomText"))result.putString(key,modernJson(string(source,key),version,registries,child(scope,key.equals("CustomText")?"custom_text":"raw_custom_text",0)));
                }else range(integer(source,"RedstoneStrength"),0,15,"original nixie redstone strength");
            }
            case "stockpile_switch"->{thresholds(source,result);for(String key:Set.of("CurrentAmount","CurrentMinAmount","CurrentMaxAmount"))integer(source,key);for(String key:Set.of("InStacks","Powered","PoweredAfterDelay"))bool(source,key);}
            case "content_observer"->{if(integer(source,"TurnOff")<0)throw invalid("Negative original observer pulse countdown");}
            case "cursed_bell"->{if(integer(source,"EffectTicks")<0)throw invalid("Negative original haunting effect countdown");}
            default->{/* Source nozzle/clock/ordinary bells/desk bell add client-only animation; crate owns only the root-converted generator filter exemplar. */}
        }
        return Optional.of(result);
    }
    /** Genuine partial writers: wheel material appearance and stockpile thresholds, never full measurement/job state. */
    public static Optional<CompoundTag> convertSafe(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        Objects.requireNonNull(type,"type");if(!type.getNamespace().equals("create")||!Set.of("water_wheel","large_water_wheel","stockpile_switch").contains(type.getPath()))return Optional.empty();
        check(version,registries);keys(source,"partial control safe writer","components","Filter","FilterAmount","UpTo","Filters","FrequencyFirst","FrequencyLast","LastKnownPosition","ScrollValue","ScrollOption","Freq","Bracket","Material","OnAboveAmount","OffBelowAmount","Inverted");
        var result=source.copy();if(type.getPath().equals("stockpile_switch"))thresholds(source,result);else material(source,result,version,registries);return Optional.of(result);
    }
    private static void material(CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries){if(source.contains("Material"))result.put("Material",NeoForgeBlockStateImport.convert(compound(source,"Material"),version,registries));}
    private static void thresholds(CompoundTag source,CompoundTag result){
        // Actual source writer emits FLOAT despite integer policy fields; original numeric getInt floors it.
        // These are source item/custom levels or millibuckets, not additional stored fluid in droplets.
        for(String key:Set.of("OnAboveAmount","OffBelowAmount"))result.putInt(key,(int)Math.floor(floating(source,key)));bool(source,"Inverted");
    }
    private static String modernJson(String source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        var fixed=NeoForgeTextComponentImport.flattened(StringTag.valueOf(source),version,registries,scope);
        var text=ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),fixed).getOrThrow();
        return ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),text).getOrThrow().toString();
    }
    private static void range(int value,int min,int max,String context){if(value<min||value>max)throw invalid("Unrepresentable "+context);}
    private static void check(int version,HolderLookup.Provider registries){if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original control machine version");Objects.requireNonNull(registries,"registries");}
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope,String role,int index){return scope==null?null:scope.item(role,index);}
    private NeoForgeControlMachineBlockEntityImport(){}
}

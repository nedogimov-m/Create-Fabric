package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.JsonOps;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/** Reviewed source inheritance/behaviours for twenty small machine schemas, with shared common metadata root-owned. */
public final class NeoForgeSimpleMachineBlockEntityImport {
    private static final Set<String> TYPES=Set.of("simple_kinetic","gearbox","encased_shaft","encased_cogwheel","encased_large_cogwheel","encased_fan","clutch","gearshift","turntable","gantry_shaft","gantry_pinion","sticker","harvester","mechanical_roller","steam_engine","flywheel","crushing_wheel","sliding_door","item_hatch","fake_track");
    public static Optional<CompoundTag> convert(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        Objects.requireNonNull(type,"type");if(!type.getNamespace().equals("create")||!TYPES.contains(type.getPath()))return Optional.empty();
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original simple machine version");
        Objects.requireNonNull(registries,"registries");var result=Objects.requireNonNull(source,"source").copy();
        switch(type.getPath()){
            case "simple_kinetic"->{if(source.contains("Bracket"))result.put("Bracket",NeoForgeBlockStateImport.convert(compound(source,"Bracket"),version,registries));}
            case "gantry_pinion"->assemblyError(source,result,version,registries,scope);
            case "steam_engine"->{int direction=integer(source,"ScrollValue");if(direction<0||direction>1)throw invalid("Unknown original steam engine rotation direction");result.putInt("MovementDirection",direction);}
            case "mechanical_roller"->{int mode=integer(source,"ScrollValue");if(mode<0||mode>2)throw invalid("Unknown original roller operating mode");}
            default->{/* Actual reviewed source owner adds no persisted domain state beyond root-owned common behaviours. */}
        }
        return Optional.of(result);
    }
    /** These source owners export only the actual safe behaviours, never a domain job/body owner. */
    public static Optional<CompoundTag> convertSafe(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        Objects.requireNonNull(type,"type");if(!type.getNamespace().equals("create")||!TYPES.contains(type.getPath()))return Optional.empty();
        keys(source,"simple machine safe behaviours","components","Filter","FilterAmount","UpTo","Filters","FrequencyFirst","FrequencyLast","LastKnownPosition","ScrollValue","ScrollOption","Freq","Bracket");
        // In particular SteamEngine's real safe ScrollValue needs the same MovementDirection alias.
        return convert(type,source,version,registries,scope);
    }
    private static void assemblyError(CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        if(!source.contains("LastException"))return;var original=compound(source,"LastException");keys(original,"gantry assembly exception","Component","Position");
        var fixed=NeoForgeTextComponentImport.flattened(StringTag.valueOf(string(original,"Component")),version,registries,scope==null?null:scope.item("assembly_exception_text",0));
        var component=ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),fixed).getOrThrow();var next=new CompoundTag();
        next.putString("Component",ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),component).getOrThrow().toString());
        if(original.contains("Position")){if(!(original.get("Position") instanceof LongTag))throw invalid("Original gantry exception position is not the genuine packed long");next.put("Position",original.get("Position").copy());}
        var wrapper=new CompoundTag();wrapper.put("LastException",next);result.put("AssemblyError",wrapper);result.remove("LastException");
    }
    private NeoForgeSimpleMachineBlockEntityImport(){}
}

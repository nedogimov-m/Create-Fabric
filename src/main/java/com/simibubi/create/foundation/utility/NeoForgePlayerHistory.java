package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

/** Actual original player metadata only. Historical flags never become receipts or an old attacker UUID. */
public record NeoForgePlayerHistory(int sourceDataVersion,UUID originalPlayer,Identifier originalDimension,int hurtByTimestamp,CompoundTag originalCoreData,ListTag originalAttributes){
    public static final String KEY="CreateOriginalPlayerHistory";
    public static final Codec<NeoForgePlayerHistory> CODEC=CompoundTag.CODEC.xmap(NeoForgePlayerHistory::read,NeoForgePlayerHistory::write);
    private static final Set<String> FIELDS=Set.of("CreateToolboxData","CreateNetheriteDivingBits","CreateFireImmune","HeavyBoots","LavaGrounded","VisualBacktankAir","ContraptionDismountLocation","ContraptionMountLocation","ContraptionGrounded","TrainHat","LastOverrideLimbSwingUpdate","OverrideLimbSwing","createExtendo","createDualExtendo","IsUsingLecternController","SilenceTankSound","SilenceVaultSound");
    public NeoForgePlayerHistory{if(sourceDataVersion!=3955)throw invalid("Unknown original player history version");Objects.requireNonNull(originalPlayer);Objects.requireNonNull(originalDimension);originalCoreData=Objects.requireNonNull(originalCoreData).copy();validateCoreData(originalCoreData);originalAttributes=Objects.requireNonNull(originalAttributes).copy();NeoForgePlayerLivingValues.validateHistoricalAttributes(originalAttributes);}
    @Override public CompoundTag originalCoreData(){return originalCoreData.copy();}
    @Override public ListTag originalAttributes(){return originalAttributes.copy();}
    static void validateCoreData(CompoundTag data){
        for(String key:data.keySet())if(!FIELDS.contains(key))throw invalid("Unknown original core/addon player persistent owner "+key);
        for(String key:Set.of("CreateFireImmune","HeavyBoots","LavaGrounded","ContraptionGrounded","TrainHat","createExtendo","createDualExtendo","IsUsingLecternController","SilenceTankSound","SilenceVaultSound"))if(data.contains(key))bool(data,key);
        if(data.contains("CreateNetheriteDivingBits")&&(!(data.get("CreateNetheriteDivingBits") instanceof ByteTag bits)||(Byte.toUnsignedInt(bits.byteValue())&~15)!=0))throw invalid("Malformed actual source player diving bit mask");
        for(String key:Set.of("VisualBacktankAir","LastOverrideLimbSwingUpdate"))if(data.contains(key)&&integer(data,key)<0)throw invalid("Negative actual source player metadata counter");if(data.contains("OverrideLimbSwing"))floating(data,"OverrideLimbSwing");
        for(String key:Set.of("ContraptionMountLocation","ContraptionDismountLocation"))if(data.contains(key))vector(list(data,key));
        if(data.contains("CreateToolboxData"))toolbox(compound(data,"CreateToolboxData"));
    }
    static CompoundTag toolbox(CompoundTag data){
        var nativeData=new CompoundTag();for(String key:data.keySet()){
            int hotbar;try{hotbar=Integer.parseInt(key);}catch(NumberFormatException failure){throw invalid("Malformed actual source toolbox hotbar key");}
            if(hotbar<0||hotbar>8||!key.equals(Integer.toString(hotbar)))throw invalid("Original toolbox binding lies outside actual hotbar slots");var row=compound(data,key);keys(row,"actual original player toolbox binding","Slot","Pos");int slot=integer(row,"Slot");if(slot<0||slot>=8)throw invalid("Original toolbox binding has no genuine compartment");var actual=pos(compound(row,"Pos"));var next=new CompoundTag();next.putInt("Slot",slot);next.store("Pos",net.minecraft.core.BlockPos.CODEC,actual);nativeData.put(key,next);
        }return nativeData;
    }
    public static NeoForgePlayerHistory read(CompoundTag data){keys(data,"original player history","SourceDataVersion","Player","Dimension","HurtByTimestamp","CoreData","Attributes");return new NeoForgePlayerHistory(integer(data,"SourceDataVersion"),uuid(data,"Player"),Identifier.parse(string(data,"Dimension")),integer(data,"HurtByTimestamp"),compound(data,"CoreData"),list(data,"Attributes"));}
    public CompoundTag write(){var data=new CompoundTag();data.putInt("SourceDataVersion",sourceDataVersion);data.store("Player",UUIDUtil.CODEC,originalPlayer);data.putString("Dimension",originalDimension.toString());data.putInt("HurtByTimestamp",hurtByTimestamp);data.put("CoreData",originalCoreData());data.put("Attributes",originalAttributes());return data;}
}

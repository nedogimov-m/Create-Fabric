package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.world.level.ChunkPos;

/** Pure original entity-region envelope preparation; the caller still owns actual disk/admission policy. */
public final class NeoForgeEntityChunkImport {
    public static CompoundTag convert(CompoundTag original,HolderLookup.Provider registries){
        keys(original,"original entity chunk envelope","DataVersion","Position","Entities");
        if(!(original.get("DataVersion") instanceof IntTag)||integer(original,"DataVersion")!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw invalid("Entity chunk migration requires its genuine original3955 envelope version");
        if(!(original.get("Position") instanceof IntArrayTag position)||position.getAsIntArray().length!=2)
            throw invalid("Original entity chunk has no actual source two-coordinate region position");
        var actualHeader=original.read("Position",ChunkPos.CODEC).orElseThrow(()->invalid("Malformed genuine original chunk coordinates"));
        for(var entry:list(original,"Entities")){
            if(!(entry instanceof CompoundTag root))throw invalid("Malformed original root entity");
            var sourcePos=vector(list(root,"Pos"));double x=((DoubleTag)sourcePos.get(0)).doubleValue(),z=((DoubleTag)sourcePos.get(2)).doubleValue();
            if(x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000)throw invalid("Original root coordinates exceed genuine native world bounds");
            var containing=new ChunkPos((int)Math.floor(x/16.0),(int)Math.floor(z/16.0));
            if(!containing.equals(actualHeader))throw invalid("Original root position disagrees with its actual saved chunk header");
        }
        var entities=NeoForgeStandaloneEntityImport.convertRegionRoots(list(original,"Entities"),NeoForgeSavedDataImport.SOURCE_DATA_VERSION,registries);
        var result=original.copy();result.put("Entities",entities);NbtUtils.addCurrentDataVersion(result);return result;
    }
    /** Actual native entity-region caller supplies its real requested position before permissive native logs. */
    public static CompoundTag convert(CompoundTag original,ChunkPos actualRequested,HolderLookup.Provider registries){
        var header=original.read("Position",ChunkPos.CODEC).orElseThrow(()->invalid("Original envelope lacks genuine requested region coordinates"));
        if(!header.equals(java.util.Objects.requireNonNull(actualRequested)))throw invalid("Original entity-region position disagrees with its actual requested native chunk");
        return convert(original,registries);
    }
    private NeoForgeEntityChunkImport(){}
}

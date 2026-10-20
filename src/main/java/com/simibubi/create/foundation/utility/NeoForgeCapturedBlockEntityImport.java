package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Captured full server owner and separate, genuinely source-shaped client snapshot. */
public final class NeoForgeCapturedBlockEntityImport {
    public static CompoundTag convert(CompoundTag originalRow,BlockPos actualOriginalLocalPos,BlockState actualNativeState,
            int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope) {
        var result=originalRow.copy();
        if(!originalRow.contains("Data")&&!originalRow.contains("UpdateTag"))return result;
        var originalType=NeoForgeBlockEntityImport.originalTypeFor(actualNativeState);
        if(!(actualNativeState.getBlock() instanceof EntityBlock factory))throw invalid("Captured BE has no actual containing block factory");
        var inspected=factory.newBlockEntity(actualOriginalLocalPos,actualNativeState);
        if(inspected==null)throw invalid("Actual captured state factory returned no native BE");
        var nativeType=BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(inspected.getType());
        if(nativeType==null||!inspected.getType().isValid(actualNativeState))throw invalid("Actual captured BE factory has no valid registered native type");
        CompoundTag converted=null;
        boolean replacedPoweredShaft=false;
        if(originalRow.contains("Data")){
            var data=compound(originalRow,"Data");
            var fullSourceType=Identifier.parse(string(data,"id"));
            // Pinned Contraption.capture explicitly replaces POWERED_SHAFT's state and client BE
            // with a fresh SHAFT/BRACKETED_KINETIC, while retaining its original full NBT.
            replacedPoweredShaft=fullSourceType.equals(Identifier.parse("create:powered_shaft"))
                &&originalType.equals(Identifier.parse("create:simple_kinetic"))&&actualNativeState.is(com.simibubi.create.AllBlocks.SHAFT);
            if(!fullSourceType.equals(originalType)&&!replacedPoweredShaft)throw invalid("Original captured BE id disagrees with its genuine containing state factory");
            converted=NeoForgeBlockEntityImport.convert(fullSourceType,data,version,registries,scope.block(actualOriginalLocalPos));
            if(converted.contains("id")){
                var emitted=Identifier.parse(string(converted,"id"));
                if(!emitted.equals(fullSourceType)&&!emitted.equals(nativeType))throw invalid("Captured converter emitted a different native BE owner");
            }
            converted.putString("id",nativeType.toString());result.put("Data",converted);
        }
        if(originalRow.contains("UpdateTag")){
            var update=compound(originalRow,"UpdateTag");
            if(update.contains("id")&&!Identifier.parse(string(update,"id")).equals(originalType))throw invalid("Original full/client captured BE type owners disagree");
            // Source Contraption.addBlock deliberately retained genuinely empty getUpdateTag values;
            // vanilla3955 BlockEntity.getUpdateTag itself returns new CompoundTag(). This is a
            // client snapshot, never an absent saved inventory/default server-material owner.
            if(update.isEmpty())result.put("UpdateTag",update.copy());
            else if(converted!=null&&additional(update).equals(additional(compound(originalRow,"Data"))))
                result.put("UpdateTag",additional(converted));
            else if(converted!=null)result.put("UpdateTag",NeoForgeClientBlockEntityImport.convert(originalType,
                replacedPoweredShaft?new CompoundTag():compound(originalRow,"Data"),converted,update,actualOriginalLocalPos,actualNativeState,version,registries,scope.block(actualOriginalLocalPos)));
            else throw invalid("Original partial client snapshot has no separate genuine full Data owner");
        }
        return result;
    }
    private static CompoundTag additional(CompoundTag full){
        var result=full.copy();for(String identity:new String[]{"id","x","y","z"})result.remove(identity);return result;
    }
    private NeoForgeCapturedBlockEntityImport(){}
}

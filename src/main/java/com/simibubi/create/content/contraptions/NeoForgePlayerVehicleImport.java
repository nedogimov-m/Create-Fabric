package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.content.trains.entity.NeoForgeRailwayPlayerReference;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Exact RootVehicle fields only; ROOT owns the containing actual PlayerData and its single PLAYER DFU. */
public final class NeoForgePlayerVehicleImport {
    public static void prepareVehicleFields(CompoundTag actualOriginalPlayer,CompoundTag preparedPlayer,ServerLevel actualSourceLevel,int sourceVersion){
        if(actualOriginalPlayer==preparedPlayer)throw invalid("Actual original player must remain untouched during pure preparation");
        if(sourceVersion!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original player vehicle version");
        if(actualOriginalPlayer.contains(NeoForgeRailwayPlayerReference.KEY))throw invalid("Original3955 player cannot already contain a native original-railway reference");
        if(!actualOriginalPlayer.contains("RootVehicle"))return;
        var originalVehicle=compound(actualOriginalPlayer,"RootVehicle");var entity=compound(originalVehicle,"Entity");
        if(string(entity,"id").equals("create:carriage_contraption")){
            var reference=NeoForgeRailwayPlayerReference.fromOriginalPlayer(actualOriginalPlayer,sourceVersion,actualSourceLevel);
            preparedPlayer.remove("RootVehicle");preparedPlayer.put(NeoForgeRailwayPlayerReference.KEY,reference.write());
        }else preparedPlayer.put("RootVehicle",NeoForgeStandaloneEntityImport.playerRootVehicle(actualOriginalPlayer,sourceVersion,actualSourceLevel.registryAccess()));
    }
    private NeoForgePlayerVehicleImport(){}
}

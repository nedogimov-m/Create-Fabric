package com.simibubi.create.foundation.utility;

import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Pure identity witness for a loaded world journal; never loads or decodes saved data. */
public final class NativeSavedDataIdentity {
    public static boolean isCurrent(ServerLevel level,SavedDataType<?> type,SavedData expected){
        if(level==null||expected==null)return false;
        var slot=((SavedDataStorageAccessor)level.getDataStorage()).create$getCache().get(type);
        return slot!=null&&slot.orElse(null)==expected;
    }
    private NativeSavedDataIdentity(){}
}

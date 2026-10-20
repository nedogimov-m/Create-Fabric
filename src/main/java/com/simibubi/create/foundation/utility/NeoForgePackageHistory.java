package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;

/** Genuine original scalar history only; it never invents an attacker UUID or a current native tick. */
public record NeoForgePackageHistory(int sourceDataVersion,int originalHurtByTimestamp) {
    public static final String KEY="CreateOriginalPackageHistory";
    public static final Codec<NeoForgePackageHistory> CODEC=CompoundTag.CODEC.xmap(NeoForgePackageHistory::read,NeoForgePackageHistory::write);
    public NeoForgePackageHistory{if(sourceDataVersion!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original package history version");}
    public static NeoForgePackageHistory read(CompoundTag actual){keys(actual,"original package scalar history","SourceDataVersion","HurtByTimestamp");return new NeoForgePackageHistory(integer(actual,"SourceDataVersion"),integer(actual,"HurtByTimestamp"));}
    public CompoundTag write(){var tag=new CompoundTag();tag.putInt("SourceDataVersion",sourceDataVersion);tag.putInt("HurtByTimestamp",originalHurtByTimestamp);return tag;}
}

package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Inert original stabilized-child pose/cache. Only StabilizedParentMotion may continue current motion. */
public record NeoForgeStabilizedSource(int sourceDataVersion,UUID actualOriginalParent,boolean originalWasStalled,
        float originalYaw,Optional<Float> originalForceYaw,Optional<Vec3> originalCachedMotion) {
    public static final String KEY="CreateOriginalStabilizedSource";
    public static final Codec<NeoForgeStabilizedSource> CODEC=CompoundTag.CODEC.xmap(NeoForgeStabilizedSource::read,NeoForgeStabilizedSource::write);
    public NeoForgeStabilizedSource{Objects.requireNonNull(actualOriginalParent);Objects.requireNonNull(originalForceYaw);Objects.requireNonNull(originalCachedMotion);if(sourceDataVersion!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION||!Float.isFinite(originalYaw)||originalForceYaw.isPresent()&&!Float.isFinite(originalForceYaw.get())||originalCachedMotion.isPresent()&&(!Double.isFinite(originalCachedMotion.get().x)||!Double.isFinite(originalCachedMotion.get().y)||!Double.isFinite(originalCachedMotion.get().z)))throw invalid("Invalid actual original stabilized source history");}
    public static NeoForgeStabilizedSource read(CompoundTag source){keys(source,"original stabilized source","SourceDataVersion","Parent","WasStalled","Yaw","ForceYaw","CachedMotion");return new NeoForgeStabilizedSource(integer(source,"SourceDataVersion"),uuid(source,"Parent"),bool(source,"WasStalled"),floating(source,"Yaw"),source.contains("ForceYaw")?Optional.of(floating(source,"ForceYaw")):Optional.empty(),source.contains("CachedMotion")?Optional.of(source.read("CachedMotion",Vec3.CODEC).orElseThrow()):Optional.empty());}
    public CompoundTag write(){var source=new CompoundTag();source.putInt("SourceDataVersion",sourceDataVersion);source.store("Parent",UUIDUtil.CODEC,actualOriginalParent);source.putBoolean("WasStalled",originalWasStalled);source.putFloat("Yaw",originalYaw);originalForceYaw.ifPresent(value->source.putFloat("ForceYaw",value));originalCachedMotion.ifPresent(value->source.store("CachedMotion",Vec3.CODEC,value));return source;}
}

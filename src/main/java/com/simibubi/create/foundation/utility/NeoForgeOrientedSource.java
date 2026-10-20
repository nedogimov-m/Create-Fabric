package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Inert original fields only. Current native controller state, never this history, resumes motion. */
public record NeoForgeOrientedSource(int sourceDataVersion,String sourceControllerKind,boolean originalWasStalled,
        float originalYaw,Optional<Float> originalForceYaw,Optional<Vec3> originalCachedMotion) {
    public static final String KEY="CreateOriginalOrientedSource";
    public static final Codec<NeoForgeOrientedSource> CODEC=CompoundTag.CODEC.xmap(NeoForgeOrientedSource::read,NeoForgeOrientedSource::write);
    public NeoForgeOrientedSource{
        if(sourceDataVersion!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION||!Set.of("ABSENT","EMPTY","NORMAL").contains(sourceControllerKind)
                ||!Float.isFinite(originalYaw)||originalForceYaw.isPresent()&&!Float.isFinite(originalForceYaw.get())
                ||originalCachedMotion.isPresent()&&!finite(originalCachedMotion.get()))throw invalid("Invalid genuine oriented source history");
    }
    private static boolean finite(Vec3 vector){return Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);}
    public static NeoForgeOrientedSource read(CompoundTag actual){
        keys(actual,"original oriented history","SourceDataVersion","ControllerKind","WasStalled","Yaw","ForceYaw","CachedMotion");
        return new NeoForgeOrientedSource(integer(actual,"SourceDataVersion"),string(actual,"ControllerKind"),bool(actual,"WasStalled"),floating(actual,"Yaw"),actual.contains("ForceYaw")?Optional.of(floating(actual,"ForceYaw")):Optional.empty(),
            actual.contains("CachedMotion")?Optional.of(Vec3.CODEC.parse(NbtOps.INSTANCE,actual.get("CachedMotion")).getOrThrow()):Optional.empty());
    }
    public CompoundTag write(){
        var actual=new CompoundTag();actual.putInt("SourceDataVersion",sourceDataVersion);actual.putString("ControllerKind",sourceControllerKind);actual.putBoolean("WasStalled",originalWasStalled);actual.putFloat("Yaw",originalYaw);
        originalForceYaw.ifPresent(value->actual.putFloat("ForceYaw",value));originalCachedMotion.ifPresent(value->actual.put("CachedMotion",Vec3.CODEC.encodeStart(NbtOps.INSTANCE,value).getOrThrow()));return actual;
    }
}

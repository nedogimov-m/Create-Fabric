package com.simibubi.create.content.trains;

import com.mojang.serialization.Codec;
import com.simibubi.create.Create;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;

/**
 * Actual globally owned source Train/carriage/cargo records. Native save captures the real manager
 * with the real server registries; unopened data remains intact until that owner is hydrated.
 */
public final class TrainTrafficSavedData extends SavedData {
    public static final Codec<TrainTrafficSavedData> CODEC=CompoundTag.CODEC.fieldOf("TrainTraffic").codec().xmap(TrainTrafficSavedData::new,TrainTrafficSavedData::snapshot);
    public static final SavedDataType<TrainTrafficSavedData> TYPE=new SavedDataType<>(Create.asResource("trains"),TrainTrafficSavedData::new,CODEC,null);
    private final CompoundTag retained;
    private TrainManager owner;
    public TrainTrafficSavedData(){retained=new CompoundTag();retained.putInt("TrafficFormatVersion",1);}
    private TrainTrafficSavedData(CompoundTag saved){retained=saved.copy();if(retained.getIntOr("TrafficFormatVersion",-1)!=1)throw new IllegalArgumentException("Unsupported actual native Train traffic format");}
    public CompoundTag retained(){return retained.copy();}
    void bind(TrainManager owner){if(this.owner!=null&&this.owner!=owner)throw new IllegalStateException("Train data already has an actual server owner");this.owner=java.util.Objects.requireNonNull(owner);}
    private CompoundTag snapshot(){return owner==null?retained.copy():owner.writeSnapshot();}
    public static TrainTrafficSavedData load(MinecraftServer server){return NeoForgeRailwayImport.load(server).second();}
}

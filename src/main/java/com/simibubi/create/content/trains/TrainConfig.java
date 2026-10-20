package com.simibubi.create.content.trains;

import com.google.gson.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

/** Actual native file-backed CTrains values, with distinct server and received client snapshots. */
public final class TrainConfig {
    public record Values(boolean trainsCauseDamage,int maxTrackPlacementLength,int maxAssemblyLength,int maxBogeyCount,float manualTrainSpeedModifier,float trainTopSpeed,float trainTurningTopSpeed,float trainAcceleration,float poweredTrainTopSpeed,float poweredTrainTurningTopSpeed,float poweredTrainAcceleration){
        public Values{
            if(maxTrackPlacementLength<16||maxTrackPlacementLength>128||maxAssemblyLength<5||maxAssemblyLength>512||maxBogeyCount<1||maxBogeyCount>200)throw new IllegalArgumentException("Train configuration outside source ranges");
            for(float value:new float[]{manualTrainSpeedModifier,trainTopSpeed,trainTurningTopSpeed,trainAcceleration,poweredTrainTopSpeed,poweredTrainTurningTopSpeed,poweredTrainAcceleration})if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Train speed/acceleration must be finite and nonnegative");
        }
        public static final Codec<Values> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.BOOL.optionalFieldOf("trainsCauseDamage",true).forGetter(Values::trainsCauseDamage),
            Codec.intRange(16,128).optionalFieldOf("maxTrackPlacementLength",32).forGetter(Values::maxTrackPlacementLength),
            Codec.intRange(5,512).optionalFieldOf("maxAssemblyLength",128).forGetter(Values::maxAssemblyLength),
            Codec.intRange(1,200).optionalFieldOf("maxBogeyCount",20).forGetter(Values::maxBogeyCount),
            Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("manualTrainSpeedModifier",.75f).forGetter(Values::manualTrainSpeedModifier),
            Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("trainTopSpeed",28f).forGetter(Values::trainTopSpeed),
            Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("trainTurningTopSpeed",14f).forGetter(Values::trainTurningTopSpeed),
            Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("trainAcceleration",3f).forGetter(Values::trainAcceleration),
            Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("poweredTrainTopSpeed",40f).forGetter(Values::poweredTrainTopSpeed),
            Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("poweredTrainTurningTopSpeed",20f).forGetter(Values::poweredTrainTurningTopSpeed),
            Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("poweredTrainAcceleration",3f).forGetter(Values::poweredTrainAcceleration)
        ).apply(i,Values::new));
        public static final StreamCodec<RegistryFriendlyByteBuf,Values> STREAM_CODEC=StreamCodec.of((buffer,value)->{buffer.writeBoolean(value.trainsCauseDamage);buffer.writeVarInt(value.maxTrackPlacementLength);buffer.writeVarInt(value.maxAssemblyLength);buffer.writeVarInt(value.maxBogeyCount);buffer.writeFloat(value.manualTrainSpeedModifier);buffer.writeFloat(value.trainTopSpeed);buffer.writeFloat(value.trainTurningTopSpeed);buffer.writeFloat(value.trainAcceleration);buffer.writeFloat(value.poweredTrainTopSpeed);buffer.writeFloat(value.poweredTrainTurningTopSpeed);buffer.writeFloat(value.poweredTrainAcceleration);},buffer->new Values(buffer.readBoolean(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readFloat(),buffer.readFloat(),buffer.readFloat(),buffer.readFloat(),buffer.readFloat(),buffer.readFloat(),buffer.readFloat()));
    }
    public static final Values DEFAULTS=new Values(true,32,128,20,.75f,28,14,3,40,20,3);
    private static volatile Values serverValues=DEFAULTS,clientValues=DEFAULTS;
    public static Values get(Level level){return level.isClientSide()?clientValues:serverValues;}
    public static Values server(){return serverValues;}
    public static Values client(){return clientValues;}
    public static void installClient(Values values){clientValues=java.util.Objects.requireNonNull(values);var config=com.simibubi.create.infrastructure.config.AllConfigs.server();com.simibubi.create.infrastructure.config.AllConfigs.installRemoteValues(java.util.Map.ofEntries(java.util.Map.entry(config.trains.trainsCauseDamage.key(),values.trainsCauseDamage()),java.util.Map.entry(config.trains.maxTrackPlacementLength.key(),values.maxTrackPlacementLength()),java.util.Map.entry(config.trains.maxAssemblyLength.key(),values.maxAssemblyLength()),java.util.Map.entry(config.trains.maxBogeyCount.key(),values.maxBogeyCount()),java.util.Map.entry(config.trains.manualTrainSpeedModifier.key(),values.manualTrainSpeedModifier()),java.util.Map.entry(config.trains.trainTopSpeed.key(),values.trainTopSpeed()),java.util.Map.entry(config.trains.trainTurningTopSpeed.key(),values.trainTurningTopSpeed()),java.util.Map.entry(config.trains.trainAcceleration.key(),values.trainAcceleration()),java.util.Map.entry(config.trains.poweredTrainTopSpeed.key(),values.poweredTrainTopSpeed()),java.util.Map.entry(config.trains.poweredTrainTurningTopSpeed.key(),values.poweredTrainTurningTopSpeed()),java.util.Map.entry(config.trains.poweredTrainAcceleration.key(),values.poweredTrainAcceleration())));}
    public static void clearClient(){clientValues=DEFAULTS;}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-trains.json"));}
    public static void reload(Path path){var config=com.simibubi.create.infrastructure.config.AllConfigs.server();com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(path,Values.CODEC,DEFAULTS,value->java.util.Map.ofEntries(java.util.Map.entry(config.trains.trainsCauseDamage.key(),value.trainsCauseDamage()),java.util.Map.entry(config.trains.maxTrackPlacementLength.key(),value.maxTrackPlacementLength()),java.util.Map.entry(config.trains.maxAssemblyLength.key(),value.maxAssemblyLength()),java.util.Map.entry(config.trains.maxBogeyCount.key(),value.maxBogeyCount()),java.util.Map.entry(config.trains.manualTrainSpeedModifier.key(),value.manualTrainSpeedModifier()),java.util.Map.entry(config.trains.trainTopSpeed.key(),value.trainTopSpeed()),java.util.Map.entry(config.trains.trainTurningTopSpeed.key(),value.trainTurningTopSpeed()),java.util.Map.entry(config.trains.trainAcceleration.key(),value.trainAcceleration()),java.util.Map.entry(config.trains.poweredTrainTopSpeed.key(),value.poweredTrainTopSpeed()),java.util.Map.entry(config.trains.poweredTrainTurningTopSpeed.key(),value.poweredTrainTurningTopSpeed()),java.util.Map.entry(config.trains.poweredTrainAcceleration.key(),value.poweredTrainAcceleration())),TrainConfig::installServer);}
    public static void installServer(Values value){serverValues=java.util.Objects.requireNonNull(value);}
    private TrainConfig(){}
}

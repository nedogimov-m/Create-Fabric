package com.simibubi.create.content.equipment;

import com.google.gson.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

/** Native values for the four original air/tool settings used by these actual equipment APIs. */
public final class EquipmentConfig {
    public record Values(int airInBacktank,int enchantedBacktankCapacity,int maxExtendoGripActions,int maxPotatoCannonShots){
        public static final Codec<Values> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("airInBacktank",900).forGetter(Values::airInBacktank),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("enchantedBacktankCapacity",300).forGetter(Values::enchantedBacktankCapacity),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("maxExtendoGripActions",1000).forGetter(Values::maxExtendoGripActions),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("maxPotatoCannonShots",200).forGetter(Values::maxPotatoCannonShots)
        ).apply(i,Values::new));
        public static final StreamCodec<RegistryFriendlyByteBuf,Values> STREAM_CODEC=StreamCodec.of((buffer,value)->{buffer.writeVarInt(value.airInBacktank);buffer.writeVarInt(value.enchantedBacktankCapacity);buffer.writeVarInt(value.maxExtendoGripActions);buffer.writeVarInt(value.maxPotatoCannonShots);},buffer->new Values(buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt()));
        public Values{if(airInBacktank<1||enchantedBacktankCapacity<1||maxExtendoGripActions<0||maxPotatoCannonShots<0)throw new IllegalArgumentException("Equipment configuration outside source ranges");}
    }
    public static final Values DEFAULTS=new Values(900,300,1000,200);
    private static volatile Values server=DEFAULTS,client=DEFAULTS;
    public static Values get(Level level){return level.isClientSide()?client:server;}
    public static java.util.function.Supplier<Values> CURRENT_VALUES=()->server;
    public static Values current(){return CURRENT_VALUES.get();}
    public static Values server(){return server;}
    public static Values client(){return client;}
    public static void installClient(Values value){client=java.util.Objects.requireNonNull(value);var config=com.simibubi.create.infrastructure.config.AllConfigs.server();com.simibubi.create.infrastructure.config.AllConfigs.installRemoteValues(java.util.Map.ofEntries(java.util.Map.entry(config.equipment.airInBacktank.key(),value.airInBacktank()),java.util.Map.entry(config.equipment.enchantedBacktankCapacity.key(),value.enchantedBacktankCapacity()),java.util.Map.entry(config.equipment.maxExtendoGripActions.key(),value.maxExtendoGripActions()),java.util.Map.entry(config.equipment.maxPotatoCannonShots.key(),value.maxPotatoCannonShots())));}
    public static void clearClient(){client=DEFAULTS;}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-equipment.json"));}
    public static void reload(Path file){var config=com.simibubi.create.infrastructure.config.AllConfigs.server();com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(file,Values.CODEC,DEFAULTS,value->java.util.Map.ofEntries(java.util.Map.entry(config.equipment.airInBacktank.key(),value.airInBacktank()),java.util.Map.entry(config.equipment.enchantedBacktankCapacity.key(),value.enchantedBacktankCapacity()),java.util.Map.entry(config.equipment.maxExtendoGripActions.key(),value.maxExtendoGripActions()),java.util.Map.entry(config.equipment.maxPotatoCannonShots.key(),value.maxPotatoCannonShots())),EquipmentConfig::installServer);}
    public static void installServer(Values value){server=java.util.Objects.requireNonNull(value);}
    private EquipmentConfig(){}
}

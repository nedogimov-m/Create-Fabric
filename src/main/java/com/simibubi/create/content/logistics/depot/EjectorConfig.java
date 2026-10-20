package com.simibubi.create.content.logistics.depot;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;

/** Original kinetics maxEjectorDistance32/min0 and ejectorScanInterval120/min10. */
public final class EjectorConfig {
    public record Values(int maxDistance,int scanInterval) {
        public static final Codec<Values> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("maxEjectorDistance",32).forGetter(Values::maxDistance),
                Codec.intRange(10,Integer.MAX_VALUE).optionalFieldOf("ejectorScanInterval",120).forGetter(Values::scanInterval)
        ).apply(i,Values::new));
        public Values{if(maxDistance<0||scanInterval<10)throw new IllegalArgumentException("Invalid source ejector limits");}
    }
    private static volatile Values server=new Values(32,120),client=new Values(32,120);
    public static Values server(){return server;}
    public static Values get(Level level){return level.isClientSide()?client:server;}
    public static void installClient(Values values){client=java.util.Objects.requireNonNull(values);var config=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics;com.simibubi.create.infrastructure.config.AllConfigs.installRemoteValues(java.util.Map.of(config.maxEjectorDistance.key(),values.maxDistance(),config.ejectorScanInterval.key(),values.scanInterval()));}
    public static void clearClient(){client=new Values(32,120);}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-weighted-ejector.json"));}
    public static void reload(Path file){var config=com.simibubi.create.infrastructure.config.AllConfigs.server();com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(file,Values.CODEC,new Values(32,120),value->java.util.Map.of(config.kinetics.maxEjectorDistance.key(),value.maxDistance(),config.kinetics.ejectorScanInterval.key(),value.scanInterval()),EjectorConfig::installServer);}
    public static void installServer(Values value){server=java.util.Objects.requireNonNull(value);}
    private EjectorConfig(){}
}

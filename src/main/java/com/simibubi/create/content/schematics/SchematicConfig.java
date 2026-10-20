package com.simibubi.create.content.schematics;

import com.google.gson.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

/** Actual file-backed source CSchematics values; native loader/cannon consume this snapshot. */
public final class SchematicConfig {
    public record Values(boolean creativePrintIncludesAir,int maxSchematics,int maxTotalSchematicSize,int maxSchematicPacketSize,int schematicIdleTimeout,int schematicannonDelay,int schematicannonShotsPerGunpowder){
        public Values{if(maxSchematics<1||maxTotalSchematicSize<16||maxSchematicPacketSize<256||maxSchematicPacketSize>32767||schematicIdleTimeout<100||schematicannonDelay<1||schematicannonShotsPerGunpowder<1)throw new IllegalArgumentException("Schematic configuration outside source ranges");}
        public static final Codec<Values> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.BOOL.optionalFieldOf("creativePrintIncludesAir",false).forGetter(Values::creativePrintIncludesAir),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("maxSchematics",10).forGetter(Values::maxSchematics),
            Codec.intRange(16,Integer.MAX_VALUE).optionalFieldOf("maxTotalSchematicSize",256).forGetter(Values::maxTotalSchematicSize),
            Codec.intRange(256,32767).optionalFieldOf("maxSchematicPacketSize",1024).forGetter(Values::maxSchematicPacketSize),
            Codec.intRange(100,Integer.MAX_VALUE).optionalFieldOf("schematicIdleTimeout",600).forGetter(Values::schematicIdleTimeout),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("schematicannonDelay",10).forGetter(Values::schematicannonDelay),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("schematicannonShotsPerGunpowder",400).forGetter(Values::schematicannonShotsPerGunpowder)
        ).apply(i,Values::new));
    }
    public static final Values DEFAULTS=new Values(false,10,256,1024,600,10,400);
    private static volatile Values current=DEFAULTS;
    public static Values get(){return !com.simibubi.create.infrastructure.config.AllConfigs.isServerThread()&&com.simibubi.create.infrastructure.config.AllConfigs.store(com.simibubi.create.infrastructure.config.ConfigScope.SERVER).hasRemote()?clientValues:current;}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-schematics.json"));}
    public static void reload(Path path){var config=com.simibubi.create.infrastructure.config.AllConfigs.server();com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(path,Values.CODEC,DEFAULTS,value->java.util.Map.ofEntries(java.util.Map.entry(config.schematics.creativePrintIncludesAir.key(),value.creativePrintIncludesAir()),java.util.Map.entry(config.schematics.maxSchematics.key(),value.maxSchematics()),java.util.Map.entry(config.schematics.maxTotalSchematicSize.key(),value.maxTotalSchematicSize()),java.util.Map.entry(config.schematics.maxSchematicPacketSize.key(),value.maxSchematicPacketSize()),java.util.Map.entry(config.schematics.schematicIdleTimeout.key(),value.schematicIdleTimeout()),java.util.Map.entry(config.schematics.schematicannonDelay.key(),value.schematicannonDelay()),java.util.Map.entry(config.schematics.schematicannonShotsPerGunpowder.key(),value.schematicannonShotsPerGunpowder())),SchematicConfig::installServer);}
    public static void installServer(Values value){current=java.util.Objects.requireNonNull(value);}
    private static volatile Values clientValues=DEFAULTS;
    public static void installClient(Values value){clientValues=java.util.Objects.requireNonNull(value);var config=com.simibubi.create.infrastructure.config.AllConfigs.server();com.simibubi.create.infrastructure.config.AllConfigs.installRemoteValues(java.util.Map.ofEntries(java.util.Map.entry(config.schematics.creativePrintIncludesAir.key(),value.creativePrintIncludesAir()),java.util.Map.entry(config.schematics.maxSchematics.key(),value.maxSchematics()),java.util.Map.entry(config.schematics.maxTotalSchematicSize.key(),value.maxTotalSchematicSize()),java.util.Map.entry(config.schematics.maxSchematicPacketSize.key(),value.maxSchematicPacketSize()),java.util.Map.entry(config.schematics.schematicIdleTimeout.key(),value.schematicIdleTimeout()),java.util.Map.entry(config.schematics.schematicannonDelay.key(),value.schematicannonDelay()),java.util.Map.entry(config.schematics.schematicannonShotsPerGunpowder.key(),value.schematicannonShotsPerGunpowder())));}
    public static void clearClient(){clientValues=DEFAULTS;}
    private SchematicConfig(){}
}

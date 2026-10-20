package com.simibubi.create.content.contraptions.minecart;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

/** Exact pinned defaults/minimum; server-owned gameplay does not depend on a client-side mirror. */
public final class MinecartConfig {
    private static int maxCouplingLength=32;
    private static boolean survivalPickup=true,insideContainers=false;
    public static int maxCouplingLength(){return remoteClient()?com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxCartCouplingLength.get():maxCouplingLength;}
    public static boolean survivalPickup(){return remoteClient()?com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.survivalContraptionPickup.get():survivalPickup;}
    public static boolean insideContainers(){return remoteClient()?com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.minecartContraptionInContainers.get():insideContainers;}
    public static void reload(){var file=FabricLoader.getInstance().getConfigDir().resolve("create-minecart.json");var config=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics;com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(file,Values.CODEC,new Values(32,true,false),value->java.util.Map.of(config.maxCartCouplingLength.key(),value.maxCartCouplingLength(),config.survivalContraptionPickup.key(),value.survivalContraptionPickup(),config.minecartContraptionInContainers.key(),value.minecartContraptionInContainers()),value->installServer(value.maxCartCouplingLength(),value.survivalContraptionPickup(),value.minecartContraptionInContainers()));}
    public static void installServer(int coupling,boolean pickup,boolean containers){if(coupling<1)throw new IllegalArgumentException("Source coupling minimum1");maxCouplingLength=coupling;survivalPickup=pickup;insideContainers=containers;}
    public record Values(int maxCartCouplingLength,boolean survivalContraptionPickup,boolean minecartContraptionInContainers){
        public Values{if(maxCartCouplingLength<1)throw new IllegalArgumentException("Source coupling minimum1");}
        public static final com.mojang.serialization.Codec<Values> CODEC=com.mojang.serialization.codecs.RecordCodecBuilder.create(i->i.group(com.mojang.serialization.Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("maxCartCouplingLength",32).forGetter(Values::maxCartCouplingLength),com.mojang.serialization.Codec.BOOL.optionalFieldOf("survivalContraptionPickup",true).forGetter(Values::survivalContraptionPickup),com.mojang.serialization.Codec.BOOL.optionalFieldOf("minecartContraptionInContainers",false).forGetter(Values::minecartContraptionInContainers)).apply(i,Values::new));
    }
    private static boolean remoteClient(){return !com.simibubi.create.infrastructure.config.AllConfigs.isServerThread()&&com.simibubi.create.infrastructure.config.AllConfigs.store(com.simibubi.create.infrastructure.config.ConfigScope.SERVER).hasRemote();}
    private MinecartConfig(){}
}

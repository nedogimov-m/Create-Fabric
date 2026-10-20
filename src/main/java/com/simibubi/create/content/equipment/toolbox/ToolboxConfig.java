package com.simibubi.create.content.equipment.toolbox;

import com.google.gson.*;
import com.mojang.serialization.*;
import java.io.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;

/** Source equipment.toolboxRange (default 10, minimum 1), persisted in native Fabric configuration. */
public final class ToolboxConfig {
    private static final Codec<Integer> RANGE=Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("toolboxRange",10).codec();
    private static volatile int serverRange=10,clientRange=10;
    public static int get(Level level){return level.isClientSide()?clientRange:serverRange;}
    public static int server(){return serverRange;}
    public static void installClient(int value){if(value<1)throw new IllegalArgumentException("Invalid toolbox range");clientRange=value;var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().equipment.toolboxRange;com.simibubi.create.infrastructure.config.AllConfigs.installRemoteValues(java.util.Map.of(actual.key(),value));}
    public static void clearClient(){clientRange=10;}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-toolbox.json"));}
    public static void reload(Path file){var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().equipment.toolboxRange;com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(file,RANGE,10,value->java.util.Map.of(actual.key(),value),ToolboxConfig::installServer);}
    public static void installServer(int value){if(value<1)throw new IllegalArgumentException("Source toolbox minimum1");serverRange=value;}
    private ToolboxConfig(){}
}

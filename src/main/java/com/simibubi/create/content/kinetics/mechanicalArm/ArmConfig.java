package com.simibubi.create.content.kinetics.mechanicalArm;

import com.google.gson.*;
import com.mojang.serialization.*;
import java.io.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;

/** Source logistics.mechanicalArmRange (default 5, minimum 1), persisted in native Fabric configuration. */
public final class ArmConfig {
    private static final Codec<Integer> RANGE=Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("mechanicalArmRange",5).codec();
    private static volatile int serverRange=5,clientRange=5;
    public static int get(Level level){return level.isClientSide()?clientRange:serverRange;}
    public static int server(){return serverRange;}
    public static void installClient(int value){if(value<1)throw new IllegalArgumentException("Invalid mechanical arm range");clientRange=value;var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.mechanicalArmRange;com.simibubi.create.infrastructure.config.AllConfigs.installRemoteValues(java.util.Map.of(actual.key(),value));}
    public static void clearClient(){clientRange=5;}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-mechanical-arm.json"));}
    public static void reload(Path file){var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.mechanicalArmRange;com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(file,RANGE,5,value->java.util.Map.of(actual.key(),value),ArmConfig::installServer);}
    public static void installServer(int value){if(value<1)throw new IllegalArgumentException("Source arm minimum1");serverRange=value;}
    private ArmConfig(){}
}

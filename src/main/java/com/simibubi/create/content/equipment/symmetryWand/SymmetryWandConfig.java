package com.simibubi.create.content.equipment.symmetryWand;

import com.google.gson.*;
import com.mojang.serialization.*;
import java.io.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

/** Original equipment.maxSymmetryWandRange: default 50, minimum 10. */
public final class SymmetryWandConfig {
    private static final Codec<Integer> RANGE=Codec.intRange(10,Integer.MAX_VALUE).optionalFieldOf("maxSymmetryWandRange",50).codec();
    private static volatile int range=50;
    public static int range(){return !com.simibubi.create.infrastructure.config.AllConfigs.isServerThread()&&com.simibubi.create.infrastructure.config.AllConfigs.store(com.simibubi.create.infrastructure.config.ConfigScope.SERVER).hasRemote()?com.simibubi.create.infrastructure.config.AllConfigs.server().equipment.maxSymmetryWandRange.get():range;}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-symmetry-wand.json"));}
    public static void reload(Path file){var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().equipment.maxSymmetryWandRange;com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(file,RANGE,50,value->java.util.Map.of(actual.key(),value),SymmetryWandConfig::installServer);}
    public static void installServer(int value){if(value<10)throw new IllegalArgumentException("Source symmetry minimum10");range=value;}
    private SymmetryWandConfig(){}
}

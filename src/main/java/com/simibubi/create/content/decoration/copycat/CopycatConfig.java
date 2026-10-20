package com.simibubi.create.content.decoration.copycat;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

/** Source equipment placementAssistRange (default12, minimum3); native helper execution is server-owned. */
public final class CopycatConfig {
    private static int placementAssistRange=12;
    public static int placementAssistRange(){return !com.simibubi.create.infrastructure.config.AllConfigs.isServerThread()&&com.simibubi.create.infrastructure.config.AllConfigs.store(com.simibubi.create.infrastructure.config.ConfigScope.SERVER).hasRemote()?com.simibubi.create.infrastructure.config.AllConfigs.server().equipment.placementAssistRange.get():placementAssistRange;}
    public static void reload(){var file=FabricLoader.getInstance().getConfigDir().resolve("create-copycat.json");var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().equipment.placementAssistRange;com.simibubi.create.infrastructure.config.NativeConfigFiles.reloadAdapter(file,com.mojang.serialization.Codec.intRange(3,Integer.MAX_VALUE).optionalFieldOf("placementAssistRange",12).codec(),12,value->java.util.Map.of(actual.key(),value),CopycatConfig::installServer);}
    public static void installServer(int value){if(value<3)throw new IllegalArgumentException("Source placement assist minimum3");placementAssistRange=value;}
    private CopycatConfig(){}
}

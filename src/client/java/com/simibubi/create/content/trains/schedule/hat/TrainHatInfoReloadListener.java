package com.simibubi.create.content.trains.schedule.hat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

public final class TrainHatInfoReloadListener {
    private static final TrainHatInfo DEFAULT=new TrainHatInfo("",0,Vec3.ZERO,1);
    private static Map<Identifier,TrainHatInfo> infos=Map.of();
    public static final String HAT_INFO_DIRECTORY="train_hat_info";
    public static int count(){return infos.size();}
    public static TrainHatInfo getHatInfoFor(LivingEntity entity){
        if(entity instanceof net.minecraft.world.entity.animal.golem.SnowGolem snow&&snow.hasPumpkin())return new TrainHatInfo("",0,new Vec3(0,-3,0),1.18f);
        return infos.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),DEFAULT);
    }
    public static void reload(ResourceManager manager){
        var next=new HashMap<Identifier,TrainHatInfo>();var converter=FileToIdConverter.json(HAT_INFO_DIRECTORY);
        converter.listMatchingResources(manager).forEach((location,resource)->{var name=converter.fileToId(location);if(!BuiltInRegistries.ENTITY_TYPE.containsKey(name))return;
            try(var reader=resource.openAsReader()){next.put(name,TrainHatInfo.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(reader)).getOrThrow());}catch(Exception error){Create.LOGGER.error("Could not load hat configuration {}",location,error);}
        });infos=Map.copyOf(next);Create.LOGGER.info("Loaded {} entity hat configurations",infos.size());
    }
    public static void register(){ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Create.asResource("entity_hat_info"),(ResourceManagerReloadListener)TrainHatInfoReloadListener::reload);}
    private TrainHatInfoReloadListener(){}
}

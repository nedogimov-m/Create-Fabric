package com.simibubi.create.content.trains.schedule.hat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public record TrainHatInfo(String part,int cubeIndex,Vec3 offset,float scale){
    public static final Codec<TrainHatInfo> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.optionalFieldOf("model_part","").forGetter(TrainHatInfo::part),Codec.INT.optionalFieldOf("cube_index",0).forGetter(TrainHatInfo::cubeIndex),Vec3.CODEC.fieldOf("offset").forGetter(TrainHatInfo::offset),Codec.FLOAT.optionalFieldOf("scale",1f).forGetter(TrainHatInfo::scale)).apply(i,TrainHatInfo::new));
}

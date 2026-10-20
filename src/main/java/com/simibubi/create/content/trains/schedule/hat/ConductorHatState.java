package com.simibubi.create.content.trains.schedule.hat;

import com.mojang.serialization.Codec;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;

/** Native persisted owner for source TrainHat state; scene mutations remain on scene entities. */
public final class ConductorHatState {
  private static final AttachmentType<Boolean> TRAIN_HAT=
      AttachmentRegistry.createPersistent(Create.asResource("train_hat"),Codec.BOOL);

  public static AttachmentType<Boolean> register(){return TRAIN_HAT;}

  public static boolean has(Entity entity){
    return ((AttachmentTarget)entity).getAttachedOrElse(TRAIN_HAT,false);
  }

  public static void set(Entity entity,boolean conductor){
    var target=(AttachmentTarget)entity;
    if(conductor)target.setAttached(TRAIN_HAT,true);
    else target.removeAttached(TRAIN_HAT);
  }

  private ConductorHatState(){}
}

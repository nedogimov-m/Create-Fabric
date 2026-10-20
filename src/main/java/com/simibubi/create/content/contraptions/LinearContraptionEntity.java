package com.simibubi.create.content.contraptions;

import com.simibubi.create.content.contraptions.piston.PistonContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A separately registered native owner: persistence must recreate the translating class. */
public class LinearContraptionEntity extends ControlledContraptionEntity {
    private static final EntityDataAccessor<Float> PROGRESS=SynchedEntityData.defineId(LinearContraptionEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> POSE_BIAS_Y=SynchedEntityData.defineId(LinearContraptionEntity.class,EntityDataSerializers.FLOAT);
    private boolean readingAdditionalData;
    public LinearContraptionEntity(EntityType<? extends LinearContraptionEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(PROGRESS,0f);data.define(POSE_BIAS_Y,0f);}
    public float getPoseBiasY(){return entityData.get(POSE_BIAS_Y);}
    public void setPoseBiasY(float bias){if(!Float.isFinite(bias))throw new IllegalArgumentException("Nonfinite native linear pose bias");entityData.set(POSE_BIAS_Y,bias);applyProgress();}
    public float getProgress(){return entityData.get(PROGRESS);}
    public void setProgress(float progress){if(!Float.isFinite(progress))throw new IllegalArgumentException("Nonfinite linear progress");entityData.set(PROGRESS,progress);applyProgress();}
    private void applyProgress(){if(readingAdditionalData)return;if(getContraption() instanceof TranslatingContraption translating)setPos(translating.positionAtProgress(getProgress()).add(0,getPoseBiasY(),0));}
    @Override public void setContraption(Contraption contraption){if(contraption!=null&&!(contraption instanceof TranslatingContraption))throw new IllegalArgumentException("Linear owner requires a translating structure");super.setContraption(contraption);applyProgress();}
    @Override protected void tickContraption(){if(level().isClientSide())applyProgress();super.tickContraption();}
    @Override public Vec3 applyRotation(Vec3 local,float partial){return local;}
    @Override public Vec3 reverseRotation(Vec3 local,float partial){return local;}
    @Override public Quaternionf getOrientationQuaternion(float partial){return new Quaternionf();}
    @Override public ContraptionRotationState getRotationState(){return new ContraptionRotationState();}
    @Override protected StructureTransform disassemblyTransform(){return getContraption() instanceof TranslatingContraption translating?translating.restorationAtProgress(getProgress()):new StructureTransform(BlockPos.containing(position()),0,0,0);}
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putFloat("LinearOffset",getProgress());out.putFloat("LinearPoseBiasY",getPoseBiasY());}
    @Override protected void readAdditionalSaveData(ValueInput in){
        // Native Pos is already loaded. Controlled must decode ControllerRelative against
        // that position before Abstract's virtual setContraption may apply linear progress.
        readingAdditionalData=true;try{super.readAdditionalSaveData(in);}finally{readingAdditionalData=false;}
        float bias=in.getFloatOr("LinearPoseBiasY",0);if(!Float.isFinite(bias))throw new IllegalArgumentException("Nonfinite saved linear pose bias");entityData.set(POSE_BIAS_Y,bias);setProgress(in.getFloatOr("LinearOffset",0));
    }
}

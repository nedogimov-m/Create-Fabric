package com.simibubi.create.content.kinetics.motor;

import java.util.List;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Genuine source scroll owner; native helpers and scene callbacks use this same setting. */
public class CreativeMotorBlockEntity extends GeneratingKineticBlockEntity {
    public static final int DEFAULT_SPEED=16, MAX_SPEED=256;
    public ScrollValueBehaviour generatedSpeed;
    public CreativeMotorBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.MOTOR,pos,state);}
    public CreativeMotorBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){
        super.addBehaviours(behaviours);
        generatedSpeed=new KineticScrollValueBehaviour(CreateLang.translateDirect("kinetics.creative_motor.rotation_speed"),this,new MotorValueBox()){
            @Override public void read(ValueInput input,boolean clientPacket){
                // Original source key wins; GeneratedSpeed is only a legacy native migration alias.
                super.read(input,clientPacket);
                // Loading retains the actual saved setting; new edits use the current bounds.
                value=input.getIntOr("ScrollValue",input.getIntOr("GeneratedSpeed",DEFAULT_SPEED));
            }
        };
        generatedSpeed.between(-KineticNetworks.maxRotationSpeed(),KineticNetworks.maxRotationSpeed())
            .withBounds(()->-KineticNetworks.maxRotationSpeed(),KineticNetworks::maxRotationSpeed);
        generatedSpeed.value=Math.clamp(DEFAULT_SPEED,-KineticNetworks.maxRotationSpeed(),KineticNetworks.maxRotationSpeed());
        generatedSpeed.withCallback(value->updateGeneratedRotation());
        behaviours.add(generatedSpeed);
    }
    @Override public float getGeneratedSpeed(){
        return getBlockState().is(AllBlocks.CREATIVE_MOTOR)
            ?convertToDirection(generatedSpeed.getValue(),getBlockState().getValue(CreativeMotorBlock.FACING)):0;
    }
    @Override public void initialize(){
        super.initialize();
        if(!hasSource()||Math.abs(getGeneratedSpeed())>Math.abs(getTheoreticalSpeed()))updateGeneratedRotation();
    }
    static class MotorValueBox extends ValueBoxTransform.Sided {
        @Override protected Vec3 getSouthLocation(){return new Vec3(8/16d,8/16d,12.5/16d);}
        @Override public Vec3 getLocalOffset(LevelAccessor level,BlockPos pos,BlockState state){
            var facing=state.getValue(CreativeMotorBlock.FACING);
            return super.getLocalOffset(level,pos,state).add(facing.getUnitVec3().scale(-1/16f));
        }
        @Override public void rotate(LevelAccessor level,BlockPos pos,BlockState state,Matrix4f matrix){
            super.rotate(level,pos,state,matrix);
            var facing=state.getValue(CreativeMotorBlock.FACING);
            if(facing.getAxis()!=Direction.Axis.Y&&getSide()==Direction.UP)
                matrix.rotateZ(AngleHelper.rad(-AngleHelper.horizontalAngle(facing)+180));
        }
        @Override protected boolean isSideActive(BlockState state,Direction side){
            var facing=state.getValue(CreativeMotorBlock.FACING);
            return (facing.getAxis()==Direction.Axis.Y||side!=Direction.DOWN)&&side.getAxis()!=facing.getAxis();
        }
    }
}

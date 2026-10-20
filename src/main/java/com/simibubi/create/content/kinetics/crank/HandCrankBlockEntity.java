package com.simibubi.create.content.kinetics.crank;

import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Source crank generator lifecycle, used by native valve handles. */
public class HandCrankBlockEntity extends com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity {
    public int inUse;
    public boolean backwards;
    public float independentAngle;
    public float chasingAngularVelocity;
    public HandCrankBlockEntity(BlockPos pos,BlockState state){this(com.simibubi.create.AllBlockEntityTypes.HAND_CRANK,pos,state);}
    public HandCrankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    protected boolean clockwise() { return backwards; }
    public void turn(boolean back) {
        inUse = 10; backwards = back; setChanged();
        if (level instanceof ServerLevel) updateGeneratedRotation();
    }
    @Override public void turnCrank(boolean backwards) { turn(backwards); }
    @Override public float getGeneratedSpeed() {
        if(!(getBlockState().getBlock() instanceof HandCrankBlock crank))return 0;
        return convertToDirection(inUse==0?0:crank.getRotationSpeed()*(clockwise()?-1:1),getBlockState().getValue(HandCrankBlock.FACING));
    }
    @Override public double getDirectionalGeneratedSpeed() { return getGeneratedSpeed(); }
    @Override public float getIndependentAngle(float partial) { return independentAngle + partial * chasingAngularVelocity; }
    protected void tickCrank() {
        chasingAngularVelocity += (convertToAngular(getSpeed()) - chasingAngularVelocity) / 4;
        independentAngle += chasingAngularVelocity;
        if (inUse > 0 && --inUse == 0 && level instanceof ServerLevel server) {
            restoreSequenceContext(null, null); updateGeneratedRotation();
        }
    }
    @Override public void tick(){if(level==null){super.tick();return;}tick(level,worldPosition,getBlockState(),this);}
    public static void tick(Level level,BlockPos pos,BlockState state,HandCrankBlockEntity crank){
        try(var scope=com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level,pos,state,crank)){
            if(!scope.shouldTick())return;
            KineticBlockEntity.tick(level,pos,state,crank);crank.tickCrank();
            if(level instanceof ServerLevel&&state.is(com.simibubi.create.AllBlocks.HAND_CRANK)&&crank.inUse>0&&level.getGameTime()%10==0){
                level.playSound(null,pos,com.simibubi.create.AllSoundEvents.CRANKING,net.minecraft.sounds.SoundSource.BLOCKS,.3f,.65f);
                level.playSound(null,pos,com.simibubi.create.AllSoundEvents.CRANKING_BUTTON,net.minecraft.sounds.SoundSource.BLOCKS,.1f,.65f);
            }
        }
    }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket); output.putInt("InUse", inUse); output.putBoolean("Backwards", backwards);
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
         inUse = Math.max(0, input.getIntOr("InUse", 0));
        backwards = input.getBooleanOr("Backwards", false);
    
        super.read(input, clientPacket);
    }
    @Override protected net.minecraft.world.level.block.Block getStressConfigKey(){return getBlockState().is(com.simibubi.create.AllBlocks.HAND_CRANK)?com.simibubi.create.AllBlocks.HAND_CRANK:com.simibubi.create.AllBlocks.COPPER_VALVE_HANDLE;}
    @Override public double getAddedStressCapacity(){return com.simibubi.create.api.stress.BlockStressValues.getCapacity(getBlockState().is(com.simibubi.create.AllBlocks.HAND_CRANK)?com.simibubi.create.AllBlocks.HAND_CRANK:com.simibubi.create.AllBlocks.COPPER_VALVE_HANDLE);}
}

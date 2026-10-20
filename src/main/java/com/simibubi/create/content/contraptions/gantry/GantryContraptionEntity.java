package com.simibubi.create.content.contraptions.gantry;

import com.mojang.serialization.Codec;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.kinetics.gantry.*;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.utility.*;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A genuine registered carrier. Its carriage BE travels inside it; no stationary controller is invented. */
public class GantryContraptionEntity extends AbstractContraptionEntity {
    private static final EntityDataAccessor<Direction> MOVEMENT_AXIS=SynchedEntityData.defineId(GantryContraptionEntity.class,EntityDataSerializers.DIRECTION);
    private double axisMotion,clientOffsetDiff;
    private double sequencedOffsetLimit=-1,rememberedSequenceRemaining=-1;
    private SequencePhase rememberedSequencePhase;
    private boolean pendingDisassembly,retryAssemblyAfterRecovery,disassembling;
    private AssemblyException lastException;
    public GantryContraptionEntity(EntityType<? extends GantryContraptionEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(MOVEMENT_AXIS,Direction.EAST);}
    @Override protected InterpolationHandler createInterpolationHandler(){
        // NO_OP returns false: native moveOrInterpolateTo would then snap the entity to
        // every vanilla tracking packet and compete with the source's double predictor.
        return new InterpolationHandler(){
            @Override public PositionAndRotation target(){return PositionAndRotation.of(position(),getYRot(),getXRot());}
            @Override public boolean interpolateTo(PositionPath path,float yaw,float pitch,boolean rotate){return level().isClientSide();}
            @Override public void interpolate(){}
            @Override public void applyPredictedMovement(Vec3 movement){}
            @Override public boolean hasActiveInterpolation(){return false;}
            @Override public void cancel(){}
        };
    }
    public void setMovementAxis(Direction direction){entityData.set(MOVEMENT_AXIS,Objects.requireNonNull(direction));}
    public Direction getMovementAxis(){return entityData.get(MOVEMENT_AXIS);}
    public double getAxisCoord(){return getMovementAxis().getAxis().choose(getX(),getY(),getZ());}
    public double getSequencedOffsetLimit(){return sequencedOffsetLimit;}
    public AssemblyException getLastAssemblyException(){return lastException;}
    @Override public Vec3 applyRotation(Vec3 local,float partial){return local;}
    @Override public Vec3 reverseRotation(Vec3 local,float partial){return local;}
    @Override public Quaternionf getOrientationQuaternion(float partial){return new Quaternionf();}
    @Override public ContraptionRotationState getRotationState(){return new ContraptionRotationState();}
    @Override protected StructureTransform disassemblyTransform(){return new StructureTransform(BlockPos.containing(getAnchorVec().add(.5,.5,.5)),0,0,0);}
    @Override public void setContraption(Contraption structure){if(structure!=null&&!(structure instanceof GantryContraption))throw new IllegalArgumentException("Gantry owner requires an actual gantry structure");super.setContraption(structure);}
    public void initializeDrive(GantryShaftBlockEntity shaft){setMovementAxis(shaft.getBlockState().getValue(GantryShaftBlock.FACING));acceptSequence(shaft);}
    public void beginAssemblyRecovery(){retryAssemblyAfterRecovery=true;pendingDisassembly=true;setDeltaMovement(Vec3.ZERO);}
    private void acceptSequence(GantryShaftBlockEntity shaft){
        var context=shaft.sequenceContext;var phase=shaft.getSequencePhase();
        if(context!=null&&context.instruction()==SequencerInstructions.TURN_DISTANCE){
            if(!Objects.equals(rememberedSequencePhase,phase)||rememberedSequenceRemaining<0)rememberedSequenceRemaining=Math.max(0,context.getEffectiveValue(shaft.getTheoreticalSpeed()));
            rememberedSequencePhase=phase;sequencedOffsetLimit=rememberedSequenceRemaining;
        }else{if(sequencedOffsetLimit>=0)rememberedSequenceRemaining=sequencedOffsetLimit;sequencedOffsetLimit=-1;}
    }
    private Vec3 drive()throws AssemblyException{
        var structure=(GantryContraption)getContraption();var centre=getAnchorVec().add(.5,.5,.5);var shaftPos=BlockPos.containing(centre).relative(structure.getFacing().getOpposite());
        if(!LoadedLevelAccess.isLoaded(level(),shaftPos))throw AssemblyException.entitiesNotReady(shaftPos);LoadedEntityAccess.requireReady(level(),shaftPos);
        var state=LoadedLevelAccess.blockState(level(),shaftPos);
        if(!(state.getBlock() instanceof GantryShaftBlock)||!(LoadedLevelAccess.blockEntity(level(),shaftPos) instanceof GantryShaftBlockEntity shaft)){pendingDisassembly=true;return Vec3.ZERO;}
        var direction=state.getValue(GantryShaftBlock.FACING);
        if(direction!=getMovementAxis()||GantryCarriageBlock.getValidGantryShaftAxis(structure.getBlocks().get(BlockPos.ZERO).state())!=direction.getAxis()){pendingDisassembly=true;return Vec3.ZERO;}
        acceptSequence(shaft);float speed=shaft.getPinionMovementSpeed();
        if(state.getValue(GantryShaftBlock.POWERED)||speed==0){pendingDisassembly=true;return Vec3.ZERO;}
        if(sequencedOffsetLimit>=0)speed=(float)Math.clamp(speed,-sequencedOffsetLimit,sequencedOffsetLimit);
        var movement=direction.getUnitVec3().scale(speed);double current=direction.getAxis().choose(centre.x,centre.y,centre.z),next=direction.getAxis().choose(centre.x+movement.x,centre.y+movement.y,centre.z+movement.z);
        if((Math.floor(current)+.5<next)!=(speed*direction.getAxisDirection().getStep()<0))if(!shaft.canAssembleOn()){pendingDisassembly=true;return Vec3.ZERO;}
        axisMotion=speed;return movement;
    }
    @Override protected void tickContraption(){
        if(!(getContraption() instanceof GantryContraption))return;
        if(level().isClientSide()){
            clientOffsetDiff*=.75;tickActors();if(isStalled()||ContraptionAssemblyGraph.isFrozen(this)){setDeltaMovement(Vec3.ZERO);return;}
            double speed=(axisMotion+clientOffsetDiff*getMovementAxis().getAxisDirection().getStep()/2)*com.simibubi.create.foundation.utility.ServerSpeedProvider.get();
            if(sequencedOffsetLimit>=0)speed=Math.clamp(speed,-sequencedOffsetLimit,sequencedOffsetLimit);var movement=getMovementAxis().getUnitVec3().scale(speed);setDeltaMovement(movement);
            if(tickCount>2){setPos(position().add(movement));if(sequencedOffsetLimit>=0)sequencedOffsetLimit=Math.max(0,sequencedOffsetLimit-movement.length());}return;
        }
        if(pendingDisassembly||getContraption().getStabilizedSubContraptions().isRecovering()){
            setDeltaMovement(Vec3.ZERO);var target=getRestorationTransform().apply(BlockPos.ZERO);boolean retry=retryAssemblyAfterRecovery;
            if(disassemble()&&retry&&LoadedLevelAccess.blockEntity(level(),target) instanceof GantryCarriageBlockEntity carriage)carriage.checkValidGantryShaft();return;
        }
        if(ContraptionAssemblyGraph.isFrozen(this)){setDeltaMovement(Vec3.ZERO);axisMotion=0;tickActors();sendMotion();return;}
        double previous=axisMotion;Vec3 movement;
        try{movement=drive();lastException=null;}catch(AssemblyException unavailable){lastException=unavailable;axisMotion=0;setDeltaMovement(Vec3.ZERO);if(level().getGameTime()%3==0)sendMotion();return;}
        if(pendingDisassembly){setDeltaMovement(Vec3.ZERO);axisMotion=0;disassemble();return;}
        setDeltaMovement(movement);tickActors();
        if(isStalled()||ContraptionAssemblyGraph.isFrozen(this)){setDeltaMovement(Vec3.ZERO);sendMotion();return;}
        try{if(ContraptionTerrainCollision.collideBlocksAt(this,position(),movement)){pendingDisassembly=true;setDeltaMovement(Vec3.ZERO);axisMotion=0;disassemble();return;}}
        catch(AssemblyException unavailable){lastException=unavailable;setDeltaMovement(Vec3.ZERO);axisMotion=0;sendMotion();return;}
        if(tickCount>2){setPos(position().add(movement));if(sequencedOffsetLimit>=0){sequencedOffsetLimit=Math.max(0,sequencedOffsetLimit-movement.length());rememberedSequenceRemaining=sequencedOffsetLimit;}}
        if(previous!=0&&Math.signum(previous)!=Math.signum(axisMotion))getContraption().stop(level());
        if(previous!=axisMotion||tickCount%3==0)sendMotion();
    }
    @Override public boolean disassemble(){
        if(level().isClientSide()||disassembling||getContraption()==null)return false;pendingDisassembly=true;setDeltaMovement(Vec3.ZERO);axisMotion=0;disassembling=true;
        try{sendMotion();if(!super.disassemble())return false;sequencedOffsetLimit=rememberedSequenceRemaining=-1;rememberedSequencePhase=null;pendingDisassembly=false;return true;}finally{disassembling=false;}
    }
    @Override public void remove(RemovalReason reason){if(!level().isClientSide()&&reason.shouldDestroy()&&hasOwnedStructure()&&!disassembling){disassemble();return;}super.remove(reason);}
    @Override protected void onContraptionStalled(){sendMotion();}
    public GantryContraptionUpdatePacket motionPacket(){
        boolean stopped=isStalled()||ContraptionAssemblyGraph.isFrozen(this)||pendingDisassembly||lastException!=null||sequencedOffsetLimit==0;
        return new GantryContraptionUpdatePacket(getId(),getUUID(),getAxisCoord(),axisMotion,sequencedOffsetLimit,stopped?position():null);
    }
    public void sendMotion(){if(!level().isClientSide()&&getContraption()!=null)for(var player:PlayerLookup.tracking(this))ServerPlayNetworking.send(player,motionPacket());}
    public boolean applyMotion(GantryContraptionUpdatePacket packet){
        if(!level().isClientSide()||packet.entityId()!=getId()||!packet.owner().equals(getUUID()))return false;axisMotion=packet.stoppedPosition()==null?packet.motion():0;sequencedOffsetLimit=packet.sequenceLimit();
        if(packet.stoppedPosition()!=null){
            // The pinned source's stall packet sets an exact XYZ pose and clears prediction.
            // Keep native STALLED/graph ownership independent; this payload carries pose only.
            var position=packet.stoppedPosition();setPos(position);xo=position.x;yo=position.y;zo=position.z;clientOffsetDiff=0;setDeltaMovement(Vec3.ZERO);
        }else clientOffsetDiff=packet.coordinate()-getAxisCoord();
        return true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out){
        super.addAdditionalSaveData(out);out.putString("GantryAxis",getMovementAxis().name());out.putDouble("AxisMotion",axisMotion);out.putBoolean("PendingDisassembly",pendingDisassembly);out.putBoolean("RetryAssemblyAfterRecovery",retryAssemblyAfterRecovery);if(sequencedOffsetLimit>=0)out.putDouble("SequencedOffsetLimit",sequencedOffsetLimit);if(rememberedSequencePhase!=null){out.store("SequenceLimitPhase",SequencePhase.CODEC,rememberedSequencePhase);out.putDouble("SequenceLimitRemaining",rememberedSequenceRemaining);}
    }
    @Override protected void readAdditionalSaveData(ValueInput in){
        super.readAdditionalSaveData(in);
        var axis=in.read("GantryAxis",Codec.either(Codec.STRING,Codec.INT))
            .map(encoded->encoded.map(GantryContraptionEntity::axisFromName,Direction::from3DDataValue)).orElse(Direction.EAST);
        setMovementAxis(axis);double motion=in.getDoubleOr("AxisMotion",0);axisMotion=Double.isFinite(motion)?Math.clamp(motion,-.49,.49):0;pendingDisassembly=in.getBooleanOr("PendingDisassembly",false);retryAssemblyAfterRecovery=in.getBooleanOr("RetryAssemblyAfterRecovery",false);double limit=in.getDoubleOr("SequencedOffsetLimit",-1);sequencedOffsetLimit=Double.isFinite(limit)&&limit>=0?limit:-1;rememberedSequencePhase=in.read("SequenceLimitPhase",SequencePhase.CODEC).orElse(null);double remaining=in.getDoubleOr("SequenceLimitRemaining",sequencedOffsetLimit);rememberedSequenceRemaining=Double.isFinite(remaining)&&remaining>=0?remaining:-1;clientOffsetDiff=0;
    }
    private static Direction axisFromName(String name){for(var axis:Direction.values())if(axis.name().equalsIgnoreCase(name))return axis;return Direction.EAST;}

}

package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.world.phys.*;

/** Mechanical controller with source assembly, RPM/angle, stop modes and persistent entity reattachment. */
public class MechanicalBearingBlockEntity extends com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity implements IBearingBlockEntity {
    private ControlledContraptionEntity movedContraption;
    private UUID movedUuid;
    private boolean running,assembleNextTick,graphRecovery,retryAssemblyAfterRecovery,queuedReassembly;
    private int assemblyRetryTicks;
    private float angle;
    private float clientAngleDiff;
    private RotationMode movementMode=RotationMode.ROTATE_PLACE;
    private AssemblyException lastException;
    private CompoundTag pendingAssemblyError;
    private double sequencedAngleLimit=-1,rememberedSequenceRemaining=-1;
    private com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase rememberedSequencePhase;
    public MechanicalBearingBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.MECHANICAL_BEARING,pos,state);}
    public MechanicalBearingBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    protected boolean isWindmill(){return false;}
    protected boolean canAssembleWhenStopped(){return false;}
    protected boolean shouldQueueAssemblyOnSpeedChange(){return true;}
    protected void onAssemblyComplete(){
        var rotor=getMovedContraption();
        if(rotor!=null&&rotor.hasOwnedStructure()&&!rotor.getContraption().getStabilizedSubContraptions().isRecovering()
            &&rotor.getContraption().containsBlockBreakers())
            award(com.simibubi.create.foundation.advancement.AllAdvancements.CONTRAPTION_ACTORS);
        setChanged();
    }
    protected void onDisassemblyComplete(){setChanged();}
    @Override protected boolean syncSequenceContext(){return true;}
    @Override public boolean isWoodenTop(){return false;}
    @Override public void setAngle(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite bearing angle");angle=value%360;if(movedContraption!=null)movedContraption.setAngle(angle);}
    public void requestAssembly(){assembleNextTick=true;assemblyRetryTicks=0;setChanged();}
    public boolean isRunning(){return running;}
    public float getInterpolatedAngle(float partial){var moved=getMovedContraption();return moved==null?angle:moved.getAngle(partial);}
    public float getAngularSpeed(){
        float speed=getSpeed()==0?0:(isWindmill()?getGeneratedSpeed():getSpeed())*.3f;
        if(level!=null&&level.isClientSide())speed=speed*com.simibubi.create.foundation.utility.ServerSpeedProvider.get()+clientAngleDiff/3f;
        return speed;
    }
    /** Only the actual UUID-bound, live native rotor can suppress its metadata fallback. */
    public boolean controlsClientRotation(ControlledContraptionEntity entity){
        return entity!=null&&level!=null&&level.isClientSide()&&running&&!graphRecovery&&entity==getMovedContraption()
            &&!entity.isStalled()&&!ContraptionAssemblyGraph.isFrozen(entity);
    }
    private void tickClientRotation(BlockState state){
        clientAngleDiff/=2;
        if(!running)return;
        var moved=getMovedContraption();
        if(graphRecovery||moved!=null&&(moved.isStalled()||ContraptionAssemblyGraph.isFrozen(moved))){
            if(moved!=null){angle=moved.getSyncedAngle();moved.setAngle(angle);}
            clientAngleDiff=0;
            return;
        }
        float step=getAngularSpeed();
        if(sequencedAngleLimit>=0){
            step=(float)Math.copySign(Math.min(Math.abs(step),sequencedAngleLimit),step);
            sequencedAngleLimit=Math.max(0,sequencedAngleLimit-Math.abs(step));
        }
        angle=(angle+step)%360;
        if(moved!=null){moved.setAngle(angle);moved.setRotationAxis(state.getValue(BearingBlock.FACING).getAxis());}
    }
    public double getSequencedAngleLimit(){return sequencedAngleLimit;}
    @Override protected void onSequenceContextChanged(com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext previous,
            com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase previousPhase){
        if(!isWindmill()&&sequenceContext!=null&&sequenceContext.instruction()==com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions.TURN_ANGLE){
            // setSource copies the phase before assigning its conveyed RPM. A fresh receipt
            // must wait for onSpeedChanged or the actual movement tick, never consume stale RPM.
            sequencedAngleLimit=Objects.equals(rememberedSequencePhase,getSequencePhase())&&rememberedSequenceRemaining>=0
                ?rememberedSequenceRemaining:-1;
        }else{
            // Keep a dormant receipt across transient upstream chunk loss. It does not clamp ordinary
            // rotation, but the SAME persisted phase can rejoin without replaying its consumed travel.
            if(previous!=null&&previousPhase!=null&&sequencedAngleLimit>=0){rememberedSequencePhase=previousPhase;rememberedSequenceRemaining=sequencedAngleLimit;}
            sequencedAngleLimit=-1;
        }
    }
    private void initializeSequenceLimit(){
        if(isWindmill()||sequenceContext==null||sequenceContext.instruction()!=com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions.TURN_ANGLE
                ||getTheoreticalSpeed()==0||Objects.equals(rememberedSequencePhase,getSequencePhase())&&rememberedSequenceRemaining>=0)return;
        sequencedAngleLimit=sequenceContext.getEffectiveValue(getTheoreticalSpeed());
        rememberedSequencePhase=getSequencePhase();rememberedSequenceRemaining=sequencedAngleLimit;
    }
    public RotationMode getMovementMode(){return movementMode;}
    public void setMovementMode(RotationMode mode){movementMode=Objects.requireNonNull(mode);setChanged();sync();}
    public AssemblyException getLastAssemblyException(){return lastException;}
    @Override public void setLevel(Level level){super.setLevel(level);if(pendingAssemblyError!=null){lastException=AssemblyException.read(pendingAssemblyError,level.registryAccess());pendingAssemblyError=null;}}
    public ControlledContraptionEntity getMovedContraption(){
        if(movedContraption!=null&&movedContraption.isRemoved()){if(movedContraption.getContraption()!=null&&movedContraption.getContraption().disassembled){running=false;movedUuid=null;angle=0;setChanged();sync();}movedContraption=null;}
        if(movedContraption==null&&movedUuid!=null&&level instanceof ServerLevel server&&server.getEntity(movedUuid) instanceof ControlledContraptionEntity entity&&matchesOwner(entity))movedContraption=entity;
        if(movedContraption==null&&movedUuid!=null&&level!=null&&level.isClientSide())for(var entity:level.getEntitiesOfClass(ControlledContraptionEntity.class,new AABB(worldPosition).inflate(2)))if(movedUuid.equals(entity.getUUID())&&matchesOwner(entity)){entity.setControllerPos(worldPosition);movedContraption=entity;break;}
        return movedContraption;
    }
    private boolean matchesOwner(ControlledContraptionEntity entity){return entity.getContraption() instanceof BearingContraption structure&&structure.getFacing()==getBlockState().getValue(BearingBlock.FACING)&&(level!=null&&level.isClientSide()||worldPosition.equals(entity.getControllerPos()));}
    protected ControlledContraptionEntity createReservation(ServerLevel level,BearingContraption structure){return new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,level);}
    public void assemble(){
        if(!(level instanceof ServerLevel server)||running||movedUuid!=null)return;
        assemblyRetryTicks=0;var facing=getBlockState().getValue(BearingBlock.FACING);var structure=new BearingContraption(isWindmill(),facing);ControlledContraptionEntity entity=null;
        try{
            if(!structure.assemble(level,worldPosition))return;
            entity=createReservation(server,structure);var anchor=worldPosition.relative(facing);entity.setControllerPos(worldPosition);entity.setPos(Vec3.atLowerCornerOf(anchor));entity.setRotationAxis(facing.getAxis());
            if(!server.addFreshEntity(entity)||entity.isRemoved()||server.getEntity(entity.getUUID())!=entity)throw AssemblyException.structureChanged(worldPosition);
            var accepted=entity;
            var result=ContraptionAssemblyGraph.assemble(server,List.of(new ContraptionAssemblyGraph.Root(accepted,structure)),complete->{
                movedContraption=accepted;movedUuid=accepted.getUUID();running=true;angle=0;graphRecovery=!complete;retryAssemblyAfterRecovery=false;
                if(complete)lastException=null;setChanged();sync();
            });
            if(!result.complete()){if(graphRecovery)retryAssemblyAfterRecovery=result.failure()!=null&&result.failure().canRetry();throw result.failure()==null?AssemblyException.entitiesNotReady(worldPosition):result.failure();}
            onAssemblyComplete();
            level.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_ASSEMBLE,SoundSource.BLOCKS,1,1);setChanged();sync();
        }catch(AssemblyException exception){lastException=exception;if(exception.canRetry())assemblyRetryTicks=5;setChanged();sync();}
        finally{if(entity!=null&&entity.getContraption()==null)entity.discard();}
    }
    public boolean disassemble(){
        if(!(level instanceof ServerLevel))return false;
        retryAssemblyAfterRecovery=false;
        var entity=getMovedContraption();if(isWindmill()&&entity!=null)setAngle(0);
        if(entity!=null&&!entity.disassemble()){graphRecovery=true;entity.getContraption().getStabilizedSubContraptions().setRecovering(true);setChanged();sync();return false;}
        if((running||movedUuid!=null)&&entity==null)return false;
        running=graphRecovery=false;movedContraption=null;movedUuid=null;angle=0;assembleNextTick=false;assemblyRetryTicks=0;queuedReassembly=false;
        sequencedAngleLimit=rememberedSequenceRemaining=-1;rememberedSequencePhase=null;onDisassemblyComplete();
        level.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_DISASSEMBLE,SoundSource.BLOCKS,1,1);setChanged();sync();return true;
    }
    /** Movement never drops a live rotor UUID: first return the exact real owner to the world. */
    public boolean disassembleForMovement(){if(!running&&movedUuid==null)return true;if(!disassemble())return false;queuedReassembly=true;setChanged();return true;}
    public boolean isNearInitialAngle(){return Math.abs(angle)<22.5||Math.abs(angle)>360-22.5;}
    @Override public void tick(){if(level==null){super.tick();return;}tick(level,worldPosition,getBlockState(),this);}
    public static void tick(Level level,BlockPos pos,BlockState state,MechanicalBearingBlockEntity bearing){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, bearing)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,bearing);bearing.initializeSequenceLimit();if(level.isClientSide()){bearing.tickClientRotation(state);return;}
        boolean relocated=bearing.queuedReassembly;bearing.queuedReassembly=false;
        try{
            var moved=bearing.getMovedContraption();
            if(bearing.graphRecovery||moved!=null&&moved.getContraption().getStabilizedSubContraptions().isRecovering()){
                boolean restart=bearing.retryAssemblyAfterRecovery;if(bearing.disassemble()){if(restart)bearing.requestAssembly();}else bearing.retryAssemblyAfterRecovery=restart;return;
            }
            if(bearing.assemblyRetryTicks>0){if(bearing.running||bearing.getSpeed()==0&&!bearing.canAssembleWhenStopped())bearing.assemblyRetryTicks=0;else if(--bearing.assemblyRetryTicks==0)bearing.assembleNextTick=true;}
            if(bearing.assembleNextTick){bearing.assembleNextTick=false;
                if(bearing.running){boolean canPlace=bearing.movementMode==RotationMode.ROTATE_PLACE||bearing.movementMode==RotationMode.ROTATE_PLACE_RETURNED&&bearing.isNearInitialAngle();if(bearing.getSpeed()==0&&canPlace){bearing.disassemble();return;}}
                else if(bearing.getSpeed()!=0||bearing.canAssembleWhenStopped())bearing.assemble();
            }
            moved=bearing.getMovedContraption();if(!bearing.running||moved==null)return;
            if(!moved.isStalled()&&!ContraptionAssemblyGraph.isFrozen(moved)){
                float step=bearing.getAngularSpeed();if(bearing.sequencedAngleLimit>=0){step=(float)Math.copySign(Math.min(Math.abs(step),bearing.sequencedAngleLimit),step);bearing.sequencedAngleLimit=Math.max(0,bearing.sequencedAngleLimit-Math.abs(step));bearing.rememberedSequenceRemaining=bearing.sequencedAngleLimit;}bearing.angle=(bearing.angle+step)%360;
            }
            moved.setAngle(bearing.angle);moved.setRotationAxis(state.getValue(BearingBlock.FACING).getAxis());bearing.setChanged();if(level.getGameTime()%3==0)bearing.sync();
        }finally{if(relocated&&!bearing.running)bearing.requestAssembly();}
    
        }
    }
    @Override public void onSpeedChanged(float previous){
        super.onSpeedChanged(previous);
        initializeSequenceLimit();
        if(previous!=getSpeed()){if(shouldQueueAssemblyOnSpeedChange())assembleNextTick=true;if(previous!=0&&Math.signum(previous)!=Math.signum(getSpeed())&&movedContraption!=null&&!movedContraption.isStalled())angle=Math.round(angle);}
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide())disassemble();super.preRemoveSideEffects(pos,state);}
    @Override public boolean isAttachedTo(AbstractContraptionEntity entity){return getMovedContraption()==entity;}
    @Override public void attach(ControlledContraptionEntity entity){
        if(entity.getContraption() instanceof BearingContraption structure&&structure.getFacing()==getBlockState().getValue(BearingBlock.FACING)){
            if(movedUuid!=null&&!movedUuid.equals(entity.getUUID())||entity.getControllerPos()!=null&&!worldPosition.equals(entity.getControllerPos()))return;
            entity.setControllerPos(worldPosition);
            movedContraption=entity;movedUuid=entity.getUUID();running=true;var anchor=worldPosition.relative(getBlockState().getValue(BearingBlock.FACING));entity.setPos(anchor.getX(),anchor.getY(),anchor.getZ());angle=entity.getAngle(1);setChanged();
        }
    }
    @Override public void onStall(){sync();}
    @Override public boolean isValid(){return !isRemoved();}
    @Override public BlockPos getBlockPosition(){return worldPosition;}
    @Override protected void write(ValueOutput output, boolean clientPacket){super.write(output, clientPacket);output.putBoolean("Running",running);output.putBoolean("GraphRecovery",graphRecovery);output.putBoolean("RetryAssemblyAfterRecovery",retryAssemblyAfterRecovery);output.putBoolean("QueuedMechanicalReassembly",queuedReassembly);output.putFloat("Angle",angle);if(sequencedAngleLimit>=0)output.putDouble("SequencedAngleLimit",sequencedAngleLimit);if(rememberedSequencePhase!=null){output.store("SequenceLimitPhase",com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase.CODEC,rememberedSequencePhase);output.putDouble("SequenceLimitRemaining",rememberedSequenceRemaining);}output.putInt("MovementMode",movementMode.ordinal());output.putInt("AssemblyRetryTicks",assemblyRetryTicks);if(movedUuid!=null)output.store("MovedContraption",net.minecraft.core.UUIDUtil.CODEC,movedUuid);var tag=pendingAssemblyError==null?new CompoundTag():pendingAssemblyError.copy();if(level!=null)AssemblyException.write(tag,level.registryAccess(),lastException);output.store("AssemblyError",CompoundTag.CODEC,tag);}
    @Override protected void read(ValueInput input, boolean clientPacket){
        float angleBefore=angle;boolean runningBefore=running;UUID ownerBefore=movedUuid;
        running=input.getBooleanOr("Running",false);graphRecovery=input.getBooleanOr("GraphRecovery",false);retryAssemblyAfterRecovery=input.getBooleanOr("RetryAssemblyAfterRecovery",false);queuedReassembly=input.getBooleanOr("QueuedMechanicalReassembly",false);sequencedAngleLimit=input.getDoubleOr("SequencedAngleLimit",-1);if(!Double.isFinite(sequencedAngleLimit)||sequencedAngleLimit<0)sequencedAngleLimit=-1;angle=input.getFloatOr("Angle",0);if(!Float.isFinite(angle))angle=0;super.read(input,clientPacket);rememberedSequencePhase=input.read("SequenceLimitPhase",com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase.CODEC).orElse(getSequencePhase());rememberedSequenceRemaining=input.getDoubleOr("SequenceLimitRemaining",sequencedAngleLimit);if(!Double.isFinite(rememberedSequenceRemaining)||rememberedSequenceRemaining<0)rememberedSequenceRemaining=-1;movementMode=RotationMode.values()[Math.clamp(input.getIntOr("MovementMode",0),0,2)];assemblyRetryTicks=Math.clamp(input.getIntOr("AssemblyRetryTicks",0),0,5);var nextUuid=input.read("MovedContraption",net.minecraft.core.UUIDUtil.CODEC).orElse(null);if(!Objects.equals(movedUuid,nextUuid))movedContraption=null;movedUuid=nextUuid;pendingAssemblyError=input.read("AssemblyError",CompoundTag.CODEC).orElse(new CompoundTag()).copy();if(level!=null){lastException=AssemblyException.read(pendingAssemblyError,level.registryAccess());pendingAssemblyError=null;}
        if(clientPacket){
            if(running&&runningBefore&&!graphRecovery&&Objects.equals(ownerBefore,movedUuid)
                    &&(movedContraption==null||!movedContraption.isStalled()&&!ContraptionAssemblyGraph.isFrozen(movedContraption))){
                clientAngleDiff=net.minecraft.util.Mth.wrapDegrees(angle%360-angleBefore%360);
                angle=angleBefore;
            }else clientAngleDiff=0;
            if(!running)movedContraption=null;
        }
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("contraption_actors");
    }
}

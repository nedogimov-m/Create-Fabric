package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Common source linear lifecycle, retaining saved identity while native entity data is absent. */
public abstract class LinearActuatorBlockEntity extends KineticBlockEntity implements IControlContraption {
    public float offset;
    public boolean running,assembleNextTick,waitingForSpeedChange;
    protected LinearContraptionEntity movedContraption;
    protected UUID movedUuid;
    protected AssemblyException lastException;
    protected int retryTicks;
    protected boolean recoveringAssembly,transferring;
    protected boolean pendingDisassembly;
    protected double sequencedOffsetLimit=-1;
    private double rememberedSequenceRemaining=-1;
    private SequencePhase rememberedSequencePhase;
    private CompoundTag pendingError;
    private boolean clientForceMovementPending;
    protected MovementMode movementMode=MovementMode.MOVE_PLACE;
    protected LinearActuatorBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    protected abstract int getExtensionRange();
    protected abstract int getInitialOffset();
    protected abstract Vec3 toMotionVector(float amount);
    protected abstract Vec3 toPosition(float amount);
    protected abstract boolean matches(LinearContraptionEntity owner);
    public abstract boolean assemble()throws AssemblyException;
    public abstract boolean disassemble();
    public abstract boolean canDisassembleNow();
    public boolean isRunning(){return running;}
    public MovementMode getMovementMode(){return movementMode;}
    public void setMovementMode(MovementMode mode){movementMode=Objects.requireNonNull(mode);waitingForSpeedChange=false;setChanged();sync();}
    public AssemblyException getLastAssemblyException(){return lastException;}
    public void requestAssembly(){assembleNextTick=true;retryTicks=0;waitingForSpeedChange=false;setChanged();}
    public LinearContraptionEntity getMovedContraption(){
        if(movedContraption!=null&&movedContraption.isRemoved()){
            if(movedContraption.getContraption()!=null&&movedContraption.getContraption().disassembled){running=false;movedUuid=null;setChanged();}
            movedContraption=null;
        }
        if(movedContraption!=null&&(!matches(movedContraption)||movedContraption.level()!=level))movedContraption=null;
        if(movedContraption==null&&movedUuid!=null&&level instanceof ServerLevel server&&server.getEntity(movedUuid) instanceof LinearContraptionEntity owner&&matches(owner))movedContraption=owner;
        if(movedContraption==null&&movedUuid!=null&&level!=null&&level.isClientSide())for(var owner:level.getEntitiesOfClass(LinearContraptionEntity.class,new AABB(worldPosition).inflate(Math.max(17,getExtensionRange()+2))))if(movedUuid.equals(owner.getUUID())&&matches(owner)){movedContraption=owner;break;}
        return movedContraption;
    }
    protected boolean canAttachWithoutSavedOwner(LinearContraptionEntity owner){return getBlockState().hasProperty(MechanicalPistonBlock.STATE)&&getBlockState().getValue(MechanicalPistonBlock.STATE)==MechanicalPistonBlock.PistonState.MOVING;}
    protected void tickWithoutContraption(){}
    protected boolean isPassive(){return false;}
    protected boolean shouldStopOnTerrainCollision(){return true;}
    protected void updateContraptionPose(LinearContraptionEntity owner,float next){owner.setProgress(next);}
    private void consumeForcedClientMovement(){
        if(!clientForceMovementPending||level==null||!level.isClientSide())return;
        var actual=getMovedContraption();
        if(actual==null||!matches(actual)||!isAttachedTo(actual))return;
        updateContraptionPose(actual,offset);clientForceMovementPending=false;
    }
    protected final void consumeSequencedTravel(double travel){if(sequencedOffsetLimit>=0){sequencedOffsetLimit=Math.max(0,sequencedOffsetLimit-travel);rememberedSequenceRemaining=sequencedOffsetLimit;}}
    @Override protected boolean syncSequenceContext(){return true;}
    @Override public void attach(ControlledContraptionEntity entity){
        if(!(entity instanceof LinearContraptionEntity owner)||!matches(owner)||movedUuid!=null&&!movedUuid.equals(owner.getUUID()))return;
        if(movedUuid==null&&!canAttachWithoutSavedOwner(owner))return;
        movedContraption=owner;movedUuid=owner.getUUID();running=true;setChanged();sync();
    }
    @Override public boolean isAttachedTo(AbstractContraptionEntity owner){return owner==getMovedContraption();}
    @Override public void onStall(){sync();}
    @Override public boolean isValid(){return !isRemoved();}
    @Override public BlockPos getBlockPosition(){return worldPosition;}
    protected int getGridOffset(float value){return Math.clamp(Math.round(value),0,getExtensionRange());}
    public float getMovementSpeed(){float speed=Math.clamp(getSpeed()/512f,-.49f,.49f);if(level!=null&&level.isClientSide())speed*=com.simibubi.create.foundation.utility.ServerSpeedProvider.get();return sequencedOffsetLimit<0?speed:(float)Math.clamp(speed,-sequencedOffsetLimit,sequencedOffsetLimit);}
    public float getInterpolatedOffset(float partial){return Math.clamp(offset+(partial-.5f)*getMovementSpeed(),0,getExtensionRange());}
    public Vec3 getMotionVector(){return toMotionVector(getMovementSpeed());}
    protected void failure(AssemblyException failure,boolean retryAssembly){lastException=failure;if(retryAssembly&&failure.canRetry())retryTicks=5;setChanged();sync();}
    protected boolean tryDisassemble(){
        if(!isRemoved()&&(movementMode==MovementMode.MOVE_NEVER_PLACE||movementMode==MovementMode.MOVE_PLACE_RETURNED&&getGridOffset(offset)!=getInitialOffset())){
            waitingForSpeedChange=true;if(getMovedContraption()!=null)getMovedContraption().setDeltaMovement(Vec3.ZERO);setChanged();sync();return false;
        }return disassemble();
    }
    protected void collided(float speed){
        offset=getGridOffset(getGridOffset(offset)-speed);var owner=getMovedContraption();if(owner!=null){owner.setProgress(offset);owner.setDeltaMovement(Vec3.ZERO);}
        if(tryDisassemble()&&speed>0)assembleNextTick=true;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,LinearActuatorBlockEntity actuator){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, actuator)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,actuator);if(level.isClientSide()){actuator.consumeForcedClientMovement();return;}
        if(actuator.isPassive()){actuator.tickWithoutContraption();return;}
        var owner=actuator.getMovedContraption();
        if(actuator.recoveringAssembly||owner!=null&&owner.getContraption().getStabilizedSubContraptions().isRecovering()){if(actuator.disassemble()){actuator.recoveringAssembly=false;actuator.requestAssembly();}return;}
        if(actuator.pendingDisassembly){if(owner!=null)owner.setDeltaMovement(Vec3.ZERO);actuator.disassemble();return;}
        if(actuator.retryTicks>0&&--actuator.retryTicks==0)actuator.assembleNextTick=true;
        if(actuator.waitingForSpeedChange){if(owner!=null)owner.setDeltaMovement(Vec3.ZERO);return;}
        if(actuator.assembleNextTick){
            actuator.assembleNextTick=false;
            if(actuator.running){if(actuator.getSpeed()==0)actuator.tryDisassemble();else actuator.sync();}
            else if(actuator.getSpeed()!=0)try{actuator.assemble();}catch(AssemblyException failure){actuator.failure(failure,true);}
            return;
        }
        if(!actuator.running)return;if(owner==null){actuator.tickWithoutContraption();return;}
        if(owner.isStalled()||ContraptionAssemblyGraph.isFrozen(owner)){owner.setDeltaMovement(Vec3.ZERO);return;}
        float speed=actuator.getMovementSpeed(),next=Math.clamp(actuator.offset+speed,0,actuator.getExtensionRange());
        var motion=actuator.toMotionVector(next-actuator.offset);
        try{
            if(ContraptionTerrainCollision.collideBlocks(owner,motion)&&actuator.shouldStopOnTerrainCollision()){actuator.collided(speed);return;}
        }catch(AssemblyException unavailable){owner.setDeltaMovement(Vec3.ZERO);actuator.failure(unavailable,false);return;}
        owner.setDeltaMovement(motion);actuator.offset=next;actuator.updateContraptionPose(owner,next);actuator.lastException=null;actuator.setChanged();
        actuator.consumeSequencedTravel(motion.length());
        if(level.getGameTime()%3==0)actuator.sync();
        if(next<=0||next>=actuator.getExtensionRange())actuator.tryDisassemble();
    
        }
    }
    @Override public void onSpeedChanged(float previous){
        super.onSpeedChanged(previous);assembleNextTick=true;waitingForSpeedChange=false;
        var owner=getMovedContraption();if(owner!=null&&previous!=0&&Math.signum(previous)!=Math.signum(getSpeed())){
            if(!owner.isStalled()&&!ContraptionAssemblyGraph.isFrozen(owner)){offset=Math.round(offset*16)/16f;owner.setProgress(offset);}
            // Preserve controls and actor ownership; the real actor lifecycle stops at reversal.
            owner.getContraption().stop(level);
        }setChanged();
    }
    @Override protected void onSequenceContextChanged(SequenceContext previous,SequencePhase previousPhase){
        super.onSequenceContextChanged(previous,previousPhase);
        if(sequenceContext!=null&&sequenceContext.instruction()==SequencerInstructions.TURN_DISTANCE){
            sequencedOffsetLimit=Objects.equals(rememberedSequencePhase,getSequencePhase())&&rememberedSequenceRemaining>=0
                ?rememberedSequenceRemaining:Math.max(0,sequenceContext.getEffectiveValue(getTheoreticalSpeed()));
            rememberedSequencePhase=getSequencePhase();rememberedSequenceRemaining=sequencedOffsetLimit;
        }else{
            // Upstream native data may disappear before this consumer unloads. Retain the
            // consumed receipt so the same persisted phase cannot replay its distance.
            if(previous!=null&&previousPhase!=null&&sequencedOffsetLimit>=0){rememberedSequencePhase=previousPhase;rememberedSequenceRemaining=sequencedOffsetLimit;}
            sequencedOffsetLimit=-1;
        }
        waitingForSpeedChange=false;if(!running)assembleNextTick=true;setChanged();sync();
    }
    protected void clearSequenceLimit(){sequencedOffsetLimit=rememberedSequenceRemaining=-1;rememberedSequencePhase=null;}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide())disassemble();super.preRemoveSideEffects(pos,state);}
    @Override public void setLevel(Level level){super.setLevel(level);if(pendingError!=null){lastException=AssemblyException.read(pendingError,level.registryAccess());pendingError=null;}}
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);out.putBoolean("Running",running);out.putBoolean("Waiting",waitingForSpeedChange);out.putFloat("Offset",offset);out.putInt("ScrollValue",movementMode.ordinal());out.putBoolean("AssemblyQueued",assembleNextTick);out.putInt("AssemblyRetryTicks",retryTicks);out.putBoolean("RecoveringAssembly",recoveringAssembly);out.putBoolean("PendingDisassembly",pendingDisassembly);
        if(movedUuid!=null)out.store("MovedEntity",UUIDUtil.CODEC,movedUuid);
        if(sequencedOffsetLimit>=0)out.putDouble("SequencedOffsetLimit",sequencedOffsetLimit);
        if(rememberedSequencePhase!=null){out.store("SequenceLimitPhase",SequencePhase.CODEC,rememberedSequencePhase);out.putDouble("SequenceLimitRemaining",rememberedSequenceRemaining);}
        var error=pendingError==null?new CompoundTag():pendingError.copy();if(level!=null)AssemblyException.write(error,level.registryAccess(),lastException);out.store("AssemblyError",CompoundTag.CODEC,error);
        if(clientPacket&&clientForceMovementPending){out.putBoolean("ForceMovement",true);clientForceMovementPending=false;}
    }
    @Override protected void read(ValueInput in, boolean clientPacket){
        movedUuid=in.read("MovedEntity",UUIDUtil.CODEC).orElse(null);movedContraption=null;running=in.getBooleanOr("Running",false)||movedUuid!=null;waitingForSpeedChange=in.getBooleanOr("Waiting",false);float saved=in.getFloatOr("Offset",0);offset=Float.isFinite(saved)?saved:0;
        movementMode=MovementMode.values()[Math.clamp(in.getIntOr("ScrollValue",0),0,MovementMode.values().length-1)];assembleNextTick=in.getBooleanOr("AssemblyQueued",false);retryTicks=Math.clamp(in.getIntOr("AssemblyRetryTicks",0),0,5);recoveringAssembly=in.getBooleanOr("RecoveringAssembly",false);pendingDisassembly=in.getBooleanOr("PendingDisassembly",false);
        double limit=in.getDoubleOr("SequencedOffsetLimit",-1);sequencedOffsetLimit=Double.isFinite(limit)&&limit>=0?limit:-1;
        super.read(in,clientPacket);rememberedSequencePhase=in.read("SequenceLimitPhase",SequencePhase.CODEC).orElse(getSequencePhase());double remembered=in.getDoubleOr("SequenceLimitRemaining",sequencedOffsetLimit);rememberedSequenceRemaining=Double.isFinite(remembered)&&remembered>=0?remembered:-1;
        pendingError=in.read("AssemblyError",CompoundTag.CODEC).orElse(new CompoundTag()).copy();if(level!=null){lastException=AssemblyException.read(pendingError,level.registryAccess());pendingError=null;}
        clientForceMovementPending=clientPacket&&in.getBooleanOr("ForceMovement",false);consumeForcedClientMovement();
    
        
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("contraption_actors");
    }
}

package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Source instruction/timer/latch semantics, driven by the actual input port selected by the solver. */
public final class SequencedGearshiftBlockEntity extends SplitShaftBlockEntity {
    public record SequenceContext(SequencerInstructions instruction,double relativeValue){
        public static final Codec<SequenceContext> CODEC=RecordCodecBuilder.<SequenceContext>create(instance->instance.group(
            SequencerInstructions.CODEC.fieldOf("Mode").forGetter(SequenceContext::instruction),
            Codec.DOUBLE.fieldOf("Value").forGetter(SequenceContext::relativeValue)).apply(instance,SequenceContext::new))
            .validate(value->value.instruction.needsPropagation()&&Double.isFinite(value.relativeValue)?DataResult.success(value):DataResult.error(()->"Invalid sequence context"));
        public static SequenceContext fromGearshift(SequencerInstructions instruction,double outputRpm,int value){return instruction.needsPropagation()?new SequenceContext(instruction,outputRpm==0?0:value/outputRpm):null;}
        public double getEffectiveValue(double targetRpm){return Math.abs(relativeValue*targetRpm);}
    }
    private List<Instruction> instructions=Instruction.createDefault();
    private int currentInstruction=-1,currentInstructionDuration=-1,timer;
    private float currentInstructionProgress;
    private boolean poweredPreviously;
    private long phaseEpoch;
    private UUID sequenceGeneration=UUID.randomUUID();

    public SequencedGearshiftBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SEQUENCED_GEARSHIFT,pos,state);}
    public List<Instruction> getInstructions(){return instructions;}
    public Instruction getInstruction(int index){return index>=0&&index<instructions.size()?instructions.get(index):null;}
    public int getInstructionIndex(){return currentInstruction;}
    public int getInstructionDuration(){return currentInstructionDuration;}
    public float getInstructionProgress(){return currentInstructionProgress;}
    public int getTimer(){return timer;}
    public UUID getSequenceGeneration(){return sequenceGeneration;}
    public boolean wasPowered(){return poweredPreviously;}
    public boolean isIdle(){return currentInstruction==-1;}
    public int getModifier(){var instruction=getInstruction(currentInstruction);return instruction==null?0:instruction.getSpeedModifier();}
    public SequenceContext getOwnSequenceContext(){return sequenceContext;}
    @Override protected void copySequenceContextFrom(KineticBlockEntity upstream){}
    @Override public float getRotationSpeedModifier(Direction face){return !hasSource()||face==getSourceFacing()?1:getModifier();}

    public static void tick(Level level,BlockPos pos,BlockState state,SequencedGearshiftBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,entity);
        if(level.isClientSide()||entity.isConflicted()||entity.isIdle())return;
        // Generator edits detach and reattach the real input synchronously. Abort only
        // a source loss that survives to this native tick, before any unpaid progress.
        if(entity.getSpeed()==0){entity.run(-1);return;}
        if(entity.currentInstructionDuration<0)return;
        if(entity.timer<entity.currentInstructionDuration){entity.timer++;entity.currentInstructionProgress+=entity.getInstruction(entity.currentInstruction).getTickProgress(entity.getSpeed());entity.setChanged();return;}
        entity.run(entity.currentInstruction+1);
    
        }
    }
    @Override public void onSpeedChanged(float previousSpeed){
        super.onSpeedChanged(previousSpeed);
        if(!(level instanceof ServerLevel server)||isConflicted())return;
        if(isIdle()){if(getSpeed()!=0)server.scheduleTick(worldPosition,getBlockState().getBlock(),1);return;}
        if(getSpeed()==0)return;
        if(Math.abs(previousSpeed)==Math.abs(getSpeed()))return;
        var instruction=getInstruction(currentInstruction);if(instruction==null)return;
        currentInstructionDuration=instruction.getDuration(currentInstructionProgress,getTheoreticalSpeed());timer=0;setChanged();sync();
    }
    public void onRedstoneUpdate(boolean powered,boolean runningState){
        if(!(level instanceof ServerLevel))return;
        if(!poweredPreviously&&powered)risingFlank();poweredPreviously=powered;
        if(!isIdle()||powered==runningState)return;
        if(!powered){setState(0);return;}
        if(getSpeed()!=0)run(0);
    }
    public void risingFlank(){
        var instruction=getInstruction(currentInstruction);if(instruction==null||poweredPreviously)return;
        poweredPreviously=true;if(instruction.onRedstonePulse()==OnIsPoweredResult.CONTINUE)run(currentInstruction+1);
    }
    public void run(int index){
        if(!(level instanceof ServerLevel server))return;
        var instruction=getInstruction(index);
        if(instruction==null||instruction.instruction()==SequencerInstructions.END){
            if(getModifier()!=0)detachKinetics();
            currentInstruction=-1;currentInstructionDuration=-1;currentInstructionProgress=0;timer=0;
            applySequenceContext(null,null);
            if(LoadedLevelAccess.getBestNeighborSignal(level,worldPosition)==0)setState(0);
            setChanged();sync();KineticNetworks.markDirty(server);return;
        }
        detachKinetics();
        currentInstruction=index;currentInstructionDuration=instruction.getDuration(0,getTheoreticalSpeed());currentInstructionProgress=0;timer=0;
        if(phaseEpoch==Long.MAX_VALUE){sequenceGeneration=UUID.randomUUID();phaseEpoch=1;}else phaseEpoch++;
        var context=SequenceContext.fromGearshift(instruction.instruction(),getTheoreticalSpeed()*getModifier(),instruction.value());
        applySequenceContext(context,context==null?null:new SequencePhase(worldPosition,phaseEpoch,sequenceGeneration));
        setState(index+1);setChanged();sync();KineticNetworks.markDirty(server);
    }
    public void configure(List<Instruction> updated){
        if(updated.isEmpty()||updated.size()>5)throw new IllegalArgumentException("Sequencer requires one to five instructions");
        run(-1);instructions=List.copyOf(updated);setChanged();sync();
    }
    private void setState(int state){
        var actual=getBlockState();if(actual.getValue(SequencedGearshiftBlock.STATE)==state)return;
        if(LoadedLevelAccess.setBlockQuiet(level,worldPosition,actual.setValue(SequencedGearshiftBlock.STATE,state)))
            for(var direction:Direction.values()){var next=worldPosition.relative(direction);if(LoadedLevelAccess.isLoaded(level,next))level.neighborChanged(LoadedLevelAccess.blockState(level,next),next,actual.getBlock(),null,false);}
    }
    @Override protected void write(ValueOutput output, boolean clientPacket){
        super.write(output, clientPacket);output.putInt("InstructionIndex",currentInstruction);output.putInt("InstructionDuration",currentInstructionDuration);
        output.putFloat("InstructionProgress",currentInstructionProgress);output.putInt("Timer",timer);output.putBoolean("PrevPowered",poweredPreviously);
        output.store("Instructions",Instruction.LIST_CODEC,instructions);output.putLong("SequenceEpoch",phaseEpoch);
        output.store("SequenceGeneration",UUIDUtil.CODEC,sequenceGeneration);
    }
    @Override protected void read(ValueInput input, boolean clientPacket){
        instructions=input.read("Instructions",Instruction.LIST_CODEC).orElse(Instruction.createDefault());
        currentInstruction=input.getIntOr("InstructionIndex",-1);if(currentInstruction< -1||currentInstruction>=instructions.size())currentInstruction=-1;
        currentInstructionDuration=input.getIntOr("InstructionDuration",-1);currentInstructionProgress=input.getFloatOr("InstructionProgress",0);
        if(!Float.isFinite(currentInstructionProgress))currentInstructionProgress=0;
        timer=Math.max(0,input.getIntOr("Timer",0));poweredPreviously=input.getBooleanOr("PrevPowered",false);phaseEpoch=Math.max(0,input.getLongOr("SequenceEpoch",0));
        // Old producer and consumer phase saves must adopt the SAME stable legacy generation.
        // Fresh native block instances keep their constructor UUID; ordinary reads never create one.
        super.read(input,clientPacket);sequenceGeneration=input.read("SequenceGeneration",UUIDUtil.CODEC).orElse(getSequencePhase()==null?SequencePhase.LEGACY_GENERATION:getSequencePhase().generation());
        if(getSequencePhase()!=null)phaseEpoch=Math.max(phaseEpoch,getSequencePhase().epoch());
        if(isIdle()||getInstruction(currentInstruction).instruction()==SequencerInstructions.END){currentInstruction=-1;currentInstructionDuration=-1;currentInstructionProgress=0;timer=0;restoreSequenceContext(null,null);}
        else if(getInstruction(currentInstruction).instruction().needsPropagation()){
            // Upstream source NBT predates explicit epochs. Reconstruct a stable owner phase without world reads/callbacks.
            phaseEpoch=Math.max(1,phaseEpoch);var instruction=getInstruction(currentInstruction);
            var context=sequenceContext==null?SequenceContext.fromGearshift(instruction.instruction(),getTheoreticalSpeed()*getModifier(),instruction.value()):sequenceContext;
            restoreSequenceContext(context,new SequencePhase(worldPosition,phaseEpoch,sequenceGeneration));
        }else restoreSequenceContext(null,null);
    
        
    }
}

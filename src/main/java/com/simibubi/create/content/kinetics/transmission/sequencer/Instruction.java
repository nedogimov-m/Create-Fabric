package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable source instruction; malformed persistent and network values fail before use. */
public record Instruction(SequencerInstructions instruction,InstructionSpeedModifiers speedModifier,int value) {
    private record Raw(SequencerInstructions type,InstructionSpeedModifiers modifier,int value){}
    private static final Codec<Raw> RAW_CODEC=RecordCodecBuilder.create(instance->instance.group(
        SequencerInstructions.CODEC.fieldOf("Type").forGetter(Raw::type),
        InstructionSpeedModifiers.CODEC.fieldOf("Modifier").forGetter(Raw::modifier),
        Codec.INT.fieldOf("Value").forGetter(Raw::value)).apply(instance,Raw::new));
    public static final Codec<Instruction> CODEC=RAW_CODEC.comapFlatMap(raw->{
        try{return DataResult.success(new Instruction(raw.type,raw.modifier,raw.value));}catch(IllegalArgumentException error){return DataResult.error(error::getMessage);}
    },instruction->new Raw(instruction.instruction,instruction.speedModifier,instruction.value));
    public static final Codec<List<Instruction>> LIST_CODEC=CODEC.listOf(1,5);
    public static final StreamCodec<ByteBuf,Instruction> STREAM_CODEC=StreamCodec.of((buffer,instruction)->{
        ByteBufCodecs.VAR_INT.encode(buffer,instruction.instruction.ordinal());ByteBufCodecs.VAR_INT.encode(buffer,instruction.speedModifier.ordinal());ByteBufCodecs.VAR_INT.encode(buffer,instruction.value);
    },buffer->{
        int type=ByteBufCodecs.VAR_INT.decode(buffer),modifier=ByteBufCodecs.VAR_INT.decode(buffer),value=ByteBufCodecs.VAR_INT.decode(buffer);
        if(type<0||type>=SequencerInstructions.values().length||modifier<0||modifier>=InstructionSpeedModifiers.values().length)throw new IllegalArgumentException("Invalid sequencer enum index");
        return new Instruction(SequencerInstructions.values()[type],InstructionSpeedModifiers.values()[modifier],value);
    });
    public static final StreamCodec<ByteBuf,List<Instruction>> LIST_STREAM_CODEC=StreamCodec.of((buffer,instructions)->{
        if(instructions.isEmpty()||instructions.size()>5)throw new IllegalArgumentException("Sequencer requires one to five instructions");
        ByteBufCodecs.VAR_INT.encode(buffer,instructions.size());for(var instruction:instructions)STREAM_CODEC.encode(buffer,instruction);
    },buffer->{
        int count=ByteBufCodecs.VAR_INT.decode(buffer);if(count<1||count>5)throw new IllegalArgumentException("Invalid sequencer instruction count");
        var instructions=new java.util.ArrayList<Instruction>(count);for(int i=0;i<count;i++)instructions.add(STREAM_CODEC.decode(buffer));return List.copyOf(instructions);
    });
    public Instruction{
        Objects.requireNonNull(instruction);Objects.requireNonNull(speedModifier);
        if(instruction.hasValueParameter()&&(value<1||value>instruction.maxValue))throw new IllegalArgumentException("Sequencer value out of range for "+instruction+": "+value);
        if(!instruction.hasValueParameter())value=1;
    }
    public Instruction(SequencerInstructions instruction){this(instruction,InstructionSpeedModifiers.FORWARD,instruction.defaultValue);}
    public Instruction(SequencerInstructions instruction,int value){this(instruction,InstructionSpeedModifiers.FORWARD,value);}
    public int getSpeedModifier(){return instruction.hasSpeedParameter?speedModifier.value:0;}
    public int getDuration(float progress,float inputSpeed){
        if(!Float.isFinite(progress)||!Float.isFinite(inputSpeed))throw new IllegalArgumentException("Nonfinite sequencer duration input");
        double target=value-progress;
        if(instruction==SequencerInstructions.AWAIT)return -1;
        if(instruction==SequencerInstructions.END)return 0;
        if(instruction==SequencerInstructions.DELAY)return (int)Math.clamp(target,Integer.MIN_VALUE+2,Integer.MAX_VALUE-2);
        // Preserve the source float arithmetic before ceil: promoting .3 to double adds a tick
        // at exact native boundaries such as 360 degrees at 3 RPM.
        float effectiveSpeed=Math.abs(inputSpeed*getSpeedModifier());
        double step=instruction==SequencerInstructions.TURN_ANGLE?effectiveSpeed*.3f:effectiveSpeed/512;
        if(step==0)return Integer.MAX_VALUE;
        // Source deliberately overshoots two ticks: the real consumer clamps its movement.
        return (int)Math.clamp(Math.ceil(target/step),Integer.MIN_VALUE+2,Integer.MAX_VALUE-2)+2;
    }
    public float getTickProgress(float inputSpeed){
        if(!Float.isFinite(inputSpeed))throw new IllegalArgumentException("Nonfinite sequencer speed");
        // Source uses signed, unmodified INPUT speed here, unlike getDuration.
        return switch(instruction){case TURN_ANGLE->inputSpeed*.3f;case TURN_DISTANCE->inputSpeed/512;case DELAY->1;default->0;};
    }
    public OnIsPoweredResult onRedstonePulse(){return instruction==SequencerInstructions.AWAIT?OnIsPoweredResult.CONTINUE:OnIsPoweredResult.NOTHING;}
    public static List<Instruction> createDefault(){return List.of(new Instruction(SequencerInstructions.TURN_ANGLE,90),new Instruction(SequencerInstructions.END));}
}

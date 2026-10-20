package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum InstructionSpeedModifiers {
    FORWARD_FAST(2,">>"),FORWARD(1,"→"),BACK(-1,"←"),BACK_FAST(-2,"<<");
    public static final Codec<InstructionSpeedModifiers> CODEC=Codec.STRING.comapFlatMap(value->{
        try{return DataResult.success(valueOf(value));}catch(IllegalArgumentException error){return DataResult.error(()->"Unknown sequencer modifier: "+value);}
    },Enum::name);
    public final int value;
    public final String label;
    InstructionSpeedModifiers(int value,String label){this.value=value;this.label=label;}
    public static InstructionSpeedModifiers getByModifier(int value){for(var candidate:values())if(candidate.value==value)return candidate;throw new IllegalArgumentException("Unsupported sequencer modifier: "+value);}
}

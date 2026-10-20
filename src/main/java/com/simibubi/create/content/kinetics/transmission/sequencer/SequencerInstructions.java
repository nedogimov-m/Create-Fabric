package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/** Source instruction order, units and editor limits. */
public enum SequencerInstructions {
    TURN_ANGLE(360,45,90,true), TURN_DISTANCE(128,5,5,true),
    DELAY(600,20,10,false), AWAIT(0,0,1,false), END(0,0,1,false);

    public static final Codec<SequencerInstructions> CODEC=Codec.STRING.comapFlatMap(value->{
        try{return DataResult.success(valueOf(value));}catch(IllegalArgumentException error){return DataResult.error(()->"Unknown sequencer instruction: "+value);}
    },Enum::name);
    public final int maxValue,shiftStep,defaultValue;
    public final boolean hasSpeedParameter;
    SequencerInstructions(int maxValue,int shiftStep,int defaultValue,boolean speed){this.maxValue=maxValue;this.shiftStep=shiftStep;this.defaultValue=defaultValue;this.hasSpeedParameter=speed;}
    public boolean hasValueParameter(){return maxValue>0;}
    public boolean needsPropagation(){return this==TURN_ANGLE||this==TURN_DISTANCE;}
    public String translationKey(){return "create.gui.sequenced_gearshift.instruction."+name().toLowerCase(Locale.ROOT);}
    public String formatValue(int value){return switch(this){case TURN_ANGLE->value+"°";case TURN_DISTANCE->value+"m";case DELAY->value>=20?value/20+"s":value+"t";default->"";};}
}

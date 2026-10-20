package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** A persisted controller generation plus phase distinguishes replacements and adjacent equal steps. */
public record SequencePhase(BlockPos origin,long epoch,UUID generation) {
    /** Stable migration identity for phase/controller saves written before generation UUIDs existed. */
    public static final UUID LEGACY_GENERATION=new UUID(0,0);
    public static final Codec<SequencePhase> CODEC=RecordCodecBuilder.create(instance->instance.group(
        BlockPos.CODEC.fieldOf("Origin").forGetter(SequencePhase::origin),
        Codec.LONG.validate(value->value>0?com.mojang.serialization.DataResult.success(value):com.mojang.serialization.DataResult.error(()->"Sequence epoch must be positive")).fieldOf("Epoch").forGetter(SequencePhase::epoch),
        UUIDUtil.CODEC.optionalFieldOf("Generation",LEGACY_GENERATION).forGetter(SequencePhase::generation)).apply(instance,SequencePhase::new));
    public SequencePhase(BlockPos origin,long epoch){this(origin,epoch,LEGACY_GENERATION);}
    public SequencePhase{origin=origin.immutable();Objects.requireNonNull(generation);if(epoch<=0)throw new IllegalArgumentException("Sequence epoch must be positive");}
}

package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.graph.RailCodecs;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Original3955 saved relative reference. A world dimension/axis exists only after loaded discovery. */
public record NeoForgeTrackTargetingReference(int sourceVersion, BlockPos offset, boolean front,
        boolean orthogonal, Optional<Vec3> previousAxis, Optional<Vec3> rotatedAxis,
        Optional<BezierTrackPointLocation> curve) {
    public static final Codec<NeoForgeTrackTargetingReference> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.intRange(3955,3955).fieldOf("SourceDataVersion").forGetter(NeoForgeTrackTargetingReference::sourceVersion),
        BlockPos.CODEC.fieldOf("TargetTrack").forGetter(NeoForgeTrackTargetingReference::offset),
        Codec.BOOL.fieldOf("TargetDirection").forGetter(NeoForgeTrackTargetingReference::front),
        Codec.BOOL.fieldOf("Ortho").forGetter(NeoForgeTrackTargetingReference::orthogonal),
        RailCodecs.VECTOR.optionalFieldOf("PrevAxis").forGetter(NeoForgeTrackTargetingReference::previousAxis),
        RailCodecs.VECTOR.optionalFieldOf("RotatedAxis").forGetter(NeoForgeTrackTargetingReference::rotatedAxis),
        BezierTrackPointLocation.CODEC.optionalFieldOf("Bezier").forGetter(NeoForgeTrackTargetingReference::curve)
    ).apply(i,NeoForgeTrackTargetingReference::new));
    public NeoForgeTrackTargetingReference {
        if(sourceVersion!=3955)throw new IllegalArgumentException("Unknown original targeting reference version");
        offset=Objects.requireNonNull(offset).immutable();
        Objects.requireNonNull(previousAxis);Objects.requireNonNull(rotatedAxis);Objects.requireNonNull(curve);
        previousAxis.ifPresent(RailCodecs::nonzero);rotatedAxis.ifPresent(RailCodecs::nonzero);
    }
    public NeoForgeTrackTargetingReference transform(StructureTransform transform){
        return new NeoForgeTrackTargetingReference(sourceVersion,transform.applyWithoutOffset(offset),front,orthogonal,
            previousAxis.map(transform::applyWithoutOffsetUncentered),
            previousAxis.or(()->rotatedAxis).map(transform::applyWithoutOffsetUncentered),
            curve.map(c->new BezierTrackPointLocation(transform.applyWithoutOffset(c.curveTarget()),c.segment())));
    }
}

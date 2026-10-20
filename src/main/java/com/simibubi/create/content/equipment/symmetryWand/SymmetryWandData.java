package com.simibubi.create.content.equipment.symmetryWand;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.equipment.symmetryWand.mirror.*;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** Immutable owned native component: type participates in equality even when source mirrors compare equal. */
public record SymmetryWandData(String type,int orientationIndex,Vec3 position,boolean enable) {
    public static final SymmetryWandData EMPTY=new SymmetryWandData(SymmetryMirror.EMPTY,0,Vec3.ZERO,true);
    public static final Codec<SymmetryWandData> CODEC=SymmetryMirror.CODEC.xmap(SymmetryWandData::fromMirror,SymmetryWandData::mirror);
    public static final StreamCodec<ByteBuf,SymmetryWandData> STREAM_CODEC=SymmetryMirror.STREAM_CODEC.map(SymmetryWandData::fromMirror,SymmetryWandData::mirror);
    public SymmetryWandData {
        Objects.requireNonNull(type);Objects.requireNonNull(position);
        position=new Vec3(position.x,position.y,position.z);
        int count=switch(type){case SymmetryMirror.PLANE,SymmetryMirror.CROSS_PLANE->2;case SymmetryMirror.EMPTY,SymmetryMirror.TRIPLE_PLANE->1;default->throw new IllegalArgumentException("Unknown native symmetry kind");};
        if(orientationIndex<0||orientationIndex>=count)throw new IllegalArgumentException("Invalid native symmetry orientation");
        // Same bounded source54 geometry validation; the stored Vec3 itself is immutable.
        new EmptyMirror(position);
    }
    public static SymmetryWandData fromMirror(SymmetryMirror source){var value=source.copy();return new SymmetryWandData(value.typeName(),value.getOrientationIndex(),value.getPosition(),value.enable);}
    /** Reconstructs a detached mutable source API value, never exposing a stored component owner. */
    public SymmetryMirror mirror(){SymmetryMirror result=switch(type){case SymmetryMirror.PLANE->new PlaneMirror(position);case SymmetryMirror.CROSS_PLANE->new CrossPlaneMirror(position);case SymmetryMirror.TRIPLE_PLANE->new TriplePlaneMirror(position);default->new EmptyMirror(position);};result.setOrientation(orientationIndex);result.enable=enable;return result;}
}

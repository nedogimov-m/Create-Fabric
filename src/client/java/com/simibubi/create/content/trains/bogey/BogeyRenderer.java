package com.simibubi.create.content.trains.bogey;
import net.minecraft.nbt.CompoundTag;

/** Native extraction contract for real world and future carriage bogey rendering. */
@FunctionalInterface
public interface BogeyRenderer {
    void extract(CompoundTag data,float wheelAngle,float partialTick,BogeyRenderState state,boolean inContraption);
}

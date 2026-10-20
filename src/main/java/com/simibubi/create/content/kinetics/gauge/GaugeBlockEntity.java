package com.simibubi.create.content.kinetics.gauge;

import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public abstract class GaugeBlockEntity extends KineticBlockEntity {
    public float dialTarget, dialState, prevDialState;
    public int color;
    protected GaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    protected abstract void updateReading(float speed, float theoreticalSpeed, KineticGraph.Stress stress);
    @Override public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        int previous = getComparatorSignal();
        updateReading(getSpeed(), getTheoreticalSpeed(), new KineticGraph.Stress(capacity, stress, isOverStressed()));
        if (level != null && !level.isClientSide() && previous != getComparatorSignal())
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        setChanged();
    }
    @Override public void setNetworkState(float speed, float theoreticalSpeed, boolean conflicted, KineticGraph.Stress stress, Map<Direction, Float> ports) {
        validateAppliedSpeed(speed,theoreticalSpeed,conflicted,stress.overstressed());
        int previous = getComparatorSignal();
        updateReading(speed, theoreticalSpeed, stress);
        super.setNetworkState(speed, theoreticalSpeed, conflicted, stress, ports);
        if (level != null && !level.isClientSide() && previous != getComparatorSignal()) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }
    @Override public void updateFromNetwork(float capacity,float stress,int size) {
        int previous = getComparatorSignal();
        super.updateFromNetwork(capacity, stress, size);
        updateReading(getSpeed(),getTheoreticalSpeed(),new KineticGraph.Stress(capacity,stress,isOverStressed()));
        if (level != null && !level.isClientSide() && previous != getComparatorSignal())
            level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
        sendData();
        setChanged();
    }
    public int getComparatorSignal() { return Mth.ceil(Math.clamp(dialTarget * 14, 0, 15)); }
    public static void tick(Level level, BlockPos pos, BlockState state, GaugeBlockEntity gauge) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, gauge)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, gauge);
        gauge.prevDialState = gauge.dialState;
        gauge.dialState += (gauge.dialTarget - gauge.dialState) * 0.125f;
        if (gauge.dialState > 1 && level.getRandom().nextFloat() < 0.5f) gauge.dialState -= (gauge.dialState - 1) * level.getRandom().nextFloat();
    
        }
    }
    public float getDial(float partialTick) { return Mth.lerp(partialTick, prevDialState, dialState); }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) { tooltip.add(GoggleTooltip.translate("gui.gauge.info_header")); return true; }
    protected static String bar(int filled) { return " " + "\u2588".repeat(Math.clamp(filled, 0, 3)) + "\u2592".repeat(3 - Math.clamp(filled, 0, 3)) + " "; }
    protected static int mix(int from, int to, float fraction) {
        int result = 0; for (int shift : new int[]{0, 8, 16}) result |= (int)Mth.lerp(fraction, (from >> shift) & 255, (to >> shift) & 255) << shift;
        return result;
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket); out.putFloat("Value", dialTarget); out.putInt("Color", color); }
    @Override protected void read(ValueInput in, boolean clientPacket) {
         float value = in.getFloatOr("Value", 0); dialTarget = Float.isFinite(value) ? value : 0; color = in.getIntOr("Color", 0);
    
        super.read(in, clientPacket);
    }
}

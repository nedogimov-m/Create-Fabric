package com.simibubi.create.content.kinetics.crank;

import java.util.UUID;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.*;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public class ValveHandleBlockEntity extends HandCrankBlockEntity {
    private int angleInput = 45;
    public int cooldown;
    protected int startAngle, targetAngle, totalUseTicks;
    private long phaseEpoch;
    private UUID generation = UUID.randomUUID();
    public ValveHandleBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.VALVE_HANDLE,pos,state);}
    public ValveHandleBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public int getAngleInput() { return angleInput; }
    public void setAngleInput(int angle) {
        angle = Math.clamp(angle, -180, 180);
        if (angle == 0) angle = 1;
        if (angleInput == angle || !showValue()) return;
        angleInput = angle; setChanged(); sync();
    }
    @Override protected boolean clockwise() { return angleInput < 0 ^ backwards; }
    public boolean showValue() { return inUse == 0; }
    public boolean activate(boolean sneak) {
        if (level == null || isRemoved() || com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,worldPosition) != this || getTheoreticalSpeed() != 0 || inUse > 0 || cooldown > 0) return false;
        if (level.isClientSide()) return true;
        int target = Math.abs(angleInput);
        inUse = (int) Math.ceil(target / (double) convertToAngular(32)) + 2;
        startAngle = (int) ((independentAngle % 90 + 360) % 90);
        targetAngle = Math.round((startAngle + (target > 135 ? 180 : 90) * Mth.sign(angleInput)) / 90f) * 90;
        totalUseTicks = inUse; backwards = sneak;
        phaseEpoch = phaseEpoch == Long.MAX_VALUE ? 1 : phaseEpoch + 1;
        applySequenceContext(SequenceContext.fromGearshift(SequencerInstructions.TURN_ANGLE, 32, target),
                new SequencePhase(worldPosition, phaseEpoch, generation));
        cooldown = 4; setChanged(); sync();
        if (level instanceof ServerLevel server) KineticNetworks.markDirty(server);
        return true;
    }
    @Override public void turnCrank(boolean backwards) { activate(backwards); }
    @Override protected void copySequenceContextFrom(KineticBlockEntity upstream) {}
    public SequenceContext getOwnSequenceContext() { return inUse > 0 ? sequenceContext : null; }
    @Override public float getIndependentAngle(float partial) {
        if (inUse == 0 && hasSource() && getSpeed() != 0) {
            var axis = getBlockState().getValue(ValveHandleBlock.FACING).getAxis();
            int parity = (axis == Direction.Axis.X ? 0 : worldPosition.getX())
                    +(axis == Direction.Axis.Y ? 0 : worldPosition.getY())+(axis == Direction.Axis.Z ? 0 : worldPosition.getZ());
            return getAngle(partial)+(parity%2 == 0 ? 22.5f : 0);
        }
        int sign = getBlockState().getValue(ValveHandleBlock.FACING).getAxisDirection().getStep();
        return (inUse > 0 && totalUseTicks > 0
                ? Mth.lerp(Math.min(totalUseTicks, totalUseTicks - inUse + partial) / totalUseTicks, startAngle, targetAngle)
                : targetAngle) * (backwards ? -1 : 1) * sign;
    }
    @Override public void tick(){if(level==null){super.tick();return;}tick(level,worldPosition,getBlockState(),this);}
    public static void tick(Level level, BlockPos pos, BlockState state, ValveHandleBlockEntity entity) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        if (entity.isRemoved() || com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) != entity) return;
        KineticBlockEntity.tick(level, pos, state, entity); entity.tickCrank();
        if (entity.inUse == 0 && entity.cooldown > 0) entity.cooldown--;
        entity.independentAngle = level.isClientSide() ? entity.getIndependentAngle(0) : 0;
    
        }
    }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket); output.putInt("ScrollValue", angleInput);
        output.putInt("TotalUseTicks", totalUseTicks); output.putInt("StartAngle", startAngle); output.putInt("TargetAngle", targetAngle);
        output.putInt("Cooldown", cooldown); output.putLong("SequenceEpoch", phaseEpoch); output.store("SequenceGeneration", UUIDUtil.CODEC, generation);
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
        super.read(input, clientPacket); angleInput = input.getIntOr("ScrollValue", 45);
        totalUseTicks = Math.max(0, input.getIntOr("TotalUseTicks", inUse));
        startAngle = input.getIntOr("StartAngle", 0);
        targetAngle = input.getIntOr("TargetAngle", 0);
        cooldown = Math.clamp(input.getIntOr("Cooldown", 0), 0, 4);
        phaseEpoch = Math.max(1, input.getLongOr("SequenceEpoch", 1));
        generation = input.read("SequenceGeneration", UUIDUtil.CODEC).orElse(getSequencePhase() == null ? SequencePhase.LEGACY_GENERATION : getSequencePhase().generation());
        if (inUse > 0) restoreSequenceContext(SequenceContext.fromGearshift(SequencerInstructions.TURN_ANGLE, 32, Math.abs(angleInput)),
                new SequencePhase(worldPosition, phaseEpoch, generation));
        else restoreSequenceContext(null, null);
    }
}

package com.simibubi.create.content.kinetics.fan;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class EncasedFanBlockEntity extends KineticBlockEntity implements IAirCurrentSource {
    private final AirCurrent airCurrent = new AirCurrent(this);
    private float previousSpeed;
    private Direction previousFacing;
    public EncasedFanBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.ENCASED_FAN, pos, state); }
    @Override public AirCurrent getAirCurrent() { return airCurrent; }
    @Override public Level getAirCurrentWorld() { return level; }
    @Override public BlockPos getAirCurrentPos() { return worldPosition; }
    @Override public Direction getAirflowOriginSide() { return getBlockState().getValue(EncasedFanBlock.FACING); }
    @Override public Direction getAirFlowDirection() { return getSpeed() == 0 ? null : isPushing() ? getAirflowOriginSide() : getAirflowOriginSide().getOpposite(); }
    @Override public boolean isSourceRemoved() { return isRemoved(); }
    public void requestRebuild() { airCurrent.rebuild(); }
    public boolean isPushing() { return getSpeed() * getAirflowOriginSide().getAxisDirection().getStep() > 0; }
    public void updateChute() {
        if (level == null || level.isClientSide()) return;
        var facing = getAirflowOriginSide();
        if (facing.getAxis().isVertical() && LoadedLevelAccess.blockEntity(level, worldPosition.relative(facing))
                instanceof com.simibubi.create.content.logistics.chute.ChuteBlockEntity chute) {
            chute.requestAirflowUpdate(); chute.refreshAirflow();
        }
    }
    @Override public void invalidate() { super.invalidate(); updateChute(); }
    public static float flowLimit(Level level, BlockPos pos, Direction facing, float distance) { return AirCurrent.getFlowLimit(level,pos,distance,facing); }
    public static void tick(Level level, BlockPos pos, BlockState state, EncasedFanBlockEntity fan) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, fan)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,fan);
        if (!level.isClientSide() && (fan.previousSpeed != fan.getSpeed() || fan.previousFacing != fan.getAirflowOriginSide() || level.getGameTime()%com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fanBlockCheckRate.get() == 0)) fan.updateChute();
        fan.previousSpeed = fan.getSpeed(); fan.previousFacing = fan.getAirflowOriginSide();
        fan.airCurrent.tick();
    
        }
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("encased_fan");
        ids.add("fan_processing");
    }
}

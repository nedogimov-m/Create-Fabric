package com.simibubi.create.content.kinetics.crusher;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class CrushingWheelBlockEntity extends KineticBlockEntity {
    private float previousSpeed = Float.NaN;
    public CrushingWheelBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.CRUSHING_WHEEL, pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, CrushingWheelBlockEntity wheel) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, wheel)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, wheel);
        if (!level.isClientSide() && (wheel.previousSpeed != wheel.getSpeed() || level.getGameTime() % 20 == 0)) {
            wheel.previousSpeed = wheel.getSpeed(); CrushingWheelBlock.updateControllers(level, pos, state);
        }
    
        }
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("crushing_wheel");
        ids.add("crusher_maxed_0000");
    }
}

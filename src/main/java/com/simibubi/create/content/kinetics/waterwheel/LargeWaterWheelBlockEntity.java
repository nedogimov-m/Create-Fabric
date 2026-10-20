package com.simibubi.create.content.kinetics.waterwheel;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class LargeWaterWheelBlockEntity extends WaterWheelBlockEntity {
    public LargeWaterWheelBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntityTypes.LARGE_WATER_WHEEL, pos, state);
    }
    public LargeWaterWheelBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override protected int getSize() { return 2; }
}

package com.simibubi.create.content.kinetics.drill;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class DrillBlockEntity extends BlockBreakingKineticBlockEntity {
    public DrillBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.DRILL, pos, state); }
    @Override protected BlockPos getBreakingPos() { return worldPosition.relative(getBlockState().getValue(DrillBlock.FACING)); }
    public static void tick(Level level, BlockPos pos, BlockState state, DrillBlockEntity drill) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, drill)) {
            if (!createTick.shouldTick()) return;
 KineticBlockEntity.tick(level, pos, state, drill); drill.tickBreaking(); 
        }
    }
}

package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.blockscan.BlockScanUtils;
import net.minecraft.world.level.blockscan.BlockStateConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native section scans must see the same detached source states as ordinary tree reads. */
@Mixin(value=BlockScanUtils.class,priority=2000)
abstract class FertilizerBlockScanMixin {
    @Inject(method="findBlocks",at=@At("HEAD"),cancellable=true)
    private static void create$treeBox(LevelReader level,BlockPos from,BlockPos to,Predicate<BlockState> filter,BlockStateConsumer consumer,CallbackInfoReturnable<Boolean> ci){
        var dream=TreeFertilizerSimulation.active(level);
        if(dream!=null)ci.setReturnValue(create$scan(dream,BlockPos.betweenClosed(from,to),filter,consumer));
    }

    @Inject(method="findBlocksWithCache",at=@At("HEAD"),cancellable=true)
    private static void create$treePositions(LevelReader level,Iterable<BlockPos> positions,Predicate<BlockState> filter,BlockStateConsumer consumer,CallbackInfoReturnable<Boolean> ci){
        var dream=TreeFertilizerSimulation.active(level);
        if(dream!=null)ci.setReturnValue(create$scan(dream,positions,filter,consumer));
    }

    private static boolean create$scan(TreeFertilizerSimulation dream,Iterable<BlockPos> positions,Predicate<BlockState> filter,BlockStateConsumer consumer){
        for(var pos:positions){
            var state=dream.blockState(pos);
            if(filter.test(state)&&consumer.apply(pos,state).shouldAbort())return true;
        }
        return false;
    }
}

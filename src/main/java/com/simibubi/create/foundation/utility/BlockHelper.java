package com.simibubi.create.foundation.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.function.Consumer;

public final class BlockHelper {
    public static boolean destroyBlock(ServerLevel level, BlockPos pos, float effectChance, Consumer<ItemStack> drops) {
        return destroyBlockAs(level,pos,null,ItemStack.EMPTY,effectChance,drops);
    }
    public static boolean destroyBlockAs(ServerLevel level,BlockPos pos,net.minecraft.world.entity.player.Player player,ItemStack tool,float effectChance,Consumer<ItemStack> drops){
        if (!level.isLoaded(pos)) return false;
        var state = level.getBlockState(pos); if (state.isAir()) return false;
        var fluid = level.getFluidState(pos);
        if (level.getRandom().nextFloat() < effectChance) level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        if (level.getGameRules().get(GameRules.BLOCK_DROPS)) {
            Block.getDrops(state, level, pos, level.getBlockEntity(pos),player,tool).forEach(drops);
            if (state.getBlock() instanceof IceBlock && !level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, pos)) {
                var below = level.getBlockState(pos.below());
                if (below.isSolid() || below.liquid()) fluid = IceBlock.meltsInto().getFluidState();
            }
            state.spawnAfterBreak(level, pos, ItemStack.EMPTY, false);
        }
        return level.setBlockAndUpdate(pos, fluid.createLegacyBlock());
    }
public static net.minecraft.world.level.block.state.BlockState setZeroAge(net.minecraft.world.level.block.state.BlockState blockState) {
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_1))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_1, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_2))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_2, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_5))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_5, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_15))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_15, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_25))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_25, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HATCH))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HATCH, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.STAGE))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.STAGE, 0);
		if (blockState.is(net.minecraft.tags.BlockTags.CAULDRONS))
			return Blocks.CAULDRON.defaultBlockState();
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_COMPOSTER))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_COMPOSTER, 0);
		if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.EXTENDED))
			return blockState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EXTENDED, false);
		return blockState;
	}
    private BlockHelper() {}
}

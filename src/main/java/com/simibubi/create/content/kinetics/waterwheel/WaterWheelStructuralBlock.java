package com.simibubi.create.content.kinetics.waterwheel;

import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

public final class WaterWheelStructuralBlock extends Block implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    @Override public BlockPos getInformationSource(Level level,BlockPos pos,BlockState state){var root=master(level,pos,state);return root==null?pos:root;}
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public WaterWheelStructuralBlock(Properties properties) {
        super(properties.noLootTable().pushReaction(PushReaction.IMMOVEABLE));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    /** At most two links. Malformed structure states cannot recurse or cross chunk boundaries. */
    public static BlockPos master(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos cursor = pos;
        for (int i = 0; i < 2 && state.is(AllBlocks.WATER_WHEEL_STRUCTURAL); i++) {
            var direction = state.getValue(FACING);
            cursor = cursor.relative(direction);
            if (!level.hasChunkAt(cursor)) return null;
            state = level.getBlockState(cursor);
            if (state.is(AllBlocks.LARGE_WATER_WHEEL) && state.getValue(LargeWaterWheelBlock.AXIS) != direction.getAxis()
                    && LargeWaterWheelBlock.structure(cursor, state.getValue(LargeWaterWheelBlock.AXIS)).containsKey(pos)) return cursor;
        }
        return null;
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction side, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        ticks.scheduleTick(pos, this, 1);
        return state;
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Do not delete cells whose centre may be in an unloaded chunk.
        var target = pos.relative(state.getValue(FACING));
        if (!level.hasChunkAt(target)) return;
        var next = level.getBlockState(target);
        if (next.is(this) && !level.hasChunkAt(target.relative(next.getValue(FACING)))) return;
        if (master(level, pos, state) == null) level.removeBlock(pos, false);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (level.getBlockState(pos).is(this)) return;
        var centre = master(level, pos, state);
        if (centre != null) level.destroyBlock(centre, true);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            var centre = master(level, pos, state);
            if (centre != null) level.destroyBlock(centre, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        var centre = master(level, pos, state);
        return centre == null ? InteractionResult.PASS : AllBlocks.WATER_WHEEL.useMaterial(stack, level, centre, player);
    }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(AllBlocks.LARGE_WATER_WHEEL);
    }
}

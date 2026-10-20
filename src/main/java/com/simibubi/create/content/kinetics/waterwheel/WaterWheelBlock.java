package com.simibubi.create.content.kinetics.waterwheel;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public final class WaterWheelBlock extends KineticBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public WaterWheelBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP)); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof WaterWheelBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getNearestLookingDirection()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face.getAxis() == getRotationAxis(state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WaterWheelBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.WATER_WHEEL, WaterWheelBlockEntity::tick); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            var neighbor = level.getBlockState(pos.relative(face));
            if (neighbor.getBlock() instanceof WaterWheelBlock && (neighbor.getValue(FACING).getAxis() != getRotationAxis(state)
                    || face.getAxis() != getRotationAxis(state))) return false;
        }
        return true;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return useMaterial(stack, level, pos, player);
    }
    public InteractionResult useMaterial(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!player.mayBuild() || !(stack.getItem() instanceof BlockItem item) || !item.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.PLANKS)) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WaterWheelBlockEntity wheel) wheel.setMaterial(item.getBlock().defaultBlockState());
        return InteractionResult.SUCCESS;
    }
    @Override public boolean hideStressImpact() { return true; }

}

package com.simibubi.create.content.kinetics.crafter;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.createmod.catnip.math.Pointing;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.util.Prediction;

public final class MechanicalCrafterBlock extends KineticBlock implements ICogWheel {
    public static final EnumProperty<Direction> HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Pointing> POINTING = EnumProperty.create("pointing", Pointing.class);
    public MechanicalCrafterBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(HORIZONTAL_FACING, Direction.NORTH).setValue(POINTING, Pointing.UP)); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof MechanicalCrafterBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HORIZONTAL_FACING, POINTING); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(HORIZONTAL_FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return false; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MechanicalCrafterBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.MECHANICAL_CRAFTER, MechanicalCrafterBlockEntity::tick); }
    public static Direction getTargetDirection(BlockState state) {
        var facing = state.getValue(HORIZONTAL_FACING);
        return switch (state.getValue(POINTING)) { case UP -> Direction.UP; case DOWN -> Direction.DOWN; case LEFT -> facing.getClockWise(); case RIGHT -> facing.getCounterClockWise(); };
    }
    public static Pointing pointingFromFacing(Direction face, Direction facing) {
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        if (face == Direction.DOWN) return Pointing.UP;
        if (face == Direction.UP) return Pointing.DOWN;
        if (face == Direction.EAST || face == Direction.NORTH) return positive ? Pointing.LEFT : Pointing.RIGHT;
        return positive ? Pointing.RIGHT : Pointing.LEFT;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace(); var below = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
        if (below.is(this) && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())) return defaultBlockState()
                .setValue(HORIZONTAL_FACING, below.getValue(HORIZONTAL_FACING)).setValue(POINTING, pointingFromFacing(face, below.getValue(HORIZONTAL_FACING)));
        var facing = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(POINTING, facing == face ? Pointing.UP : pointingFromFacing(face, facing));
    }
    public InteractionResult onWrenched(UseOnContext context) {
        var level = context.getLevel(); var pos = context.getClickedPos(); var state = level.getBlockState(pos); var face = context.getClickedFace(); var facing = state.getValue(HORIZONTAL_FACING);
        if (face == facing) {
            if (!level.isClientSide()) { var crafter = CrafterHelper.getCrafter(level, pos); if (crafter != null && crafter.phase != MechanicalCrafterBlockEntity.Phase.IDLE) crafter.ejectWholeGrid(); level.setBlockAndUpdate(pos, state.cycle(POINTING)); }
            return InteractionResult.SUCCESS;
        }
        var local = context.getClickLocation().subtract(Vec3.atCenterOf(pos));
        Direction edge = null; double distance = 0.375;
        for (var side : Direction.values()) {
            if (side.getAxis() == facing.getAxis() || side.getAxis() == face.getAxis()) continue;
            double d = local.x * side.getStepX() + local.y * side.getStepY() + local.z * side.getStepZ();
            if (d > distance) { edge = side; distance = d; }
        }
        if (edge != null && ConnectedInputHandler.toggleConnection(level, pos, pos.relative(edge))) return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!player.mayBuild() || hit.getDirection() != state.getValue(HORIZONTAL_FACING) || stack.is(AllItems.WRENCH)) return InteractionResult.PASS;
        var crafter = CrafterHelper.getCrafter(level, pos); if (crafter == null) return InteractionResult.PASS;
        if (crafter.phase != MechanicalCrafterBlockEntity.Phase.IDLE) { if (!level.isClientSide()) crafter.ejectWholeGrid(); return InteractionResult.SUCCESS; }
        if (stack.is(AllItems.CRAFTER_SLOT_COVER)) {
            if (!crafter.inventory.isResourceBlank() || crafter.covered) return InteractionResult.PASS;
            if (!level.isClientSide()) { crafter.covered = true; crafter.contentsChanged(); stack.consume(1, player); } return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!crafter.inventory.isResourceBlank() && ItemStack.isSameItemSameComponents(stack, crafter.inventory.stack())) {
            player.getInventory().placeItemBackInInventory(crafter.inventory.remove(), Prediction.SERVER_ONLY); return InteractionResult.SUCCESS;
        }
        try (Transaction tx = Transaction.openOuter()) {
            long inserted = crafter.storage().insert(ItemVariant.of(stack), stack.getCount(), tx);
            if (inserted == 0) return InteractionResult.PASS; tx.commit(); if (!player.isCreative()) stack.shrink((int) inserted);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || hit.getDirection() != state.getValue(HORIZONTAL_FACING)) return InteractionResult.PASS;
        var crafter = CrafterHelper.getCrafter(level, pos); if (crafter == null) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (crafter.phase != MechanicalCrafterBlockEntity.Phase.IDLE) crafter.ejectWholeGrid();
            else if (crafter.covered) { crafter.covered = false; crafter.contentsChanged(); if (!player.isCreative()) player.getInventory().placeItemBackInInventory(new ItemStack(AllItems.CRAFTER_SLOT_COVER), Prediction.SERVER_ONLY); }
            else player.getInventory().placeItemBackInInventory(crafter.inventory.remove(), Prediction.SERVER_ONLY);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING))); }
}

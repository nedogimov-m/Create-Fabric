package com.simibubi.create.content.processing.basin;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class BasinBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING_HOPPER;
    private static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 2, 16), box(0, 2, 0, 2, 16, 16),
            box(14, 2, 0, 16, 16, 16), box(2, 2, 0, 14, 16, 2), box(2, 2, 14, 14, 16, 16));
    public BasinBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.block(); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var above = level.getBlockState(pos.above()).getBlock();
        return !(above instanceof MechanicalMixerBlock || above instanceof MechanicalPressBlock);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BasinBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AllBlockEntityTypes.BASIN, BasinBlockEntity::tick);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return 0;
        double fullness = basin.inventory.inputs.stream().mapToDouble(slot -> (double) slot.getAmount() / slot.getCapacity()).sum() / 9;
        return fullness == 0 ? 0 : 1 + (int) (fullness * 14);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!player.mayBuild() || !(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return InteractionResult.PASS;
        if (stack.getItem() instanceof com.simibubi.create.content.processing.AssemblyOperatorBlockItem) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (stack.is(net.minecraft.world.item.Items.SPONGE)) {
            try (Transaction transaction = Transaction.openOuter()) {
                for (var tank : basin.fluids.allTanks()) {
                    if (!tank.isResourceBlank()) tank.extract(tank.getResource(), tank.getAmount(), transaction);
                }
                transaction.commit();
            }
            return InteractionResult.SUCCESS;
        }
        if (FluidStorageUtil.interactWithFluidStorage(basin.fluids.automation, player, hand)) return InteractionResult.SUCCESS;
        if (ContainerItemContext.forPlayerInteraction(player, hand).find(FluidStorage.ITEM) != null) return InteractionResult.SUCCESS;
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = basin.inventory.automation.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            transaction.commit();
            stack.consume((int) inserted, player);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BasinBlockEntity basin) {
            for (var slot : basin.allItems()) {
                var stack = slot.stack();
                if (stack.isEmpty()) continue;
                try (Transaction transaction = Transaction.openOuter()) {
                    slot.extract(slot.getResource(), slot.getAmount(), transaction); transaction.commit();
                }
                player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
            }
        }
        return InteractionResult.SUCCESS;
    }
}

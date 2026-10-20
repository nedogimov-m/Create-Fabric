package com.simibubi.create.content.kinetics.millstone;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public final class MillstoneBlock extends KineticBlock implements ICogWheel {
    public MillstoneBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face == Direction.DOWN; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MillstoneBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AllBlockEntityTypes.MILLSTONE, (world, pos, blockState, mill) -> {
            MillstoneBlockEntity.tick(world, pos, blockState, mill);
            if (!world.isClientSide()) {
                for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0, 0.25, 0))) {
                    if (item.isRemoved() || item.getY() < pos.getY() + 0.8) continue;
                    ItemStack stack = item.getItem();
                    try (Transaction transaction = Transaction.openOuter()) {
                        long inserted = mill.inventory.automation.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                        if (inserted == 0) continue;
                        transaction.commit();
                        stack.shrink((int) inserted);
                        if (stack.isEmpty()) item.discard();
                        else item.setItem(stack);
                    }
                }
            }
        });
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MillstoneBlockEntity mill) {
            boolean emptyOutput = mill.inventory.outputs.stream().allMatch(slot -> slot.isResourceBlank());
            var slots = emptyOutput ? mill.inventory.inputs : mill.inventory.outputs;
            for (var slot : slots) {
                ItemStack stack = slot.stack();
                if (stack.isEmpty()) continue;
                try (Transaction transaction = Transaction.openOuter()) {
                    slot.extract(slot.getResource(), slot.getAmount(), transaction);
                    transaction.commit();
                }
                player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        return InteractionResult.SUCCESS;
    }
}

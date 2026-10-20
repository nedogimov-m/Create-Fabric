package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class StockTickerBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 4, 15), box(2, 0, 2, 14, 16, 14));
    public StockTickerBlock(Properties properties) { super(properties.strength(1.5f, 6).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? context.getHorizontalDirection() : context.getHorizontalDirection().getOpposite()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StockTickerBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.STOCK_TICKER, (l,p,s,ticker) -> ticker.tick()); }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof LogisticallyLinkedBlockItem || stack.is(AllItems.WRENCH)) return InteractionResult.PASS;
        return useWithoutItem(state, level, pos, player, hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator() || player instanceof net.fabricmc.fabric.api.entity.FakePlayer || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (!level.isClientSide() && LoadedLevelAccess.blockEntity(level, pos) instanceof StockTickerBlockEntity ticker && ticker.behaviour.mayInteractMessage(player)) {
            if (ticker.hasPayments()) { ticker.retrievePayments(player); level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, .2f, 1); }
            else if (ticker.isKeeperPresent()) StockTickerMenu.open(player, ticker, true);
            else player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.stock_ticker.keeper_missing"));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}

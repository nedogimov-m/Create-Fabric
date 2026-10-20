package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IHaveBigOutline;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class ChainConveyorBlock extends KineticBlock implements IHaveBigOutline {
    private static final VoxelShape SHAPE = Shapes.or(Shapes.block(),box(-10,2,0,26,14,16),box(0,2,-10,16,14,26),box(-5,2,-5,21,14,21));
    public ChainConveyorBlock(Properties properties) { super(properties.noOcclusion()); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face.getAxis() == Direction.Axis.Y; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) if (LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().offset(x,0,z)).getBlock() == this) return null;
        return defaultBlockState();
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ChainConveyorBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.CHAIN_CONVEYOR,ChainConveyorBlockEntity::tick); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && LoadedLevelAccess.blockEntity(level,pos) instanceof ChainConveyorBlockEntity conveyor) conveyor.cancelChainDrops();
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.IRON_CHAIN)) return InteractionResult.PASS;
        if (!player.mayBuild() || player.isSpectator() || !level.mayInteract(player,pos)) return InteractionResult.PASS;
        if (!level.isClientSide()) ChainConveyorConnectionHandler.use(level,pos,player);
        return InteractionResult.SUCCESS;
    }
}

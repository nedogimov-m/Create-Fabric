package com.simibubi.create.content.processing.burner;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllItems;
import net.minecraft.core.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.Locale;

public final class BlazeBurnerBlock extends BaseEntityBlock {
    public enum HeatLevel implements StringRepresentable {
        NONE, SMOULDERING, FADING, KINDLED, SEETHING;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final EnumProperty<HeatLevel> HEAT_LEVEL = EnumProperty.create("blaze", HeatLevel.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public BlazeBurnerBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(HEAT_LEVEL) == HeatLevel.NONE ? 0 : s.getValue(HEAT_LEVEL) == HeatLevel.SMOULDERING ? 8 : 15));
        registerDefaultState(stateDefinition.any().setValue(HEAT_LEVEL, HeatLevel.NONE).setValue(FACING, Direction.SOUTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HEAT_LEVEL, FACING); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(1, 0, 1, 15, 14, 15); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean filled = context.getItemInHand().getItem() instanceof BlazeBurnerBlockItem item && item.hasCapturedBlaze();
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HEAT_LEVEL, filled ? HeatLevel.SMOULDERING : HeatLevel.NONE);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BlazeBurnerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AllBlockEntityTypes.HEATER, BlazeBurnerBlockEntity::tick);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction side) { return Math.max(0, state.getValue(HEAT_LEVEL).ordinal() - 1); }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(state.getValue(HEAT_LEVEL) == HeatLevel.NONE ? AllItems.EMPTY_BLAZE_BURNER : AllItems.BLAZE_BURNER);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild() || !level.mayInteract(player,pos)) return InteractionResult.PASS;
        if(state.getValue(HEAT_LEVEL)==HeatLevel.NONE) {
            if(!stack.is(Items.FLINT_AND_STEEL))return InteractionResult.PASS;
            level.playSound(player,pos,net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE,net.minecraft.sounds.SoundSource.BLOCKS,1,level.getRandom().nextFloat()*0.4f+0.8f);
            if(!level.isClientSide()){stack.hurtAndBreak(1,player,hand);level.setBlockAndUpdate(pos,com.simibubi.create.AllBlocks.LIT_BLAZE_BURNER.defaultBlockState());}
            return InteractionResult.SUCCESS;
        }
        if(!(level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner))return InteractionResult.PASS;
        if(stack.is(AllItems.GOGGLES)){if(burner.goggles)return InteractionResult.PASS;if(!level.isClientSide())burner.setGoggles(true);return InteractionResult.SUCCESS;}
        var ticker=com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler.getBurnerTicker(level,pos);
        if(ticker!=null&&com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler.interactWithLogisticsManagerAt(player,level,ticker))return InteractionResult.SUCCESS;
        if(stack.isEmpty())return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (stack.is(AllItems.CREATIVE_BLAZE_CAKE)) { burner.applyCreativeFuel(); return InteractionResult.SUCCESS; }
        if (!burner.tryFuel(stack, true, false)) return InteractionResult.PASS;
        var remainder = stack.getItem().getCraftingRemainder();
        if (!player.isCreative()) {
            stack.shrink(1);
            if (remainder != null) player.getInventory().placeItemBackInInventory(remainder.create(), net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(state.getValue(HEAT_LEVEL)!=HeatLevel.NONE){var ticker=com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler.getBurnerTicker(level,pos);if(ticker!=null&&com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler.interactWithLogisticsManagerAt(player,level,ticker))return InteractionResult.SUCCESS;}
        if(!player.mayBuild()||state.getValue(HEAT_LEVEL)==HeatLevel.NONE||!(level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner)||!burner.goggles)return InteractionResult.PASS;
        if(!level.isClientSide())burner.setGoggles(false);return InteractionResult.SUCCESS;
    }
}

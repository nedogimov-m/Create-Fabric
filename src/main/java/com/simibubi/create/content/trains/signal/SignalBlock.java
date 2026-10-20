package com.simibubi.create.content.trains.signal;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Locale;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/** Actual source mode/power block and four-tick falling edge on the current native API. */
public final class SignalBlock extends Block implements EntityBlock {
    public enum SignalType implements StringRepresentable {
        ENTRY_SIGNAL, CROSS_SIGNAL;
        public static final Codec<SignalType> CODEC = Codec.STRING.xmap(SignalType::valueOf, SignalType::name);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final EnumProperty<SignalType> TYPE = EnumProperty.create("type", SignalType.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public SignalBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(TYPE, SignalType.ENTRY_SIGNAL).setValue(POWERED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(TYPE, POWERED); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SignalBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == AllBlockEntityTypes.TRACK_SIGNAL ? (world, pos, block, entity) -> SignalBlockEntity.tick(world, pos, block, (SignalBlockEntity) entity) : null;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, new SignalView(context.getLevel()).hasNeighborSignal(context.getClickedPos()));
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        if (level.isClientSide()) return;
        boolean before = state.getValue(POWERED), next = new SignalView(level).hasNeighborSignal(pos);
        if (before == next) return;
        if (before) level.scheduleTick(pos, this, 4);
        else level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED) && !new SignalView(level).hasNeighborSignal(pos))
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
    }
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var player = context.getPlayer(); var level = context.getLevel(); var pos = context.getClickedPos();
        if (player == null || !SignalMenu.canEdit(player, pos)) return InteractionResult.PASS;
        if (!level.isClientSide() && LoadedLevelAccess.blockEntity(level, pos) instanceof SignalBlockEntity signal)
            signal.cycleType(player);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !SignalMenu.canEdit(player, pos)) return InteractionResult.PASS;
        if (!level.isClientSide() && LoadedLevelAccess.blockEntity(level, pos) instanceof SignalBlockEntity signal)
            SignalMenu.open(player, signal);
        return InteractionResult.SUCCESS;
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return LoadedLevelAccess.blockEntity(level, pos) instanceof SignalBlockEntity signal && signal.isPowered() ? 15 : 0;
    }
    private record SignalView(Level level) implements SignalGetter {
        @Override public BlockState getBlockState(BlockPos pos) { return LoadedLevelAccess.blockState(level, pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return LoadedLevelAccess.blockEntity(level, pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }
}

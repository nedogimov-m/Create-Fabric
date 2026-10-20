package com.simibubi.create.content.kinetics.crank;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class ValveHandleBlock extends HandCrankBlock {
    public final DyeColor color;
    public static ValveHandleBlock copper(Properties properties) { return new ValveHandleBlock(properties, null); }
    public static ValveHandleBlock dyed(Properties properties, DyeColor color) { return new ValveHandleBlock(properties, color); }
    private ValveHandleBlock(Properties properties, DyeColor color) { super(properties); this.color = color; }
    @Override protected boolean shouldChangedStateKeepBlockEntity(BlockState previous) {
        return previous.getBlock() instanceof ValveHandleBlock;
    }
    public int getRotationSpeed() { return 32; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ValveHandleBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AllBlockEntityTypes.VALVE_HANDLE, ValveHandleBlockEntity::tick);
    }
    @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType type) { return false; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ValveHandleGeometry.shape(state.getValue(FACING));
    }
    public boolean clicked(Level level, BlockPos pos, BlockState state, Player player, InteractionHand hand) {
        if (!player.mayBuild() || player.isSpectator() || !level.mayInteract(player, pos)) return false;
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.is(AllItems.WRENCH)) return false;
        var dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null && dye != color) {
            if (!level.isClientSide()) {
                // Vanilla keeps an existing BE when the new family block accepts its actual type.
                var changed = AllBlocks.DYED_VALVE_HANDLES.get(dye).defaultBlockState()
                        .setValue(FACING, state.getValue(FACING)).setValue(WATERLOGGED, state.getValue(WATERLOGGED));
                level.setBlockAndUpdate(pos, changed);
            }
            return true;
        }
        if (LoadedLevelAccess.blockEntity(level, pos) instanceof ValveHandleBlockEntity valve) valve.activate(player.isShiftKeyDown());
        return true;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return clicked(level, pos, state, player, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return clicked(level, pos, state, player, InteractionHand.MAIN_HAND) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}

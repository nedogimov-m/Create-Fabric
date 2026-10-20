package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WindmillBearingBlock extends BearingBlock {
    public WindmillBearingBlock(Properties properties) { super(properties); }
    @Override public void setPlacedBy(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof WindmillBearingBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WindmillBearingBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AllBlockEntityTypes.WINDMILL_BEARING, WindmillBearingBlockEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WindmillBearingBlockEntity bearing) {
            if (bearing.isRunning()) bearing.disassemble(); else bearing.requestAssembly();
        }
        return InteractionResult.SUCCESS;
    }
    /** Functional native side configuration until shared source value-settings overlays are ported. */
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!stack.is(AllItems.WRENCH) || player.isShiftKeyDown() || !player.mayBuild() || !level.mayInteract(player, pos) || hit.getDirection().getAxis() == state.getValue(FACING).getAxis()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WindmillBearingBlockEntity bearing) {
            var next = bearing.getMovementDirection() == WindmillBearingBlockEntity.RotationDirection.CLOCKWISE ? WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE : WindmillBearingBlockEntity.RotationDirection.CLOCKWISE;
            bearing.setMovementDirection(next); player.sendOverlayMessage(Component.translatable("create.generic." + next.name().toLowerCase(java.util.Locale.ROOT)));
            level.playSound(null, pos, AllSoundEvents.CONTROLLER_CLICK, SoundSource.BLOCKS, .5f, 1);
        }
        return InteractionResult.SUCCESS;
    }
}

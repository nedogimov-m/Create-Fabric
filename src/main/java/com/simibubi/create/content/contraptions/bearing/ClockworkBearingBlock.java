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

public class ClockworkBearingBlock extends BearingBlock {
    public ClockworkBearingBlock(Properties properties) { super(properties); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(placer instanceof net.minecraft.server.level.ServerPlayer player && !(placer instanceof net.fabricmc.fabric.api.entity.FakePlayer) && com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof ClockworkBearingBlockEntity bearing) bearing.placedBy(player);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ClockworkBearingBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.CLOCKWORK_BEARING, ClockworkBearingBlockEntity::tick); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ClockworkBearingBlockEntity bearing) { if (bearing.isRunning()) bearing.disassemble(); else bearing.requestAssembly(); }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!stack.is(AllItems.WRENCH) || player.isShiftKeyDown() || !player.mayBuild() || !level.mayInteract(player, pos) || hit.getDirection().getAxis() == state.getValue(FACING).getAxis()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ClockworkBearingBlockEntity bearing) {
            var values = ClockworkBearingBlockEntity.ClockHands.values(); var next = values[(bearing.getOperationMode().ordinal() + 1) % values.length]; bearing.setOperationMode(next);
            player.sendOverlayMessage(Component.translatable("create.contraptions.clockwork." + next.name().toLowerCase(java.util.Locale.ROOT))); level.playSound(null, pos, AllSoundEvents.CONTROLLER_CLICK, SoundSource.BLOCKS, .5f, 1);
        }
        return InteractionResult.SUCCESS;
    }
}

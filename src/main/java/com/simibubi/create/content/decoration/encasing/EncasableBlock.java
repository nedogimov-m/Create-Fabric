package com.simibubi.create.content.decoration.encasing;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public interface EncasableBlock {
    default InteractionResult tryEncase(BlockState state, Level level, BlockPos pos, ItemStack heldItem,
                                       Player player, InteractionHand hand, BlockHitResult ray) {
        if (!player.mayBuild() || player.isSpectator() || !level.mayInteract(player, pos))
            return InteractionResult.PASS;
        for (var block : EncasingRegistry.getVariants(state.getBlock())) {
            if (!(block instanceof EncasedBlock encased) || encased.getCasing().asItem() != heldItem.getItem())
                continue;
            if (!level.isClientSide()) {
                encased.handleEncasing(state, level, pos, heldItem, player, hand, ray);
                playEncaseSound(level, pos);
            }
            // Source casing is a reusable tool: neither shrink nor a later refund.
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    default void playEncaseSound(Level level, BlockPos pos) {
        var sound = level.getBlockState(pos).getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * .8f);
    }
}

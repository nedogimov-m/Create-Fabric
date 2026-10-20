package com.simibubi.create.content.kinetics.saw;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The row helper supplies orientation; vanilla placement still validates and consumes the item. */
public final class SawBlockItem extends BlockItem {
    public SawBlockItem(SawBlock block, Item.Properties properties) { super(block, properties); }
    public static final class RowPlacement extends BlockPlaceContext {
        final BlockState state;
        RowPlacement(Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit, BlockState state) { super(player, hand, stack, hit); this.state = state; }
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        if (context instanceof RowPlacement row) return canPlace(context, row.state) ? row.state : null;
        return super.getPlacementState(context);
    }
}

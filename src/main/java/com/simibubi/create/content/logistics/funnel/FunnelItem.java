package com.simibubi.create.content.logistics.funnel;

import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

public final class FunnelItem extends BlockItem {
    public static void register(){
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,hit.getBlockPos())||!(player.getItemInHand(hand).getItem() instanceof FunnelItem))return net.minecraft.world.InteractionResult.PASS;
            return player.getItemInHand(hand).useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
        });
    }
    public FunnelItem(FunnelBlock block,Properties properties){super(block,properties);}
    @Override protected BlockState getPlacementState(BlockPlaceContext context){var state=super.getPlacementState(context);if(state==null||!state.getValue(FunnelBlock.FACING).getAxis().isHorizontal()||!BeltFunnelBlock.isOnValidBelt(state,context.getLevel(),context.getClickedPos()))return state;var block=(FunnelBlock)getBlock();var facing=state.getValue(FunnelBlock.FACING);return block.belt().getStateForPlacement(context).setValue(BeltFunnelBlock.HORIZONTAL_FACING,facing).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.getShapeForPosition(context.getLevel(),context.getClickedPos(),facing,state.getValue(FunnelBlock.EXTRACTING)));}
}

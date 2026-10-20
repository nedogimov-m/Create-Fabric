package com.simibubi.create.content.redstone.link;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class LinkHandler {
    private static boolean registered;
    public static InteractionResult interact(Player player,Level level,InteractionHand hand,BlockHitResult hit){
        if(player.isSpectator()||player.isShiftKeyDown()||!player.mayBuild()||!level.mayInteract(player,hit.getBlockPos())
            ||!player.isWithinBlockInteractionRange(hit.getBlockPos(),1)||!LoadedLevelAccess.isLoaded(level,hit.getBlockPos()))return InteractionResult.PASS;
        var stack=player.getItemInHand(hand);if(stack.is(AllItems.WRENCH)||stack.getItem()instanceof LinkedControllerItem)return InteractionResult.PASS;
        var behaviour=BlockEntityBehaviour.get(level,hit.getBlockPos(),LinkBehaviour.TYPE);if(behaviour==null)return InteractionResult.PASS;
        var state=behaviour.blockEntity.getBlockState();var local=hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        boolean fake=player instanceof net.fabricmc.fabric.api.entity.FakePlayer;
        Boolean selected=null;
        for(boolean first:new boolean[]{false,true})if(RedstoneLinkFrequencySlot.test(state,first,local))selected=first;
        if(fake){var adjusted=local.add(hit.getDirection().getUnitVec3().scale(.25));
            selected=adjusted.distanceToSqr(RedstoneLinkFrequencySlot.offset(state.getValue(RedstoneLinkBlock.FACING),true))
                <adjusted.distanceToSqr(RedstoneLinkFrequencySlot.offset(state.getValue(RedstoneLinkBlock.FACING),false));}
        if(selected==null)return InteractionResult.PASS;
        if(!level.isClientSide())behaviour.setFrequency(selected,stack);
        level.playSound(player,hit.getBlockPos(),SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,.25f,.1f);
        return InteractionResult.SUCCESS;
    }
    public static void register(){if(registered)return;registered=true;com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register(LinkHandler::interact);}
    private LinkHandler(){}
}

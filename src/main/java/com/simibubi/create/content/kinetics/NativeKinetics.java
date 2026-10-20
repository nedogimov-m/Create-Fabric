package com.simibubi.create.content.kinetics;

import com.simibubi.create.content.kinetics.crank.ValveHandleSettings;
import com.simibubi.create.content.kinetics.simpleRelays.encased.*;
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents;
import net.minecraft.world.phys.BlockHitResult;

public final class NativeKinetics {
    public static void register() {
        ValveHandleSettings.register();
        PlayerPickItemEvents.BLOCK.register((player,pos,state,includeData) -> {
            if (!(state.getBlock() instanceof EncasedShaftBlock || state.getBlock() instanceof EncasedCogwheelBlock)) return null;
            if (!(player.pick(player.blockInteractionRange(),0,false) instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) return null;
            return state.getBlock() instanceof EncasedShaftBlock shaft ? shaft.getCloneItemStack(state,hit.getDirection())
                    : ((EncasedCogwheelBlock)state.getBlock()).getCloneItemStack(state,hit.getDirection());
        });
    }
    private NativeKinetics() {}
}

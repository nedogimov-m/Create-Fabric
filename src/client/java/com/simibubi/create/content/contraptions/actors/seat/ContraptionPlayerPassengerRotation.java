package com.simibubi.create.content.contraptions.actors.seat;

import com.simibubi.create.content.contraptions.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Mth;

/** Source default: retain the player's view direction relative to a rotating Y-axis seat. */
public final class ContraptionPlayerPassengerRotation {
    private static int previousId;
    private static float previousYaw;
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(client->{var player=client.player;if(!ContraptionSettings.rotateWhenSeated||player==null||!(player.getVehicle() instanceof AbstractContraptionEntity vehicle)){previousId=0;return;}float yaw=vehicle.getRotationState().yRotation;if(previousId!=vehicle.getId()){previousId=vehicle.getId();previousYaw=yaw;return;}float difference=Mth.wrapDegrees(previousYaw-yaw);previousYaw=yaw;player.setYRot(player.getYRot()+difference);player.setYHeadRot(player.getYRot());});}
    private ContraptionPlayerPassengerRotation(){}
}

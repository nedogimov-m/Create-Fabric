package com.simibubi.create.test.mixin;

import com.simibubi.create.content.contraptions.actors.trainControls.*;
import com.simibubi.create.test.TrainVehiclePacketProbe;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ControlsServerHandler.class,remap=false)
abstract class TrainControlsPacketProbeMixin {
    @Inject(method="receive",at=@At("HEAD"))
    private static void createTest$actualSourceControls(ServerPlayer player,ControlsInputPacket packet,CallbackInfo callback){TrainVehiclePacketProbe.controlsReceived(player,packet);}
}

package com.simibubi.create.test.mixin;

import com.simibubi.create.test.TrainVehiclePacketProbe;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class TrainServerVehiclePacketProbeMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleMoveVehicle",at=@At("HEAD"))
    private void createTest$actualNativeVehiclePacket(ServerboundMoveVehiclePacket packet,CallbackInfo callback){TrainVehiclePacketProbe.vehicleReceived(player);}
}

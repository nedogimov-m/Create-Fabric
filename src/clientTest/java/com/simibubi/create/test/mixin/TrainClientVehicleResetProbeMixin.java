package com.simibubi.create.test.mixin;

import com.simibubi.create.test.TrainVehiclePacketProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class TrainClientVehicleResetProbeMixin {
    @Inject(method="handleMoveVehicle",at=@At("HEAD"))
    private void createTest$actualNativeReset(ClientboundMoveVehiclePacket packet,CallbackInfo callback){TrainVehiclePacketProbe.resetReceived();}
}

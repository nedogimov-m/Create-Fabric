package com.simibubi.create.test.mixin;

import com.simibubi.create.test.TrainVehiclePacketProbe;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class TrainClientPacketProbeMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"))
    private void createTest$actualOutboundPacket(Packet<?> packet,CallbackInfo callback){TrainVehiclePacketProbe.sent(packet);}
}

package com.simibubi.create.test.mixin;

import com.simibubi.create.test.TrainVehiclePacketProbe;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class TrainLocalPlayerPacketProbeMixin {
    @Inject(method="sendChanges",at=@At("HEAD"))
    private void createTest$actualNativeSendTick(CallbackInfo callback){TrainVehiclePacketProbe.tick();}
}

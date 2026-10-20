package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source local carriage interpolation is independent of vanilla passenger coordinate authority. */
@Mixin(LocalPlayer.class)
abstract class TrainVehiclePacketMixin {
    @WrapOperation(method="sendChanges",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;isLocalInstanceAuthoritative()Z"),require=1)
    private boolean create$serverOwnedCarriageMovement(Entity vehicle,Operation<Boolean> original){
        // Real controls send accepted key deltas to the server Train. The source's TRUE
        // local-authority hook must not authorize vanilla client vehicle coordinates.
        return !(vehicle instanceof CarriageContraptionEntity)&&original.call(vehicle);
    }
}

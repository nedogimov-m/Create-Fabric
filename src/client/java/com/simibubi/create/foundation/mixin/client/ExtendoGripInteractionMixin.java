package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.equipment.extendoGrip.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MultiPlayerGameMode.class)
public abstract class ExtendoGripInteractionMixin {
    @Inject(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;ensureHasSentCarriedItem()V",shift=At.Shift.AFTER),cancellable=true)
    private void create$sourceExtendedAttack(Player player,Entity target,CallbackInfo ci){if(!ExtendoGripItem.isHoldingExtendoGrip(player)||player.isWithinAttackRange(player.getMainHandItem(),target.getBoundingBox(),3))return;ClientPlayNetworking.send(new ExtendoGripInteractionPacket(target));player.attack(target);player.resetAttackStrengthTicker();ci.cancel();}
    @Inject(method="interact",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;ensureHasSentCarriedItem()V",shift=At.Shift.AFTER),cancellable=true)
    private void create$sourceExtendedInteract(Player player,Entity target,EntityHitResult hit,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir){if(!ExtendoGripItem.isHoldingExtendoGrip(player)||player.isWithinEntityInteractionRange(target,3))return;var point=hit.getLocation().subtract(target.position());ClientPlayNetworking.send(new ExtendoGripInteractionPacket(target,hand,point));cir.setReturnValue(player.interactOn(target,hand,point));}
}

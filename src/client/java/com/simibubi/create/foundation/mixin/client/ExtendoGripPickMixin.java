package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.equipment.extendoGrip.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Minecraft.class,priority=900)
public abstract class ExtendoGripPickMixin {
    @Inject(method="pick(F)V",at=@At("TAIL")) private void create$sourceGripEntityPick(float partial,CallbackInfo ci){ExtendoGripRenderHandler.pick((Minecraft)(Object)this,partial);}
    @WrapOperation(method="startUseItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean create$gripLocalInteraction(LocalPlayer player,Entity target,double allowance,Operation<Boolean> original){if(original.call(player,target,allowance))return true;double range=player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);return ExtendoGripItem.isHoldingExtendoGrip(player)&&player.distanceToSqr(target)<=range*range;}
}

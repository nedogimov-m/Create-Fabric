package com.simibubi.create.foundation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class ArmorRenderStateMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL")) private void create$extractActualArmor(LivingEntity entity,LivingEntityRenderState state,float partial,CallbackInfo ci){BacktankArmorLayer.extract(entity,state,partial);CardboardArmorHandlerClient.extract(entity,state,partial);}
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"),cancellable=true) private void create$submitCapturedBox(LivingEntityRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo ci){if(CardboardArmorHandlerClient.submit(state,pose,collector))ci.cancel();}
}
